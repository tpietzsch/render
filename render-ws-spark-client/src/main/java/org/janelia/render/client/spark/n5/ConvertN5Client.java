package org.janelia.render.client.spark.n5;

import com.beust.jcommander.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.janelia.render.client.ClientRunner;
import org.janelia.render.client.parameter.CommandLineParameters;
import org.janelia.render.client.spark.LogUtilities;
import org.janelia.saalfeldlab.n5.spark.N5ConvertSpark;
import org.janelia.saalfeldlab.n5.spark.util.CmdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convert n5 to another block size.
 */
public class ConvertN5Client {

    @SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal"})
    public static class Parameters
            extends CommandLineParameters {

        @Parameter(
                names = "--inputN5Path",
                description = "Path to the input N5 container, e.g. /nrs/fibsem/jrc_mpi_psc120_1a2.n5",
                required = true)
        public String inputN5String;

        @Parameter(
                names = "--inputDatasetPath",
                description = "Path to the input dataset within the N5 container, e.g. /test/v1_acquire_align",
                required = true)
        public String inputDatasetString;

        public String removeLeadingSlash(final String pathString) {
            return pathString.startsWith("/") || pathString.startsWith("\\") ? pathString.substring(1) : pathString;
        }

        public Path getFullInputPath() {
            return Paths.get(inputN5String).resolve(removeLeadingSlash(inputDatasetString));
        }

        @Parameter(
                names = "--outputDatasetPath",
                description = "Path to the output dataset within the N5 container, e.g. /test/v1_acquire_align_big",
                required = true)
        public String outputDatasetString;

        public Path getFullOutputPath() {
            return Paths.get(inputN5String).resolve(removeLeadingSlash(outputDatasetString));
        }

        @Parameter(
                names = "--blockSize",
                description = "Block size for the output dataset, e.g. 256,256,256",
                required = true)
        public String blockSizeString;

        public int[] getBlockSize() {
            return CmdUtils.parseIntArray(blockSizeString);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(ConvertN5Client.class);

    public static void main(final String[] args)
            throws IOException {
        final ClientRunner clientRunner = new ClientRunner(args) {
            @Override
            public void runClient(final String[] args)
                    throws Exception {

                final ConvertN5Client.Parameters parameters = new ConvertN5Client.Parameters();
                parameters.parse(args);

                LOG.info("runClient: entry, parameters={}", parameters);

                final ConvertN5Client client = new ConvertN5Client(parameters);
                client.run();
            }
        };
        clientRunner.run();
    }

    private final Parameters parameters;

    public ConvertN5Client(final Parameters parameters) {
        this.parameters = parameters;
    }

    public Parameters getParameters() {
        return parameters;
    }

    public void run()
            throws IOException, IllegalArgumentException {

        final SparkConf conf = new SparkConf().setAppName("ConvertN5Client");
        final JavaSparkContext sparkContext = new JavaSparkContext(conf);

        final String sparkAppId = sparkContext.getConf().getAppId();
        final String executorsJson = LogUtilities.getExecutorsApiJson(sparkAppId);

        LOG.info("run: appId is {}, executors data is {}", sparkAppId, executorsJson);

        final Path fullInputPath = parameters.getFullInputPath();
        if (! fullInputPath.toFile().exists()) {
            throw new IllegalArgumentException("input dataset does not exist: " + fullInputPath);
        }

        final Path fullOutputPath = parameters.getFullOutputPath();
        if (fullOutputPath.toFile().exists()) {
            throw new IllegalArgumentException("output dataset already exists: " + fullOutputPath);
        }

        final Path inputDatasetPath = Paths.get(parameters.inputDatasetString);
        final Path outputDatasetPath = Paths.get(parameters.outputDatasetString);
        final List<String> inputDatasetStrings = new ArrayList<>();
        final List<String> outputDatasetStrings = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            final String sName = "s" + i;
            final File sDirectory = new File(fullInputPath.toString(), sName);
            if (sDirectory.exists()) {
                inputDatasetStrings.add(inputDatasetPath.resolve(sName).toString());
                outputDatasetStrings.add(outputDatasetPath.resolve(sName).toString());
            } else {
                break;
            }
        }

        if (inputDatasetStrings.isEmpty()) {
            inputDatasetStrings.add(parameters.inputDatasetString);
            outputDatasetStrings.add(parameters.outputDatasetString);
        }

        LOG.info("run: converting {} datasets in {}", inputDatasetStrings.size(), fullInputPath);

        for (int i = 0; i < inputDatasetStrings.size(); i++) {
            N5ConvertSpark.convert(sparkContext,
                                   new Util.N5PathSupplier(parameters.inputN5String),
                                   inputDatasetStrings.get(i),
                                   new Util.N5PathSupplier(parameters.inputN5String),
                                   outputDatasetStrings.get(i),
                                   Optional.of(parameters.getBlockSize()),
                                   Optional.empty(),
                                   Optional.empty(),
                                   Optional.empty(),
                                   false
            );
        }

        sparkContext.close();
    }

}
