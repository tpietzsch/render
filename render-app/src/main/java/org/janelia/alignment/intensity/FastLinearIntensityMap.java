package org.janelia.alignment.intensity;

import net.imglib2.Dimensions;
import net.imglib2.algorithm.blocks.AbstractBlockProcessor;
import net.imglib2.algorithm.blocks.BlockProcessor;
import net.imglib2.algorithm.blocks.BlockSupplier;
import net.imglib2.algorithm.blocks.ClampType;
import net.imglib2.algorithm.blocks.DefaultUnaryBlockOperator;
import net.imglib2.algorithm.blocks.UnaryBlockOperator;
import net.imglib2.blocks.TempArray;
import net.imglib2.type.NativeType;
import net.imglib2.type.PrimitiveType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Cast;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;

import java.util.Arrays;
import java.util.function.Function;

import static net.imglib2.type.PrimitiveType.FLOAT;
import static net.imglib2.util.Util.safeInt;


public class FastLinearIntensityMap {

    /**
     * Holds flattened coefficient array and dimensions.
     */
    public static class Coefficients {

        final int[] size;
        final int[] strides;

        /**
         * {@code flattenedCoefficients[i]} holds the flattened array of the i-the coefficient.
         * That is, for linear map {@code y=a*x+b}, {@code flattenedCoefficients[0]} holds all the {@code a}s and
         * {@code flattenedCoefficients[1]} holds all the {@code b}s.
         */
        final float[][] flattenedCoefficients;

        public Coefficients(
                final double[][] coefficients,
                final int... fieldDimensions) {
            this((c, f) -> coefficients[f][c], coefficients[0].length, fieldDimensions);
        }

        @FunctionalInterface
        public interface CoefficientFunction {
            double apply(int coefficientIndex, int flattenedFieldIndex);
        }

        public Coefficients(
                final CoefficientFunction coefficients,
                final int numCoefficients,
                final int... fieldDimensions) {
            final int numElements = safeInt(Intervals.numElements(fieldDimensions));
            size = fieldDimensions.clone();
            strides = IntervalIndexer.createAllocationSteps(size);
            flattenedCoefficients = new float[numCoefficients][numElements];
            for (int j = 0; j < numCoefficients; ++j) {
                for (int i = 0; i < numElements; ++i) {
                    flattenedCoefficients[j][i] = (float) coefficients.apply(j, i);
                }
            }
        }

        int size(final int d) {
            return size[d];
        }

        int stride(final int d) {
            return strides[d];
        }

        int numDimensions() {
            return size.length;
        }
    }

    /**
     * Apply an interpolated linear intensity map to blocks of the standard
     * ImgLib2 {@code RealType}s.
     * <p>
     * The returned factory function creates an operator matching the type a
     * given input {@code BlockSupplier<T>}.
     *
     * @param coefficients
     * @param imageDimensions
     * @param <T>             the input/output type
     * @return factory for {@code UnaryBlockOperator} to intensity-map blocks of type {@code T}
     */
    public static <T extends NativeType<T>> Function<BlockSupplier<T>, UnaryBlockOperator<T, T>> linearIntensityMap(
            final Coefficients coefficients,
            final Dimensions imageDimensions) {
        return s -> createLinearIntensityMapOperator(
                s.getType(), s.numDimensions(), coefficients, imageDimensions, ClampType.CLAMP);
    }

    /**
     * Create a {@code UnaryBlockOperator} to apply an interpolated linear
     * intensity map to blocks of the standard ImgLib2 {@code RealType}s.
     *
     * @param type            instance of the input type
     * @param coefficients
     * @param imageDimensions
     * @param clampType
     * @param <T>             the input/output type
     * @return {@code UnaryBlockOperator} to intensity-map blocks of type {@code T}
     */
    public static <T extends NativeType<T>> UnaryBlockOperator<T, T> createLinearIntensityMapOperator(
            final T type,
            final int numDimensions,
            final Coefficients coefficients,
            final Dimensions imageDimensions,
            final ClampType clampType) {
        if (numDimensions != imageDimensions.numDimensions() || numDimensions != coefficients.numDimensions()) {
            throw new IllegalArgumentException("numDimensions mismatch");
        }

        final FloatType floatType = new FloatType();
        final LinearIntensityMapProcessor processor = new LinearIntensityMapProcessor(TransformCoefficients.create(imageDimensions, coefficients));
        final UnaryBlockOperator<FloatType, FloatType> op = new DefaultUnaryBlockOperator<>(floatType, floatType, numDimensions,numDimensions, processor);
        return op.adaptSourceType(type, ClampType.NONE).adaptTargetType(type, clampType);
    }

