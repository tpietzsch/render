package org.janelia.render.client.embackground;

import ij.ImageJ;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

public class CorrectBackground {
	private static final String containerPath = System.getenv("HOME") + "/big-data/render-exports/cerebellum-3.n5";
	private static final String dataset = "data/s4";

	public static void main(final String[] args) {
		try (final N5Reader reader = new N5FSReader(containerPath)) {
			final Img<UnsignedByteType> stack = N5Utils.open(reader, dataset);

			new ImageJ();
			ImageJFunctions.show(stack);
		}
	}
}
