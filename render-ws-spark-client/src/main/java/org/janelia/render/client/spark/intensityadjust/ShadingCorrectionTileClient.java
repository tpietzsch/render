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
import org.janelia.render.client.spark.intensityadjust.ShadingCorrectionClient.BackgroundModelProvider;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        final BackgroundModelProvider modelProvider = BackgroundModelProvider.fromJsonFile(parameters.parameterFile);

        final RenderDataClient renderClient = parameters.webservice.getDataClient();
        setUpTargetStack(renderClient);

        final List<Double> zValues = renderClient.getStackZValues(parameters.stack);
        final double minZ = zValues.get(0);
        final double maxZ = zValues.get(zValues.size() - 1);
        final ResolvedTileSpecCollection rtsc = renderClient.getResolvedTilesForZRange(parameters.stack, minZ, maxZ);

        final List<SectionData> sectionData = renderClient.getStackSectionData(parameters.stack, minZ, maxZ);
        final Map<Integer, SectionData> sectionDataMap = new HashMap<>();
        sectionData.forEach(section -> sectionDataMap.put(section.getZ().intValue(), section));

        // parallelize computation over tile specs (broadcasting some data that is needed for all tile specs)
        final Broadcast<BackgroundModelProvider> modelProviderBroadcast = sparkContext.broadcast(modelProvider);
        final Broadcast<Map<Integer, SectionData>> sectionDataBroadcast = sparkContext.broadcast(sectionDataMap);

		final List<TileSpec> tileSpecs = new ArrayList<>(rtsc.getTileSpecs());
        final List<TileSpec> enrichedTileSpecs = sparkContext.parallelize(tileSpecs)
                .map(tileSpec -> addBackgroundCorrection(tileSpec, modelProviderBroadcast.value(), sectionDataBroadcast.value()))
                .collect();

        final ResolvedTileSpecCollection enrichedRtsc = new ResolvedTileSpecCollection(rtsc.getTransformSpecs(), enrichedTileSpecs);
        renderClient.saveResolvedTiles(enrichedRtsc, parameters.targetStack, null);

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

    private TileSpec addBackgroundCorrection(
            final TileSpec tileSpec,
            final BackgroundModelProvider modelProvider,
            final Map<Integer, SectionData> sectionData
    ) {
        final int z = tileSpec.getZ().intValue();
        final ShadingModel layerModel = modelProvider.getModel(z);
        if (layerModel == null) {
            LOG.warn("No model found for z={}", z);
            return tileSpec;
        }

        // get uniform grid of points in tile
        final int nSamples = (int) Math.ceil(Math.sqrt(layerModel.getMinNumMatches()));
        final List<double[]> points = gridOnTile(tileSpec, nSamples);

        final SectionData section = sectionData.get(z);
        final double scaleX = section.getMinX() + 0.5 * section.getWidth();
        final double scaleY = section.getMinY() + 0.5 * section.getHeight();

        final CoordinateTransformList<CoordinateTransform> transforms = tileSpec.getTransformList();
        final List<PointMatch> matches = new ArrayList<>();

        for (final double[] tilePoint : points) {
            // transform point to global space and evaluate shading model
            final double[] globalPoint = transforms.apply(tilePoint);
            final double[] value = layerModel.apply(globalPoint);

            // scale coordinates to [-1, 1] to fit a local model on the tile
            tilePoint[0] = ShadingModel.scaleCoordinate(tilePoint[0], scaleX);
            tilePoint[1] = ShadingModel.scaleCoordinate(tilePoint[1], scaleY);
            matches.add(new PointMatch(new Point(tilePoint), new Point(value)));
        }

		try {
			layerModel.fit(matches);
		} catch (final NotEnoughDataPointsException | IllDefinedDataPointsException e) {
			throw new RuntimeException(e);
		}

        // convert to filter and add to tile spec
        final ShadingCorrectionFilter filter = new ShadingCorrectionFilter(layerModel);
        tileSpec.setFilterSpec(FilterSpec.forFilter(filter));

		return tileSpec;
    }

    final List<double[]> gridOnTile(final TileSpec tileSpec, final int n) {
        final List<double[]> points = new ArrayList<>();

        final double incrementX = (double) tileSpec.getWidth() / (n - 1);
        final double incrementY = (double) tileSpec.getHeight() / (n - 1);

        for (double x = tileSpec.getMinX(); x < tileSpec.getMaxX(); x += incrementX) {
            for (double y = tileSpec.getMinY(); y < tileSpec.getMaxY(); y += incrementY) {
                final double[] point = new double[] {x, y};
                points.add(point);
            }
        }

        return points;
    }
}
