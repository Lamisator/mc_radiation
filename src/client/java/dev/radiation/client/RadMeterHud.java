package dev.radiation.client;

import dev.radiation.network.RadiationSettingsPayload;
import dev.radiation.registry.ModRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.util.Locale;

/** The Fallout-style rad meter in the top left corner. */
public class RadMeterHud implements HudElement {
	private static final int WIDTH = 150;
	private static final int BAR_X = 5;
	private static final int BAR_Y = 15;
	private static final int BAR_W = WIDTH - 10;
	private static final int BAR_H = 7;

	private float alpha;
	private float shownRads;
	private long lastFrame = Util.getMillis();

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		long now = Util.getMillis();
		float frameSeconds = Mth.clamp((now - lastFrame) / 1000f, 0f, 0.25f);
		lastFrame = now;
		if (player == null || !ClientRadiationState.received || minecraft.getDebugOverlay().showDebugScreen()) {
			return;
		}

		ClientConfig config = ClientConfig.get();
		float predicted = ClientRadiationState.predictedRads();
		boolean holdingGeiger = config.showWhileHoldingGeiger
				&& (player.getMainHandItem().is(ModRegistry.GEIGER_COUNTER) || player.getOffhandItem().is(ModRegistry.GEIGER_COUNTER));
		boolean recentlyActive = now - ClientRadiationState.lastActivityMillis < config.hudLingerSeconds * 1000;
		boolean visible = switch (config.hudMode) {
			case ALWAYS -> true;
			case NONZERO -> predicted >= 0.5f || recentlyActive || holdingGeiger;
			case EXPOSURE -> recentlyActive || holdingGeiger;
			case HIDDEN -> false;
		};

		alpha = Mth.approach(alpha, visible ? 1f : 0f, frameSeconds * (visible ? 6f : 1.5f));
		if (alpha <= 0.02f) {
			shownRads = predicted;
			return;
		}
		shownRads += (predicted - shownRads) * Math.min(1f, frameSeconds * 10f);

		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(config.hudX, config.hudY);
		pose.scale(config.hudScale, config.hudScale);
		draw(graphics, minecraft.font, now);
		pose.popMatrix();
	}

	private void draw(GuiGraphicsExtractor g, Font font, long now) {
		float max = ClientRadiationState.maxRads;
		float exposure = ClientRadiationState.exposure;
		float protection = ClientRadiationState.protection;
		int stageIndex = ClientRadiationState.stageIndex(shownRads);
		RadiationSettingsPayload.StageInfo stage = stageIndex >= 0 ? ClientRadiationState.stages.get(stageIndex) : null;
		boolean showProtection = protection > 0.005f;

		int height = 36 + (stage != null ? 11 : 0);
		g.fill(0, 0, WIDTH, height, a(0x96000000));
		g.fill(0, 0, WIDTH, 1, a(0xFFB01515));

		// header: symbol + label, rate on the right
		g.text(font, "☢ RADS", 5, 4, a(0xFFFF3B30), true);
		String rateText;
		int rateColor;
		if (exposure < 0) {
			rateText = "?? RAD/s";
			rateColor = 0xFF9A9A9A;
		} else if (exposure > 0.005f) {
			rateText = "+" + formatRate(exposure) + " RAD/s";
			float pulse = 0.5f + 0.5f * Mth.sin(now / 140f);
			rateColor = lerpColor(0xFFFF2A2A, 0xFFFFFFFF, pulse * Math.min(1f, exposure / 20f));
		} else if (ClientRadiationState.netRate < -0.005f) {
			rateText = "-" + formatRate(-ClientRadiationState.netRate) + " RAD/s";
			rateColor = 0xFF55FF55;
		} else {
			rateText = "0.0 RAD/s";
			rateColor = 0xFF9A9A9A;
		}
		g.text(font, rateText, WIDTH - 5 - font.width(rateText), 4, a(rateColor), true);

		// bar
		boolean critical = shownRads >= max * 0.9f;
		int border = critical && (now / 250) % 2 == 0 ? 0xFFFF6060 : 0xFF8A1010;
		g.outline(BAR_X - 1, BAR_Y - 1, BAR_W + 2, BAR_H + 2, a(border));
		g.fill(BAR_X, BAR_Y, BAR_X + BAR_W, BAR_Y + BAR_H, a(0xFF2A0606));
		int filled = Math.round(BAR_W * Mth.clamp(shownRads / max, 0f, 1f));
		if (filled > 0) {
			g.fillGradient(BAR_X, BAR_Y, BAR_X + filled, BAR_Y + BAR_H, a(0xFFFF4A3A), a(0xFFA80E0E));
		}
		for (int x = BAR_X + 4; x < BAR_X + BAR_W; x += 5) {
			g.fill(x, BAR_Y, x + 1, BAR_Y + BAR_H, a(0x70000000));
		}
		for (RadiationSettingsPayload.StageInfo info : ClientRadiationState.stages) {
			if (info.threshold() > 0 && info.threshold() < max) {
				int x = BAR_X + Math.round(BAR_W * info.threshold() / max);
				g.fill(x, BAR_Y - 2, x + 1, BAR_Y + BAR_H + 2, a(info.color()));
			}
		}

		// numbers + protection
		String amount = String.format(Locale.ROOT, "%.0f / %.0f", shownRads, max);
		g.text(font, amount, 5, 26, a(0xFFE0E0E0), true);
		if (showProtection) {
			String res = String.format(Locale.ROOT, "RES %.0f%%", protection * 100);
			g.text(font, res, WIDTH - 5 - font.width(res), 26, a(0xFF55D8FF), true);
		}

		// sickness stage
		if (stage != null) {
			g.text(font, stage.name().toUpperCase(Locale.ROOT), 5, 37, a(stage.color()), true);
		}
	}

	private static String formatRate(float value) {
		return value < 10 ? String.format(Locale.ROOT, "%.2f", value) : String.format(Locale.ROOT, "%.1f", value);
	}

	private int a(int argb) {
		int alphaChannel = Math.round(((argb >>> 24) & 0xFF) * alpha);
		return (Math.max(alphaChannel, 0) << 24) | (argb & 0xFFFFFF);
	}

	private static int lerpColor(int from, int to, float t) {
		int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
		int gr = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
		int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
		return 0xFF000000 | (r << 16) | (gr << 8) | b;
	}
}