    /**
     * Apply scaling and translation to {@code Coefficients} array.
     * Use {@link #line} to extract an X line segment of transformed interpolated coefficients.
     */
    static class TransformCoefficients {

        private final Coefficients coefficients;
        private final int n;
        private final double[] g;
        private final double[] h;
        private final float[] sof;
        private final int[] S;
        private final TempArray< float[] >[] tempArrays;

        static TransformCoefficients create(final Dimensions target, final Coefficients coefficients) {
            final int n = coefficients.numDimensions();

            final double[] scale = new double[n];
            Arrays.setAll(scale, d -> target.dimension(d) / coefficients.size(d));
            // TODO: probably a bug!? integer division in floating point context...
            //       It should be this instead:
            //           Arrays.setAll(scale, d -> (double) target.dimension(d) / coefficients.size(d));
            //       but we keep the bug for compatibility for now

            // shift everything in xy by 0.5 pixels so the coefficient sits in the middle of the block
            final double[] translation = new double[n];
            Arrays.setAll(translation, d -> 0.5 * scale[d]);

            return new TransformCoefficients(scale, translation, coefficients);
        }

        TransformCoefficients(final double[] scale, final double[] translation, final Coefficients coefficients) {
            this.coefficients = coefficients;
            n = coefficients.numDimensions();
            g = new double[n];
            h = new double[n];
            sof = new float[n];
            S = new int[n];
            Arrays.setAll(g, d -> 1.0 / scale[d]);
            Arrays.setAll(h, d -> -translation[d] * g[d]);
            tempArrays = Cast.unchecked( new TempArray[ n ] );
            Arrays.setAll( tempArrays, i -> TempArray.forPrimitiveType( FLOAT ) );
        }

        private TransformCoefficients( TransformCoefficients t ) {
            this.coefficients = t.coefficients;
            this.n = t.n;
            this.g = t.g;
            this.h = t.h;
            this.sof = new float[n];
            this.S = new int[n];
            tempArrays = Cast.unchecked( new TempArray[ n ] );
            Arrays.setAll( tempArrays, i -> TempArray.forPrimitiveType( FLOAT ) );
        }

        void line(final long[] start, final int len0, final int coeff_index, final float[] target) {

            final float[] coeff = coefficients.flattenedCoefficients[coeff_index];

            for (int d = 0; d < n; ++d) {
                sof[d] = (float) (g[d] * start[d] + h[d]);
                S[d] = (int) Math.floor(sof[d]);
            }
            final int Smax = (int) Math.floor(len0 * g[0] + sof[0]) + 1;
            final int L0 = Smax - S[0] + 1;

            // interpolate all dimensions > 0 into tmp array
            final float[] tmp = tempArrays[0].get(L0);
            int o = 0;
            for (int d = 1; d < n; ++d) {
                final int posd = Math.min(Math.max(S[d], 0), coefficients.size(d) - 1);
                o += coefficients.stride(d) * posd;
            }
            interpolate_coeff_line(1, L0, coeff, tmp, o);

            // interpolate in dim0 into target array
            float s0f = sof[0] - S[0];
            final float step = (float) g[0];
            for (int x = 0; x < len0; ++x) {
                final int s0 = (int) s0f;
                final float r0 = s0f - s0;
                final float a0 = tmp[s0];
                final float a1 = tmp[s0+1];
                target[x] = a0 + r0 * (a1 - a0);
                s0f += step;
            }
        }

        private void interpolate_coeff_line(final int d, final int L0, final float[] coeff, final float[] dest, final int o) {
            if (d < n) {
                interpolate_coeff_line(d + 1, L0, coeff, dest, o);
                if (S[d] >= 0 && S[d] <= coefficients.size(d) - 2) {
                    final float[] tmp = tempArrays[d].get(L0);
                    interpolate_coeff_line(d + 1, L0, coeff, tmp, o + coefficients.stride(d));
                    final float r = sof[d] - S[d];
                    interpolate(dest, tmp,r, L0);
                }
            } else {
                padded_coeff_line(S[0], L0, coeff, dest, o);
            }
        }

