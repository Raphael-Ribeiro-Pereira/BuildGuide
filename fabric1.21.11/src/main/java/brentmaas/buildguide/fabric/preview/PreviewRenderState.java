package brentmaas.buildguide.fabric.preview;

import org.jetbrains.annotations.Nullable;

import brentmaas.buildguide.common.shape.PreviewModel;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;

// One frame of the 3D preview, submitted to the GUI render state and routed by its class to PreviewRenderer.
// The camera is copied as values so the renderer can tell whether the last texture is still valid
public record PreviewRenderState(PreviewModel model, float yaw, float pitch, float zoom, int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
	public PreviewRenderState(PreviewModel model, float yaw, float pitch, float zoom, int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea) {
		this(model, yaw, pitch, zoom, x0, y0, x1, y1, scissorArea, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
	}

	@Override
	public float scale() {
		return 1.0f;
	}
}
