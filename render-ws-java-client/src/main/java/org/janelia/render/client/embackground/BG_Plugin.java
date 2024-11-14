package org.janelia.render.client.embackground;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;

import javax.swing.SwingUtilities;

import com.beust.jcommander.Parameter;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import org.janelia.render.client.parameter.CommandLineParameters;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import ij.IJ;
import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.view.Views;

public class BG_Plugin implements PlugIn {

	private static class Parameters extends CommandLineParameters {
		@Parameter(names = "--n5Path",
				description = "Path to the N5 container",
				required = true)
		public String n5Path;

		@Parameter(names = "--dataset",
				description = "Name of the dataset",
				required = true)
		public String dataset;

		@Parameter(names = "--z",
				description = "Z slice to process")
		public int z = 1;

		@Parameter(names = "--downscaleFactor",
				description = "Downscale factor")
		public int downscale = 0;
	}


	public static int defaultType = 0;
	public static boolean defaultShowBackground = false;
	public static String[] fitTypes = new String[] { "Quadratic", "Fourth Order" };

	@Override
	public void run(final String arg) {
		final List<Roi> rois = getROIs();

		if (rois == null || rois.isEmpty()) {
			IJ.log("No ROIs specified.");
			return;
		}

		final GenericDialog gd = new GenericDialog("Fit background correction");

		gd.addChoice("Fit type", fitTypes, fitTypes[defaultType]);
		gd.addCheckbox("Show background image", defaultShowBackground);
		gd.showDialog();

		if (gd.wasCanceled()) {
			return;
		}

		final int type = gd.getNextChoiceIndex();
		defaultType = type;

		final boolean showBackground = gd.getNextBoolean();
		defaultShowBackground = showBackground;

		try {
			fit(type, rois, showBackground);
		} catch (final NotEnoughDataPointsException | IllDefinedDataPointsException e) {
			IJ.log("Fitting failed: " + e.getMessage());
		}
	}

	public static List<Roi> getROIs() {
		final RoiManager rm = RoiManager.getInstance();

		if (rm == null || rm.getCount() == 0) {
			IJ.log("Please define ROIs first before running background correction.");
			return null;
		}

		return Arrays.asList(rm.getRoisAsArray());
	}

	public static void fit(final int type, final List<Roi> rois, final boolean showBackground) throws NotEnoughDataPointsException, IllDefinedDataPointsException {
		IJ.log("Fitting with " + fitTypes[type] + " model...");

		final BackgroundModel<?> backgroundModel;
		if (fitTypes[type].equals("Quadratic")) {
			backgroundModel = new QuadraticBackground();
		} else if (fitTypes[type].equals("Fourth Order")) {
			backgroundModel = new FourthOrderBackground();
		} else {
			throw new IllegalArgumentException("Unknown fit type: " + fitTypes[type]);
		}

		final long start = System.currentTimeMillis();
		final RandomAccessibleInterval<UnsignedByteType> img = ImageJFunctions.wrap(IJ.getImage());
		CorrectBackground.fitBackgroundModel(rois, img, backgroundModel);
		IJ.log("Fitted background model: " + backgroundModel);
		IJ.log("Fitting took " + (System.currentTimeMillis() - start) + "ms.");
		IJ.log("Raw coefficients: " + Arrays.toString(backgroundModel.getCoefficients()));

		final RandomAccessibleInterval<FloatType> background = CorrectBackground.createBackgroundImage(backgroundModel, img);
		final RandomAccessibleInterval<UnsignedByteType> corrected = CorrectBackground.correctBackground(img, background, new UnsignedByteType());

		if (showBackground) {
			ImageJFunctions.show(background, "Background");
		}
		ImageJFunctions.show(corrected, "Corrected");

	}

	private static void addKeyListener() {
		System.out.println("Mapped 'Background Correction' to F1.");

        new Thread(() -> KeyboardFocusManager.getCurrentKeyboardFocusManager()
				.addKeyEventDispatcher(e -> {
					if (e.getID() == KeyEvent.KEY_PRESSED) {
						switch (e.getKeyCode()) {
							case KeyEvent.VK_F1:
								new BG_Plugin().run(null);
								break;
							case KeyEvent.VK_F2:
								System.out.println("F2 key pressed (not assigned)");
								break;
						}
					}
					return false;
				})).start();
	}

	public static void main(final String[] args) {
		final Parameters params = new Parameters();
		params.parse(args);

		new ImageJ();
		SwingUtilities.invokeLater(BG_Plugin::addKeyListener);

		IJ.log("Opening " + params.dataset + " from " + params.n5Path);
		RandomAccessibleInterval<UnsignedByteType> img = N5Utils.open(new N5FSReader(params.n5Path), params.dataset);
		if (img.numDimensions() > 2) {
			IJ.log("Showing slice " + params.z);
			img = Views.hyperSlice(img, 2, params.z);
		}
		final RandomAccessibleInterval<UnsignedByteType> downscaled = downSample(img, params.downscale);
		ImageJFunctions.show(downscaled, "Original");
	}

	private static RandomAccessibleInterval<UnsignedByteType> downSample(final RandomAccessibleInterval<UnsignedByteType> img, int downscale) {
		if (downscale < 1) {
			// automatically determine downscale factor
			IJ.log("No downscaling factor given, choosing factor automatically...");
			final long[] dims = img.dimensionsAsLongArray();
			final long maxSize = 2000 * 2000;
			downscale = (int) Math.ceil(Math.sqrt((double) (dims[0] * dims[1]) / maxSize));
		}

		if (downscale == 1) {
			IJ.log("No downscaling, showing original image at full size.");
			return img;
		} else {
			IJ.log("Isotropically downscaling by factor of " + downscale);
			Gauss3.gauss(downscale / 2.0, Views.extendMirrorSingle(img), img);
			return Views.subsample(img, downscale);
		}
	}
}
