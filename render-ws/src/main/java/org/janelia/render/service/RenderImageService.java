package org.janelia.render.service;

import java.io.File;
import java.net.UnknownHostException;
import java.nio.file.Paths;

import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

import org.janelia.alignment.RenderParameters;
import org.janelia.alignment.Utils;
import org.janelia.alignment.spec.Bounds;
import org.janelia.alignment.spec.stack.StackId;
import org.janelia.alignment.spec.stack.StackMetaData;
import org.janelia.alignment.spec.stack.StackStats;
import org.janelia.render.service.model.RenderQueryParameters;
import org.janelia.render.service.util.RenderServiceUtil;
import org.janelia.render.service.util.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * APIs for rendering images server-side.
 *
 * @author Eric Trautman
 */
@Path("/")
@Api(tags = {"Image APIs"})
public class RenderImageService {

    private final RenderDataService renderDataService;

    @SuppressWarnings("UnusedDeclaration")
    public RenderImageService()
            throws UnknownHostException {
        this(new RenderDataService());
    }

    private RenderImageService(final RenderDataService renderDataService) {
        this.renderDataService = renderDataService;
    }

    @Path("v1/owner/{owner}/jpeg-image")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(RenderServiceUtil.IMAGE_JPEG_MIME_TYPE)
    @ApiOperation(
            tags = "Spec Image APIs",
            value = "Render JPEG image from a provided spec")
    public Response renderJpegImageFromProvidedParameters(@PathParam("owner") final String owner,
                                                          final RenderParameters renderParameters) {
        return RenderServiceUtil.renderImageStream(renderParameters,
                                                   Utils.JPEG_FORMAT,
                                                   RenderServiceUtil.IMAGE_JPEG_MIME_TYPE,
                                                   null,
                                                   ResponseHelper.NO_CACHE_HELPER);
    }

    @Path("v1/owner/{owner}/png-image")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(RenderServiceUtil.IMAGE_PNG_MIME_TYPE)
    @ApiOperation(
            tags = "Spec Image APIs",
            value = "Render PNG image from a provided spec")
    public Response renderPngImageFromProvidedParameters(@PathParam("owner") final String owner,
                                                         final RenderParameters renderParameters) {
        return RenderServiceUtil.renderImageStream(renderParameters,
                                                   Utils.PNG_FORMAT,
                                                   RenderServiceUtil.IMAGE_PNG_MIME_TYPE,
                                                   null,
                                                   ResponseHelper.NO_CACHE_HELPER);
    }

