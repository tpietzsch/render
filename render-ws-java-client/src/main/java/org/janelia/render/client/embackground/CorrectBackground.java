package org.janelia.render.client.embackground;

import ij.ImageJ;
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

public class CorrectBackground {
	private static final String containerPath = System.getenv("HOME") + "/big-data/render-exports/cerebellum-3.n5";
	private static final String dataset = "data";
	private static final int scale = 4;

	public static void main(final String[] args) {
		try (final N5Reader reader = new N5FSReader(containerPath)) {
			final Img<UnsignedByteType> stack = N5Utils.open(reader, dataset + "/s" + scale);

			final int width = (int) stack.dimension(0);
			final int height = (int) stack.dimension(1);

			final int midX = width / 2;
			final int midY = height / 2;

			final RealRandomAccessible<FloatType> background = new FunctionRealRandomAccessible<>(3, (pos, value) -> {
				final double x = (pos.getDoublePosition(0) - midX) / width;
				final double y = (pos.getDoublePosition(1) - midY) / height;
				value.setReal(1 - (x * x + y * y) / 2.0);
			}, FloatType::new);

			final RandomAccessibleInterval<FloatType> materializedBackground = Views.interval(Views.raster(background), stack);

			final RandomAccessibleInterval<UnsignedByteType> corrected = Converters.convert(stack, materializedBackground, (s, b, o) -> {
				o.set((int) (s.getRealDouble() / b.getRealDouble()));
			}, new UnsignedByteType());

			new ImageJ();
			ImageJFunctions.show(stack);
			ImageJFunctions.show(corrected);
		}
	}
}
