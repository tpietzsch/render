package org.janelia.render.client.embackground;

import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class QuadraticBackgroundTest {

	@Test
	public void simpleModelIsComputedCorrectly() throws NotEnoughDataPointsException, IllDefinedDataPointsException {
		final List<PointMatch> matches = new ArrayList<>();

		// minimal set of points to fit 0.5 * x^2 + 0.5 * y^2
		matches.add(new PointMatch(new Point(new double[]{0, 0}), new Point(new double[]{0})));
		matches.add(new PointMatch(new Point(new double[]{1, 1}), new Point(new double[]{1})));
		matches.add(new PointMatch(new Point(new double[]{-1, 1}), new Point(new double[]{1})));
		matches.add(new PointMatch(new Point(new double[]{1, -1}), new Point(new double[]{1})));
		matches.add(new PointMatch(new Point(new double[]{-1, -1}), new Point(new double[]{1})));
		matches.add(new PointMatch(new Point(new double[]{0, 1}), new Point(new double[]{0.5})));

		final QuadraticBackground background = new QuadraticBackground();
		background.fit(matches);

		// order of coefficients: {x^2, xy, y^2, x, y, 1}
		Assert.assertArrayEquals(new double[]{0.5, 0, 0.5, 0, 0, 0}, background.getCoefficients(), 1e-12);
	}

	@Test
	public void modelCanBeRecoveredWithManyPoints() throws NotEnoughDataPointsException, IllDefinedDataPointsException {
		final QuadraticBackground background = new QuadraticBackground(new double[]{1, 2, 3, 4, 5, 6});

		final List<PointMatch> matches = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			final double x = Math.random() * 2 - 1;
			final double y = Math.random() * 2 - 1;
			final double[] location = new double[]{x, y};
			background.applyInPlace(location);
			matches.add(new PointMatch(new Point(new double[]{x, y}), new Point(new double[]{location[0]})));
		}

		final QuadraticBackground recovered = new QuadraticBackground();
		recovered.fit(matches);

		Assert.assertArrayEquals(background.getCoefficients(), recovered.getCoefficients(), 1e-12);
	}
}