    @Path("v1/owner/{owner}/tiff-image")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(RenderServiceUtil.IMAGE_TIFF_MIME_TYPE)
    @ApiOperation(
            tags = "Spec Image APIs",
            value = "Render TIFF image from a provided spec")
    public Response renderTiffImageFromProvidedParameters(@PathParam("owner") final String owner,
                                                          final RenderParameters renderParameters) {
        return RenderServiceUtil.renderImageStream(renderParameters,
                                                   Utils.TIFF_FORMAT,
                                                   RenderServiceUtil.IMAGE_TIFF_MIME_TYPE,
                                                   null,
                                                   ResponseHelper.NO_CACHE_HELPER);
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/z/{z}/jpeg-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_JPEG_MIME_TYPE)
    @ApiOperation(
            tags = "Section Image APIs",
            value = "Render JPEG image for a section")
    public Response renderJpegImageForZ(@PathParam("owner") final String owner,
                                        @PathParam("project") final String project,
                                        @PathParam("stack") final String stack,
                                        @PathParam("z") final Double z,
                                        @BeanParam final RenderQueryParameters renderQueryParameters,
                                        @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                        @Context final Request request) {

        LOG.info("renderJpegImageForZ: entry, owner={}, project={}, stack={}, z={}",
                 owner, project, stack, z);

        renderQueryParameters.setDefaultScale(0.01);

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    renderDataService.getRenderParametersForZ(owner, project, stack, z, renderQueryParameters);
            return RenderServiceUtil.renderJpegImage(renderParameters, maxTileSpecsToRender, responseHelper);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/z/{z}/png-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_PNG_MIME_TYPE)
    @ApiOperation(
            tags = "Section Image APIs",
            value = "Render PNG image for a section")
    public Response renderPngImageForZ(@PathParam("owner") final String owner,
                                       @PathParam("project") final String project,
                                       @PathParam("stack") final String stack,
                                       @PathParam("z") final Double z,
                                       @BeanParam final RenderQueryParameters renderQueryParameters,
                                       @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                       @Context final Request request) {

        LOG.info("renderPngImageForZ: entry, owner={}, project={}, stack={}, z={}",
                 owner, project, stack, z);

        renderQueryParameters.setDefaultScale(0.01);

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    renderDataService.getRenderParametersForZ(owner, project, stack, z, renderQueryParameters);
            return RenderServiceUtil.renderPngImage(renderParameters, maxTileSpecsToRender, responseHelper);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/z/{z}/tiff-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_TIFF_MIME_TYPE)
    @ApiOperation(
            tags = "Section Image APIs",
            value = "Render TIFF image for a section")
    public Response renderTiffImageForZ(@PathParam("owner") final String owner,
                                        @PathParam("project") final String project,
                                        @PathParam("stack") final String stack,
                                        @PathParam("z") final Double z,
                                        @BeanParam final RenderQueryParameters renderQueryParameters,
                                        @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                        @Context final Request request) {

        LOG.info("renderTiffImageForZ: entry, owner={}, project={}, stack={}, z={}",
                 owner, project, stack, z);

        renderQueryParameters.setDefaultScale(0.01);

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    renderDataService.getRenderParametersForZ(owner, project, stack, z, renderQueryParameters);
            return RenderServiceUtil.renderTiffImage(renderParameters, maxTileSpecsToRender, responseHelper);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/z/{z}/box/{x},{y},{width},{height},{scale}/jpeg-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_JPEG_MIME_TYPE)
    @ApiOperation(
            tags = "Bounding Box Image APIs",
            value = "Render JPEG image for the specified bounding box")
    public Response renderJpegImageForBox(@PathParam("owner") final String owner,
                                          @PathParam("project") final String project,
                                          @PathParam("stack") final String stack,
                                          @PathParam("x") final Double x,
                                          @PathParam("y") final Double y,
                                          @PathParam("z") final Double z,
                                          @PathParam("width") final Integer width,
                                          @PathParam("height") final Integer height,
                                          @PathParam("scale") final Double scale,
                                          @BeanParam final RenderQueryParameters renderQueryParameters,
                                          @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                          @Context final Request request) {

        LOG.info("renderJpegImageForBox: entry");

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    getRenderParametersForGroupBox(owner, project, stack, null,
                                                   x, y, z, width, height, scale,
                                                   renderQueryParameters);
            return RenderServiceUtil.renderJpegImage(renderParameters, maxTileSpecsToRender, responseHelper);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/dvid/imagetile/raw/xy/{width}_{height}/{x}_{y}_{z}/jpg")
    @GET
    @Produces(RenderServiceUtil.IMAGE_JPEG_MIME_TYPE)
    @ApiOperation(
            tags = {"Bounding Box Image APIs", "DVID Style APIs"},
            value = "DVID style API to render JPEG image for the specified bounding box")
    public Response renderJpegImageForDvidBox(@PathParam("owner") final String owner,
                                              @PathParam("project") final String project,
                                              @PathParam("stack") final String stack,
                                              @PathParam("x") final Double x,
                                              @PathParam("y") final Double y,
                                              @PathParam("z") final Double z,
                                              @PathParam("width") final Integer width,
                                              @PathParam("height") final Integer height,
                                              @BeanParam final RenderQueryParameters renderQueryParameters,
                                              @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                              @Context final Request request) {
        return renderJpegImageForBox(owner, project, stack, x, y, z, width, height, null,
                                     renderQueryParameters, maxTileSpecsToRender, request);
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/z/{z}/box/{x},{y},{width},{height},{scale}/png-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_PNG_MIME_TYPE)
    @ApiOperation(
            tags = "Bounding Box Image APIs",
            value = "Render PNG image for the specified bounding box")
    public Response renderPngImageForBox(@PathParam("owner") final String owner,
                                         @PathParam("project") final String project,
                                         @PathParam("stack") final String stack,
                                         @PathParam("x") final Double x,
                                         @PathParam("y") final Double y,
                                         @PathParam("z") final Double z,
                                         @PathParam("width") final Integer width,
                                         @PathParam("height") final Integer height,
                                         @PathParam("scale") final Double scale,
                                         @BeanParam final RenderQueryParameters renderQueryParameters,
                                         @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                         @Context final Request request) {

        LOG.info("renderPngImageForBox: entry");

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    getRenderParametersForGroupBox(owner, project, stack, null,
                                                   x, y, z, width, height, scale,
                                                   renderQueryParameters);
            return RenderServiceUtil.renderPngImage(renderParameters, maxTileSpecsToRender, responseHelper);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/dvid/imagetile/raw/xy/{width}_{height}/{x}_{y}_{z}/png")
    @GET
    @Produces(RenderServiceUtil.IMAGE_PNG_MIME_TYPE)
    @ApiOperation(
            tags = {"Bounding Box Image APIs", "DVID Style APIs"},
            value = "DVID style API to render PNG image for the specified bounding box")
    public Response renderPngImageForDvidBox(@PathParam("owner") final String owner,
                                             @PathParam("project") final String project,
                                             @PathParam("stack") final String stack,
                                             @PathParam("x") final Double x,
                                             @PathParam("y") final Double y,
                                             @PathParam("z") final Double z,
                                             @PathParam("width") final Integer width,
                                             @PathParam("height") final Integer height,
                                             @BeanParam final RenderQueryParameters renderQueryParameters,
                                             @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                             @Context final Request request) {
        return renderPngImageForBox(owner, project, stack, x, y, z, width, height, null,
                                    renderQueryParameters, maxTileSpecsToRender, request);
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/z/{z}/box/{x},{y},{width},{height},{scale}/tiff-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_TIFF_MIME_TYPE)
    @ApiOperation(
            tags = "Bounding Box Image APIs",
            value = "Render TIFF image for the specified bounding box")
    public Response renderTiffImageForBox(@PathParam("owner") final String owner,
                                          @PathParam("project") final String project,
                                          @PathParam("stack") final String stack,
                                          @PathParam("x") final Double x,
                                          @PathParam("y") final Double y,
                                          @PathParam("z") final Double z,
                                          @PathParam("width") final Integer width,
                                          @PathParam("height") final Integer height,
                                          @PathParam("scale") final Double scale,
                                          @BeanParam final RenderQueryParameters renderQueryParameters,
                                          @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                          @Context final Request request) {

        LOG.info("renderTiffImageForBox: entry");

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    getRenderParametersForGroupBox(owner, project, stack, null,
                                                   x, y, z, width, height, scale,
                                                   renderQueryParameters);
            return RenderServiceUtil.renderTiffImage(renderParameters, maxTileSpecsToRender, responseHelper);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/z/{z}/box/{x},{y},{width},{height},{scale}/tiff16-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_TIFF_MIME_TYPE)
    @ApiOperation(
            tags = "Bounding Box Image APIs",
            value = "Render TIFF image for the specified bounding box")
    public Response renderTiff16ImageForBox(@PathParam("owner") final String owner,
                                            @PathParam("project") final String project,
                                            @PathParam("stack") final String stack,
                                            @PathParam("x") final Double x,
                                            @PathParam("y") final Double y,
                                            @PathParam("z") final Double z,
                                            @PathParam("width") final Integer width,
                                            @PathParam("height") final Integer height,
                                            @PathParam("scale") final Double scale,
                                            @BeanParam final RenderQueryParameters renderQueryParameters,
                                            @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                            @Context final Request request) {

        LOG.info("renderTiffImageForBox: entry");

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    getRenderParametersForGroupBox(owner, project, stack, null,
                                                   x, y, z, width, height, scale,
                                                   renderQueryParameters);
            return RenderServiceUtil.renderTiffImage(renderParameters, maxTileSpecsToRender, responseHelper, true);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/z/{z}/box/{x},{y},{width},{height},{scale}/png16-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_PNG_MIME_TYPE)
    @ApiOperation(
            tags = "Bounding Box Image APIs",
            value = "Render 16-bit PNG image for the specified bounding box")
    public Response renderPng16ImageForBox(@PathParam("owner") final String owner,
                                           @PathParam("project") final String project,
                                           @PathParam("stack") final String stack,
                                           @PathParam("x") final Double x,
                                           @PathParam("y") final Double y,
                                           @PathParam("z") final Double z,
                                           @PathParam("width") final Integer width,
                                           @PathParam("height") final Integer height,
                                           @PathParam("scale") final Double scale,
                                           @BeanParam final RenderQueryParameters renderQueryParameters,
                                           @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                           @Context final Request request) {

        LOG.info("renderPng16ImageForBox: entry");

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    getRenderParametersForGroupBox(owner, project, stack, null,
                                                   x, y, z, width, height, scale,
                                                   renderQueryParameters);
            return RenderServiceUtil.renderPngImage(renderParameters, maxTileSpecsToRender, responseHelper, true);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/z/{z}/box/{x},{y},{width},{height},{scale}/raw16-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_RAW_MIME_TYPE)
    @ApiOperation(
            tags = "Bounding Box Image APIs",
            value = "Render 16-bit raw image for the specified bounding box")
    public Response renderRaw16ImageForBox(@PathParam("owner") final String owner,
                                           @PathParam("project") final String project,
                                           @PathParam("stack") final String stack,
                                           @PathParam("x") final Double x,
                                           @PathParam("y") final Double y,
                                           @PathParam("z") final Double z,
                                           @PathParam("width") final Integer width,
                                           @PathParam("height") final Integer height,
                                           @PathParam("scale") final Double scale,
                                           @BeanParam final RenderQueryParameters renderQueryParameters,
                                           @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                           @Context final Request request) {

        LOG.info("renderRaw16ImageForBox: entry");

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    getRenderParametersForGroupBox(owner, project, stack, null,
                                                   x, y, z, width, height, scale,
                                                   renderQueryParameters);
            return RenderServiceUtil.renderRawImage(renderParameters, maxTileSpecsToRender, responseHelper, true);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/z/{z}/box/{x},{y},{width},{height},{scale}/raw-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_RAW_MIME_TYPE)
    @ApiOperation(
            tags = "Bounding Box Image APIs",
            value = "Render raw image for the specified bounding box")
    public Response renderRawImageForBox(@PathParam("owner") final String owner,
                                           @PathParam("project") final String project,
                                           @PathParam("stack") final String stack,
                                           @PathParam("x") final Double x,
                                           @PathParam("y") final Double y,
                                           @PathParam("z") final Double z,
                                           @PathParam("width") final Integer width,
                                           @PathParam("height") final Integer height,
                                           @PathParam("scale") final Double scale,
                                           @BeanParam final RenderQueryParameters renderQueryParameters,
                                           @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                           @Context final Request request) {

        LOG.info("renderRawImageForBox: entry");

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    getRenderParametersForGroupBox(owner, project, stack, null,
                                                   x, y, z, width, height, scale,
                                                   renderQueryParameters);
            return RenderServiceUtil.renderRawImage(renderParameters, maxTileSpecsToRender, responseHelper, false);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/dvid/imagetile/raw/xy/{width}_{height}/{x}_{y}_{z}/tif")
    @GET
    @Produces(RenderServiceUtil.IMAGE_TIFF_MIME_TYPE)
    @ApiOperation(
            tags = {"Bounding Box Image APIs", "DVID Style APIs"},
            value = "DVID style API to render TIFF image for the specified bounding box")
    public Response renderTiffImageForDvidBox(@PathParam("owner") final String owner,
                                              @PathParam("project") final String project,
                                              @PathParam("stack") final String stack,
                                              @PathParam("x") final Double x,
                                              @PathParam("y") final Double y,
                                              @PathParam("z") final Double z,
                                              @PathParam("width") final Integer width,
                                              @PathParam("height") final Integer height,
                                              @BeanParam final RenderQueryParameters renderQueryParameters,
                                              @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                              @Context final Request request) {
        return renderTiffImageForBox(owner, project, stack, x, y, z, width, height, null,
                                     renderQueryParameters, maxTileSpecsToRender, request);
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/group/{groupId}/z/{z}/box/{x},{y},{width},{height},{scale}/jpeg-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_JPEG_MIME_TYPE)
    @ApiOperation(
            tags = "Bounding Box Image APIs",
            value = "Render JPEG image for the specified bounding box and groupId")
    public Response renderJpegImageForGroupBox(@PathParam("owner") final String owner,
                                               @PathParam("project") final String project,
                                               @PathParam("stack") final String stack,
                                               @PathParam("groupId") final String groupId,
                                               @PathParam("x") final Double x,
                                               @PathParam("y") final Double y,
                                               @PathParam("z") final Double z,
                                               @PathParam("width") final Integer width,
                                               @PathParam("height") final Integer height,
                                               @PathParam("scale") final Double scale,
                                               @BeanParam final RenderQueryParameters renderQueryParameters,
                                               @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                               @Context final Request request) {

        LOG.info("renderJpegImageForGroupBox: entry");

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    getRenderParametersForGroupBox(owner, project, stack, groupId,
                                                   x, y, z, width, height, scale,
                                                   renderQueryParameters);
            return RenderServiceUtil.renderJpegImage(renderParameters, maxTileSpecsToRender, responseHelper);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/group/{groupId}/dvid/imagetile/raw/xy/{width}_{height}/{x}_{y}_{z}/jpg")
    @GET
    @Produces(RenderServiceUtil.IMAGE_JPEG_MIME_TYPE)
    @ApiOperation(
            tags = {"Bounding Box Image APIs", "DVID Style APIs"},
            value = "DVID style API to render JPEG image for the specified bounding box and groupId")
    public Response renderJpegImageForDvidGroupBox(@PathParam("owner") final String owner,
                                                   @PathParam("project") final String project,
                                                   @PathParam("stack") final String stack,
                                                   @PathParam("groupId") final String groupId,
                                                   @PathParam("x") final Double x,
                                                   @PathParam("y") final Double y,
                                                   @PathParam("z") final Double z,
                                                   @PathParam("width") final Integer width,
                                                   @PathParam("height") final Integer height,
                                                   @BeanParam final RenderQueryParameters renderQueryParameters,
                                                   @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                                   @Context final Request request) {
        return renderJpegImageForGroupBox(owner, project, stack, groupId, x, y, z, width, height, null,
                                          renderQueryParameters, maxTileSpecsToRender, request);
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/group/{groupId}/z/{z}/box/{x},{y},{width},{height},{scale}/png-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_PNG_MIME_TYPE)
    @ApiOperation(
            tags = "Bounding Box Image APIs",
            value = "Render PNG image for the specified bounding box and groupId")
    public Response renderPngImageForGroupBox(@PathParam("owner") final String owner,
                                              @PathParam("project") final String project,
                                              @PathParam("stack") final String stack,
                                              @PathParam("groupId") final String groupId,
                                              @PathParam("x") final Double x,
                                              @PathParam("y") final Double y,
                                              @PathParam("z") final Double z,
                                              @PathParam("width") final Integer width,
                                              @PathParam("height") final Integer height,
                                              @PathParam("scale") final Double scale,
                                              @BeanParam final RenderQueryParameters renderQueryParameters,
                                              @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                              @Context final Request request) {

        LOG.info("renderPngImageForGroupBox: entry");

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    getRenderParametersForGroupBox(owner, project, stack, groupId,
                                                   x, y, z, width, height, scale,
                                                   renderQueryParameters);
            return RenderServiceUtil.renderPngImage(renderParameters, maxTileSpecsToRender, responseHelper);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/group/{groupId}/dvid/imagetile/raw/xy/{width}_{height}/{x}_{y}_{z}/png")
    @GET
    @Produces(RenderServiceUtil.IMAGE_PNG_MIME_TYPE)
    @ApiOperation(
            tags = {"Bounding Box Image APIs", "DVID Style APIs"},
            value = "DVID style API to render PNG image for the specified bounding box and groupId")
    public Response renderPngImageForDvidGroupBox(@PathParam("owner") final String owner,
                                                  @PathParam("project") final String project,
                                                  @PathParam("stack") final String stack,
                                                  @PathParam("groupId") final String groupId,
                                                  @PathParam("x") final Double x,
                                                  @PathParam("y") final Double y,
                                                  @PathParam("z") final Double z,
                                                  @PathParam("width") final Integer width,
                                                  @PathParam("height") final Integer height,
                                                  @BeanParam final RenderQueryParameters renderQueryParameters,
                                                  @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                                  @Context final Request request) {
        return renderPngImageForGroupBox(owner, project, stack, groupId, x, y, z, width, height, null,
                                         renderQueryParameters, maxTileSpecsToRender, request);
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/group/{groupId}/z/{z}/box/{x},{y},{width},{height},{scale}/tiff-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_TIFF_MIME_TYPE)
    @ApiOperation(
            tags = "Bounding Box Image APIs",
            value = "Render TIFF image for the specified bounding box and groupId")
    public Response renderTiffImageForGroupBox(@PathParam("owner") final String owner,
                                               @PathParam("project") final String project,
                                               @PathParam("stack") final String stack,
                                               @PathParam("groupId") final String groupId,
                                               @PathParam("x") final Double x,
                                               @PathParam("y") final Double y,
                                               @PathParam("z") final Double z,
                                               @PathParam("width") final Integer width,
                                               @PathParam("height") final Integer height,
                                               @PathParam("scale") final Double scale,
                                               @BeanParam final RenderQueryParameters renderQueryParameters,
                                               @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                               @Context final Request request) {

        LOG.info("renderTiffImageForGroupBox: entry");

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    getRenderParametersForGroupBox(owner, project, stack, groupId,
                                                   x, y, z, width, height, scale,
                                                   renderQueryParameters);
            return RenderServiceUtil.renderTiffImage(renderParameters, maxTileSpecsToRender, responseHelper);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/group/{groupId}/dvid/imagetile/raw/xy/{width}_{height}/{x}_{y}_{z}/tif")
    @GET
    @Produces(RenderServiceUtil.IMAGE_TIFF_MIME_TYPE)
    @ApiOperation(
            tags = {"Bounding Box Image APIs", "DVID Style APIs"},
            value = "DVID style API to render TIFF image for the specified bounding box and groupId")
    public Response renderTiffImageForDvidGroupBox(@PathParam("owner") final String owner,
                                                   @PathParam("project") final String project,
                                                   @PathParam("stack") final String stack,
                                                   @PathParam("groupId") final String groupId,
                                                   @PathParam("x") final Double x,
                                                   @PathParam("y") final Double y,
                                                   @PathParam("z") final Double z,
                                                   @PathParam("width") final Integer width,
                                                   @PathParam("height") final Integer height,
                                                   @BeanParam final RenderQueryParameters renderQueryParameters,
                                                   @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                                   @Context final Request request) {
        return renderTiffImageForGroupBox(owner, project, stack, groupId, x, y, z, width, height, null,
                                          renderQueryParameters, maxTileSpecsToRender, request);
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/largeDataTileSource/{width}/{height}/{level}/{z}/{row}/{column}.jpg")
    @GET
    @Produces(RenderServiceUtil.IMAGE_JPEG_MIME_TYPE)
    @ApiOperation(
            tags = "Bounding Box Image APIs",
            value = "Render JPEG image for the specified large data (type 5) tile")
    public Response renderLargeDataTileSourceJpeg(@PathParam("owner") final String owner,
                                                  @PathParam("project") final String project,
                                                  @PathParam("stack") final String stack,
                                                  @PathParam("width") final Integer width,
                                                  @PathParam("height") final Integer height,
                                                  @PathParam("level") final Integer level,
                                                  @PathParam("z") final Double z,
                                                  @PathParam("row") final Integer row,
                                                  @PathParam("column") final Integer column,
                                                  @BeanParam final RenderQueryParameters renderQueryParameters,
                                                  @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                                  @Context final Request request) {

        return renderLargeDataTileSource(owner, project, stack, width, height, level, z, row, column,
                                         Utils.JPEG_FORMAT, RenderServiceUtil.IMAGE_JPEG_MIME_TYPE,
                                         renderQueryParameters, maxTileSpecsToRender, request);
    }


    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/largeDataTileSource/{width}/{height}/small/{z}.jpg")
    @GET
    @Produces(RenderServiceUtil.IMAGE_JPEG_MIME_TYPE)
    @ApiOperation(
            tags = "Bounding Box Image APIs",
            value = "Render JPEG image for the specified large data (type 5) section overview")
    public Response renderLargeDataOverviewJpeg(@PathParam("owner") final String owner,
                                                @PathParam("project") final String project,
                                                @PathParam("stack") final String stack,
                                                @PathParam("width") final Integer width,
                                                @PathParam("height") final Integer height,
                                                @PathParam("z") final Double z,
                                                @QueryParam("maxOverviewWidthAndHeight") final Integer maxOverviewWidthAndHeight,
                                                @BeanParam final RenderQueryParameters renderQueryParameters,
                                                @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                                @QueryParam("translateOrigin") final Boolean translateOrigin,
                                                @Context final Request request) {

        return renderLargeDataOverview(owner, project, stack, width, height, z,
                                       Utils.JPEG_FORMAT, RenderServiceUtil.IMAGE_JPEG_MIME_TYPE,
                                       maxOverviewWidthAndHeight, renderQueryParameters,
                                       maxTileSpecsToRender, translateOrigin, request);
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/largeDataTileSource/{width}/{height}/{level}/{z}/{row}/{column}.png")
    @GET
    @Produces(RenderServiceUtil.IMAGE_PNG_MIME_TYPE)
    @ApiOperation(
            tags = "Bounding Box Image APIs",
            value = "Render PNG image for the specified large data (type 5) tile")
    public Response renderLargeDataTileSourcePng(@PathParam("owner") final String owner,
                                                 @PathParam("project") final String project,
                                                 @PathParam("stack") final String stack,
                                                 @PathParam("width") final Integer width,
                                                 @PathParam("height") final Integer height,
                                                 @PathParam("level") final Integer level,
                                                 @PathParam("z") final Double z,
                                                 @PathParam("row") final Integer row,
                                                 @PathParam("column") final Integer column,
                                                 @BeanParam final RenderQueryParameters renderQueryParameters,
                                                 @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                                 @Context final Request request) {

        return renderLargeDataTileSource(owner, project, stack, width, height, level, z, row, column,
                                         Utils.PNG_FORMAT, RenderServiceUtil.IMAGE_PNG_MIME_TYPE,
                                         renderQueryParameters, maxTileSpecsToRender, request);
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/largeDataTileSource/{width}/{height}/small/{z}.png")
    @GET
    @Produces(RenderServiceUtil.IMAGE_PNG_MIME_TYPE)
    @ApiOperation(
            tags = "Bounding Box Image APIs",
            value = "Render PNG image for the specified large data (type 5) section overview")
    public Response renderLargeDataOverviewPng(@PathParam("owner") final String owner,
                                               @PathParam("project") final String project,
                                               @PathParam("stack") final String stack,
                                               @PathParam("width") final Integer width,
                                               @PathParam("height") final Integer height,
                                               @PathParam("z") final Double z,
                                               @QueryParam("maxOverviewWidthAndHeight") final Integer maxOverviewWidthAndHeight,
                                               @BeanParam final RenderQueryParameters renderQueryParameters,
                                               @QueryParam("maxTileSpecsToRender") final Integer maxTileSpecsToRender,
                                               @QueryParam("translateOrigin") final Boolean translateOrigin,
                                               @Context final Request request) {

        return renderLargeDataOverview(owner, project, stack, width, height, z,
                                       Utils.PNG_FORMAT, RenderServiceUtil.IMAGE_PNG_MIME_TYPE,
                                       maxOverviewWidthAndHeight, renderQueryParameters,
                                       maxTileSpecsToRender, translateOrigin, request);
    }

    private Response renderLargeDataTileSource(final String owner,
                                               final String project,
                                               final String stack,
                                               final Integer width,
                                               final Integer height,
                                               final Integer level,
                                               final Double z,
                                               final Integer row,
                                               final Integer column,
                                               final String format,
                                               final String mimeType,
                                               final RenderQueryParameters renderQueryParameters,
                                               Integer maxTileSpecsToRender,
                                               final Request request) {

        LOG.info("renderLargeDataTileSource: entry, stack={}, width={}, height={}, z={}, row={}, column={}",
                 stack, width, height, z, row, column);

        final StackMetaData stackMetaData = getStackMetaData(owner, project, stack);
        final ResponseHelper responseHelper = new ResponseHelper(request, stackMetaData);
        if (responseHelper.isModified()) {

            final File sourceFile;
            if ((maxTileSpecsToRender == null) || (maxTileSpecsToRender != 0)) {
                sourceFile = getLargeDataFile(stackMetaData,
                                              width + "x" + height,
                                              level.toString(),
                                              String.valueOf(z.intValue()),
                                              row.toString(),
                                              column + "." + format);
            } else {
                sourceFile = null;
            }

            if (sourceFile == null) {

                final double factor = 1 << level;
                final double scaledWidth = width * factor;
                final double scaledHeight = height * factor;
                final double x = column * scaledWidth;
                final double y = row * scaledHeight;
                final double scale = 1.0 / factor;

                final RenderParameters renderParameters =
                        getRenderParametersForGroupBox(owner, project, stack, null,
                                                       x, y, z, (int) scaledWidth, (int) scaledHeight, scale,
                                                       renderQueryParameters);

                if (maxTileSpecsToRender == null) {
                    maxTileSpecsToRender = DEFAULT_MAX_TILE_SPECS_FOR_LARGE_DATA;
                }
                return RenderServiceUtil.renderImageStream(renderParameters,
                                                           format,
                                                           mimeType,
                                                           maxTileSpecsToRender,
                                                           responseHelper);

            }  else {

                return RenderServiceUtil.streamImageFile(sourceFile, mimeType, responseHelper);

            }

        } else {

            return responseHelper.getNotModifiedResponse();

        }
    }

    private Response renderLargeDataOverview(final String owner,
                                             final String project,
                                             final String stack,
                                             final Integer width,
                                             final Integer height,
                                             final Double z,
                                             final String format,
                                             final String mimeType,
                                             Integer maxOverviewWidthAndHeight,
                                             final RenderQueryParameters renderQueryParameters,
                                             Integer maxTileSpecsToRender,
                                             final Boolean translateOrigin,
                                             final Request request) {

        LOG.info("renderLargeDataOverview: entry, stack={}, width={}, height={}, z={}",
                 stack, width, height, z);

        final StackMetaData stackMetaData = getStackMetaData(owner, project, stack);
        final ResponseHelper responseHelper = new ResponseHelper(request, stackMetaData);
        if (responseHelper.isModified()) {

            final File overviewSourceFile = getLargeDataFile(stackMetaData,
                                                             width + "x" + height,
                                                             "small",
                                                             z.intValue() + "." + format);

            if (overviewSourceFile == null) {

                Double stackMinX = 0.0;
                Double stackMinY = 0.0;
                int stackWidth = 1;
                int stackHeight = 1;

                final StackStats stats = stackMetaData.getStats();
                if (stats != null) {
                    final Bounds stackBounds = stats.getStackBounds();
                    if (stackBounds != null) {

                        stackWidth = stackBounds.getMaxX().intValue();
                        stackHeight = stackBounds.getMaxY().intValue();

                        // CATMAID overviews are expected to reflect a (0,0) origin and
                        // stacks are expected to be entirely in positive space.
                        // If the request explicitly asks for translation or
                        // if one dimension of the stack is in negative space,
                        // render the overview as if the stack's minimum coordinate was (0,0).

                        if (((translateOrigin != null) && translateOrigin) ||
                            (stackBounds.getMinX().intValue() < 0) || (stackBounds.getMinY().intValue() < 0)) {
                            stackMinX = stackBounds.getMinX();
                            stackMinY = stackBounds.getMinY();
                            stackWidth = stackBounds.getMaxX().intValue() - stackMinX.intValue();
                            stackHeight = stackBounds.getMaxY().intValue() - stackMinY.intValue();
                        }

                    }
                }

                if ((maxOverviewWidthAndHeight == null) || (maxOverviewWidthAndHeight < 1)) {
                    // default to 192 since CATMAID overview box is 192x192
                    maxOverviewWidthAndHeight = 192;
                }

                // scale overview image based upon larger dimension - width or height
                final double scale;
                if (stackWidth > stackHeight) {
                    scale = (double) maxOverviewWidthAndHeight / stackWidth;
                } else {
                    scale = (double) maxOverviewWidthAndHeight / stackHeight;
                }

                final RenderParameters renderParameters =
                        getRenderParametersForGroupBox(owner, project, stack, null,
                                                       stackMinX, stackMinY, z, stackWidth, stackHeight, scale,
                                                       renderQueryParameters);

                if (maxTileSpecsToRender == null) {
                    maxTileSpecsToRender = DEFAULT_MAX_TILE_SPECS_FOR_LARGE_DATA;
                }

                return RenderServiceUtil.renderImageStream(renderParameters,
                                                           format,
                                                           mimeType,
                                                           maxTileSpecsToRender,
                                                           responseHelper);

            }  else {

                return RenderServiceUtil.streamImageFile(overviewSourceFile, mimeType, responseHelper);

            }

        } else {

            return responseHelper.getNotModifiedResponse();

        }
    }

    private File getLargeDataFile(final StackMetaData stackMetaData,
                                  final String... additionalPathElements) {

        File file = null;

        final String rootPath = stackMetaData.getCurrentMaterializedBoxRootPath();
        if (rootPath != null) {

            file = Paths.get(rootPath, additionalPathElements).toFile();

            queueLargeDataFileRequest(file);

            if (! file.exists()) {
                // force dynamic rendering if materialized box does not exist
                file = null;
            }

        }

        return file;
    }

    private void queueLargeDataFileRequest(final File file) {
        // TODO: queue (POST) request for materialize service (will either generate file or just mark usage)
        LOG.info("need to queue materialize service request for {}",
                 file);
    }

    private RenderParameters getRenderParametersForGroupBox(final String owner,
                                                            final String project,
                                                            final String stack,
                                                            final String groupId,
                                                            final Double x,
                                                            final Double y,
                                                            final Double z,
                                                            final Integer width,
                                                            final Integer height,
                                                            final Double scale,
                                                            final RenderQueryParameters renderQueryParameters) {

        final StackId stackId = new StackId(owner, project, stack);
        return renderDataService.getInternalRenderParameters(stackId,
                                                             groupId,
                                                             x,
                                                             y,
                                                             z,
                                                             width,
                                                             height,
                                                             scale,
                                                             renderQueryParameters);
    }

    private StackMetaData getStackMetaData(final String owner,
                                           final String project,
                                           final String stack) {
        final StackId stackId = new StackId(owner, project, stack);
        return renderDataService.getStackMetaData(stackId);
    }

    private static final Logger LOG = LoggerFactory.getLogger(RenderImageService.class);

    private static final Integer DEFAULT_MAX_TILE_SPECS_FOR_LARGE_DATA = 40;
}