        private void padded_coeff_line(final int x0, final int len, final float[] coeff, final float[] dest, final int o)
        {
            final int w = coefficients.size(0);
            final int pad_left = Math.max(0, Math.min(len, -x0));
            final int pad_right = Math.max(0, Math.min(len, x0 + len - w));
            final int copy_len = len - pad_left - pad_right;

            if (pad_left > 0) {
                Arrays.fill(dest, 0, pad_left, coeff[o]);
            }
            if (copy_len > 0) {
                System.arraycopy(coeff, o + x0 + pad_left, dest, pad_left, copy_len);
            }
            if (pad_right > 0) {
                Arrays.fill(dest, len - pad_right, len, coeff[w - 1]);
            }
        }

        // elements a[i] are set to (1-r) * a[i] + r * b[i]
        private void interpolate(final float[] a, final float[] b, final float r, final int len) {
            for (int i = 0; i < len; ++i) {
                a[i] += r * (b[i] - a[i]);
            }
        }

        TransformCoefficients independentCopy() {
            return new TransformCoefficients(this);
        }

        int numDimensions() {
            return n;
        }
    }


    /**
     * Apply LinearIntensityMap defined by {@code TransformCoefficients} to {@code float[]} blocks.
     */
    static class LinearIntensityMapProcessor extends AbstractBlockProcessor<float[], float[]> {

        private final TransformCoefficients coefficients;
        private final int[] sourceStride;
        private final long[] start;
        private final TempArray< float[] >[] tempArrays;

        public LinearIntensityMapProcessor(final TransformCoefficients coefficients) {
            super(PrimitiveType.FLOAT, coefficients.numDimensions());
            this.coefficients = coefficients;

            final int n = coefficients.numDimensions();
            sourceStride = new int[n];
            start = new long[n];

            tempArrays = Cast.unchecked(new TempArray[4]);
            Arrays.setAll(tempArrays, i -> TempArray.forPrimitiveType(FLOAT));
        }

        private LinearIntensityMapProcessor(final LinearIntensityMapProcessor processor) {
            super(processor);
            this.coefficients = processor.coefficients.independentCopy();

            final int n = coefficients.numDimensions();
            sourceStride = new int[n];
            start = new long[n];

            tempArrays = Cast.unchecked(new TempArray[4]);
            Arrays.setAll(tempArrays, i -> TempArray.forPrimitiveType(FLOAT));
        }

        @Override
        public BlockProcessor<float[], float[]> independentCopy() {
            return new LinearIntensityMapProcessor(this);
        }

        @Override
        public void compute(final float[] src, final float[] dst) {
            start[0] = sourcePos[0];
            IntervalIndexer.createAllocationSteps(sourceSize, sourceStride);
            final int len = sourceSize[0];
            final float[] tmp_coeff0 = tempArrays[0].get(len);
            final float[] tmp_coeff1 = tempArrays[1].get(len);
            final float[] tmp_lsrc = tempArrays[2].get(len);
            final float[] tmp_ldst = tempArrays[3].get(len);
            compute(sourcePos.length - 1, src, dst, 0, tmp_coeff0, tmp_coeff1, tmp_lsrc, tmp_ldst);
        }

        private void compute(final int d, final float[] src, final float[] dst, final int o,
                             final float[] tmp_coeff0, final float[] tmp_coeff1,
                             final float[] tmp_lsrc, final float[] tmp_ldst) {
            final int len = sourceSize[d];
            if (d > 0) {
                final long p0 = sourcePos[d];
                for (int p = 0; p < len; ++p) {
                    start[d] = p0 + p;
                    compute(d - 1, src, dst, o + p * sourceStride[d], tmp_coeff0, tmp_coeff1, tmp_lsrc, tmp_ldst);
                }
            } else {
                coefficients.line(start, len, 0, tmp_coeff0);
                coefficients.line(start, len, 1, tmp_coeff1);
                System.arraycopy(src, o, tmp_lsrc, 0, len);
                map(tmp_lsrc, tmp_coeff0, tmp_coeff1, tmp_ldst, len);
                System.arraycopy(tmp_ldst, 0, dst, o, len);
            }
        }

        private static void map(final float[] src, final float[] a, final float[] b, final float[] dst, final int len) {
            for (int x = 0; x < len; ++x) {
                dst[x] = src[x] * a[x] + b[x];
            }
        }
    }
}
