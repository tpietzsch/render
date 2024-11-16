package org.janelia.render.client.multisem;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the {@link Utilities} class.
 *
 * @author Eric Trautman
 */
public class UtilitiesTest {

    @Test
    public void testTileIdParsers() {
        final String tileId = "w60_magc0399_scan005_m0013_s001";
        Assert.assertEquals("invalid MFOVForTileId",
                            "0399_m0013", Utilities.getMFOVForTileId(tileId));
        Assert.assertEquals("invalid SFOVForTileId",
                            "0399_m0013_s001", Utilities.getSFOVForTileId(tileId));
        Assert.assertEquals("invalid SFOVIndexForTileId",
                            "001", Utilities.getSFOVIndexForTileId(tileId));
    }

}
