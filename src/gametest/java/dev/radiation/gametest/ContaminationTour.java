package dev.radiation.gametest;

import dev.radiation.item.DecontaminationSprayerItem;
import dev.radiation.registry.ModRegistry;
import dev.radiation.world.Contamination;
import dev.radiation.world.RadiationTracker;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Contaminated ground and water (Radiation 1.10): fallout lands on a meadow and a pond, the player washes a patch of the
 * meadow with the decontamination sprayer (part of the runoff goes into the pond) and then swims in the pond.
 * Run with {@code ./gradlew runClientGameTest -Pscene=contamination}.
 */
public class ContaminationTour implements FabricClientGameTest {
	private int g;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!"contamination".equals(System.getProperty("radiation.scene", ""))) {
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
					"gamemode survival @a", "gamerule spawn_mobs false", "gamerule keep_inventory true", "effect give @a minecraft:resistance infinite 4 true"}) {
				sp.getServer().runCommand(c);
			}
			this.g = sp.getServer().computeOnServer(server -> this.build(server.overworld()));
			context.waitTicks(40);
			System.out.println("[contamination] before: " + sp.getServer().computeOnServer(server -> this.measure(server.overworld())));

			// the Geiger counter's probe: sneak + right-click on the meadow
			sp.getServer().runOnServer(server -> {
				ServerPlayer p = server.getPlayerList().getPlayers().get(0);
				p.getInventory().setItem(1, new ItemStack(ModRegistry.GEIGER_COUNTER));
				p.teleportTo(0.5, this.g + 1, -3.5);
			});
			context.waitTicks(10);
			context.getInput().pressKey(o -> o.keyHotbarSlots[1]);
			context.waitTicks(5);
			context.getInput().lookAt(new BlockPos(0, this.g, 0));
			context.waitTicks(5);
			context.getInput().holdShift();
			context.waitTicks(3);
			context.getInput().pressKey(o -> o.keyUse);
			context.waitTicks(5);
			context.getInput().releaseShift();
			context.waitTicks(10);
			context.takeScreenshot("contamination_probe");
			context.getInput().pressKey(o -> o.keyHotbarSlots[0]);

			// spray the meadow in front of the player for 5 seconds
			sp.getServer().runOnServer(server -> {
				ServerPlayer p = server.getPlayerList().getPlayers().get(0);
				ItemStack sprayer = new ItemStack(ModRegistry.DECONTAMINATION_SPRAYER);
				sprayer.set(DecontaminationSprayerItem.WATER, 32);
				p.getInventory().setItem(0, sprayer);
				p.getInventory().setSelectedSlot(0);
				p.teleportTo(0.5, this.g + 1, -3.5);
			});
			context.waitTicks(10);
			context.getInput().lookAt(new BlockPos(0, this.g, 0));
			context.waitTicks(5);
			context.getInput().holdKey(o -> o.keyUse);
			context.waitTicks(40);
			context.takeScreenshot("contamination_spraying");
			context.waitTicks(60);
			context.getInput().releaseKey(o -> o.keyUse);
			context.waitTicks(5);
			System.out.println("[contamination] after spraying: " + sp.getServer().computeOnServer(server -> this.measure(server.overworld()))
					+ " | water left in the sprayer " + context.computeOnClient(mc -> DecontaminationSprayerItem.water(mc.player.getInventory().getItem(0))));

			// a swim in the pond: rads before and after 10 seconds under water
			float before = sp.getServer().computeOnServer(server -> RadiationTracker.getRads(server.getPlayerList().getPlayers().get(0)));
			sp.getServer().runOnServer(server -> server.getPlayerList().getPlayers().get(0).teleportTo(24.5, this.g - 1.5, 4.5));
			context.waitTicks(200);
			context.takeScreenshot("contamination_swimming");
			float after = sp.getServer().computeOnServer(server -> RadiationTracker.getRads(server.getPlayerList().getPlayers().get(0)));
			System.out.println(String.format(Locale.ROOT, "[contamination] swimming 10 s in the pond: %.1f -> %.1f rad", before, after));
		}
	}

	private int build(ServerLevel level) {
		int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0) - 1;
		// a pond 12 x 12, 3 deep, 18 blocks east of the meadow
		for (int x = 18; x < 30; x++) {
			for (int z = -2; z < 10; z++) {
				for (int d = 0; d < 3; d++) {
					level.setBlock(new BlockPos(x, y - d, z), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		// fallout over both, 1 rad/s in the middle
		Contamination.depositFallout(level, 10, 4, 1.0, 40, 0.15f);
		return y;
	}

	private String measure(ServerLevel level) {
		return String.format(Locale.ROOT, "meadow at the sprayed spot %.3f rad/s, meadow 10 blocks away %.3f rad/s, in the pond %.3f rad/s",
				RadiationTracker.exposureAt(level, new Vec3(0.5, this.g + 2, 0.5), null),
				RadiationTracker.exposureAt(level, new Vec3(0.5, this.g + 2, 10.5), null),
				RadiationTracker.exposureAt(level, new Vec3(24.5, this.g - 0.5, 4.5), null));
	}
}
