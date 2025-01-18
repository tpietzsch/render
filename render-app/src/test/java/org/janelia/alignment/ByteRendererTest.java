package org.janelia.alignment;

import ij.ImagePlus;
import ij.process.ImageProcessor;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the {@link ByteRenderer} class.
 */
public class ByteRendererTest {

    private File outputFile;

    @Before
    public void setup() throws Exception {
        final SimpleDateFormat TIMESTAMP = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        final String timestamp = TIMESTAMP.format(new Date());
        outputFile = new File("test-render-" + timestamp +".jpg").getCanonicalFile();
    }

    @After
    public void tearDown() {
        ArgbRendererTest.deleteTestFile(outputFile);
    }

    @Test
    public void testSfovStitchForSinglePixelGap() throws Exception {

        final String[] args = {
                "--tile_spec_url", "src/test/resources/stitch-test/sfov_stitch_test.json",
                "--out", outputFile.getAbsolutePath(),
                "--x", "40000",
                "--y", "71800",
                "--width", "200",
                "--height", "1600",
                "--scale", "1.0"
        };

        ByteRenderer.renderUsingCommandLineArguments(args);

        Assert.assertTrue("stitched file " + outputFile.getAbsolutePath() + " not created", outputFile.exists());

        final ImageProcessor renderedIp = new ImagePlus(outputFile.getAbsolutePath()).getProcessor();
        for (int y = 0; y < renderedIp.getHeight(); y++) {
          for (int x = 0; x < renderedIp.getWidth(); x++) {
                final int pixel = renderedIp.get(x, y);
                if (pixel == 0) {
                    Assert.fail("pixel at " + x + ", " + y + " is 0");
                }
            }
        }
    }
}
