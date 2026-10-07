package dev.radiation.gametest;

import dev.radiation.api.RadiationApi;
import dev.radiation.config.RadiationConfig;
import dev.radiation.registry.ModRegistry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * What radiation does to the land (Radiation 1.4): a strip of grassland with flowers, a tree and cows leading away
 * from a strong source, three wheat fields at different dose rates, and concrete walls of 0 to 4 blocks in front of
 * emitters. Run with {@code ./gradlew runClientGameTest -Pscene=ecology}.
 */
public class EcologyTour implements FabricClientGameTest {
	private static final int FIELD_Z = -60;
	private int g;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!"ecology".equals(System.getProperty("radiation.scene", ""))) {
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
					"gamemode creative @a", "gamerule spawn_mobs false"}) {
				sp.getServer().runCommand(c);
			}
			// quick: every column looked at every second, every change happens at once
			RadiationConfig.Ecology eco = RadiationConfig.get().ecology;
			eco.samplesPerChunk = 256;
			eco.changeChance = 1;
			this.g = sp.getServer().computeOnServer(server -> this.build(server.overworld()));
			int y = this.g;
			context.waitTicks(40);
			this.look(context, sp, -6.5, y + 14, -22.5, new Vec3(24, y, 2));
			this.shot(context, "ecology_before");
			// shielding first, before anything else is around
			System.out.println("[ecology] " + sp.getServer().computeOnServer(server -> this.shielding(server.overworld())));

			sp.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				RadiationApi.addSource(level, "test_hot", new Vec3(0.5, y + 1, 2.5), 40, 48, RadiationApi.Falloff.LINEAR, true);
				RadiationApi.addSource(level, "test_mild", new Vec3(10.5, y + 1, FIELD_Z + 2.5), 0.4f, 30, RadiationApi.Falloff.LINEAR, true);
			});
			sp.getServer().runCommand("gamerule random_tick_speed 300");
			context.waitTicks(600);
			sp.getServer().runCommand("gamerule random_tick_speed 3");
			System.out.println("[ecology] " + sp.getServer().computeOnServer(server -> this.report(server.overworld())));
			this.look(context, sp, -6.5, y + 14, -22.5, new Vec3(24, y, 2));
			this.shot(context, "ecology_after");
			this.look(context, sp, 10.5, y + 9, FIELD_Z - 14.5, new Vec3(45, y, FIELD_Z + 2));
			this.shot(context, "ecology_fields");
		}
	}

	private int build(ServerLevel level) {
		int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0) - 1;
		// the strip: grass with a flower or tall grass on every block, 0 to 47 blocks from the source
		for (int x = 0; x <= 47; x++) {
			for (int z = 0; z <= 4; z++) {
				level.setBlock(new BlockPos(x, y, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
				BlockState plant = (x + z) % 3 == 0 ? Blocks.POPPY.defaultBlockState() : (x + z) % 3 == 1 ? Blocks.SHORT_GRASS.defaultBlockState()
						: Blocks.DANDELION.defaultBlockState();
				level.setBlock(new BlockPos(x, y + 1, z), plant, Block.UPDATE_CLIENTS);
			}
		}
		// oak trees at 20 and 40 blocks
		for (int tx : new int[]{20, 40}) {
			for (int h = 1; h <= 4; h++) level.setBlock(new BlockPos(tx, y + h, 8), Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
			for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int h = 4; h <= 6; h++) {
				BlockPos p = new BlockPos(tx + dx, y + h, 8 + dz);
				if (level.getBlockState(p).isAir()) level.setBlock(p, Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), Block.UPDATE_CLIENTS);
			}
		}
		// cows at 5 and 25 blocks
		for (int cx : new int[]{5, 25}) {
			for (int i = 0; i < 2; i++) {
				Cow cow = net.minecraft.world.entity.EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
				cow.snapTo(cx + 0.5, y + 1, -3.5 - 2 * i, 0, 0);
				cow.setNoAi(true);
				level.addFreshEntity(cow);
			}
		}
		// wheat fields: 5 and 20 blocks from the mild source, and one far from anything
		for (int fx : new int[]{15, 30, 70}) {
			for (int dx = 0; dx < 5; dx++) {
				for (int dz = 0; dz < 5; dz++) {
					BlockPos p = new BlockPos(fx + dx, y, FIELD_Z + dz);
					if (dx == 2 && dz == 2) {
						level.setBlock(p, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
						continue;
					}
					level.setBlock(p, Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE, 7), Block.UPDATE_CLIENTS);
					level.setBlock(p.above(), Blocks.WHEAT.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		return y;
	}

	/** Emitters of 1000 rad/s at one metre, measured 6 m away behind 0 to 4 blocks of concrete. */
	private String shielding(ServerLevel level) {
		StringBuilder out = new StringBuilder("concrete shielding, 1000 rad/s emitter, 6 m:");
		for (int walls = 0; walls <= 4; walls++) {
			int x = 300 + 10 * walls;
			BlockPos source = new BlockPos(x, this.g + 1, 0);
			level.setBlock(source, Blocks.IRON_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
			RadiationApi.setEmitter(level, source, 1000, 32);
			for (int w = 0; w < walls; w++) {
				for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) {
					level.setBlock(new BlockPos(x + dx, this.g + 1 + dy, 2 + w), Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.LIGHT_GRAY).defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			float r = RadiationApi.exposureAt(level, new Vec3(x + 0.5, this.g + 1.5, 6.5));
			out.append(String.format(Locale.ROOT, "  %d blocks %.4f", walls, r));
			RadiationApi.removeEmitter(level, source);
		}
		return out.toString();
	}

	private String report(ServerLevel level) {
		StringBuilder out = new StringBuilder();
		// the strip, in bands of 6 blocks
		out.append("strip (distance: rad/s  ground grass/dirt/sand  plants alive/dead bush):");
		for (int band = 0; band < 8; band++) {
			int grass = 0, dirt = 0, sand = 0, alive = 0, dead = 0;
			for (int x = band * 6; x < band * 6 + 6; x++) {
				for (int z = 0; z <= 4; z++) {
					BlockState gnd = level.getBlockState(new BlockPos(x, this.g, z));
					BlockState top = level.getBlockState(new BlockPos(x, this.g + 1, z));
					if (gnd.is(Blocks.GRASS_BLOCK)) grass++; else if (gnd.is(Blocks.DIRT)) dirt++; else if (gnd.is(Blocks.SAND)) sand++;
					if (top.is(Blocks.DEAD_BUSH)) dead++; else if (!top.isAir()) alive++;
				}
			}
			float rate = RadiationApi.exposureAt(level, new Vec3(band * 6 + 3, this.g + 1, 2.5));
			out.append(String.format(Locale.ROOT, "\n[ecology]   %2d-%2d m %6.2f rad/s: %2d/%2d/%2d  %2d/%2d", band * 6, band * 6 + 5, rate, grass, dirt, sand, alive, dead));
		}
		for (int tx : new int[]{20, 40}) {
			int leaves = 0;
			for (BlockPos p : BlockPos.betweenClosed(tx - 2, this.g + 4, 6, tx + 2, this.g + 6, 10)) {
				if (level.getBlockState(p).is(Blocks.OAK_LEAVES)) leaves++;
			}
			out.append(String.format(Locale.ROOT, "\n[ecology] tree at %d m (%.2f rad/s): %d leaves left of 69", tx,
					RadiationApi.exposureAt(level, new Vec3(tx, this.g + 5, 8)), leaves));
		}
		for (Cow cow : level.getEntitiesOfClass(Cow.class, new AABB(-10, this.g - 5, -20, 60, this.g + 10, 10))) {
			Float rads = cow.getAttached(ModRegistry.RADS);
			out.append(String.format(Locale.ROOT, "\n[ecology] cow at %.0f m: %s, %.0f rads, health %.1f, effects %s", cow.getX(),
					cow.isAlive() ? "alive" : "dead", rads == null ? 0f : rads, cow.getHealth(), cow.getActiveEffects().size()));
		}
		long deadCows = 4 - level.getEntitiesOfClass(Cow.class, new AABB(-10, this.g - 5, -20, 60, this.g + 10, 10)).size();
		out.append("\n[ecology] cows gone (died): ").append(deadCows);
		for (int fx : new int[]{15, 30, 70}) {
			int sum = 0, n = 0;
			for (int dx = 0; dx < 5; dx++) for (int dz = 0; dz < 5; dz++) {
				BlockState s = level.getBlockState(new BlockPos(fx + dx, this.g + 1, FIELD_Z + dz));
				if (s.getBlock() instanceof CropBlock crop) {
					sum += crop.getAge(s);
					n++;
				}
			}
			out.append(String.format(Locale.ROOT, "\n[ecology] wheat at x=%d (%.3f rad/s): %d plants, average age %.2f of 7", fx,
					RadiationApi.exposureAt(level, new Vec3(fx + 2.5, this.g + 1.5, FIELD_Z + 2.5)), n, n == 0 ? 0 : (double) sum / n));
		}
		return out.toString();
	}

	private void shot(ClientGameTestContext context, String name) {
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		context.takeScreenshot(name);
	}

	private void look(ClientGameTestContext context, TestSingleplayerContext sp, double x, double y, double z, Vec3 target) {
		double dx = target.x - x, dy = target.y - (y + 1.62), dz = target.z - z;
		float yaw = (float) (-Math.toDegrees(Math.atan2(dx, dz)));
		float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		sp.getServer().runOnServer(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				p.getAbilities().flying = true;
				p.onUpdateAbilities();
			}
		});
		context.waitTicks(30);
	}
}
