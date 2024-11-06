package org.janelia.render.client.embackground;

import java.awt.FlowLayout;
import java.util.Arrays;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
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

	public static int defaultType = 0;
	public static boolean defaultShowBackground = false;
	public static String[] fitTypes = new String[] { "Quadratic", "Fourth Order" };

	public static RandomAccessibleInterval<UnsignedByteType> img;

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

	private static void showNonBlockingDialog() {
        new Thread(() -> {
            final JDialog dialog = new JDialog((JFrame)null, "Run Background plugin...", false);
            dialog.setLayout(new FlowLayout());

            final JButton closeButton = new JButton("Run Background plugin...");
            closeButton.addActionListener(e -> new BG_Plugin().run(null ));
            dialog.add(closeButton);

            dialog.pack();
            dialog.setVisible(true);
        }).start();
	}

	public static void main(final String[] args) {
		new ImageJ();

		SwingUtilities.invokeLater(BG_Plugin::showNonBlockingDialog);

		final String n5Path = System.getenv("HOME") + "/big-data/render-exports/cerebellum-3.n5";
		img = N5Utils.open(new N5FSReader(n5Path), "data/s4");
		final int z = 0;
		img = Views.hyperSlice(img, 2, z);
		ImageJFunctions.show(img);

		new RoiManager();
	}
}
