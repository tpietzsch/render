package org.janelia.render.client.embackground;

import ij.ImageJ;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import java.util.ArrayList;
import java.util.List;

public class CorrectBackground {
	private static final String containerPath = System.getenv("HOME") + "/big-data/render-exports/cerebellum-3.n5";
	private static final String dataset = "data";
	private static final int scale = 4;

	public static void main(final String[] args) throws NotEnoughDataPointsException, IllDefinedDataPointsException {
		try (final N5Reader reader = new N5FSReader(containerPath)) {
			final Img<UnsignedByteType> stack = N5Utils.open(reader, dataset + "/s" + scale);
			final RandomAccessibleInterval<UnsignedByteType> firstSlice = Views.hyperSlice(stack, 2, 0);

			final int width = (int) stack.dimension(0);
			final int height = (int) stack.dimension(1);

			final int midX = width / 2;
			final int midY = height / 2;

			// fit a quadratic background model with all the points in the first slice
			final long start = System.currentTimeMillis();
			final QuadraticBackground backgroundModel = new QuadraticBackground();
			final List<PointMatch> matches = new ArrayList<>();
			final Cursor<UnsignedByteType> cursor = Views.iterable(firstSlice).localizingCursor();
			while (cursor.hasNext()) {
				cursor.fwd();
				final double x = (cursor.getDoublePosition(0) - midX) / width;
				final double y = (cursor.getDoublePosition(1) - midY) / height;
				final double z = cursor.get().getRealDouble();
				matches.add(new PointMatch(new Point(new double[]{x, y}), new Point(new double[]{z})));
			}
			backgroundModel.fit(matches);
			System.out.println("Fitted background model: " + backgroundModel);
			System.out.println("Fitting took " + (System.currentTimeMillis() - start) + "ms.");

			// we assume that the model is concave, so the offset is the maximum value
			final double maxValue = backgroundModel.getCoefficients()[0];

			// create a background image from the model
			final double[] location = new double[2];
			final RealRandomAccessible<FloatType> background = new FunctionRealRandomAccessible<>(2, (pos, value) -> {
				location[0] = (pos.getDoublePosition(0) - midX) / width;
				location[1] = (pos.getDoublePosition(1) - midY) / height;
				backgroundModel.applyInPlace(location);
				value.setReal(location[0] / maxValue);
			}, FloatType::new);

			final RandomAccessibleInterval<FloatType> materializedBackground = Views.interval(Views.raster(background), firstSlice);

			final RandomAccessibleInterval<UnsignedByteType> corrected = Converters.convert(firstSlice, materializedBackground, (s, b, o) -> {
				o.set(UnsignedByteType.getCodedSignedByteChecked((int) (s.getRealDouble() / b.getRealDouble())));
			}, new UnsignedByteType());

			new ImageJ();
			ImageJFunctions.show(firstSlice, "Original");
			ImageJFunctions.show(materializedBackground, "Background");
			ImageJFunctions.show(corrected, "Corrected");
		}
	}
}
