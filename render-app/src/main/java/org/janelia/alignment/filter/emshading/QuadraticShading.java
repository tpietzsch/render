package org.janelia.alignment.filter.emshading;

import java.util.List;


/**
 * A quadratic model for shading correction in 2D slices of EM data.
 */
public class QuadraticShading extends ShadingModel {

	public QuadraticShading() {
		super();
	}

	public QuadraticShading(final double[] coefficients) {
		super(coefficients);
	}

	public QuadraticShading(final QuadraticShading shading) {
		super(shading.getCoefficients());
	}


	@Override
	protected int nCoefficients() {
		return 6;
	}

	@Override
	protected void fillRowA(final double[] rowA, final double x, final double y) {
		rowA[0] = 1;
		rowA[1] = y;
		rowA[2] = x;
		rowA[3] = y * y;
		rowA[4] = x * y;
		rowA[5] = x * x;
	}

	@Override
	protected List<String> coefficientNames() {
		return List.of("1", "y", "x", "y^2", "xy", "x^2");
	}

	@Override
	public void applyInPlace(final double[] location) {
		final double x = location[0];
		final double y = location[1];
		final double[] coefficients = getCoefficients();
		final double result = coefficients[5] * x * x
				+ coefficients[4] * x * y
				+ coefficients[3] * y * y
				+ coefficients[2] * x
				+ coefficients[1] * y
				+ coefficients[0];
		location[0] = result;
		location[1] = 0.0;
	}

	@Override
	public QuadraticShading copy() {
		return new QuadraticShading(this);
	}
}
