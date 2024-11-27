package org.janelia.render.client.spark.intensityadjust;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
import mpicbg.models.CoordinateTransform;
import mpicbg.models.CoordinateTransformList;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;
import org.janelia.alignment.filter.FilterSpec;
import org.janelia.alignment.filter.ShadingCorrectionFilter;
import org.janelia.alignment.spec.ResolvedTileSpecCollection;
import org.janelia.alignment.spec.SectionData;
import org.janelia.alignment.spec.TileSpec;
import org.janelia.alignment.spec.stack.StackMetaData;
import org.janelia.render.client.ClientRunner;
import org.janelia.render.client.RenderDataClient;
import org.janelia.alignment.filter.emshading.ShadingModel;
import org.janelia.render.client.emshading.ShadingCorrection_Plugin;
import org.janelia.render.client.parameter.CommandLineParameters;
import org.janelia.render.client.parameter.RenderWebServiceParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.janelia.render.client.spark.intensityadjust.ShadingCorrectionClient.ShadingModelProvider;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Spark client for shading correction by a layer-wise quadratic or fourth order model.
 * The client takes as input a render stack and a parameter file and creates a new stack with appropriate shading
 * correction filters. The shading is assumed to be an artifact in the imaging space, so it is applied by finding
 * the position of a tile within the z-layer.
 * </p>
 * The parameter file is a json file containing a list of z values and corresponding models. The model for each z value
 * is valid for all z layers starting at the given z value until the next z value in the list.
 * Models are specified by an identifier ("quadratic" or "fourthOrder") and a list of coefficients (6 or 9,
 * respectively). Coefficients can be found interactively using {@link ShadingCorrection_Plugin}.
 * </p>
 * In particular, the parameter file should have the following format. There is one root array, whose elements have
 * exactly keys: "fromZ", "modelType", and "coefficients"s, e.g.:
 * <pre>
 * [ {
 *     "fromZ": 1,
 *     "modelType": "quadratic",
 *     "coefficients": [ 1.0, 1.0, 1.0, 1.0, 1.0, 1.0 ]
 *   }, {
 *     "fromZ": 3,
 *     "modelType": "fourthOrder",
 *     "coefficients": [ 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0 ]
 *   }
 * ]
 * </pre>
 */
public class ShadingCorrectionTileClient implements Serializable {

    public static class Parameters extends CommandLineParameters {
        @ParametersDelegate
        public RenderWebServiceParameters webservice = new RenderWebServiceParameters();

        @Parameter(names = "--stack",
                description = "Name of the source stack in the render project",
                required = true)
        public String stack;

        @Parameter(names = "--targetStack",
                description = "Name of the target stack in the render project",
                required = true)
        public String targetStack;

        @Parameter(names = "--parameterFile",
                description = "Path to the parameter json file containing a list of z values and their corresponding models. See class description for details.",
                required = true)
        public String parameterFile;
    }

    private static final Logger LOG = LoggerFactory.getLogger(ShadingCorrectionTileClient.class);

    private final Parameters parameters;

    public static void main(final String[] args) {
        final ClientRunner clientRunner = new ClientRunner(args) {
            @Override
            public void runClient(final String[] args) throws Exception {
                final Parameters parameters = new Parameters();
                parameters.parse(args);
                final ShadingCorrectionTileClient client = new ShadingCorrectionTileClient(parameters);
                client.run();
            }
        };
        clientRunner.run();
    }


    public ShadingCorrectionTileClient(final Parameters parameters) {
        LOG.info("init: parameters={}", parameters);
        this.parameters = parameters;
    }

    public void run() throws IOException {
        final SparkConf conf = new SparkConf().setAppName("ShadingCorrectionClient");
        try (final JavaSparkContext sparkContext = new JavaSparkContext(conf)) {
            final String sparkAppId = sparkContext.getConf().getAppId();
            LOG.info("run: appId is {}", sparkAppId);
            runWithContext(sparkContext);
        }
    }

    public void runWithContext(final JavaSparkContext sparkContext) throws IOException {

        LOG.info("runWithContext: entry");

        final ShadingModelProvider modelProvider = ShadingModelProvider.fromJsonFile(parameters.parameterFile);

        final RenderDataClient renderClient = parameters.webservice.getDataClient();
        setUpTargetStack(renderClient);

        final List<Double> zValues = renderClient.getStackZValues(parameters.stack);
        final List<SectionData> stackSectionData = renderClient.getStackSectionData(parameters.stack, null, null);

        // parallelize computation over z-layers (broadcasting some data that is needed for all tile specs)
        final Broadcast<ShadingModelProvider> modelProviderBroadcast = sparkContext.broadcast(modelProvider);
        final Broadcast<Parameters> parametersBroadcast = sparkContext.broadcast(parameters);
        final Broadcast<List<SectionData>> sectionDataBroadcast = sparkContext.broadcast(stackSectionData);
        final Broadcast<List<Double>> zValuesBroadcast = sparkContext.broadcast(zValues);

        final List<Integer> zIndices = IntStream.range(0, zValues.size()).boxed().collect(Collectors.toList());
        sparkContext.parallelize(zIndices)
                .foreach(zIndex -> {
                    final Double z = zValuesBroadcast.getValue().get(zIndex);
                    final ShadingModel layerModel = modelProviderBroadcast.getValue().getModel(z.intValue());
                    final SectionData layerSectionData = sectionDataBroadcast.getValue().get(zIndex);
                    addShadingCorrectionToLayer(z, layerModel, parametersBroadcast.value(), layerSectionData);
                });

        completeTargetStack(renderClient);

        LOG.info("runWithContext: exit");
    }

