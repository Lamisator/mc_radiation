package dev.radiation.gametest;

import dev.radiation.api.RadiationApi;
import dev.radiation.config.RadiationConfig;
import dev.radiation.food.FoodContamination;
import dev.radiation.registry.ModRegistry;
import dev.radiation.world.RadiationTracker;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * Contaminated food (Radiation 1.7, caps and gamma since 1.8): wheat from an irradiated field and from a clean one, bread baked from it, beef
 * from an irradiated cow and cooked in a furnace, bread stored in a chest, lying on the ground and carried by the player
 * next to a source, and the player eating a contaminated loaf. Run with {@code ./gradlew runClientGameTest -Pscene=food}.
 */
public class FoodTour implements FabricClientGameTest {
	private int g;
	private String storeSource;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!"food".equals(System.getProperty("radiation.scene", ""))) {
			return;
		}
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext sp = context.worldBuilder()
				.setUseConsistentSettings(true)
				.adjustSettings(s -> s.getNormalPresetList().stream().filter(e -> e.preset() != null && e.preset().is(WorldPresets.FLAT)).findFirst()
						.ifPresent(s::setWorldType))
				.create()) {
			sp.getConnection().waitForChunksRender();
			for (String c : new String[]{"time set 6000", "weather clear", "gamerule advance_time false", "gamerule advance_weather false",
					"gamemode survival @a", "gamerule spawn_mobs false", "gamerule keep_inventory true"}) {
				sp.getServer().runCommand(c);
			}
			this.g = sp.getServer().computeOnServer(server -> this.build(server.overworld()));
			context.waitTicks(40);
			System.out.println("[food] " + sp.getServer().computeOnServer(server -> this.harvest(server.overworld())));
			System.out.println("[food] " + sp.getServer().computeOnServer(server -> this.animal(server.overworld())));
			// storage: a chest, bread on the ground and bread in the player's pocket, 2 rad/s, for a minute (faster uptake for the test)
			RadiationConfig.get().food.storageUptake = 0.05f;
			sp.getServer().runOnServer(server -> this.store(server.overworld(), server.getPlayerList().getPlayers().get(0)));
			context.waitTicks(1200);
			System.out.println("[food] " + sp.getServer().computeOnServer(server -> this.stored(server.overworld(), server.getPlayerList().getPlayers().get(0))));
			RadiationConfig.get().food.storageUptake = 0.005f;
			System.out.println("[food] " + sp.getServer().computeOnServer(server -> this.furnace(server.overworld())));
			System.out.println("[food] " + sp.getServer().computeOnServer(server -> this.eat(server.overworld(), server.getPlayerList().getPlayers().get(0))));

			// the tooltip: bread in the hotbar, inventory open, mouse over it
			sp.getServer().runOnServer(server -> {
				ServerPlayer p = server.getPlayerList().getPlayers().get(0);
				p.teleportTo(500.5, this.g + 1, 500.5);
				ItemStack bread = new ItemStack(Items.BREAD, 7);
				FoodContamination.set(bread, 30);
				p.getInventory().setItem(0, bread);
				p.getInventory().setItem(1, new ItemStack(Items.BREAD, 12));
				ItemStack beef = new ItemStack(Items.COOKED_BEEF, 3);
				FoodContamination.set(beef, 50);
				p.getInventory().setItem(2, beef);
			});
			context.waitTicks(20);
			for (int slot = 0; slot < 3; slot++) {
				final int s = slot;
				System.out.println("[food] tooltip slot " + s + ": " + context.computeOnClient(mc -> {
					ItemStack st = mc.player.getInventory().getItem(s);
					StringBuilder b = new StringBuilder();
					for (var line : st.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, net.minecraft.world.item.TooltipFlag.NORMAL)) {
						b.append(" | ").append(line.getString());
					}
					return b.toString();
				}));
			}
			context.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
			context.waitTicks(10);
			for (int slot : new int[]{0, 2}) {
				double[] at = context.computeOnClient(mc -> {
					int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
					double scale = mc.getWindow().getGuiScale();
					return new double[]{((w - 176) / 2.0 + 8 + 18 * slot + 8) * scale, ((h - 166) / 2.0 + 142 + 8) * scale};
				});
				context.getInput().setCursorPos(at[0], at[1]);
				context.waitTicks(5);
				context.takeScreenshot(slot == 0 ? "food_tooltip_bread" : "food_tooltip_beef");
			}
		}
	}

	private int build(ServerLevel level) {
		int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0) - 1;
		// two wheat fields, ripe: one 5 blocks from a fallout source, one far away
		for (int fx : new int[]{0, 200, 600}) {
			for (int dx = 0; dx < 3; dx++) {
				BlockPos p = new BlockPos(fx + dx, y, 0);
				level.setBlock(p, Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_CLIENTS);
				level.setBlock(p.above(), ((CropBlock) Blocks.WHEAT).getStateForAge(7), Block.UPDATE_CLIENTS);
			}
		}
		RadiationApi.addSource(level, "fallout_test", new Vec3(1.5, y + 1, 5.5), 0.6f, 40, RadiationApi.Falloff.LINEAR, true);
		// the third field next to a radiating block (gamma only, nothing in the soil): since 1.8.0 its wheat counts too
		level.setBlock(new BlockPos(601, y + 1, 4), Blocks.IRON_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
		RadiationApi.setEmitter(level, new BlockPos(601, y + 1, 4), 8, 32);
		return y;
	}

	private String harvest(ServerLevel level) {
		StringBuilder out = new StringBuilder("harvest:");
		for (int fx : new int[]{0, 200, 600}) {
			out.append(String.format(Locale.ROOT, "  field at %d (%.3f rad/s):", fx, RadiationApi.exposureAt(level, new Vec3(fx + 1.5, this.g + 1.5, 0.5))));
			for (int dx = 0; dx < 3; dx++) level.destroyBlock(new BlockPos(fx + dx, this.g + 1, 0), true);
			List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(fx - 3, this.g - 2, -3, fx + 6, this.g + 4, 3));
			ItemStack wheat = ItemStack.EMPTY;
			for (ItemEntity e : items) {
				ItemStack s = e.getItem();
				out.append(String.format(Locale.ROOT, " %dx %s %.2f rad", s.getCount(), s.getItem().toString(), FoodContamination.of(s)));
				if (s.is(Items.WHEAT) && wheat.isEmpty()) wheat = s.copy();
				e.discard();
			}
			// bread from three of that wheat
			if (!wheat.isEmpty()) {
				ItemStack w = wheat.copyWithCount(1);
				CraftingInput input = CraftingInput.of(3, 1, List.of(w, w.copy(), w.copy()));
				ItemStack bread = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
						.map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY);
				out.append(String.format(Locale.ROOT, " -> bread %.2f rad", FoodContamination.of(bread)));
			}
		}
		return out.toString();
	}

	private String animal(ServerLevel level) {
		Cow cow = net.minecraft.world.entity.EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
		cow.snapTo(100.5, this.g + 1, 100.5, 0, 0);
		cow.setNoAi(true);
		level.addFreshEntity(cow);
		cow.setAttached(ModRegistry.RADS, 400f);
		cow.kill(level);
		StringBuilder out = new StringBuilder("cow with 400 rads gives:");
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(97, this.g - 2, 97, 104, this.g + 4, 104))) {
			out.append(String.format(Locale.ROOT, " %dx %s %.2f rad", e.getItem().getCount(), e.getItem().getItem(), FoodContamination.of(e.getItem())));
			e.discard();
		}
		return out.toString();
	}

	private void store(ServerLevel level, ServerPlayer player) {
		BlockPos chest = new BlockPos(300, this.g + 1, 0);
		level.setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		((ChestBlockEntity) level.getBlockEntity(chest)).setItem(0, new ItemStack(Items.BREAD, 16));
		((ChestBlockEntity) level.getBlockEntity(chest)).setItem(1, new ItemStack(Items.WHEAT, 9));
		((ChestBlockEntity) level.getBlockEntity(chest)).setItem(2, new ItemStack(Items.COBBLESTONE, 9));
		ItemEntity onGround = new ItemEntity(level, 302.5, this.g + 1, 0.5, new ItemStack(Items.APPLE, 4));
		onGround.setUnlimitedLifetime();
		level.addFreshEntity(onGround);
		player.teleportTo(301.5, this.g + 1, 2.5);
		// a second chest next to a radiating block only
		BlockPos chest2 = new BlockPos(340, this.g + 1, 0);
		level.setBlock(chest2, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		((ChestBlockEntity) level.getBlockEntity(chest2)).setItem(0, new ItemStack(Items.BREAD, 16));
		level.setBlock(new BlockPos(342, this.g + 1, 0), Blocks.IRON_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
		RadiationApi.setEmitter(level, new BlockPos(342, this.g + 1, 0), 8, 32);
		player.getInventory().setItem(8, new ItemStack(Items.BREAD, 5));
		this.storeSource = RadiationApi.addSource(level, "store_test", new Vec3(301.5, this.g + 1.5, 0.5), 2f, 20, RadiationApi.Falloff.LINEAR, true);
	}

	private String stored(ServerLevel level, ServerPlayer player) {
		ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(new BlockPos(300, this.g + 1, 0));
		StringBuilder out = new StringBuilder(String.format(Locale.ROOT, "a minute at %.2f rad/s:", RadiationApi.exposureAt(level, new Vec3(300.5, this.g + 1.5, 0.5))));
		out.append(String.format(Locale.ROOT, " chest bread %.2f, wheat %.2f, cobblestone has none: %s;", FoodContamination.of(chest.getItem(0)),
				FoodContamination.of(chest.getItem(1)), chest.getItem(2).get(FoodContamination.CONTAMINATION) == null));
		ChestBlockEntity chest2 = (ChestBlockEntity) level.getBlockEntity(new BlockPos(340, this.g + 1, 0));
		out.append(String.format(Locale.ROOT, " chest next to a radiating block only (%.2f rad/s): bread %.2f;",
				RadiationApi.exposureAt(level, new Vec3(340.5, this.g + 1.5, 0.5)), FoodContamination.of(chest2.getItem(0))));
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(299, this.g - 2, -3, 306, this.g + 4, 4))) {
			out.append(String.format(Locale.ROOT, " apple on the ground %.2f;", FoodContamination.of(e.getItem())));
		}
		out.append(String.format(Locale.ROOT, " bread in the player's inventory %.2f (player took %.0f rads)", FoodContamination.of(player.getInventory().getItem(8)),
				RadiationTracker.getRads(player)));
		RadiationApi.removeSource(this.storeSource);
		RadiationTracker.setRads(player, 0);
		return out.toString();
	}

	private String furnace(ServerLevel level) {
		BlockPos pos = new BlockPos(400, this.g + 1, 0);
		level.setBlock(pos, Blocks.FURNACE.defaultBlockState(), Block.UPDATE_ALL);
		FurnaceBlockEntity f = (FurnaceBlockEntity) level.getBlockEntity(pos);
		ItemStack beef = new ItemStack(Items.BEEF, 2);
		FoodContamination.set(beef, 50);
		float rawHeld = FoodContamination.of(beef);
		f.setItem(0, beef);
		f.setItem(1, new ItemStack(Items.COAL, 1));
		for (int i = 0; i < 520; i++) FurnaceBlockEntity.serverTick(level, pos, level.getBlockState(pos), f);
		ItemStack out = f.getItem(2);
		return String.format(Locale.ROOT, "furnace: 2 raw beef set to 50 rad (held %.0f, cap %.0f) -> %dx %s at %.2f rad (cap %.0f)", rawHeld,
				FoodContamination.cap(new ItemStack(Items.BEEF)), out.getCount(), out.getItem(), FoodContamination.of(out), FoodContamination.cap(out));
	}

	private String eat(ServerLevel level, ServerPlayer player) {
		RadiationTracker.setRads(player, 0);
		player.getFoodData().setFoodLevel(4);
		ItemStack bread = new ItemStack(Items.BREAD, 1);
		FoodContamination.set(bread, 30);
		float before = RadiationTracker.getRads(player);
		float breadCap = FoodContamination.cap(bread);
		bread.finishUsingItem(level, player);
		float afterBread = RadiationTracker.getRads(player);
		ItemStack clean = new ItemStack(Items.APPLE, 1);
		clean.finishUsingItem(level, player);
		return String.format(Locale.ROOT, "eating: rads %.1f -> %.1f after a loaf set to 30 rad (cap %.0f) -> %.1f after a clean apple; food level %d",
				before, afterBread, breadCap, RadiationTracker.getRads(player), player.getFoodData().getFoodLevel());
	}
}
