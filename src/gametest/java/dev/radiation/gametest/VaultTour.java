package dev.radiation.gametest;

import dev.radiation.registry.ModRegistry;
import dev.radiation.vault.SlidingDoorBlock;
import dev.radiation.vault.VaultBlocks;
import dev.radiation.vault.VaultDoorBlock;
import dev.radiation.vault.VaultDoorBlockEntity;
import dev.radiation.vault.VaultLampBlock;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Builds a vault entrance in a flat world and photographs the door, its animation and the interior blocks. */
public class VaultTour implements FabricClientGameTest {
	private static final int X = 0;
	private static final int Z = 0;
	private int ground;

	@Override
	public void runTest(ClientGameTestContext context) {
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
			this.ground = sp.getServer().computeOnServer(server -> build(server.overworld()));
			int g = this.ground;
			BlockPos door = new BlockPos(X, g + 3, Z);
			context.waitTicks(40);

			this.look(context, sp, X + 0.5, g, Z + 11.5, Vec3.atCenterOf(door));
			this.shot(context, "vault_door_closed");
			this.look(context, sp, X + 7.5, g + 1, Z + 6.5, Vec3.atCenterOf(door));
			this.shot(context, "vault_door_closed_angle");

			sp.getServer().runOnServer(server -> ((VaultDoorBlockEntity) server.overworld().getBlockEntity(door)).configure(73, false));
			context.waitTicks(10);
			this.shot(context, "vault_door_73");

			// settings screen
			context.runOnClient(mc -> mc.gui.setScreen(new dev.radiation.client.vault.VaultDoorScreen(
					(VaultDoorBlockEntity) mc.level.getBlockEntity(door))));
			context.waitTicks(5);
			this.shot(context, "vault_door_settings");
			context.runOnClient(mc -> mc.gui.setScreen(null));

			// the screw arm at work, seen from inside the vault
			this.look(context, sp, X + 6.5, g + 1, Z - 8.5, new Vec3(X + 0.5, g + 3, Z - 2.5));
			this.shot(context, "vault_arm_idle");
			sp.getServer().runOnServer(server -> ((VaultDoorBlockEntity) server.overworld().getBlockEntity(door)).setOpen(true));
			context.waitTicks(15);
			this.shot(context, "vault_arm_extending");
			boolean alarm = context.computeOnClient(mc -> ((VaultDoorBlockEntity) mc.level.getBlockEntity(door)).alarmPlaying);
			System.out.println("[vault-tour] alarm loop playing while opening: " + alarm);
			context.waitTicks(22);
			this.shot(context, "vault_arm_screwing");
			context.waitTicks(30);
			this.shot(context, "vault_arm_pulling");
			context.waitTicks(22);
			this.shot(context, "vault_arm_releasing");
			context.waitTicks(40);
			this.shot(context, "vault_door_rolling");
			context.waitTicks(60);
			this.shot(context, "vault_door_open_inside");
			this.look(context, sp, X + 6.5, g + 1, Z + 8.5, Vec3.atCenterOf(door));
			this.shot(context, "vault_door_open");
			this.look(context, sp, X + 0.5, g, Z + 9.5, Vec3.atCenterOf(door.north(4)));
			this.shot(context, "vault_door_open_front");
			// the room: sliding doors, lamps
			this.look(context, sp, X + 0.5, g, Z - 3.5, new Vec3(X + 0.5, g + 1.5, Z - 13));
			this.shot(context, "vault_room");
			BlockPos slide = new BlockPos(X, g, Z - 15);
			sp.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				BlockState s = level.getBlockState(slide);
				s.useWithoutItem(level, server.getPlayerList().getPlayers().get(0),
						new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(slide), Direction.SOUTH, slide, false));
			});
			System.out.println("[vault-tour] sliding door after use: " + sp.getServer().computeOnServer(server -> server.overworld().getBlockState(slide).toString()));
			context.waitTicks(6);
			this.shot(context, "vault_sliding_half");
			context.waitTicks(20);
			this.shot(context, "vault_sliding_open");

			// closing
			sp.getServer().runOnServer(server -> ((VaultDoorBlockEntity) server.overworld().getBlockEntity(door)).setOpen(false));
			this.look(context, sp, X + 6.5, g + 1, Z + 8.5, Vec3.atCenterOf(door));
			context.waitTicks(20);
			this.shot(context, "vault_alarm_lights");
			context.waitTicks(170);
			this.shot(context, "vault_door_closed_again");
			boolean parts = sp.getServer().computeOnServer(server -> server.overworld().getBlockState(door.above(2)).is(VaultBlocks.VAULT_DOOR_PART));
			System.out.println("[vault-tour] parts restored after closing: " + parts);

			this.creativeTab(context);
		}
	}

	/** Builds the vault entrance wall (facing south, +z) and a small room behind it; returns the floor level. */
	private static int build(ServerLevel level) {
		int g = level.getMinY() + 4;
		while (!level.getBlockState(new BlockPos(X, g, Z)).isAir()) {
			g++;
		}
		BlockState wall = VaultBlocks.VAULT_WALL.defaultBlockState();
		BlockState stripe = VaultBlocks.VAULT_WALL_STRIPE.defaultBlockState();
		// outer wall at z = Z with the round opening
		fill(level, X - 8, g, Z, X + 8, g + 7, Z, Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.GRAY).defaultBlockState());
		fill(level, X - 3, g, Z, X + 3, g + 6, Z, VaultBlocks.VAULT_DOOR_FRAME.defaultBlockState());
		BlockPos door = new BlockPos(X, g + 3, Z);
		level.setBlock(door, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		for (BlockPos p : VaultDoorBlock.footprint(door, Direction.SOUTH)) {
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		level.setBlock(door, VaultBlocks.VAULT_DOOR.defaultBlockState().setValue(VaultDoorBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
		// hazard stripes on the ground in front, console beside the door
		fill(level, X - 3, g - 1, Z + 1, X + 3, g - 1, Z + 2, VaultBlocks.VAULT_HAZARD.defaultBlockState());
		level.setBlock(new BlockPos(X + 4, g, Z + 2), VaultBlocks.VAULT_CONSOLE.defaultBlockState()
				.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
		// room behind: x -8..8, z -14..-1, y g..g+6
		fill(level, X - 9, g - 1, Z - 15, X + 9, g - 1, Z - 1, VaultBlocks.VAULT_FLOOR.defaultBlockState());
		fill(level, X - 9, g, Z - 15, X - 9, g + 6, Z - 1, wall);
		fill(level, X + 9, g, Z - 15, X + 9, g + 6, Z - 1, wall);
		fill(level, X - 9, g, Z - 15, X + 9, g + 6, Z - 15, wall);
		fill(level, X - 9, g + 2, Z - 15, X - 9, g + 2, Z - 1, stripe);
		fill(level, X + 9, g + 2, Z - 15, X + 9, g + 2, Z - 1, stripe);
		fill(level, X - 9, g + 7, Z - 15, X + 9, g + 7, Z, wall);
		fill(level, X - 2, g - 1, Z - 9, X + 2, g - 1, Z - 6, VaultBlocks.VAULT_GRATE.defaultBlockState());
		fill(level, X + 6, g, Z - 14, X + 8, g + 6, Z - 14, VaultBlocks.VAULT_WALL_PIPES.defaultBlockState());
		// sliding doors in the back wall (2 wide)
		for (int dx = 0; dx <= 1; dx++) {
			BlockPos p = new BlockPos(X + dx, g, Z - 15);
			BlockState s = VaultBlocks.SLIDING_DOOR.defaultBlockState().setValue(SlidingDoorBlock.FACING, Direction.SOUTH);
			level.setBlock(p, s, Block.UPDATE_ALL);
			level.setBlock(p.above(), s.setValue(SlidingDoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
		}
		fill(level, X - 1, g, Z - 17, X + 2, g + 3, Z - 16, Blocks.AIR.defaultBlockState());
		// lamps
		for (int dx = -6; dx <= 6; dx += 4) {
			for (int dz = -12; dz <= -3; dz += 4) {
				level.setBlock(new BlockPos(X + dx, g + 6, Z + dz), VaultBlocks.VAULT_LIGHT_PANEL.defaultBlockState()
						.setValue(VaultLampBlock.FACING, Direction.DOWN), Block.UPDATE_ALL);
			}
		}
		for (int dz = -14; dz <= -1; dz++) {
			level.setBlock(new BlockPos(X - 8, g + 5, Z + dz), VaultBlocks.NEON_BLUE.defaultBlockState()
					.setValue(VaultLampBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
			level.setBlock(new BlockPos(X + 8, g + 5, Z + dz), VaultBlocks.NEON_YELLOW.defaultBlockState()
					.setValue(VaultLampBlock.FACING, Direction.WEST), Block.UPDATE_ALL);
		}
		level.setBlock(new BlockPos(X - 2, g + 3, Z - 14), VaultBlocks.NEON_WHITE.defaultBlockState()
				.setValue(VaultLampBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
		level.setBlock(new BlockPos(X + 3, g + 3, Z - 14), VaultBlocks.NEON_WHITE.defaultBlockState()
				.setValue(VaultLampBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
		level.setBlock(new BlockPos(X - 6, g, Z - 12), ModRegistry.NUCLEAR_WASTE_BARREL.defaultBlockState(), Block.UPDATE_ALL);
		for (int dx : new int[]{-4, 4}) {
			level.setBlock(new BlockPos(X + dx, g + 6, Z + 1), VaultBlocks.ALARM_LIGHT.defaultBlockState()
					.setValue(dev.radiation.vault.VaultAlarmLightBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
			level.setBlock(new BlockPos(X + dx, g + 6, Z - 1), VaultBlocks.ALARM_LIGHT.defaultBlockState()
					.setValue(dev.radiation.vault.VaultAlarmLightBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
		}
		return g;
	}

	private static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		for (BlockPos p : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
			level.setBlock(p, state, Block.UPDATE_CLIENTS);
		}
	}

	private void creativeTab(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			try {
				java.lang.reflect.Field f = net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.class.getDeclaredField("selectedTab");
				f.setAccessible(true);
				f.set(null, ModRegistry.TAB);
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
			mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), true));
		});
		context.waitTicks(5);
		context.runOnClient(mc -> {
			var screen = (net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen) mc.gui.screen();
			screen.switchToPage(screen.getPage(ModRegistry.TAB));
		});
		context.waitTicks(5);
		context.runOnClient(mc -> {
			try {
				java.lang.reflect.Method m = net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab",
						net.minecraft.world.item.CreativeModeTab.class);
				m.setAccessible(true);
				m.invoke(mc.gui.screen(), ModRegistry.TAB);
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
		context.getInput().setCursorPos(5, 5);
		context.waitTicks(5);
		this.shot(context, "creative_tab");
		context.runOnClient(mc -> mc.gui.setScreen(null));
	}

	private void shot(ClientGameTestContext context, String name) {
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		context.takeScreenshot(name);
	}

	private void tp(ClientGameTestContext context, TestSingleplayerContext sp, double x, double y, double z, float yaw, float pitch) {
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		sp.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
				player.setDeltaMovement(Vec3.ZERO);
			}
		});
		context.runOnClient(mc -> {
			if (mc.player != null) {
				mc.player.getAbilities().flying = true;
				mc.player.setDeltaMovement(Vec3.ZERO);
			}
		});
		context.waitTicks(5);
		try {
			sp.getConnection().waitForChunksRender(false, 600);
		} catch (AssertionError e) {
			System.out.println("[vault-tour] chunks still rendering");
		}
	}

	private void look(ClientGameTestContext context, TestSingleplayerContext sp, double x, double y, double z, Vec3 target) {
		double dx = target.x - x;
		double dy = target.y - (y + 1.62);
		double dz = target.z - z;
		float yaw = (float) (-Math.toDegrees(Math.atan2(dx, dz)));
		float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
		this.tp(context, sp, x, y, z, yaw, pitch);
	}
}
