package dev.radiation.gametest;

import dev.radiation.config.RadiationConfig;
import dev.radiation.food.FoodContamination;
import dev.radiation.purifier.PurifierBlocks;
import dev.radiation.purifier.WaterPurifierBlock;
import dev.radiation.purifier.WaterPurifierBlockEntity;
import dev.radiation.thirst.CanteenItem;
import dev.radiation.thirst.Thirst;
import dev.radiation.world.Contamination;
import dev.radiation.world.RadiationTracker;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Thirst (Radiation 1.11): the droplet bar, sips from a contaminated pond, a bottle and a canteen filled there, the water
 * purifier cleaning it (on Gridworks power with {@code -PwithGridworks}), and dehydration.
 * Run with {@code ./gradlew runClientGameTest -Pscene=thirst [-PwithGridworks]}.
 */
public class ThirstTour implements FabricClientGameTest {
	private int g;
	private int failures;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!"thirst".equals(System.getProperty("radiation.scene", ""))) {
			return;
		}
		boolean gridworks = FabricLoader.getInstance().isModLoaded("gridworks");
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext sp = context.worldBuilder()
				.setUseConsistentSettings(true)
				.adjustSettings(s -> s.getNormalPresetList().stream().filter(e -> e.preset() != null && e.preset().is(WorldPresets.FLAT)).findFirst()
						.ifPresent(s::setWorldType))
				.create()) {
			sp.getConnection().waitForChunksRender();
			for (String c : new String[]{"time set 6000", "weather clear", "gamerule advance_time false", "gamerule advance_weather false",
					"gamemode survival @a", "gamerule spawn_mobs false", "gamerule keep_inventory true", "difficulty normal"}) {
				sp.getServer().runCommand(c);
			}
			this.g = sp.getServer().computeOnServer(server -> this.build(server.overworld()));
			context.waitTicks(40);

			// 1. the bar: low thirst, too thirsty to sprint
			this.server(sp, p -> {
				Thirst.set(p, 5);
				p.teleportTo(-3.5, this.g + 1, 2.5);
			});
			context.waitTicks(30);
			context.getInput().lookAt(new BlockPos(0, this.g, 6));
			context.waitTicks(5);
			boolean noSprint = context.computeOnClient(mc -> Thirst.tooThirstyToSprint(mc.player));
			float clientThirst = context.computeOnClient(mc -> Thirst.get(mc.player));
			this.check(noSprint && Math.abs(clientThirst - 5) < 0.2f, "client sees thirst " + clientThirst + ", too thirsty to sprint: " + noSprint);
			context.takeScreenshot("thirst_hud_low");

			// 2. sips from the contaminated pond with an empty hand
			float[] before = this.state(sp);
			this.server(sp, p -> p.teleportTo(0.5, this.g + 1, 0.3));
			context.waitTicks(10);
			context.getInput().lookAt(new BlockPos(0, this.g, 2));
			context.waitTicks(5);
			context.getInput().holdKey(o -> o.keyUse);
			context.waitTicks(40);
			context.getInput().releaseKey(o -> o.keyUse);
			context.waitTicks(5);
			float[] after = this.state(sp);
			this.check(after[0] > before[0] + 3 && after[1] > before[1], String.format(Locale.ROOT,
					"sips: thirst %.1f -> %.1f, rads %.2f -> %.2f", before[0], after[0], before[1], after[1]));
			context.takeScreenshot("thirst_sips");

			// 3. a glass bottle filled at the pond carries its radiation
			this.server(sp, p -> {
				p.getInventory().setItem(0, new ItemStack(Items.GLASS_BOTTLE, 4));
				p.getInventory().setSelectedSlot(0);
			});
			context.getInput().pressKey(o -> o.keyHotbarSlots[0]);
			context.waitTicks(5);
			context.getInput().pressKey(o -> o.keyUse);
			context.waitTicks(10);
			float bottleRads = sp.getServer().computeOnServer(server -> {
				var inv = server.getPlayerList().getPlayers().get(0).getInventory();
				for (int i = 0; i < inv.getContainerSize(); i++) {
					if (inv.getItem(i).is(Items.POTION)) return FoodContamination.of(inv.getItem(i));
				}
				return -1f;
			});
			float rate = sp.getServer().computeOnServer(server -> RadiationTracker.contaminationAt(server.overworld(), Vec3.atCenterOf(new BlockPos(0, this.g, 1))));
			this.check(bottleRads > 0, String.format(Locale.ROOT, "bottle from the pond (%.3f rad/s there): %.2f rad", rate, bottleRads));

			// 4. a canteen from the pond, one serving drunk
			this.server(sp, p -> {
				p.getInventory().setItem(1, new ItemStack(PurifierBlocks.CANTEEN));
				p.getInventory().setSelectedSlot(1);
				Thirst.set(p, 8);
			});
			context.getInput().pressKey(o -> o.keyHotbarSlots[1]);
			context.waitTicks(5);
			context.getInput().pressKey(o -> o.keyUse);
			context.waitTicks(10);
			String canteen = sp.getServer().computeOnServer(server -> describe(server.getPlayerList().getPlayers().get(0).getInventory().getItem(1)));
			before = this.state(sp);
			context.getInput().lookAt(new BlockPos(0, this.g + 2, -10));
			context.waitTicks(3);
			context.getInput().holdKey(o -> o.keyUse);
			context.waitTicks(45);
			context.getInput().releaseKey(o -> o.keyUse);
			context.waitTicks(5);
			after = this.state(sp);
			String drunk = sp.getServer().computeOnServer(server -> describe(server.getPlayerList().getPlayers().get(0).getInventory().getItem(1)));
			this.check(canteen.startsWith("canteen 5/5") && drunk.startsWith("canteen 4/5") && after[0] >= before[0] + 5.5f,
					String.format(Locale.ROOT, "canteen filled: %s, after a drink %s; thirst %.1f -> %.1f, rads %.2f -> %.2f", canteen, drunk,
							before[0], after[0], before[1], after[1]));
			context.takeScreenshot("thirst_canteen");

			// 4b. plastic bottles: filled at the pond they hold Dirty Water, which carries the pond's radiation; drunk, the bottle stays
			this.server(sp, p -> {
				p.getInventory().setItem(2, new ItemStack(PurifierBlocks.PLASTIC_BOTTLE, 3));
				p.getInventory().setSelectedSlot(2);
				Thirst.set(p, 8);
			});
			context.getInput().pressKey(o -> o.keyHotbarSlots[2]);
			context.getInput().lookAt(new BlockPos(0, this.g, 2));
			context.waitTicks(5);
			context.getInput().pressKey(o -> o.keyUse);
			context.waitTicks(10);
			String dirty = sp.getServer().computeOnServer(server -> {
				var inv = server.getPlayerList().getPlayers().get(0).getInventory();
				for (int i = 0; i < inv.getContainerSize(); i++) {
					if (inv.getItem(i).is(PurifierBlocks.DIRTY_WATER)) {
						return inv.getItem(i).getHoverName().getString() + " x" + inv.getItem(i).getCount() + " " + FoodContamination.of(inv.getItem(i)) + " rad in slot " + i;
					}
				}
				return "none";
			});
			this.check(dirty.startsWith("Dirty Water x1") && !dirty.contains(" 0.0 rad"), "plastic bottle filled at the pond: " + dirty);
			context.takeScreenshot("thirst_dirty_water");
			// drink it: the dirty water went into a free slot; select it
			int dirtySlot = sp.getServer().computeOnServer(server -> {
				var inv = server.getPlayerList().getPlayers().get(0).getInventory();
				for (int i = 0; i < 9; i++) if (inv.getItem(i).is(PurifierBlocks.DIRTY_WATER)) return i;
				return -1;
			});
			if (dirtySlot >= 0) {
				context.getInput().pressKey(o -> o.keyHotbarSlots[dirtySlot]);
			}
			before = this.state(sp);
			context.getInput().lookAt(new BlockPos(0, this.g + 2, -10));
			context.waitTicks(3);
			context.getInput().holdKey(o -> o.keyUse);
			context.waitTicks(45);
			context.getInput().releaseKey(o -> o.keyUse);
			context.waitTicks(5);
			after = this.state(sp);
			int plastic = sp.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().get(0).getInventory().countItem(PurifierBlocks.PLASTIC_BOTTLE));
			this.check(after[0] >= before[0] + 5.5f && after[1] > before[1] + 5 && plastic == 3, String.format(Locale.ROOT,
					"drank the dirty water (slot %d): thirst %.1f -> %.1f, rads %.2f -> %.2f, empty plastic bottles %d", dirtySlot, before[0], after[0], before[1], after[1], plastic));

			// 5. the purifier: the dirty canteen in, glass bottles to fill; a water block touches it
			BlockPos purifier = new BlockPos(-2, this.g, 3);
			RadiationConfig.get().purifier.secondsPerServing = 2f;
			this.server(sp, p -> {
				ServerLevel level = p.level();
				if (level.getBlockEntity(purifier) instanceof WaterPurifierBlockEntity be) {
					be.setItem(WaterPurifierBlockEntity.INPUT, p.getInventory().getItem(1).copy());
					be.setItem(WaterPurifierBlockEntity.CONTAINERS, new ItemStack(Items.GLASS_BOTTLE, 3));
					p.getInventory().setItem(1, ItemStack.EMPTY);
				}
				p.teleportTo(-2.5, this.g + 1, 0.5);
			});
			context.waitTicks(100);
			String noPower = this.purifier(sp, purifier);
			if (gridworks) {
				// no grid yet: nothing happens
				this.check(noPower.contains("progress 0.00") && !noPower.contains("working"), "with Gridworks but no grid: " + noPower);
				this.openPurifier(sp, purifier);
				context.waitTicks(10);
				context.takeScreenshot("thirst_purifier_unpowered");
				context.runOnClient(mc -> mc.player.closeContainer());
				// a fuelled 5 kW generator next to it
				sp.getServer().runCommand(String.format(Locale.ROOT, "setblock %d %d %d gridworks:generator[facing=north]", -3, this.g, 3));
				sp.getServer().runCommand(String.format(Locale.ROOT, "item replace block %d %d %d container.0 with minecraft:coal_block 64", -3, this.g, 3));
				context.waitTicks(100);
			}
			String running = this.purifier(sp, purifier);
			this.check(running.contains("out ") && !running.contains("out empty"), "purifier after 5 s" + (gridworks ? " on a generator" : "") + ": " + running);
			this.openPurifier(sp, purifier);
			context.waitTicks(20);
			context.takeScreenshot("thirst_purifier");
			context.runOnClient(mc -> mc.player.closeContainer());
			// take the clean output out, let it run on
			for (int i = 0; i < 7; i++) {
				this.server(sp, p -> {
					if (p.level().getBlockEntity(purifier) instanceof WaterPurifierBlockEntity be) {
						ItemStack out = be.removeItemNoUpdate(WaterPurifierBlockEntity.OUTPUT);
						if (!out.isEmpty()) p.getInventory().add(out);
					}
				});
				context.waitTicks(60);
			}
			String clean = sp.getServer().computeOnServer(server -> {
				var inv = server.getPlayerList().getPlayers().get(0).getInventory();
				StringBuilder s = new StringBuilder();
				for (int i = 0; i < inv.getContainerSize(); i++) {
					ItemStack st = inv.getItem(i);
					if (st.is(Items.POTION) || st.getItem() instanceof CanteenItem) s.append(describe(st)).append("; ");
				}
				return s.toString();
			});
			this.check(clean.contains("canteen 5/5 0 rad"), "clean water from the purifier: " + clean + " | " + this.purifier(sp, purifier));
			this.server(sp, p -> p.teleportTo(-6.5, this.g + 1, 0.5));
			context.waitTicks(10);
			context.getInput().lookAt(purifier);
			context.waitTicks(10);
			context.takeScreenshot("thirst_purifier_block");

			// 5b. dirty water in plastic bottles in, empty plastic bottles: Purified Water out, the emptied bottles refilled
			this.server(sp, p -> {
				if (p.level().getBlockEntity(purifier) instanceof WaterPurifierBlockEntity be) {
					ItemStack in = new ItemStack(PurifierBlocks.DIRTY_WATER, 2);
					FoodContamination.set(in, 9.7f);
					be.setItem(WaterPurifierBlockEntity.INPUT, in);
					be.setItem(WaterPurifierBlockEntity.CONTAINERS, new ItemStack(PurifierBlocks.PLASTIC_BOTTLE, 2));
					be.setItem(WaterPurifierBlockEntity.OUTPUT, ItemStack.EMPTY);
				}
			});
			context.waitTicks(300);
			String plasticRun = this.purifier(sp, purifier);
			this.check(plasticRun.contains("out radiation:purified_water x4 0 rad") && plasticRun.contains("in empty"), "plastic bottles in the purifier: " + plasticRun);
			this.openPurifier(sp, purifier);
			context.waitTicks(10);
			context.takeScreenshot("thirst_purifier_plastic");
			context.runOnClient(mc -> mc.player.closeContainer());

			// 5c. like food: purified water stored where the fallout is takes it up; carried, it does not
			BlockPos chest = new BlockPos(1, this.g + 1, -1);
			float uptake = RadiationConfig.get().food.storageUptake;
			RadiationConfig.get().food.storageUptake = 0.05f; // ten times as fast, for the test
			this.server(sp, p -> {
				p.level().setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
				if (p.level().getBlockEntity(chest) instanceof net.minecraft.world.Container c) {
					c.setItem(0, new ItemStack(PurifierBlocks.PURIFIED_WATER, 2));
				}
				p.getInventory().setItem(8, new ItemStack(PurifierBlocks.PURIFIED_WATER, 2));
			});
			context.waitTicks(400);
			String stored = sp.getServer().computeOnServer(server -> {
				ItemStack inChest = server.overworld().getBlockEntity(chest) instanceof net.minecraft.world.Container c ? c.getItem(0) : ItemStack.EMPTY;
				ItemStack carried = server.getPlayerList().getPlayers().get(0).getInventory().getItem(8);
				return String.format(Locale.ROOT, "in the chest %s (%.3f rad/s there), carried %s", describe(inChest),
						RadiationTracker.contaminationAt(server.overworld(), Vec3.atCenterOf(chest)), describe(carried));
			});
			RadiationConfig.get().food.storageUptake = uptake;
			this.check(!stored.contains("in the chest radiation:purified_water x2 0 rad") && stored.contains("carried radiation:purified_water x2 0 rad"),
					"purified water after 20 s: " + stored);

			// 6. dehydration: at 0 the player takes damage (normal: down to half a heart)
			this.server(sp, p -> {
				Thirst.set(p, 0);
				p.getFoodData().setFoodLevel(10);
				p.getFoodData().setSaturation(0);
				p.setHealth(20);
			});
			context.waitTicks(220);
			float health = sp.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().get(0).getHealth());
			this.check(health < 20 && health >= 1, "dehydrated for 11 s: health " + health);
			context.takeScreenshot("thirst_dehydrated");
		}
		System.out.println("[thirst-check] done, " + this.failures + " failure(s)");
	}

	private void check(boolean ok, String what) {
		if (!ok) this.failures++;
		System.out.println("[thirst-check] " + (ok ? "OK   " : "FAIL ") + what);
	}

	private void server(TestSingleplayerContext sp, java.util.function.Consumer<ServerPlayer> action) {
		sp.getServer().runOnServer(server -> action.accept(server.getPlayerList().getPlayers().get(0)));
	}

	private float[] state(TestSingleplayerContext sp) {
		return sp.getServer().computeOnServer(server -> {
			ServerPlayer p = server.getPlayerList().getPlayers().get(0);
			return new float[]{Thirst.get(p), RadiationTracker.getRads(p)};
		});
	}

	private static String describe(ItemStack s) {
		if (s.getItem() instanceof CanteenItem) {
			return String.format(Locale.ROOT, "canteen %d/%d %s rad", CanteenItem.servings(s), CanteenItem.capacity(), FoodContamination.format(FoodContamination.of(s)));
		}
		return s.isEmpty() ? "empty" : s.getItem() + " x" + s.getCount() + " " + FoodContamination.format(FoodContamination.of(s)) + " rad";
	}

	private String purifier(TestSingleplayerContext sp, BlockPos pos) {
		return sp.getServer().computeOnServer(server -> {
			if (!(server.overworld().getBlockEntity(pos) instanceof WaterPurifierBlockEntity be)) return "no purifier";
			return String.format(Locale.ROOT, "tank %d, in %s, containers %s, out %s, power %.2f, demand %.0f W, %s, progress %.2f, removed %.1f rad",
					be.tank(), describe(be.getItem(0)), describe(be.getItem(1)), describe(be.getItem(WaterPurifierBlockEntity.OUTPUT)), be.power(), be.demand(),
					be.working() ? "working" : "idle", be.progress(), be.removedRads());
		});
	}

	private void openPurifier(TestSingleplayerContext sp, BlockPos pos) {
		this.server(sp, p -> {
			if (p.level().getBlockEntity(pos) instanceof WaterPurifierBlockEntity be) p.openMenu(be);
		});
	}

	private int build(ServerLevel level) {
		int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0) - 1;
		// a pond 8 x 8, 2 deep, north edge at z 1
		for (int x = -4; x < 4; x++) {
			for (int z = 1; z < 9; z++) {
				for (int d = 0; d < 2; d++) {
					level.setBlock(new BlockPos(x, y - d, z), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		// the purifier stands in the pond near its west bank (water on three sides), the generator's spot west of it
		level.setBlock(new BlockPos(-2, y, 3), PurifierBlocks.WATER_PURIFIER.defaultBlockState().setValue(WaterPurifierBlock.FACING, Direction.NORTH),
				Block.UPDATE_ALL);
		level.setBlock(new BlockPos(-3, y, 3), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
		// fallout on the pond, 1 rad/s in the middle
		Contamination.depositFallout(level, 0, 5, 1.0, 30, 0.15f);
		return y;
	}
}
