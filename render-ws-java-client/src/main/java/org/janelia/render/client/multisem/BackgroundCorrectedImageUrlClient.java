package org.janelia.render.client.multisem;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
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

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java client to copy a stack's tiles, changing the image URL paths for all tiles in the resulting stack.
 * In this case, the image URLs are changed to point to images that have been contrast-adjusted using the Hayworth
 * pipeline.
 */
public class BackgroundCorrectedImageUrlClient {

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

    public static void main(final String[] args) {

        final ClientRunner clientRunner = new ClientRunner(args) {
            @Override
            public void runClient(final String[] args)
                    throws Exception {

                final Parameters parameters = new Parameters();
                parameters.parse(args);

                LOG.info("runClient: entry, parameters={}", parameters);

                final BackgroundCorrectedImageUrlClient client = new BackgroundCorrectedImageUrlClient(parameters);
                client.fixStackData();
            }
        };
        clientRunner.run();
    }

    private final Parameters parameters;

    private final RenderDataClient renderDataClient;

    private BackgroundCorrectedImageUrlClient(final Parameters parameters) {
        this.parameters = parameters;
        this.renderDataClient = parameters.renderWeb.getDataClient();
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

            if ((entry != null) && zeroLevelKey.equals(entry.getKey())) {

                final ImageAndMask sourceImageAndMask = entry.getValue();

                // file:/nrs/hess/ibeammsem/system_02/wafers/wafer_60/acquisition/background_corrected/scans/scan_004/slabs/slab_0399/mfovs/mfov_0000/sfov_001.png
                final String imageUrl = sourceImageAndMask.getImageUrl();
                final String newUrl = imageUrl.substring(5, 62) + "/background_corrected" + imageUrl.substring(62);

                final File newFile = new File(newUrl);
                if (! newFile.exists()) {
                    throw new IllegalArgumentException("file does not exist: " + newFile);
                }

                final ImageAndMask newImageAndMask =
                        sourceImageAndMask.copyWithDerivedUrls("file:" + newFile.getAbsolutePath(),
                                                               sourceImageAndMask.getMaskUrl());

                channelSpec.putMipmap(zeroLevelKey, newImageAndMask);
            }
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(BackgroundCorrectedImageUrlClient.class);
}
