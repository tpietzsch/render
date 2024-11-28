package org.janelia.render.client.specificfixes;

import ij.ImageJ;
import mpicbg.models.AffineModel1D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Specific fix for jrc_mpi_psc120_1b dataset: there is an area that is a lot darker than the rest of the stack. This
 * script infers parameters for affine intensity transformations that can be used to correct this issue.
 */
public class Fix_jrc_mpi_psc120_1b {
	static final String N5_PATH = "/nrs/fibsem/data/jrc_mpi_psc120_1b/jrc_mpi_psc120_1b.n5";
	static final String DATASET = "/render/jrc_mpi_psc120_1b/v3_acquire_align_16bit_destreak___20241124_174721/s0";
	static final Interval ROI = new FinalInterval(new long[] {1000, 1000}, new long[] {2000, 2000});
	static final boolean VISUALIZE = true;


	public static void main(final String[] args) throws IOException {
		try (final N5Reader n5 = new N5FSReader(N5_PATH)) {
			final RandomAccessibleInterval<UnsignedShortType> image = N5Utils.open(n5, DATASET);

			// for these two and all layers in between, a = 0.705, b = 11800 seems to work well
			compareIntensities(image, 110, 111);
			compareIntensities(image, 468, 466);

			// for this outlier layer, 0.507, b = 19960 seems to be a good value
			compareIntensities(image, 468, 467);
		}
	}

	private static void compareIntensities(
			final RandomAccessibleInterval<UnsignedShortType> image,
			final int baselineZ,
			final int otherZ
	) {
		System.out.println("Comparing intensities at z = " + baselineZ + " and z = " + otherZ);
		final RandomAccessibleInterval<UnsignedShortType> baseline = Views.interval(Views.hyperSlice(image, 2, baselineZ - 1), ROI);
		final RandomAccessibleInterval<UnsignedShortType> other = Views.interval(Views.hyperSlice(image, 2, otherZ - 1), ROI);

		if (VISUALIZE) {
			new ImageJ();
			final RandomAccessibleInterval<UnsignedShortType> stack = Views.stack(baseline, other);
			ImageJFunctions.show(stack, "z = " + baselineZ + " vs. z = " + otherZ + " (before)");
		}

		System.out.println("Matching intensities...");
		final Cursor<UnsignedShortType> baselineCursor = Views.flatIterable(baseline).cursor();
		final Cursor<UnsignedShortType> otherCursor = Views.flatIterable(other).cursor();
		final List<PointMatch> candidates = new ArrayList<>();

		// read out pixel values and sort them
		final int n = (int) ROI.dimension(0) * (int) ROI.dimension(1);
		final double[] baselinePixels = new double[n];
		final double[] otherPixels = new double[n];
		int k = 0;
		while (baselineCursor.hasNext()) {
			final double px1 = otherCursor.next().getRealDouble();
			final double px2 = baselineCursor.next().getRealDouble();
			baselinePixels[k] = px2;
			otherPixels[k++] = px1;
		}

		Arrays.sort(baselinePixels);
		Arrays.sort(otherPixels);

		// match pixels in the middle of the intensity range
		final double cutoff = 0.05;
		final int start = (int) (cutoff * n);
		final int end = (int) ((1 - cutoff) * n);

		for (int j = start; j < end; j++) {
			final double px1 = otherPixels[j];
			final double px2 = baselinePixels[j];
			final PointMatch match = new PointMatch(new Point(new double[] {px1}), new Point(new double[] {px2}), 1.0);
			candidates.add(match);
		}

		System.out.println("Fitting affine model...");
		final AffineModel1D model = new AffineModel1D();
		try {
			model.fit(candidates);
		} catch (final NotEnoughDataPointsException | IllDefinedDataPointsException e) {
			throw new RuntimeException(e);
		}

		final double[] ab = new double[2];
		model.toArray(ab);
		System.out.println("Affine model parameters for z = " + otherZ + " (baseline: z = " + baselineZ + "): a = " + ab[0] + ", b = " + ab[1]);

		final RandomAccessibleInterval<UnsignedShortType> corrected = Converters.convert(other, (i, o) -> o.setReal(i.getRealDouble() * ab[0] + ab[1]), new UnsignedShortType());

		if (VISUALIZE) {
			final RandomAccessibleInterval<UnsignedShortType> stack = Views.stack(baseline, corrected);
			ImageJFunctions.show(stack, "z = " + baselineZ + " vs. z = " + otherZ + " (after)");
		}
	}
}
