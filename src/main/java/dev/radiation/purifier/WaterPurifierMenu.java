package dev.radiation.purifier;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Water purifier: raw water in, empty bottles and canteens, clean water out; tank, progress and power. */
public class WaterPurifierMenu extends AbstractContainerMenu {
	public static final int INPUT_X = 26;
	public static final int INPUT_Y = 35;
	public static final int CONTAINERS_X = 116;
	public static final int CONTAINERS_Y = 17;
	public static final int OUTPUT_X = 116;
	public static final int OUTPUT_Y = 53;
	private final Container container;
	private final ContainerData data;

	public WaterPurifierMenu(int id, Inventory inventory) {
		this(id, inventory, new SimpleContainer(WaterPurifierBlockEntity.SLOTS), new SimpleContainerData(7));
	}

	public WaterPurifierMenu(int id, Inventory inventory, Container container, ContainerData data) {
		super(PurifierBlocks.PURIFIER_MENU, id);
		this.container = container;
		this.data = data;
		this.addSlot(new Slot(container, WaterPurifierBlockEntity.INPUT, INPUT_X, INPUT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return WaterPurifierBlockEntity.rawWater(stack);
			}
		});
		this.addSlot(new Slot(container, WaterPurifierBlockEntity.CONTAINERS, CONTAINERS_X, CONTAINERS_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return WaterPurifierBlockEntity.emptyContainer(stack);
			}
		});
		this.addSlot(new Slot(container, WaterPurifierBlockEntity.OUTPUT, OUTPUT_X, OUTPUT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, 8 + col * 18, 142));
		}
		this.addDataSlots(data);
	}

	public int tank() {
		return this.data.get(0);
	}

	public int tankCapacity() {
		return Math.max(1, this.data.get(1));
	}

	public float progress() {
		return this.data.get(2) / 1000f;
	}

	/** Share of full power, 0..1. */
	public float power() {
		return this.data.get(3) / 100f;
	}

	public int watts() {
		return this.data.get(4);
	}

	public boolean gridworks() {
		return this.data.get(5) != 0;
	}

	public boolean pumping() {
		return this.data.get(6) != 0;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		int own = WaterPurifierBlockEntity.SLOTS;
		if (index < own) {
			if (!this.moveItemStackTo(stack, own, own + 36, true)) {
				return ItemStack.EMPTY;
			}
		} else if (WaterPurifierBlockEntity.rawWater(stack)) {
			if (!this.moveItemStackTo(stack, WaterPurifierBlockEntity.INPUT, WaterPurifierBlockEntity.INPUT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (WaterPurifierBlockEntity.emptyContainer(stack)) {
			if (!this.moveItemStackTo(stack, WaterPurifierBlockEntity.CONTAINERS, WaterPurifierBlockEntity.CONTAINERS + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return copy;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.container.stillValid(player);
	}
}
