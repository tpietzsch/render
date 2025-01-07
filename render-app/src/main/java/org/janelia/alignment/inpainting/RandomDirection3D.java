package org.janelia.alignment.inpainting;

import java.util.Random;

/**
 * A statistic that yields a completely random 3D direction for each sample.
 */
public class RandomDirection3D implements DirectionalStatistic {

	private final Random random;

	/**
	 * Creates a new statistic with a random seed.
	 */
	public RandomDirection3D() {
		this(new Random());
	}

	/**
	 * Creates a new statistic with the given random number generator.
	 *
	 * @param random the random number generator to use
	 */
	public RandomDirection3D(final Random random) {
		this.random = random;
	}

	@Override
	public void sample(final double[] direction) {
		final double x = random.nextGaussian();
		final double y = random.nextGaussian();
		final double z = random.nextGaussian();
		final double norm = Math.sqrt(x * x + y * y + z * z);
		direction[0] = x / norm;
		direction[1] = y / norm;
		direction[2] = z / norm;
	}
}
