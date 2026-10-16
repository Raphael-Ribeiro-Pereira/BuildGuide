package brentmaas.buildguide.fabric.preview;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.joml.Quaternionf;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import brentmaas.buildguide.common.TimingLog;
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
 * The mesh (GPU buffer, one draw call) is rebuilt only when it would show something else
 * (PreviewModel.meshChange). Performance step 2: its vertices are written on a worker thread
 * (PreviewMesh.fill writes only the ShapeBuffer's native memory, and a model never changes) and
 * sent to the GPU on the render thread once ready; meanwhile the previous mesh stays on screen,
 * framed by the model it was built for. One build at a time: a model that needs a mesh while one is
 * being built waits for it, and a finished build no model wants any more is freed unsent. The
 * texture is kept while the model and the camera do not change, so a still preview costs one blit
 * per frame.
 */
public class PreviewRenderer extends PictureInPictureRenderer<PreviewRenderState> {
	// Keep the model inside the projection's depth range (+-1000) at any zoom
	private static final float maxDepth = 900;
	private static final ExecutorService meshBuilder = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Build Guide preview mesh");
		thread.setDaemon(true);
		return thread;
	});

	private PreviewModel meshModel = null;
	private ShapeBuffer mesh = null;
	// The build running (null: none), for which model and why, and the next model that needs one meanwhile
	private Future<ShapeBuffer> building = null;
	private PreviewModel buildingModel = null, wantedModel = null;
	private String buildingReason = null, wantedReason = null;
	private long buildingStarted = 0, buildingMillis = 0;
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

	// A finished build is drawn on the next frame, so the texture is redrawn then even if nothing else changed
	@Override
	protected boolean textureIsReadyToBlit(PreviewRenderState state) {
		if(building != null && building.isDone()) return false;
		return state.model() == shownModel && state.yaw() == shownYaw && state.pitch() == shownPitch && state.zoom() == shownZoom;
	}

	@Override
	protected void renderToTexture(PreviewRenderState state, PoseStack poseStack) {
		PreviewModel model = state.model();
		collectBuild();
		// Rebuild only when the mesh would show something else (PreviewModel.meshChange), compared with the mesh
		// that will be on screen once the build running (if any) lands: a new instance with the same key keeps it
		String reason = PreviewModel.meshChange(building != null ? buildingModel : meshModel, model);
		if(reason != null) requestBuild(model, reason);
		else if(building == null) meshModel = model;
		if(mesh == null) {
			shownModel = model;
			shownYaw = state.yaw();
			shownPitch = state.pitch();
			shownZoom = state.zoom();
			return; // the first mesh is not built yet: an empty texture until then
		}
		// Framed by the model the mesh was built for (a newer geometry may still be on its way)
		PreviewModel drawn = meshModel;

		float scale = (float) PreviewCamera.fitScale(drawn, state.x1() - state.x0(), state.y1() - state.y0(), state.zoom());
		// guiScale, read back from the pose vanilla prepared; depth is compressed if the model would leave +-maxDepth
		float guiScale = poseStack.last().pose().m00();
		float depthScale = (float) Math.min(scale, maxDepth / (guiScale * drawn.radius()));

		// Orientation. The projection has Y pointing down, so flip Y: +Y local (up in the world) is up on
		// screen. After that the viewer looks along +Z. Pitch tilts the top towards the viewer (rotateX by
		// -pitch; +pitch would show the bottom). Yaw turns the model about the vertical: at yaw 0 the viewer
		// is north (-Z) of it, at 45 north-east, with the north face on the left and the east face on the right
		poseStack.scale(1, -1, 1);
		poseStack.scale(scale, scale, depthScale);
		poseStack.mulPose(new Quaternionf().rotationX((float) Math.toRadians(-state.pitch())));
		poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(state.yaw())));
		poseStack.translate(-drawn.centreX(), -drawn.centreY(), -drawn.centreZ());

		mesh.render(RenderSystem.outputColorTextureOverride, RenderSystem.outputDepthTextureOverride, poseStack.last().pose(), RenderHandler.BUILD_GUIDE_PREVIEW);

		shownModel = model;
		shownYaw = state.yaw();
		shownPitch = state.pitch();
		shownZoom = state.zoom();
	}

	// A mesh for this model: built now on the worker, or after the one running (the latest request wins)
	private void requestBuild(PreviewModel model, String reason) {
		if(building != null) {
			wantedModel = model;
			wantedReason = reason;
			return;
		}
		buildingModel = model;
		buildingReason = reason;
		buildingStarted = System.nanoTime();
		// Geometry and colours come from common (PreviewMesh); the worker only writes them
		building = meshBuilder.submit(() -> {
			long started = System.nanoTime();
			ShapeBuffer buffer = new ShapeBuffer();
			PreviewMesh.fill(buffer, model);
			buildingMillis = (System.nanoTime() - started) / 1000000;
			return buffer;
		});
	}

	/**
	 * The build running, once it is done: sent to the GPU and shown, unless a newer model already wants another
	 * mesh (then it is freed unsent and that one is built). Render thread.
	 */
	private void collectBuild() {
		if(building == null || !building.isDone()) return;
		ShapeBuffer built;
		try {
			built = building.get();
		}catch(Exception e) {
			built = null;
		}
		PreviewModel model = buildingModel;
		String reason = buildingReason;
		building = null;
		buildingModel = null;
		if(wantedModel != null) {
			if(built != null) built.releaseVertexData();
			PreviewModel next = wantedModel;
			String why = wantedReason;
			wantedModel = null;
			wantedReason = null;
			requestBuild(next, why);
			return;
		}
		if(built == null) return;
		long sending = System.nanoTime();
		built.end();
		if(mesh != null) mesh.close();
		mesh = built;
		meshModel = model;
		TimingLog.recordPreview((System.nanoTime() - sending) / 1000000, buildingMillis, (System.nanoTime() - buildingStarted) / 1000000, model.positions.length, reason);
	}

	@Override
	public void close() {
		super.close();
		if(mesh != null) mesh.close();
		mesh = null;
		meshModel = null;
		// A build still running is freed once it ends (the same worker, so after it)
		Future<ShapeBuffer> running = building;
		building = null;
		buildingModel = null;
		wantedModel = null;
		if(running != null) meshBuilder.submit(() -> {
			try {
				running.get().releaseVertexData();
			}catch(Exception e) {
				// nothing was built
			}
		});
	}
}
