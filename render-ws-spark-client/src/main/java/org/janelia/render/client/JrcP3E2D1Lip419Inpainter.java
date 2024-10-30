package org.janelia.render.client;

import ij.ImageJ;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import org.janelia.alignment.inpainting.DirectionalStatistic;
import org.janelia.alignment.inpainting.RandomDirection3D;
import org.janelia.alignment.inpainting.RayCastingInpainter;
import org.janelia.render.client.spark.n5.N5Client;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

public class JrcP3E2D1Lip419Inpainter {

	// path to the N5 container containing the data and a mask
	public static final String N5_BASE_PATH = System.getenv("HOME") + "/big-data/streak-correction/jrc_P3-E2-D1-Lip4-19/broken_layers.n5";

	public static void main(final String[] args) {
		// saveSubStack();
		doInpainting();
	}

	@SuppressWarnings("unused")
	private static void saveSubStack() {
		final String[] n5ClientArgs = {
				"--baseDataUrl", "http://renderer-dev.int.janelia.org:8080/render-ws/v1",
				"--owner", "fibsem",
				"--project", "jrc_P3_E2_D1_Lip4_19",
				"--stack", "v1_acquire_trimmed_align_v2",
				"--n5Path", N5_BASE_PATH,
				"--n5Dataset", "tissue",
				"--tileWidth", "2048",
				"--tileHeight", "2048",
				"--blockSize", "512,512,128",
				"--minZ", "14763",
				"--maxZ", "14805",
		};

		N5Client.main(n5ClientArgs);
	}

	@SuppressWarnings("unused")
	private static void doInpainting() {
		final DirectionalStatistic directionalStatistic = new RandomDirection3D();
		final RayCastingInpainter inpainter = new RayCastingInpainter(64, 50, directionalStatistic);

		try (final N5Reader n5 = new N5FSReader(N5_BASE_PATH)) {
			final RandomAccessibleInterval<UnsignedByteType> tissue = N5Utils.open(n5, "tissue_crop");
			final RandomAccessibleInterval<UnsignedByteType> mask = N5Utils.open(n5, "mask_crop_v2");

			// convert to float (and copy tissue)
			final Img<FloatType> tissueFloat = Util.getSuitableImgFactory(tissue, new FloatType()).create(tissue);
			LoopBuilder.setImages(tissue, tissueFloat).forEachPixel((i, o) -> o.setReal(i.getRealDouble()));
			final RandomAccessibleInterval<FloatType> maskFloat = Converters.convert(mask, (i, o) -> o.setReal(i.getRealDouble()), new FloatType());

			inpainter.inpaint(tissueFloat, maskFloat);

			new ImageJ();
			ImageJFunctions.show(tissueFloat, "Tissue");
			ImageJFunctions.show(maskFloat, "Mask");
		}
	}

}
