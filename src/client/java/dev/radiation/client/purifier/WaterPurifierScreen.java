package dev.radiation.client.purifier;

import dev.radiation.purifier.WaterPurifierMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

/** Purifier panel: raw water slot, tank gauge, purification progress, container and output slots, power. */
public class WaterPurifierScreen extends AbstractContainerScreen<WaterPurifierMenu> {
	private static final int BG = 0xFF3B4146;
	private static final int PANEL = 0xFF262A2E;
	private static final int SLOT = 0xFF16191C;
	private static final int ACCENT = 0xFF3F9FE4;
	private static final int TANK_X = 50;
	private static final int TANK_Y = 17;
	private static final int TANK_W = 14;
	private static final int TANK_H = 44;

	public WaterPurifierScreen(WaterPurifierMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		g.fill(x, y, x + this.imageWidth, y + this.imageHeight, BG);
		g.fill(x + 2, y + 2, x + this.imageWidth - 2, y + 4, ACCENT);
		g.fill(x + 6, y + 14, x + 170, y + 74, PANEL);
		for (int i = 0; i < 3; i++) {
			var slot = this.menu.slots.get(i);
			g.fill(x + slot.x - 2, y + slot.y - 2, x + slot.x + 18, y + slot.y + 18, 0xFF101113);
		}
		for (var slot : this.menu.slots) {
			g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, SLOT);
		}
		// raw water tank: murky water, the pump's ripple when a water block feeds it
		int tx = x + TANK_X, ty = y + TANK_Y;
		g.outline(tx - 1, ty - 1, TANK_W + 2, TANK_H + 2, 0xFF8A949C);
		g.fill(tx, ty, tx + TANK_W, ty + TANK_H, 0xFF101315);
		int level = Math.round(TANK_H * Math.min(1f, this.menu.tank() / (float) this.menu.tankCapacity()));
		if (level > 0) {
			g.fillGradient(tx, ty + TANK_H - level, tx + TANK_W, ty + TANK_H, 0xFF6E8A5A, 0xFF3E5A3A);
		}
		for (int i = 1; i < this.menu.tankCapacity(); i++) {
			int ly = ty + TANK_H - Math.round(TANK_H * i / (float) this.menu.tankCapacity());
			g.fill(tx + TANK_W - 4, ly, tx + TANK_W, ly + 1, 0x90FFFFFF);
		}
		// progress: filter column from the tank to the containers
		int px = x + 72, py = y + 38, pw = 36;
		g.fill(px, py, px + pw, py + 6, SLOT);
		int done = Math.round(pw * this.menu.progress());
		if (done > 0) {
			g.fillGradient(px, py, px + done, py + 6, 0xFF9AD8FF, 0xFF2F7FD0);
		}
		g.fill(px + pw, py - 2, px + pw + 2, py + 8, SLOT);
		// power LED
		float power = this.menu.power();
		int led = power > 0.95f ? 0xFF40E060 : power > 0.01f ? 0xFFFFA020 : 0xFFB02020;
		g.fill(x + 158, y + 6, x + 164, y + 10, led);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		g.text(this.font, this.title, 8, 5, 0xFFE8E8E8, false);
		String power = !this.menu.gridworks() ? "" : this.menu.watts() >= 1000
				? String.format(Locale.ROOT, "%.2f kW", this.menu.watts() / 1000.0) : this.menu.watts() + " W";
		g.text(this.font, power, 154 - this.font.width(power), 5, 0xFFB0B0B0, false);
		String tank = this.menu.tank() + "/" + this.menu.tankCapacity();
		g.text(this.font, tank, TANK_X + TANK_W / 2 - this.font.width(tank) / 2 + 1, TANK_Y + TANK_H + 2, 0xFFB0B0B0, false);
		if (this.menu.pumping()) {
			g.text(this.font, Component.translatable("gui.radiation.purifier.pump"), 8, 58, 0xFF7FC8FF, false);
		}
		String pct = String.format(Locale.ROOT, "%.0f%%", this.menu.progress() * 100);
		g.text(this.font, pct, 90 - this.font.width(pct) / 2, 47, 0xFFE8E8E8, false);
		g.text(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, 0xFFB0B0B0, false);
	}
}
