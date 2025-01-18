package org.janelia.alignment.multisem;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the {@link MultiSemUtilities} class.
 *
 * @author Eric Trautman
 */
public class MultiSemUtilitiesTest {

    @Test
    public void testTileIdParsers() {
        final String tileId = "w60_magc0399_scan005_m0013_s001";
        Assert.assertEquals("invalid MFOVForTileId",
                            "0399_m0013", MultiSemUtilities.getMFOVForTileId(tileId));
        Assert.assertEquals("invalid SFOVForTileId",
                            "0399_m0013_s001", MultiSemUtilities.getSFOVForTileId(tileId));
        Assert.assertEquals("invalid SFOVIndexForTileId",
                            "001", MultiSemUtilities.getSFOVIndexForTileId(tileId));
    }

}
