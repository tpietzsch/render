package org.janelia.alignment.filter;

import ij.process.ImageProcessor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A simple filter that applies an affine transformation y = a*x + b to the intensity values of an image.
 */
public class AffineIntensityFilter implements Filter {

    private double a;
    private double b;

    // empty constructor required to create instances from specifications
    @SuppressWarnings("unused")
    public AffineIntensityFilter() {
        this(0.0, 0.0);
    }

    public AffineIntensityFilter(final double a, final double b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public void init(final Map<String, String> params) {
        this.a = Filter.getDoubleParameter("a", params);
        this.b = Filter.getDoubleParameter("b", params);
    }

    @Override
    public Map<String, String> toParametersMap() {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put("a", String.valueOf(a));
        map.put("b", String.valueOf(b));
        return map;
    }

    @Override
    public void process(final ImageProcessor ip, final double scale) {
        for (int y = 0; y < ip.getHeight(); y++) {
            for (int x = 0; x < ip.getWidth(); x++) {
                final float intensity = ip.getf(x, y);
                ip.setf(x, y, (float) (a * intensity + b));
            }
        }
    }
}
