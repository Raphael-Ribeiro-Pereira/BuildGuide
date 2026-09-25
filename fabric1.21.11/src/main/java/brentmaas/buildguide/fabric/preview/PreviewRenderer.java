package brentmaas.buildguide.fabric.preview;

import org.joml.Quaternionf;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import brentmaas.buildguide.common.shape.PreviewCamera;
import brentmaas.buildguide.common.shape.PreviewMesh;
import brentmaas.buildguide.common.shape.PreviewModel;
import brentmaas.buildguide.fabric.RenderHandler;
import brentmaas.buildguide.fabric.shape.ShapeBuffer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Draws the 3D preview into its picture-in-picture texture (registered through Fabric's
 * SpecialGuiElementRegistry). Vanilla has already bound the texture and its depth buffer
 * (RenderSystem.output*TextureOverride), set an orthographic projection with Y pointing down
 * and z in [-1000, 1000], and given a pose translated to the texture centre (getTranslateY) and
 * scaled by (guiScale, guiScale, -guiScale), i.e. one unit = one GUI pixel.
 *
 * The mesh is built once per model instance (GPU buffer, one draw call): a colour refresh
 * (PreviewModel.withValidation, at most every 100 ms) is a new instance and rebuilds it. The
 * texture is kept while the model and the camera do not change, so a still preview costs one
 * blit per frame.
 */
public class PreviewRenderer extends PictureInPictureRenderer<PreviewRenderState> {
	// Keep the model inside the projection's depth range (+-1000) at any zoom
	private static final float maxDepth = 900;

	private PreviewModel meshModel = null;
	private ShapeBuffer mesh = null;
	// What the current texture shows
	private PreviewModel shownModel = null;
	private float shownYaw, shownPitch, shownZoom;

	public PreviewRenderer(MultiBufferSource.BufferSource bufferSource) {
		super(bufferSource);
	}

	@Override
	public Class<PreviewRenderState> getRenderStateClass() {
		return PreviewRenderState.class;
	}

	@Override
	protected String getTextureLabel() {
		return "build_guide_preview";
	}

	// Centre of the texture instead of its bottom edge
	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2.0f;
	}

	@Override
	protected boolean textureIsReadyToBlit(PreviewRenderState state) {
		return state.model() == shownModel && state.yaw() == shownYaw && state.pitch() == shownPitch && state.zoom() == shownZoom;
	}

	@Override
	protected void renderToTexture(PreviewRenderState state, PoseStack poseStack) {
		PreviewModel model = state.model();
		if(model != meshModel) buildMesh(model);

		float scale = (float) PreviewCamera.fitScale(model, state.x1() - state.x0(), state.y1() - state.y0(), state.zoom());
		// guiScale, read back from the pose vanilla prepared; depth is compressed if the model would leave +-maxDepth
		float guiScale = poseStack.last().pose().m00();
		float depthScale = (float) Math.min(scale, maxDepth / (guiScale * model.radius()));

		// Orientation. The projection has Y pointing down, so flip Y: +Y local (up in the world) is up on
		// screen. After that the viewer looks along +Z. Pitch tilts the top towards the viewer (rotateX by
		// -pitch; +pitch would show the bottom). Yaw turns the model about the vertical: at yaw 0 the viewer
		// is north (-Z) of it, at 45 north-east, with the north face on the left and the east face on the right
		poseStack.scale(1, -1, 1);
		poseStack.scale(scale, scale, depthScale);
		poseStack.mulPose(new Quaternionf().rotationX((float) Math.toRadians(-state.pitch())));
		poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(state.yaw())));
		poseStack.translate(-model.centreX(), -model.centreY(), -model.centreZ());

		mesh.render(RenderSystem.outputColorTextureOverride, RenderSystem.outputDepthTextureOverride, poseStack.last().pose(), RenderHandler.BUILD_GUIDE_PREVIEW);

		shownModel = model;
		shownYaw = state.yaw();
		shownPitch = state.pitch();
		shownZoom = state.zoom();
	}

	// Geometry and colours come from common (PreviewMesh); this only uploads them
	private void buildMesh(PreviewModel model) {
		if(mesh != null) mesh.close();
		ShapeBuffer buffer = new ShapeBuffer();
		PreviewMesh.fill(buffer, model);
		buffer.end();
		mesh = buffer;
		meshModel = model;
	}

	@Override
	public void close() {
		super.close();
		if(mesh != null) mesh.close();
		mesh = null;
		meshModel = null;
	}
}
