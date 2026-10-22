package dev.radiation.purifier;

import dev.radiation.config.RadiationConfig;
import dev.radiation.food.FoodContamination;
import dev.radiation.thirst.CanteenItem;
import dev.radiation.thirst.WaterSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The water purifier. Raw water goes into its tank from a water bottle or canteen in the input slot (the emptied bottle or
 * canteen moves on to the containers slot), or is pumped from a water block touching it. With power it purifies one
 * serving after the other into the bottles and canteens in the containers slot; whatever radiation the water had stays in
 * the filters. Clean bottles come out in the output slot; canteens are filled where they are and move to the output when full.
 */
public class WaterPurifierBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
	public static final int INPUT = 0;
	public static final int CONTAINERS = 1;
	public static final int OUTPUT = 2;
	public static final int SLOTS = 3;
	private static final int[] ALL_SLOTS = {INPUT, CONTAINERS, OUTPUT};

	private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
	private int tank;
	/** How much of the tank was pumped from a water block (free water: it makes room for water poured in). */
	private int pumped;
	private float progress;
	private float pump;
	/** Share of the demand the grid delivered at the last solve, and when that was. */
	private double fraction;
	private long suppliedAt = Long.MIN_VALUE / 2;
	private boolean working;
	/** Contamination taken out of the water so far, in rad (for the panel; filters never need changing). */
	private float removedRads;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int i) {
			WaterPurifierBlockEntity be = WaterPurifierBlockEntity.this;
			return switch (i) {
				case 0 -> be.tank;
				case 1 -> RadiationConfig.get().purifier.tankServings;
				case 2 -> Math.round(be.progress * 1000);
				case 3 -> (int) Math.round(be.power() * 100);
				case 4 -> (int) Math.round(be.drawWatts());
				case 5 -> PurifierPower.gridworks() ? 1 : 0;
				case 6 -> be.pumping() ? 1 : 0;
				default -> 0;
			};
		}

		@Override
		public void set(int i, int value) {
		}

		@Override
		public int getCount() {
			return 7;
		}
	};

	public WaterPurifierBlockEntity(BlockPos pos, BlockState state) {
		super(PurifierBlocks.PURIFIER_ENTITY, pos, state);
	}

	// ------------------------------------------------------------------ power

	/** Power wanted from the grid right now (watts). */
	public double demand() {
		return this.wantsToWork() ? RadiationConfig.get().purifier.watts : RadiationConfig.get().purifier.standbyWatts;
	}

	/** Called by the grid after every solve. */
	public void supplied(double fraction, long gameTime) {
		this.fraction = Math.clamp(fraction, 0, 1);
		this.suppliedAt = gameTime;
	}

	/** Share of full power available now: 1 without Gridworks (or with requireGridPower off), else what the grid delivered. */
	public double power() {
		if (!PurifierPower.needsGrid()) {
			return 1;
		}
		if (this.level == null || this.level.getGameTime() - this.suppliedAt > 30) {
			return 0;
		}
		return this.fraction;
	}

	private double drawWatts() {
		if (PurifierPower.needsGrid() && this.power() <= 0) {
			return 0;
		}
		return this.demand() * this.power();
	}

	// ------------------------------------------------------------------ work

	private boolean wantsToWork() {
		return (this.tank > 0 || this.pumping()) && this.target() != Target.NONE;
	}

	private enum Target { NONE, OUTPUT_CANTEEN, CANTEEN, BOTTLE, PLASTIC }

	private Target target() {
		ItemStack out = this.items.get(OUTPUT);
		ItemStack box = this.items.get(CONTAINERS);
		if (out.getItem() instanceof CanteenItem && CanteenItem.servings(out) < CanteenItem.capacity()) return Target.OUTPUT_CANTEEN;
		if (box.getItem() instanceof CanteenItem && (CanteenItem.servings(box) < CanteenItem.capacity() || out.isEmpty())) return Target.CANTEEN;
		if (box.is(Items.GLASS_BOTTLE) && out.isEmpty()) return Target.BOTTLE;
		if (box.is(PurifierBlocks.PLASTIC_BOTTLE) && (out.isEmpty()
				|| out.is(PurifierBlocks.PURIFIED_WATER) && out.getCount() < out.getMaxStackSize() && FoodContamination.of(out) <= 0)) return Target.PLASTIC;
		return Target.NONE;
	}

	/** Whether a water block touches it (it pumps from that). */
	public boolean pumping() {
		if (this.level == null) return false;
		for (Direction d : Direction.values()) {
			if (d != Direction.UP && this.level.getFluidState(this.worldPosition.relative(d)).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, WaterPurifierBlockEntity be) {
		if (level instanceof ServerLevel) {
			be.serverTick(state);
		}
	}

	private void serverTick(BlockState state) {
		RadiationConfig.Purifier cfg = RadiationConfig.get().purifier;
		boolean changed = this.takeInput(cfg);
		if (this.tank < cfg.tankServings && this.level.getGameTime() % 20 == 0 && this.pumping()) {
			this.pump += 1f / cfg.pumpSecondsPerServing;
		}
		while (this.pump >= 1 && this.tank < cfg.tankServings) {
			this.pump -= 1;
			this.tank++;
			this.pumped++;
			changed = true;
		}
		if (this.tank >= cfg.tankServings) {
			this.pump = 0;
		}

		// a full canteen waiting in the containers slot moves on
		ItemStack box = this.items.get(CONTAINERS);
		if (box.getItem() instanceof CanteenItem && CanteenItem.servings(box) >= CanteenItem.capacity() && this.items.get(OUTPUT).isEmpty()) {
			this.items.set(OUTPUT, box);
			this.items.set(CONTAINERS, ItemStack.EMPTY);
			changed = true;
		}

		Target target = this.tank > 0 ? this.target() : Target.NONE;
		double power = this.power();
		boolean running = target != Target.NONE && power > 0.01;
		if (running) {
			this.progress += (float) (power / (cfg.secondsPerServing * 20.0));
			if (this.progress >= 1) {
				this.progress = 0;
				this.tank--;
				this.pumped = Math.min(this.pumped, this.tank);
				this.deliver(target);
				changed = true;
			}
		} else if (target == Target.NONE) {
			this.progress = 0;
		}
		if (running != this.working || state.getValue(WaterPurifierBlock.WORKING) != running) {
			this.working = running;
			this.level.setBlock(this.worldPosition, state.setValue(WaterPurifierBlock.WORKING, running), Block.UPDATE_CLIENTS);
		}
		if (changed || running) {
			this.setChanged();
		}
	}

	/** Empties a water bottle or one serving of a canteen from the input slot into the tank. */
	private boolean takeInput(RadiationConfig.Purifier cfg) {
		ItemStack in = this.items.get(INPUT);
		if (in.isEmpty()) return false;
		if (this.tank >= cfg.tankServings && this.pumped > 0 && rawWater(in) && (!in.is(PurifierBlocks.DIRTY_WATER) && !in.is(PurifierBlocks.PURIFIED_WATER)
				|| this.makeRoomForPlastic())) {
			// water poured in comes first: a pumped serving goes back where it came from
			this.tank--;
			this.pumped--;
		}
		if (this.tank < cfg.tankServings) {
			if (in.is(Items.POTION) && WaterSources.isWater(in)) {
				this.removedRads += FoodContamination.of(in);
				this.items.set(INPUT, new ItemStack(Items.GLASS_BOTTLE));
				this.tank++;
				return true;
			}
			if ((in.is(PurifierBlocks.DIRTY_WATER) || in.is(PurifierBlocks.PURIFIED_WATER)) && this.makeRoomForPlastic()) {
				// one bottle at a time; the empty one goes to the containers slot to be filled with clean water
				this.removedRads += FoodContamination.of(in);
				in.shrink(1);
				ItemStack box = this.items.get(CONTAINERS);
				if (box.isEmpty()) this.items.set(CONTAINERS, new ItemStack(PurifierBlocks.PLASTIC_BOTTLE));
				else box.grow(1);
				this.tank++;
				return true;
			}
			if (in.getItem() instanceof CanteenItem && CanteenItem.servings(in) > 0) {
				float rads = CanteenItem.take(in);
				this.removedRads += Math.max(0, rads);
				this.tank++;
				return true;
			}
		}
		// the empty bottle or canteen goes on to be filled with clean water
		boolean empty = in.is(Items.GLASS_BOTTLE) || in.is(PurifierBlocks.PLASTIC_BOTTLE) || in.getItem() instanceof CanteenItem && CanteenItem.servings(in) == 0;
		if (empty) {
			ItemStack box = this.items.get(CONTAINERS);
			if (box.isEmpty()) {
				this.items.set(CONTAINERS, in);
				this.items.set(INPUT, ItemStack.EMPTY);
				return true;
			}
			if (!(in.getItem() instanceof CanteenItem) && ItemStack.isSameItemSameComponents(in, box) && box.getCount() < box.getMaxStackSize()) {
				int move = Math.min(in.getCount(), box.getMaxStackSize() - box.getCount());
				box.grow(move);
				in.shrink(move);
				return true;
			}
		}
		return false;
	}

	private void deliver(Target target) {
		switch (target) {
			case OUTPUT_CANTEEN -> CanteenItem.fill(this.items.get(OUTPUT), 1, 0);
			case CANTEEN -> {
				ItemStack box = this.items.get(CONTAINERS);
				if (CanteenItem.servings(box) < CanteenItem.capacity()) {
					CanteenItem.fill(box, 1, 0);
				}
			}
			case BOTTLE -> {
				this.items.get(CONTAINERS).shrink(1);
				this.items.set(OUTPUT, PotionContents.createItemStack(Items.POTION, Potions.WATER));
			}
			case PLASTIC -> {
				this.items.get(CONTAINERS).shrink(1);
				ItemStack out = this.items.get(OUTPUT);
				if (out.isEmpty()) this.items.set(OUTPUT, new ItemStack(PurifierBlocks.PURIFIED_WATER));
				else out.grow(1);
			}
			case NONE -> {
			}
		}
	}

	/** Whether an emptied plastic bottle fits into the containers slot. */
	private boolean makeRoomForPlastic() {
		ItemStack box = this.items.get(CONTAINERS);
		return box.isEmpty() || box.is(PurifierBlocks.PLASTIC_BOTTLE) && box.getCount() < box.getMaxStackSize();
	}

	public boolean working() {
		return this.working;
	}

	public int tank() {
		return this.tank;
	}

	public float progress() {
		return this.progress;
	}

	public float removedRads() {
		return this.removedRads;
	}

	// ------------------------------------------------------------------ container

	public static boolean rawWater(ItemStack stack) {
		return stack.is(Items.POTION) && WaterSources.isWater(stack) || stack.is(PurifierBlocks.DIRTY_WATER) || stack.is(PurifierBlocks.PURIFIED_WATER)
				|| stack.getItem() instanceof CanteenItem && CanteenItem.servings(stack) > 0;
	}

	public static boolean emptyContainer(ItemStack stack) {
		return stack.is(Items.GLASS_BOTTLE) || stack.is(PurifierBlocks.PLASTIC_BOTTLE) || stack.getItem() instanceof CanteenItem && CanteenItem.servings(stack) < CanteenItem.capacity();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return switch (slot) {
			case INPUT -> rawWater(stack);
			case CONTAINERS -> emptyContainer(stack);
			default -> false;
		};
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return ALL_SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
		return this.canPlaceItem(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return slot == OUTPUT;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.radiation.water_purifier");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	public int getContainerSize() {
		return SLOTS;
	}

	@Override
	protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
		return new WaterPurifierMenu(id, inventory, this, this.data);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
		this.tank = input.getIntOr("tank", 0);
		this.pumped = Math.min(this.tank, input.getIntOr("pumped", 0));
		this.progress = input.getFloatOr("progress", 0);
		this.removedRads = input.getFloatOr("removed_rads", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items, true);
		output.putInt("tank", this.tank);
		output.putInt("pumped", this.pumped);
		output.putFloat("progress", this.progress);
		output.putFloat("removed_rads", this.removedRads);
	}
}
