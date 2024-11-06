package org.janelia.render.client.embackground;

import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

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
	public static String[] fitTypes = new String[] { "Quadratic", "Fourth Order" };

	@Override
	public void run(final String arg) {
		final Roi rois = getROIs();

		if (rois == null) {
			return;
		}

		final GenericDialog gd = new GenericDialog("fit...");

		gd.addChoice("fit_type", fitTypes, fitTypes[defaultType]);
		gd.showDialog();

		if (gd.wasCanceled()) {
			return;
		}

		defaultType = gd.getNextChoiceIndex();
		final int type = gd.getNextChoiceIndex();

		fit(type);
	}

	public static Roi getROIs() {
		final RoiManager rm = RoiManager.getInstance();

		if (rm == null || rm.getCount() == 0) {
			IJ.log("please define rois ... ");
			return null;
		}

		return rm.getRoi( 0 );
	}

	public static void fit(final int type) {
		IJ.log("fitting with ... " + fitTypes[type]);
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

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void main(final String[] args) {
		new ImageJ();

		SwingUtilities.invokeLater(BG_Plugin::showNonBlockingDialog);

		final String n5Path = System.getenv("HOME") + "/big-data/render-exports/cerebellum-3.n5";
		RandomAccessibleInterval img = N5Utils.open(new N5FSReader(n5Path), "data/s4");
		final int z = 0;
		img = Views.hyperSlice(img, 2, z);
		ImageJFunctions.show(img);

		new RoiManager();
	}
}
