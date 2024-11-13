package org.janelia.render.client.embackground;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.janelia.render.client.parameter.CommandLineParameters;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.Arrays;
import java.util.List;

@Plugin(type=Command.class, headless=true, menuPath="Render Tools>Background Correction")
public class BackgroundCorrectionPlugin implements Command {

	private static class Parameters extends CommandLineParameters {
		@com.beust.jcommander.Parameter(names = "--n5Path",
				description = "Path to the N5 container",
				required = true)
		public String n5Path;

		@com.beust.jcommander.Parameter(names = "--dataset",
				description = "Name of the dataset",
				required = true)
		public String dataset;

		@com.beust.jcommander.Parameter(names = "--z",
				description = "Z slice to process",
				required = true)
		public int z;
	}

	@Parameter
	private Dataset source;

	@Parameter
	private LogService log;

	@Parameter(label="Fit type:", choices={"Quadratic", "Fourth Order"}, style="radioButtonHorizontal")
	private String fitType;

	@Parameter(label="Show background:")
	private boolean showBackground;

	@Override
	public void run() {
		final Img<UnsignedByteType> img = source.typedImg(new UnsignedByteType());

		log.info("Fitting with " + fitType + " model...");

		final BackgroundModel<?> backgroundModel;
		if (fitType.equals("Quadratic")) {
			backgroundModel = new QuadraticBackground();
		} else if (fitType.equals("Fourth Order")) {
			backgroundModel = new FourthOrderBackground();
		} else {
			throw new IllegalArgumentException("Unknown fit type: " + fitType);
		}

		final List<Roi> rois = getROIs();

		final long start = System.currentTimeMillis();
		try {
			CorrectBackground.fitBackgroundModel(rois, img, backgroundModel);
		} catch (final NotEnoughDataPointsException | IllDefinedDataPointsException e) {
			log.error("Fitting failed: " + e.getMessage());
			return;
		}
		log.info("Fitted background model: " + backgroundModel);
		log.info("Fitting took " + (System.currentTimeMillis() - start) + "ms.");
		log.info("Raw coefficients: " + Arrays.toString(backgroundModel.getCoefficients()));

		final RandomAccessibleInterval<FloatType> background = CorrectBackground.createBackgroundImage(backgroundModel, img);
		final RandomAccessibleInterval<UnsignedByteType> corrected = CorrectBackground.correctBackground(img, background, new UnsignedByteType());

		if (showBackground) {
			ImageJFunctions.show(background, "Background");
		}
		ImageJFunctions.show(corrected, "Corrected");

	}

	private List<Roi> getROIs() {
		final RoiManager rm = RoiManager.getInstance();

		if (rm == null || rm.getCount() == 0) {
			log.error("Please define ROIs first before running background correction.");
			return null;
		}

		return Arrays.asList(rm.getRoisAsArray());
	}

	public static void main(final String[] args) {
		final Parameters params = new Parameters();
		params.parse(args);

		RandomAccessibleInterval<UnsignedByteType> img = N5Utils.open(new N5FSReader(params.n5Path), params.dataset);
		if (img.numDimensions() > 2) {
			img = Views.hyperSlice(img, 2, params.z);
		}

		// select ROIs and press "t" to add them to the ROI manager
		// then run the plugin through the "Render Tools" menu
		final ImageJ ij = new ImageJ();
		ij.launch();
		final ImagePlus imgPlus = ImageJFunctions.wrap(img, "Original");
		imgPlus.show();
	}
}
