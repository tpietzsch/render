package org.janelia.alignment.filter.emshading;

import java.util.List;


/**
 * A 4th order model for shading correction in 2D slices of EM data.
 * To ensure that the model is concave, no 3rd order terms are included.
 */
public class FourthOrderShading extends ShadingModel {

	public FourthOrderShading() {
		super();
	}

	public FourthOrderShading(final double[] coefficients) {
		super(coefficients);
	}

	public FourthOrderShading(final FourthOrderShading shading) {
		super(shading.getCoefficients());
	}


	@Override
	protected int nCoefficients() {
		return 9;
	}

	@Override
	protected void fillRowA(final double[] rowA, final double x, final double y) {
		final double xx = x * x;
		final double yy = y * y;
		rowA[0] = 1;
		rowA[1] = y;
		rowA[2] = x;
		rowA[3] = yy;
		rowA[4] = x * y;
		rowA[5] = xx;
		rowA[6] = yy * yy;
		rowA[7] = xx * yy;
		rowA[8] = xx * xx;
	}

	@Override
	protected List<String> coefficientNames() {
		return List.of("1", "y", "x", "y^2", "xy", "x^2", "y^4", "x^2 y^2", "x^4");
	}

	@Override
	public void applyInPlace(final double[] location) {
		final double x = location[0];
		final double y = location[1];
		final double xx = x * x;
		final double yy = y * y;
		final double[] coefficients = getCoefficients();
		final double result = coefficients[8] * xx * xx
				+ coefficients[7] * xx * yy
				+ coefficients[6] * yy * yy
				+ coefficients[5] * xx
				+ coefficients[4] * x * y
				+ coefficients[3] * yy
				+ coefficients[2] * x
				+ coefficients[1] * y
				+ coefficients[0];
		location[0] = result;
		location[1] = 0.0;
	}

	@Override
	public FourthOrderShading copy() {
		return new FourthOrderShading(this);
	}
}
