package org.janelia.render.client;

import org.janelia.render.client.parameter.CommandLineParameters;
import org.junit.Test;

/**
 * Tests the {@link CopyMatchClient} class.
 *
 * @author Eric Trautman
 */
public class CopyMatchClientTest {

    @Test
    public void testParameterParsing() throws Exception {
        CommandLineParameters.parseHelp(new CopyMatchClient.Parameters());
    }

}
