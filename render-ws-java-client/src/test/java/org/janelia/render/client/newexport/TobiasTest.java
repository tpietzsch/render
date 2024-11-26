package org.janelia.render.client.newexport;

import ij.ImagePlus;

import java.io.File;
import java.util.regex.Pattern;

import mpicbg.trakem2.transform.TransformMeshMappingWithMasks.ImageProcessorWithMasks;

import org.janelia.alignment.RenderParameters;
import org.janelia.alignment.Renderer;
import org.janelia.alignment.spec.Bounds;
import org.janelia.alignment.spec.stack.StackMetaData;
import org.janelia.alignment.util.ImageProcessorCache;
import org.janelia.render.client.solver.visualize.RenderTools;

import net.imglib2.Interval;

public class TobiasTest {

    public static void main(final String[] args)
            throws Exception {

        final String baseUrl = "http://em-services-1.int.janelia.org:8080/render-ws/v1";

        final String owner = args.length < 1 ? "pietzscht" : args[0];
        final String project = args.length < 2 ? "w60_serial_290_to_299" : args[1];
        final String stack = args.length < 3 ? "w60_s296_r00_small_test_align" : args[2];

        final StackMetaData meta = RenderTools.openStackMetaData(baseUrl, owner, project, stack);
        final Bounds stackBounds = meta.getStackBounds();

        final Interval interval = RenderTools.stackBounds(meta);
        final long xMin = interval.min(0);
        final long yMin = interval.min(1);
        final long zMin = interval.min(2);
        final long zMax = interval.max(2);

        // hack to replace source paths with local paths:
        //   will change imageUrl values like file:/nrs/hess/ibeammsem/system_02/wafers/wafer_60/acquisition/scans/scan_020/slabs/slab_0399/mfovs/mfov_0014/sfov_001.png
        //   to file:/Users/trautmane/Desktop/local_wafer_60_scans/scan_020/slabs/slab_0399/mfovs/mfov_0014/sfov_001.png
        final Pattern hackSourcePathPattern = Pattern.compile("file:.*scans/");
        final String hackReplacementString = "file:/Users/trautmane/Desktop/local_wafer_60_scans/";

        // alternative:
        //   map nrs ( e.g. on Mac use smb://nrsv.hhmi.org/hess ) and symbolically link /nrs/hess to /Volumes/hess
        // final Pattern hackSourcePathPattern = null;
        // final String hackReplacementString = null;

        for (long z = zMin; z <= zMax; z++) {
            final ImageProcessorWithMasks ipwm =
                    renderUncachedImage(baseUrl, owner, project, stack,
                                        xMin, yMin, z,
                                        stackBounds.getWidth(), stackBounds.getHeight(), 1.0,
                                        hackSourcePathPattern, hackReplacementString);
            final String title = "z_" + z + "_" + stack;
            final ImagePlus imagePlus = new ImagePlus(title, ipwm.ip);
            imagePlus.show();
        }
    }

    public static ImageProcessorWithMasks renderUncachedImage(final String baseUrl,
                                                              final String owner,
                                                              final String project,
                                                              final String stack,
                                                              final long minX,
                                                              final long minY,
                                                              final long z,
                                                              final long width,
                                                              final long height,
                                                              final double scale,
                                                              final Pattern hackSourcePathPattern,
                                                              final String hackReplacementString)
            throws IllegalArgumentException {

        final String renderParametersUrlString = String.format(RenderTools.renderParametersFormat,
                                                               baseUrl,
                                                               owner,
                                                               project,
                                                               stack,
                                                               z, // full res coordinates
                                                               minX, // full res coordinates
                                                               minY, // full res coordinates
                                                               width, // full res coordinates
                                                               height, // full res coordinates
                                                               scale);

        final RenderParameters renderParameters = RenderParameters.loadFromUrl(renderParametersUrlString);

        renderParameters.getTileSpecs().forEach(ts -> {
            if ((hackSourcePathPattern != null) && (hackReplacementString != null)) {
                ts.replaceFirstChannelImageUrl(hackSourcePathPattern, hackReplacementString);
            }
            final File tile = new File(ts.getFirstMipmapEntry().getValue().getImageFilePath());
            if (! tile.exists()) {
                throw new IllegalArgumentException("tile id " + ts.getTileId() + " image file " + tile +
                                                   " does not exist, storage filesystem may not be mapped locally");
            }
        });

        return Renderer.renderImageProcessorWithMasks(renderParameters, ImageProcessorCache.DISABLED_CACHE);
    }
}
