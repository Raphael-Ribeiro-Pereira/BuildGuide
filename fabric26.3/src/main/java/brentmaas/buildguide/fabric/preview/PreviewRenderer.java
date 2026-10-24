package brentmaas.buildguide.fabric.preview;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import brentmaas.buildguide.common.TimingLog;
import brentmaas.buildguide.common.WorldUpload;
import brentmaas.buildguide.common.shape.PreviewCamera;
import brentmaas.buildguide.common.shape.PreviewMesh;
import brentmaas.buildguide.common.shape.PreviewModel;
import brentmaas.buildguide.common.shape.SectionedBuffer;
import brentmaas.buildguide.fabric.RenderHandler;
import brentmaas.buildguide.fabric.shape.ShapeBuffer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.renderpearl.api.textures.GpuTextureView;

/**
 * Draws the 3D preview into its picture-in-picture texture (registered through Fabric's
 * SpecialGuiElementRegistry). Vanilla has already bound the texture and its depth buffer
 * (RenderSystem.output*TextureOverride), set an orthographic projection with Y pointing down
 * and z in [-1000, 1000], and given a pose translated to the texture centre (getTranslateY) and
 * scaled by (guiScale, guiScale, -guiScale), i.e. one unit = one GUI pixel.
 *
 * The mesh is rebuilt only when it would show something else (PreviewModel.meshChange). Performance step 2:
 * its vertices are written on a worker thread (PreviewMesh.fill writes only native memory, and a model never
 * changes); meanwhile the previous mesh stays on screen, framed by the model it was built for. One build at a
 * time: a model that needs a mesh while one is being built waits for it, and a finished build no model wants
 * any more is freed unsent.
 *
 * Performance step 4: the mesh is in 16-block sections (SectionedBuffer, like the world buffer) and reaches the
 * GPU a few sections per frame within WorldUpload's budget; the previous mesh is drawn until every section is
 * there. Sending it whole took 8 to 25 ms in one frame at 50 000 blocks, every time the shape changed with the
 * menu open. The texture is kept while the model and the camera do not change and nothing is being sent, so a
 * still preview costs one blit per frame.
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
	private SectionedBuffer mesh = null;
	// The build running (null: none), for which model and why, and the next model that needs one meanwhile
	private Future<SectionedBuffer> building = null;
	private PreviewModel buildingModel = null, wantedModel = null;
	private String buildingReason = null, wantedReason = null;
	private long buildingStarted = 0, buildingMillis = 0;
	// A finished build being sent to the GPU, a few sections per frame (null: none), and its timing
	private SectionedBuffer sending = null;
	private PreviewModel sendingModel = null;
	private String sendingReason = null;
	private long sendingBuildMillis = 0, sendingStarted = 0, sendNanos = 0, maxSendNanos = 0;
	private int sendFrames = 0;
	// What the current texture shows
	private PreviewModel shownModel = null;
	private float shownYaw, shownPitch, shownZoom;
	// The sections drawn this frame (reused, render thread)
	private final List<ShapeBuffer> drawList = new ArrayList<ShapeBuffer>();

	public PreviewRenderer() {
		super();
	}

	// 26.3 keeps the texture (and its depth buffer) private to the vanilla renderer, which draws what is submitted to
	// a SubmitNodeCollector into it. This preview draws its own buffers into the same texture, so it reads the two views
	// the vanilla renderer prepared (cleared, with the orthographic projection already set) by their names
	private static final java.lang.reflect.Field colourViewField = viewField("textureView"), depthViewField = viewField("depthTextureView");

	private static java.lang.reflect.Field viewField(String name) {
		try {
			java.lang.reflect.Field field = PictureInPictureRenderer.class.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		}catch(ReflectiveOperationException e) {
			throw new IllegalStateException("The picture-in-picture renderer has no " + name, e);
		}
	}

	private GpuTextureView view(java.lang.reflect.Field field) {
		try {
			return (GpuTextureView) field.get(this);
		}catch(IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
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

	// A finished build, or one being sent, needs renderToTexture on every frame until its mesh is on screen
	@Override
	protected boolean textureIsReadyToBlit(PreviewRenderState state) {
		if(building != null && building.isDone()) return false;
		if(sending != null) return false;
		return state.model() == shownModel && state.yaw() == shownYaw && state.pitch() == shownPitch && state.zoom() == shownZoom;
	}

	@Override
	protected void renderToTexture(PreviewRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
		PreviewModel model = state.model();
		collectBuild();
		sendPart();
		// Rebuild only when the mesh would show something else (PreviewModel.meshChange), compared with the mesh
		// that will be on screen once the build running or being sent (if any) lands: a new instance with the same
		// key keeps it
		PreviewModel coming = building != null ? buildingModel : sending != null ? sendingModel : meshModel;
		String reason = PreviewModel.meshChange(coming, model);
		if(reason != null) requestBuild(model, reason);
		else if(building == null && sending == null) meshModel = model;
		if(mesh == null) {
			shownModel = model;
			shownYaw = state.yaw();
			shownPitch = state.pitch();
			shownZoom = state.zoom();
			return; // the first mesh is not there yet: an empty texture until then
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
		poseStack.mulPose(new Matrix4f().rotationX((float) Math.toRadians(-state.pitch())));
		poseStack.mulPose(new Matrix4f().rotationY((float) Math.toRadians(state.yaw())));
		poseStack.translate(-drawn.centreX(), -drawn.centreY(), -drawn.centreZ());

		drawList.clear();
		for(SectionedBuffer.Section section: mesh.getSections()) drawList.add((ShapeBuffer) section.getBuffer());
		ShapeBuffer.renderAll(drawList, view(colourViewField), view(depthViewField), poseStack.last().pose(), RenderHandler.BUILD_GUIDE_PREVIEW);
		drawList.clear();

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
			SectionedBuffer buffer = new SectionedBuffer(ShapeBuffer::new);
			PreviewMesh.fill(buffer, model);
			buildingMillis = (System.nanoTime() - started) / 1000000;
			return buffer;
		});
	}

	/**
	 * The build running, once it is done: it starts being sent to the GPU, unless a newer model already wants
	 * another mesh (then it is freed unsent and that one is built). A mesh still being sent from an earlier build
	 * is dropped for it. Render thread.
	 */
	private void collectBuild() {
		if(building == null || !building.isDone()) return;
		SectionedBuffer built;
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
		dropSending();
		sending = built;
		sendingModel = model;
		sendingReason = reason;
		sendingBuildMillis = buildingMillis;
		sendingStarted = buildingStarted;
		sendNanos = 0;
		maxSendNanos = 0;
		sendFrames = 0;
	}

	// This frame's part of the mesh being sent; once all of it is there it replaces the one on screen
	private void sendPart() {
		if(sending == null) return;
		long started = System.nanoTime();
		boolean sent = sending.endPart(System::nanoTime, started + WorldUpload.budgetNanos);
		long spent = System.nanoTime() - started;
		sendNanos += spent;
		maxSendNanos = Math.max(maxSendNanos, spent);
		++sendFrames;
		if(!sent) return;
		if(mesh != null) mesh.close();
		mesh = sending;
		meshModel = sendingModel;
		sending = null;
		TimingLog.recordPreview(sendNanos / 1000000, sendingBuildMillis, (System.nanoTime() - sendingStarted) / 1000000, meshModel.positions.length, sendingReason, sendFrames, maxSendNanos);
		sendingModel = null;
	}

	// A mesh being sent that will not be shown: its sections on the GPU closed, the rest of its native copy freed
	private void dropSending() {
		if(sending == null) return;
		sending.close();
		sending.releaseVertexData();
		sending = null;
		sendingModel = null;
	}

	@Override
	public void close() {
		super.close();
		if(mesh != null) mesh.close();
		mesh = null;
		meshModel = null;
		dropSending();
		// A build still running is freed once it ends (the same worker, so after it)
		Future<SectionedBuffer> running = building;
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