    private void setUpTargetStack(final RenderDataClient dataClient) throws IOException {
        final StackMetaData sourceStackMetaData = dataClient.getStackMetaData(parameters.stack);
        dataClient.setupDerivedStack(sourceStackMetaData, parameters.targetStack);
        LOG.info("setUpTargetStack: setup stack {}", parameters.targetStack);
    }

    private void completeTargetStack(final RenderDataClient dataClient) throws IOException {
        dataClient.setStackState(parameters.targetStack, StackMetaData.StackState.COMPLETE);
        LOG.info("completeTargetStack: setup stack {}", parameters.targetStack);
    }

    private void addShadingCorrectionToLayer(
            final Double z,
            final ShadingModel layerModel,
            final Parameters parameters,
            final SectionData layerSectionData
    ) throws IOException {

        final RenderDataClient renderClient = parameters.webservice.getDataClient();
        final ResolvedTileSpecCollection rtsc = renderClient.getResolvedTiles(parameters.stack, z);

        if (layerModel == null) {
            LOG.warn("No model found for z={}", z);
        } else {
            final Collection<TileSpec> tileSpecs = rtsc.getTileSpecs();
            LOG.info("Adding shading correction for {} tile specs, z={}", tileSpecs.size(), z);

            for (final TileSpec tileSpec : tileSpecs) {
                addShadingCorrectionToTileSpec(tileSpec, layerModel, layerSectionData);
            }
        }

        renderClient.saveResolvedTiles(rtsc, parameters.targetStack, z);
    }

    private void addShadingCorrectionToTileSpec(
        final TileSpec tileSpec,
        final ShadingModel layerModel,
        final SectionData sectionData
    ) {
		// get uniform grid of points in tile
        final int nSamples = (int) Math.ceil(Math.sqrt(layerModel.getMinNumMatches()));
        final List<double[]> points = uniformGrid(nSamples);

        final double centerX = sectionData.getMinX() + 0.5 * sectionData.getWidth();
        final double centerY = sectionData.getMinY() + 0.5 * sectionData.getHeight();

        final CoordinateTransformList<CoordinateTransform> transforms = tileSpec.getTransformList();
        final List<PointMatch> matches = new ArrayList<>();

        final double[] transformedPoint = new double[2];
        for (final double[] tilePointNormalized : points) {
            // transform normalized tile point (in [-1, 1] x [-1, 1]) to tile coordinates
            transformedPoint[0] = (tilePointNormalized[0] + 1) * tileSpec.getWidth() / 2.0;
            transformedPoint[1] = (tilePointNormalized[1] + 1) * tileSpec.getHeight() / 2.0;

            // transform tile point to global coordinate system
            transforms.applyInPlace(transformedPoint);

            // transform global coordinate to [-1, 1] x [-1, 1] wrt to the layer bounds
            transformedPoint[0] = (transformedPoint[0] - centerX) / (sectionData.getWidth() / 2.0);
            transformedPoint[1] = (transformedPoint[1] - centerY) / (sectionData.getHeight() / 2.0);

            // scale coordinates to [-1, 1] to evaluate the global model
            layerModel.applyInPlace(transformedPoint);
            final double[] value = new double[] {transformedPoint[0], 0};
            matches.add(new PointMatch(new Point(tilePointNormalized), new Point(value)));
        }

        final ShadingModel tileModel = layerModel.copy();
		try {
			tileModel.fit(matches);
		} catch (final NotEnoughDataPointsException | IllDefinedDataPointsException e) {
			throw new RuntimeException(e);
		}

        // convert to filter and add to tile spec
        final ShadingCorrectionFilter filter = new ShadingCorrectionFilter(tileModel);
        tileSpec.setFilterSpec(FilterSpec.forFilter(filter));
    }

    final List<double[]> uniformGrid(final int n) {
        final List<double[]> points = new ArrayList<>();

        final double increment = 2.0 / (n - 1);
        final double eps = 1e-8;

        for (double x = -1; x < 1 + eps; x += increment) {
            for (double y = -1; y < 1 + eps; y += increment) {
                final double[] point = new double[] {x, y};
                points.add(point);
            }
        }

        return points;
    }
}
