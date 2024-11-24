package org.janelia.render.client;

import org.janelia.render.client.parameter.CommandLineParameters;
import org.junit.Test;

/**
 * Tests the {@link RenderTilesClient} class.
 *
 * @author Eric Trautman
 */
public class RenderTilesClientTest {

    @Test
    public void testParameterParsing() throws Exception {
        CommandLineParameters.parseHelp(new RenderTilesClient.Parameters());
    }

    // --------------------------------------------------------------
    // The following methods support ad-hoc interactive testing with external render web services.
    // Consequently, they aren't included in the unit test suite.

    public static void main(final String[] args) {
        try {
            final String[] testArgs = {
                    "--baseDataUrl", "http://em-services-1.int.janelia.org:8080/render-ws/v1",
                    "--owner", "fibsem",
                    "--project", "jrc_mpi_psc120_1a1",
                    "--stack", "v2_acquire_align_16bit",
                    "--rootDirectory", "/Users/trautmane/Desktop/tiles_destreak",
                    "--runTimestamp", "20241123_160000",
                    "--scale", "1.0",
                    "--format", "png",
                    "--excludeMask",
                    "--excludeAllTransforms",
                    "--filterListName", "jrc_mpi_psc120_1a1-destreak-16bit",
//                    "--hackStack", "v3_acquire_align_16bit_destreak_test",
                    "--renderType", "SIXTEEN_BIT",
//                    "--completeHackStack",
                    "--z", "78.0"
            };

            RenderTilesClient.main(testArgs);
            
        } catch (final Throwable t) {
            //noinspection CallToPrintStackTrace
            t.printStackTrace();
        }
    }


}
