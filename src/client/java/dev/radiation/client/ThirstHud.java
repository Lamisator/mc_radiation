package dev.radiation.client;

import dev.radiation.RadiationMod;
import dev.radiation.thirst.Thirst;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/** Ten droplets above the hunger bar: the thirst bar. They shiver when you are too thirsty to sprint. */
public final class ThirstHud implements HudElement {
	public static final Identifier ID = RadiationMod.id("thirst_bar");
	private static final Identifier EMPTY = RadiationMod.id("hud/thirst_empty");
	private static final Identifier HALF = RadiationMod.id("hud/thirst_half");
	private static final Identifier FULL = RadiationMod.id("hud/thirst_full");
	private final RandomSource random = RandomSource.create();
	private int ticks;

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR, ID, new ThirstHud());
		HudStatusBarHeightRegistry.addRight(ID, player -> visible(player) ? 10 : 0);
	}

	private static boolean visible(Player player) {
		Minecraft mc = Minecraft.getInstance();
		return Thirst.clientEnabled && ClientRadiationState.received && mc.gameMode != null && mc.gameMode.canHurtPlayer()
				&& player == mc.getCameraEntity() && !mc.gui.hud.isHidden();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || !visible(player)) {
			return;
		}
		this.ticks = mc.gui.hud.getGuiTicks();
		int thirst = (int) Math.ceil(Thirst.get(player));
		boolean shiver = thirst <= Thirst.clientSprintThreshold;
		int xRight = g.guiWidth() / 2 + 91;
		int y = g.guiHeight() - HudStatusBarHeightRegistry.getHeight(ID);
		this.random.setSeed(this.ticks * 312871L);
		for (int i = 0; i < 10; i++) {
			int yo = y;
			if (shiver && this.ticks % (thirst * 3 + 1) == 0) {
				yo += this.random.nextInt(3) - 1;
			}
			int xo = xRight - i * 8 - 9;
			g.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY, xo, yo, 9, 9);
			if (i * 2 + 1 < thirst) {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, FULL, xo, yo, 9, 9);
			} else if (i * 2 + 1 == thirst) {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, HALF, xo, yo, 9, 9);
			}
		}
	}
}
