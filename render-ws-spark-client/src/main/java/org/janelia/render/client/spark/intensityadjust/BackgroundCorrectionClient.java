package org.janelia.render.client.spark.intensityadjust;

import com.beust.jcommander.Parameter;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.janelia.alignment.util.Grid;
import org.janelia.render.client.ClientRunner;
import org.janelia.render.client.embackground.BackgroundModel;
import org.janelia.render.client.embackground.FourthOrderBackground;
import org.janelia.render.client.embackground.QuadraticBackground;
import org.janelia.render.client.parameter.CommandLineParameters;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Spark client for background correction by a layer-wise quadratic or fourth order model.
 * The client takes as input an N5 container with a 3D dataset and a parameter file, and writes the corrected data to a
 * new dataset in a given container.
 * </p>
 * The parameter file is a json file containing a list of z values and corresponding models. The model for each z value
 * is valid for all z layers starting at the given z value until the next z value in the list.
 * Models are specified by an identifier ("quadratic" or "fourthOrder") and a list of coefficients (6 or 9,
 * respectively). Coefficients can be found interactively using {@link org.janelia.render.client.embackground.BG_Plugin}.
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
 *     "coefficients": [ 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0
 *   }
 * ]
 * </pre>
 */
public class BackgroundCorrectionClient implements Serializable {

    public static class Parameters extends CommandLineParameters {
        @Parameter(names = "--n5In",
                description = "Path to the input N5 container",
                required = true)
        public String n5In;

        @Parameter(names = "--datasetIn",
                description = "Name of the input dataset",
                required = true)
        public String datasetIn;

        @Parameter(names = "--n5Out",
                description = "Path to the output N5 container",
                required = true)
        public String n5Out;

        @Parameter(names = "--datasetOut",
                description = "Name of the output dataset",
                required = true)
        public String datasetOut;

        @Parameter(names = "--parameterFile",
                description = "Path to the parameter json file containing a list of z values and their corresponding models. See class description for details.",
                required = true)
        public String parameterFile;
    }

    public static void main(final String[] args) {
        final ClientRunner clientRunner = new ClientRunner(args) {
            @Override
            public void runClient(final String[] args) throws Exception {
                final Parameters parameters = new Parameters();
                parameters.parse(args);
                final BackgroundCorrectionClient client = new BackgroundCorrectionClient(parameters);
                client.run();
            }
        };
        clientRunner.run();
    }

    private final Parameters parameters;

    public BackgroundCorrectionClient(final Parameters parameters) {
        LOG.info("init: parameters={}", parameters);
        this.parameters = parameters;
    }

    public void run() throws IOException {
        final SparkConf conf = new SparkConf().setAppName("BackgroundCorrectionClient");
        try (final JavaSparkContext sparkContext = new JavaSparkContext(conf)) {
            final String sparkAppId = sparkContext.getConf().getAppId();
            LOG.info("run: appId is {}", sparkAppId);
            runWithContext(sparkContext);
        }
    }

    public void runWithContext(final JavaSparkContext sparkContext) throws IOException {

        LOG.info("runWithContext: entry");

        // read parameters
        final BackgroundModelProvider modelProvider = BackgroundModelProvider.fromJsonFile(parameters.parameterFile);
        System.exit(0);

        // set up input and output N5 datasets
        final DatasetAttributes inputAttributes;
        try (final N5Reader in = new N5FSReader(parameters.n5In)) {
            inputAttributes = in.getDatasetAttributes(parameters.datasetIn);
            final Map<String, Class<?>> otherAttributes = in.listAttributes(parameters.datasetIn);

            try (final N5Writer out = new N5FSWriter(parameters.n5Out)) {
                if (out.exists(parameters.datasetOut)) {
                    throw new IllegalArgumentException("Output dataset already exists: " + parameters.datasetOut);
                }

                out.createDataset(parameters.datasetOut, inputAttributes);
                out.setAttribute(parameters.datasetOut, "BackgroundCorrectionClientParameters", parameters);
                otherAttributes.forEach((key, clazz) -> {
                    final Object attribute = in.getAttribute(parameters.datasetIn, key, clazz);
                    out.setAttribute(parameters.datasetOut, key, attribute);
                });
            }
        }

        // parallelize computation over blocks of the input/output dataset
        final List<Grid.Block> blocks = Grid.create(inputAttributes.getDimensions(), inputAttributes.getBlockSize());
        final JavaRDD<Grid.Block> blockRDD = sparkContext.parallelize(blocks, blocks.size());
//        blockRDD.foreach(block -> processSingleBlock(parameters, models));

        LOG.info("runWithContext: exit");
    }

    private static void processSingleBlock(final Parameters parameters, final Map<Integer, BackgroundModel<?>> models) {

    }


    private static class BackgroundModelProvider implements Serializable {
        private final List<ModelSpec> sortedModelSpecs;

        private BackgroundModelProvider(final List<ModelSpec> modelSpecs) {
            this.sortedModelSpecs = modelSpecs;
            this.sortedModelSpecs.sort(Collections.reverseOrder(Comparator.comparingInt(ModelSpec::getZ)));
        }

        public BackgroundModel<?> getModel(final int z) {
            for (final ModelSpec modelSpec : sortedModelSpecs) {
                if (z >= modelSpec.getZ()) {
                    return modelSpec.getModel();
                }
            }
            return null;
        }

        public static BackgroundModelProvider fromJsonFile(final String fileName) throws IOException {
            LOG.info("Reading model specs from file: {}", fileName);
            try (final FileReader reader = new FileReader(fileName)) {
                return fromJson(JsonParser.parseReader(reader));
            }
        }

        public static BackgroundModelProvider fromJson(final JsonElement jsonData) throws IOException {

            final List<ModelSpec> modelSpecs = new ArrayList<>();
            Collections.addAll(modelSpecs, new Gson().fromJson(jsonData, ModelSpec[].class));

            // validation of json data
            for (final ModelSpec modelSpec : modelSpecs) {
                LOG.info("Found model spec: {}", modelSpec);
                final BackgroundModel<?> ignored = modelSpec.getModel();
            }

            return new BackgroundModelProvider(modelSpecs);
        }


        private static class ModelSpec implements Serializable {
            private int fromZ;
            private String modelType;
            private double[] coefficients;

            // no explicit constructor; meant to be deserialized from json

            public int getZ() {
                return fromZ;
            }

            public BackgroundModel<?> getModel() {
                if (modelType.equals("quadratic")) {
                    return new QuadraticBackground(coefficients);
                } else if (modelType.equals("fourthOrder")) {
                    return new FourthOrderBackground(coefficients);
                } else {
                    throw new IllegalArgumentException("Unknown model type: " + modelType);
                }
            }

            public String toString() {
                return "ModelSpec{fromZ=" + fromZ + ", modelType=" + modelType + ", coefficients=" + Arrays.toString(coefficients) + "}";
            }
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(BackgroundCorrectionClient.class);
}
