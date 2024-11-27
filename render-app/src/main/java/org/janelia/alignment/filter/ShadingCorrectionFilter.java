package org.janelia.alignment.filter;

import ij.process.ImageProcessor;
import org.janelia.alignment.filter.emshading.FourthOrderShading;
import org.janelia.alignment.filter.emshading.QuadraticShading;
import org.janelia.alignment.filter.emshading.ShadingModel;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class ShadingCorrectionFilter implements Filter {

    public enum ShadingCorrectionMethod {
        QUADRATIC(QuadraticShading::new),
        FOURTH_ORDER(FourthOrderShading::new);

        private final Function<double[], ShadingModel> modelFactory;

        ShadingCorrectionMethod(final Function<double[], ShadingModel> modelFactory) {
            this.modelFactory = modelFactory;
        }

        public ShadingModel create(final double[] coefficients) {
            return modelFactory.apply(coefficients);
        }

        public static ShadingCorrectionMethod fromString(final String method) {
            for (final ShadingCorrectionMethod shadingCorrectionMethod : values()) {
                if (shadingCorrectionMethod.name().equalsIgnoreCase(method)) {
                    return shadingCorrectionMethod;
                }
            }
            throw new IllegalArgumentException("Unknown shading correction method: " + method);
        }

        public static ShadingCorrectionMethod forModel(final ShadingModel model) {
            if (model instanceof QuadraticShading) {
                return QUADRATIC;
            } else if (model instanceof FourthOrderShading) {
                return FOURTH_ORDER;
            } else {
                throw new IllegalArgumentException("Unknown shading model class: " + model.getClass().getName());
            }
        }
    }

    private ShadingCorrectionMethod correctionMethod;
    private double[] coefficients;

    // empty constructor required to create instances from specifications
    @SuppressWarnings("unused")
    public ShadingCorrectionFilter() {
    }

    public ShadingCorrectionFilter(final ShadingModel model) {
        this(ShadingCorrectionMethod.forModel(model), model.getCoefficients());
    }

    public ShadingCorrectionFilter(final ShadingCorrectionMethod correctionMethod, final double[] coefficients) {
        this.correctionMethod = correctionMethod;
        this.coefficients = coefficients;
    }

    @Override
    public void init(final Map<String, String> params) {
        this.correctionMethod = ShadingCorrectionMethod.fromString(Filter.getStringParameter("correctionMethod", params));
        final String[] rawCoefficients = Filter.getCommaSeparatedStringParameter("coefficients", params);
        this.coefficients = Arrays.stream(rawCoefficients).mapToDouble(Double::parseDouble).toArray();
    }

    @Override
    public Map<String, String> toParametersMap() {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put("correctionMethod", correctionMethod.name());
        map.put("coefficients", Arrays.toString(coefficients));
        return map;
    }

    @Override
    public void process(final ImageProcessor ip, final double scale) {
        // transform pixel coordinates into [-1, 1] x [-1, 1]
        final double scaleX = ip.getWidth() / 2.0;
        final double scaleY = ip.getHeight() / 2.0;
        final ShadingModel shadingModel = correctionMethod.create(coefficients);

        // subtract shading model from image
        final double[] location = new double[2];
        for (int i = 0; i < ip.getWidth(); i++) {
            for (int j = 0; j < ip.getHeight(); j++) {
                location[0] = ShadingModel.scaleCoordinate(i, scaleX);
                location[1] = ShadingModel.scaleCoordinate(j, scaleY);
                shadingModel.applyInPlace(location);

                final double value = ip.getPixelValue(i, j) - location[0];
                ip.putPixelValue(i, j, value);
            }
        }
    }
}
