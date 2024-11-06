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

public class BG_Plugin implements PlugIn
{
	public static int defaultType = 0;
	public static String[] fitTypes = new String[] { "Quadratic", "Fourth Order" };

	@Override
	public void run(String arg)
	{
		Roi rois = getROIs();

		if ( rois == null )
			return;

		GenericDialog gd = new GenericDialog( "fit..." );

		gd.addChoice( "fit_type", fitTypes, fitTypes[ defaultType ]);
		gd.showDialog();

		if (gd.wasCanceled() )
			return;

		int type = defaultType = gd.getNextChoiceIndex();

		fit( type );
	}

	public static Roi getROIs()
	{
		RoiManager rm = RoiManager.getInstance();

		if (rm == null || rm.getCount() == 0)
		{
			IJ.log( "please define rois ... " );
			return null;
		}

		int numRois = rm.getCount();
		return rm.getRoi( 0 );
	}

	public static void fit( int type )
	{
		IJ.log( "fitting with ... " + fitTypes[ type ] );
	}

	private static void showNonBlockingDialog() {
        new Thread(() -> {
            JDialog dialog = new JDialog((JFrame)null, "Run Background plugin...", false);
            dialog.setLayout(new FlowLayout());

            JButton closeButton = new JButton("Run Background plugin...");
            closeButton.addActionListener(e -> {
            	new BG_Plugin().run( null );
            });
            dialog.add(closeButton);

            dialog.pack();
            dialog.setVisible(true);
        }).start();
	}

	public static void main( String[] args )
	{
		new ImageJ();

		SwingUtilities.invokeLater(() ->
		{
			showNonBlockingDialog();
		});

		// open image
		String n5Path = "/Volumes/public/ForPreibisch/cerebellum-3.n5";

		RandomAccessibleInterval img = N5Utils.open( new N5FSReader( n5Path ), "data/s4" );
		int z = 0;
		img = Views.hyperSlice( img, 2, z );
		ImageJFunctions.show( img );

		new RoiManager(); // Create a new instance
		//
		//new BG_Plugin().run( null );
	}
}
