package org.janelia.render.client.embackground;

import mpicbg.models.AbstractModel;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.PointMatch;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;

import java.util.Arrays;
import java.util.Collection;


/**
 * A quadratic model for background correction in 2D slices of EM data.
 */
public class QuadraticBackground extends AbstractModel<QuadraticBackground> {

	private final int N_COEFFICIENTS = 6;
	private final double[] coefficients = new double[N_COEFFICIENTS];


	public QuadraticBackground() {
		Arrays.fill(coefficients, 0);
	}

	public QuadraticBackground(final double[] coefficients) {
		System.arraycopy(coefficients, 0, this.coefficients, 0, N_COEFFICIENTS);
	}

	public QuadraticBackground(final QuadraticBackground background) {
		this(background.coefficients);
	}

	@Override
	public int getMinNumMatches() {
		return N_COEFFICIENTS;
	}

	@Override
	public <P extends PointMatch> void fit(final Collection<P> matches) throws NotEnoughDataPointsException, IllDefinedDataPointsException {
		final DMatrixRMaj ATA = new DMatrixRMaj(N_COEFFICIENTS, N_COEFFICIENTS, true, new double[N_COEFFICIENTS * N_COEFFICIENTS]);
		final DMatrixRMaj ATb = new DMatrixRMaj(N_COEFFICIENTS, 1);

		final double[] rowA = new double[N_COEFFICIENTS];

		for (final P match : matches) {
			final double x = match.getP1().getL()[0];
			final double y = match.getP1().getL()[1];
			final double z = match.getP2().getL()[0];

			// compute one row of the least-squares matrix A
			rowA[0] = 1;
			rowA[1] = y;
			rowA[2] = x;
			rowA[3] = y * y;
			rowA[4] = x * y;
			rowA[5] = x * x;

			// update upper triangle of A^T * A
			for (int i = 0; i < N_COEFFICIENTS; i++) {
				for (int j = i; j < N_COEFFICIENTS; j++) {
					ATA.data[i * N_COEFFICIENTS + j] += rowA[i] * rowA[j];
				}
			}

			// update right-hand side A^T * b
			for (int i = 0; i < N_COEFFICIENTS; i++) {
				ATb.data[i] += rowA[i] * z;
			}
		}

		// set up Cholesky decomposition for A^T * A x = A^T * b (only upper triangle of A^T * A is used)
		final LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.chol(N_COEFFICIENTS);
		solver.setA(ATA);

		// coefficients are modified in place
		final DMatrixRMaj x = new DMatrixRMaj(N_COEFFICIENTS, 1);
		x.setData(coefficients);
		solver.solve(ATb, x);
	}

	@Override
	public void set(final QuadraticBackground quadraticBackground) {

	}

	@Override
	public QuadraticBackground copy() {
		return null;
	}

	@Override
	public double[] apply(final double[] location) {
		final double[] result = location.clone();
		applyInPlace(result);
		return result;
	}

	@Override
	public void applyInPlace(final double[] location) {
		final double x = location[0];
		final double y = location[1];
		final double result = coefficients[5] * x * x
				+ coefficients[4] * x * y
				+ coefficients[3] * y * y
				+ coefficients[2] * x
				+ coefficients[1] * y
				+ coefficients[0];
		location[0] = result;
		location[1] = 0.0;
	}

	public double[] getCoefficients() {
		return coefficients;
	}

	@Override
	public String toString() {
		return "QuadraticBackground{ "
				+ coefficients[5] + " x^2 + "
				+ coefficients[4] + " xy + "
				+ coefficients[3] + " y^2 + "
				+ coefficients[2] + " x + "
				+ coefficients[1] + " y + "
				+ coefficients[0] + " }";
	}
}
