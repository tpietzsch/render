package org.janelia.alignment.destreak;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import org.janelia.alignment.filter.Filter;

/**
 * Streak corrector with a configurable/parameterized mask that can also be used as {@link Filter}.
 * This applies the mask of the {@link SmoothMaskStreakCorrector} to parts of the 16bit input image.
 *
 * @author Michael Innerberger
 */
public class LocalSmoothMaskStreakCorrector16Bit extends LocalSmoothMaskStreakCorrector {

	public LocalSmoothMaskStreakCorrector16Bit() {
		this(1);
	}

	public LocalSmoothMaskStreakCorrector16Bit(final int numThreads) {
		super(numThreads);
	}

	public LocalSmoothMaskStreakCorrector16Bit(
			final SmoothMaskStreakCorrector corrector,
			final int gaussianBlurRadius,
			final float initialThreshold,
			final float finalThreshold) {
		super(corrector, gaussianBlurRadius, initialThreshold, finalThreshold);
	}

    @Override
    public void process(final ImageProcessor ip, final double scale) {
		// save original image for later subtraction
		final ImagePlus originalIP = new ImagePlus("original", ip.convertToShortProcessor());
		final Img<UnsignedShortType> original = ImageJFunctions.wrapShort(originalIP);
		checkWrappingSucceeded(original, ip, UnsignedByteType.class);

		// de-streak image
		super.process16bit(ip, scale);

		final ImagePlus fixedIP = new ImagePlus("fixed", ip);
		final Img<UnsignedShortType> fixed = ImageJFunctions.wrapShort(fixedIP);
		checkWrappingSucceeded(fixed, ip, UnsignedByteType.class);

		// subtract fixed from original to get streaks, which is where the correction should be applied
		final RandomAccessibleInterval<FloatType> weight =
				Converters.convertRAI(original,
									  fixed,
									  (i1,i2,o) -> o.set(Math.abs(i1.get() - i2.get())),
									  new FloatType());

		weightedSum(ip, originalIP.getProcessor(), weight);
	}
}
