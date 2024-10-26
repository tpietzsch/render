package org.janelia.render.client.spark.n5;

import java.io.IOException;

import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.spark.supplier.N5WriterSupplier;

/**
 * Utilities for N5 operations.
 *
 * @author Eric Trautman
 */
public class Util {

    // serializable supplier for spark
    public static class N5PathSupplier implements N5WriterSupplier {
        private final String path;
        public N5PathSupplier(final String path) {
            this.path = path;
        }
        @Override
        public N5Writer get()
                throws IOException {
            return new N5FSWriter(path);
        }
    }


}
