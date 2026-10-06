package dev.radiation.client.vault;

import dev.radiation.network.VaultDoorSettingsPayload;
import dev.radiation.vault.VaultDoorBlockEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Sets the number printed on a vault door and the side it rolls to when it opens. */
public class VaultDoorScreen extends Screen {
	private final BlockPos pos;
	private final int initialNumber;
	private boolean rollLeft;
	private EditBox number;
	private Button rollButton;
	private Button done;

	public VaultDoorScreen(VaultDoorBlockEntity door) {
		super(Component.translatable("screen.radiation.vault_door"));
		this.pos = door.getBlockPos();
		this.initialNumber = door.number();
		this.rollLeft = door.rollLeft();
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = this.height / 2 - 40;
		this.number = this.addRenderableWidget(new EditBox(this.font, cx - 40, y + 14, 80, 20, Component.translatable("screen.radiation.vault_door.number")));
		this.number.setMaxLength(3);
		this.number.setValue(String.valueOf(this.initialNumber));
		this.number.setResponder(s -> {
			String digits = s.replaceAll("[^0-9]", "");
			if (!digits.equals(s)) {
				this.number.setValue(digits);
			}
			this.updateDone();
		});
		this.rollButton = this.addRenderableWidget(Button.builder(this.rollLabel(), b -> {
			this.rollLeft = !this.rollLeft;
			b.setMessage(this.rollLabel());
		}).bounds(cx - 75, y + 44, 150, 20).build());
		this.done = this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.save()).bounds(cx - 75, y + 74, 72, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.onClose()).bounds(cx + 3, y + 74, 72, 20).build());
		this.setInitialFocus(this.number);
		this.updateDone();
	}

	private Component rollLabel() {
		return Component.translatable(this.rollLeft ? "screen.radiation.vault_door.roll_left" : "screen.radiation.vault_door.roll_right");
	}

	private void updateDone() {
		if (this.done != null) {
			this.done.active = !this.number.getValue().isEmpty();
		}
	}

	private void save() {
		String s = this.number.getValue();
		if (!s.isEmpty()) {
			ClientPlayNetworking.send(new VaultDoorSettingsPayload(this.pos, Integer.parseInt(s), this.rollLeft));
		}
		this.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int cx = this.width / 2;
		int y = this.height / 2 - 40;
		graphics.centeredText(this.font, this.title, cx, y - 14, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.translatable("screen.radiation.vault_door.number"), cx, y + 2, 0xFFA0A0A0);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
