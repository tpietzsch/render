package org.janelia.render.client.embackground;

import ij.ImageJ;
import ij.gui.Roi;
import ij.io.RoiDecoder;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class CorrectBackground {
	private static final String containerPath = System.getenv("HOME") + "/big-data/render-exports/cerebellum-3.n5";
	private static final String roiPath = containerPath + "/roi-set.zip";
	private static final String dataset = "data";
	private static final int scale = 4;

	public static void main(final String[] args) throws NotEnoughDataPointsException, IllDefinedDataPointsException, IOException {
		try (final N5Reader reader = new N5FSReader(containerPath)) {
			final Img<UnsignedByteType> stack = N5Utils.open(reader, dataset + "/s" + scale);
			final RandomAccessibleInterval<UnsignedByteType> firstSlice = Views.hyperSlice(stack, 2, 0);

			final int width = (int) stack.dimension(0);
			final int height = (int) stack.dimension(1);

			final double midX = width / 2.0;
			final double midY = height / 2.0;

			final List<Roi> rois = readRois(roiPath);
			if (rois.isEmpty()) {
				throw new IllegalArgumentException("No ROIs found in " + roiPath);
			}

			final Set<java.awt.Point> pointsOfInterest = new HashSet<>();
			for (final Roi roi : rois) {
				final java.awt.Point[] containedPoints = roi.getContainedPoints();
				Collections.addAll(pointsOfInterest, containedPoints);
			}

			final Img<UnsignedByteType> roiImg = ArrayImgs.unsignedBytes(width, height);
			final RandomAccess<UnsignedByteType> roiRa = roiImg.randomAccess();
			for (final java.awt.Point point : pointsOfInterest) {
				roiRa.setPositionAndGet(point.x, point.y).set(255);
			}

			// fit a quadratic background model with all the points in the first slice
			final long start = System.currentTimeMillis();
			final FourthOrderBackground backgroundModel = new FourthOrderBackground();
			final List<PointMatch> matches = new ArrayList<>();
			final RandomAccess<UnsignedByteType> ra = firstSlice.randomAccess();
			for (final java.awt.Point point : pointsOfInterest) {
				final double x = (point.x - midX) / width;
				final double y = (point.y - midY) / height;
				final double z = ra.setPositionAndGet(point.x, point.y).getRealDouble();
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
			ImageJFunctions.show(roiImg, "ROI");
		}
	}

	private static List<Roi> readRois(final String path) throws IOException {
		if (path.endsWith(".zip")) {
			return extractRoisFromZip(path);
		} else {
			final RoiDecoder rd = new RoiDecoder(path);
			final Roi roi = rd.getRoi();
			return List.of(roi);
		}
	}

	private static List<Roi> extractRoisFromZip(final String path) throws IOException {
		final List<Roi> rois = new ArrayList<>();

		try (final ZipFile zipFile = new ZipFile(path)) {
			final Enumeration<? extends ZipEntry> entries = zipFile.entries();

			while (entries.hasMoreElements()) {
				final ZipEntry entry = entries.nextElement();
				final String name = entry.getName();

				if (name.endsWith(".roi")) {
					final RoiDecoder rd = new RoiDecoder(zipFile.getInputStream(entry).readAllBytes(), entry.getName());
					final Roi roi = rd.getRoi();
					if (roi != null) {
						rois.add(roi);
					}
				}
			}
		}

		return rois;
	}
}
