package brentmaas.buildguide.fabric.place;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.State;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Area 3: while placing into the guideline is on, a short label just below the crosshair (a spot no
 * vanilla HUD element, MiniHUD, Tweakeroo or Litematica uses): light green when a guideline cell is
 * targeted, grey when none is.
 */
public final class PlacementHud {
	private static final int offsetBelowCrosshair = 12;
	private static final int colourTarget = 0xFF99EE99, colourNoTarget = 0xFFA0A0A0;

	private PlacementHud() {}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Identifier.fromNamespaceAndPath(BuildGuide.modid, "placement"), PlacementHud::render);
	}

	private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null || mc.level == null || mc.options.hideGui) return;
		State state = BuildGuide.stateManager.getState();
		if(!state.placeMode) return;
		String text = Component.translatable("hud.buildguide.place").getString();
		int x = (graphics.guiWidth() - mc.font.width(text)) / 2;
		int y = graphics.guiHeight() / 2 + offsetBelowCrosshair;
		graphics.drawString(mc.font, text, x, y, state.placeTarget != null ? colourTarget : colourNoTarget, true);
	}
}
