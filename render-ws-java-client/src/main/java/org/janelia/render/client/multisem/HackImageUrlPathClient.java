package org.janelia.render.client.multisem;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;

import java.io.File;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.janelia.alignment.ImageAndMask;
import org.janelia.alignment.spec.ChannelSpec;
import org.janelia.alignment.spec.ResolvedTileSpecCollection;
import org.janelia.alignment.spec.TileSpec;
import org.janelia.alignment.spec.stack.StackMetaData;
import org.janelia.render.client.ClientRunner;
import org.janelia.render.client.RenderDataClient;
import org.janelia.render.client.parameter.CommandLineParameters;
import org.janelia.render.client.parameter.RenderWebServiceParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Java client to copy a stack's tiles, changing the image URL paths for all tiles in the resulting stack.
 * Currently implemented URL transformations:
 * - HayworthContrastPathTransformation: changes the image URL to point to images that have been contrast-adjusted using
 *   the Hayworth pipeline.
 * - BasicBackgroundCorrectionPathTransformation: changes the image URL to point to images that have been
 *   background-corrected using the BaSiC background correction method.
 *
 * @author Eric Trautman
 */
public class HackImageUrlPathClient {

    @SuppressWarnings("ALL")
    public static class Parameters extends CommandLineParameters {

        @ParametersDelegate
        public RenderWebServiceParameters renderWeb = new RenderWebServiceParameters();

        @Parameter(
                names = "--stack",
                description = "Name of stack from which tile specs should be read",
                required = true)
        private String stack;

        @Parameter(
                names = "--targetStack",
                description = "Name of stack to which updated tile specs should be written",
                required = true)
        private String targetStack;
    }

    private static final Logger LOG = LoggerFactory.getLogger(HackImageUrlPathClient.class);

    public static void main(final String[] args) {

        final ClientRunner clientRunner = new ClientRunner(args) {
            @Override
            public void runClient(final String[] args)
                    throws Exception {

                final Parameters parameters = new Parameters();
                parameters.parse(args);

                LOG.info("runClient: entry, parameters={}", parameters);

                final UnaryOperator<String> pathTransformation = new BasicBackgroundCorrectionPathTransformation();
                final HackImageUrlPathClient client = new HackImageUrlPathClient(parameters, pathTransformation);
                client.fixStackData();
            }
        };
        clientRunner.run();
    }

    private final Parameters parameters;
    private final RenderDataClient renderDataClient;
    private final UnaryOperator<String> pathTransformation;

    private HackImageUrlPathClient(final Parameters parameters, final UnaryOperator<String> pathTransformation) {
        this.parameters = parameters;
        this.renderDataClient = parameters.renderWeb.getDataClient();
        this.pathTransformation = pathTransformation;
    }

    private void fixStackData() throws Exception {
        final StackMetaData fromStackMetaData = renderDataClient.getStackMetaData(parameters.stack);

        // remove mipmap path builder if it is defined since we did not generate mipmaps for the hacked source images
        fromStackMetaData.setCurrentMipmapPathBuilder(null);

        renderDataClient.setupDerivedStack(fromStackMetaData, parameters.targetStack);

        for (final Double z : renderDataClient.getStackZValues(parameters.stack)) {
            final ResolvedTileSpecCollection resolvedTiles = renderDataClient.getResolvedTiles(parameters.stack, z);
            for (final TileSpec tileSpec : resolvedTiles.getTileSpecs()) {
                fixTileSpec(tileSpec);
            }
            renderDataClient.saveResolvedTiles(resolvedTiles, parameters.targetStack, z);
        }

        renderDataClient.setStackState(parameters.targetStack, StackMetaData.StackState.COMPLETE);
    }

    private void fixTileSpec(final TileSpec tileSpec) {
        final Integer zeroLevelKey = 0;

        for (final ChannelSpec channelSpec : tileSpec.getAllChannels()) {
            final Map.Entry<Integer, ImageAndMask> entry = channelSpec.getFirstMipmapEntry();
			if ((entry == null) || !zeroLevelKey.equals(entry.getKey())) {
                continue;
			}

			final ImageAndMask sourceImageAndMask = entry.getValue();

            final String imageUrl = sourceImageAndMask.getImageUrl();
            final String transformedUrl = pathTransformation.apply(imageUrl);

            if (transformedUrl == null) {
                throw new IllegalArgumentException("could not transform image URL: " + imageUrl);
            }

            final File hackFile = new File(transformedUrl);
            if (! hackFile.exists()) {
                throw new IllegalArgumentException("target file does not exist: " + hackFile);
            }

            final ImageAndMask hackedImageAndMask = sourceImageAndMask.copyWithDerivedUrls("file:" + hackFile.getAbsolutePath(),
                                                                                           sourceImageAndMask.getMaskUrl());
            channelSpec.putMipmap(zeroLevelKey, hackedImageAndMask);
        }
    }


    private static class HayworthContrastPathTransformation implements UnaryOperator<String> {
        private final Pattern PATH_PATTERN = Pattern.compile("^file:/nrs.*/(scan_\\d\\d\\d/)wafer.*/(\\d\\d\\d_/.*png)$");

        // original: file:/nrs/hess/data/hess_wafer_53/raw/imaging/msem/scan_001/wafer_53_scan_001_20220427_23-16-30/402_/000005/402_000005_001_2022-04-28T1457426331720.png
        // target:   file:/nrs/hess/data/hess_wafer_53/msem_with_hayworth_contrast/scan_001/402_/000005/402_000005_001_2022-04-28T1457426331720.png
        @Override
        public String apply(final String path) {
            final Matcher matcher = PATH_PATTERN.matcher(path);
            if (matcher.matches()) {
                return "/nrs/hess/data/hess_wafer_53/msem_with_hayworth_contrast/" + matcher.group(1) + matcher.group(2);
            } else {
                return null;
            }
        }
    }

    private static class BasicBackgroundCorrectionPathTransformation implements UnaryOperator<String> {

        // original: file:/nrs/hess/ibeammsem/system_02/wafers/wafer_60/acquisition/scans/scan_004/slabs/slab_0399/mfovs/mfov_0000/sfov_001.png
        // target:   file:/nrs/hess/ibeammsem/system_02/wafers/wafer_60/acquisition/background_corrected/scans/scan_004/slabs/slab_0399/mfovs/mfov_0000/sfov_001.png
        @Override
        public String apply(final String path) {
            return path.substring(5, 62) + "/background_corrected" + path.substring(62);
        }
    }

}
