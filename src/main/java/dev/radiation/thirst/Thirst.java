package dev.radiation.thirst;

import com.mojang.serialization.Codec;
import dev.radiation.RadiationMod;
import dev.radiation.config.RadiationConfig;
import dev.radiation.food.FoodContamination;
import dev.radiation.world.RadiationTracker;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thirst: 20 points (ten droplets above the hunger bar). It drops over time, faster with everything that makes you hungry
 * and faster in hot biomes. Low, you cannot sprint; empty, you take damage like a starving player. Drinking - from a water
 * block, a bottle or a canteen - fills it up, and water taken where there is radiation carries rads like contaminated food.
 */
public final class Thirst {
	public static final float MAX = 20f;

	/** Points of thirst left (20 = not thirsty). Saved with the player, full again after respawning, synced to its owner for the HUD. */
	public static final AttachmentType<Float> THIRST = AttachmentRegistry.<Float>builder()
			.persistent(Codec.FLOAT)
			.initializer(() -> MAX)
			.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(RadiationMod.id("thirst"));

	public static final ResourceKey<DamageType> DEHYDRATION = ResourceKey.create(Registries.DAMAGE_TYPE, RadiationMod.id("dehydration"));

	/** On the client: what the server's config says (from the settings payload). */
	public static volatile boolean clientEnabled = true;
	public static volatile float clientSprintThreshold = 6f;

	private static final Map<UUID, State> STATES = new HashMap<>();

	private static final class State {
		float exhaustion;
		float damageTimer;
		long lastSip;
	}

	private Thirst() {
	}

	/** Registers the attachment early: its sync to clients is agreed on when they join. */
	public static void init() {
	}

	public static float get(Player player) {
		Float v = player.getAttached(THIRST);
		return v == null ? MAX : v;
	}

	public static void set(Player player, float value) {
		float v = Math.clamp(value, 0f, MAX);
		Float had = player.getAttached(THIRST);
		// only send it when the HUD would show something different (half droplets), or it reaches an end
		if (had == null || Math.abs(had - v) > 1e-4f) {
			player.setAttached(THIRST, v);
		}
	}

	public static boolean enabled(Player player) {
		return player.level().isClientSide() ? clientEnabled : RadiationConfig.get().thirst.enabled;
	}

	/** Too thirsty to sprint (like hunger at 6 or less); flying creative players always can. */
	public static boolean tooThirstyToSprint(Player player) {
		if (!enabled(player) || player.getAbilities().mayfly) {
			return false;
		}
		float threshold = player.level().isClientSide() ? clientSprintThreshold : RadiationConfig.get().thirst.sprintThreshold;
		return get(player) <= threshold;
	}

	/** Everything that makes you hungry makes you thirsty too (called with vanilla's food exhaustion). */
	public static void exhaust(ServerPlayer player, float amount) {
		if (amount <= 0 || !RadiationConfig.get().thirst.enabled) {
			return;
		}
		STATES.computeIfAbsent(player.getUUID(), id -> new State()).exhaustion += amount;
	}

	public static void forget(ServerPlayer player) {
		STATES.remove(player.getUUID());
	}

	public static void clear() {
		STATES.clear();
	}

	/** Once a second for every player. */
	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		RadiationConfig.Thirst cfg = RadiationConfig.get().thirst;
		if (!cfg.enabled) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isAlive()) {
				update(player, cfg);
			}
		}
	}

	private static void update(ServerPlayer player, RadiationConfig.Thirst cfg) {
		State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
		if (player.getAbilities().invulnerable) {
			// creative and spectator: no thirst, like no hunger
			state.exhaustion = 0;
			return;
		}
		ServerLevel level = player.level();
		float before = get(player);
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			state.exhaustion = 0;
			set(player, before + 1);
			return;
		}
		float hot = hot(level, player.blockPosition()) ? cfg.hotBiomeFactor : 1f;
		float loss = 1f / cfg.idleSecondsPerPoint;
		if (cfg.exhaustionPerPoint > 0) {
			loss += state.exhaustion / cfg.exhaustionPerPoint;
		}
		state.exhaustion = 0;
		float after = Math.max(0, before - loss * hot);
		set(player, after);

		if (before > cfg.sprintThreshold && after <= cfg.sprintThreshold) {
			player.sendOverlayMessage(Component.translatable("message.radiation.thirsty").withStyle(ChatFormatting.GOLD));
		}
		if (after <= 0) {
			if (before > 0) {
				player.sendOverlayMessage(Component.translatable("message.radiation.dehydrated").withStyle(ChatFormatting.RED));
			}
			state.damageTimer += 1;
			if (state.damageTimer >= cfg.damageIntervalSeconds) {
				state.damageTimer = 0;
				// like starvation: easy leaves you at 5 hearts, normal at half a heart, hard kills
				Difficulty d = level.getDifficulty();
				float floor = d == Difficulty.EASY ? 10 : d == Difficulty.NORMAL ? 1 : 0;
				float amount = floor == 0 ? cfg.damage : Math.min(cfg.damage, player.getHealth() - floor);
				if (amount > 0) {
					player.hurtServer(level, level.damageSources().source(DEHYDRATION), amount);
				}
			}
		} else {
			state.damageTimer = 0;
		}
	}

	/** Desert, savanna, badlands, the Nether: biomes warmer than 1.0. */
	public static boolean hot(ServerLevel level, BlockPos pos) {
		return level.getBiome(pos).value().getBaseTemperature() > 1.0f;
	}

	// ------------------------------------------------------------------ drinking

	/**
	 * Drinks: {@code points} of thirst back and {@code rads} of radiation from the water. A message says how much
	 * radiation came with it.
	 */
	public static void drink(ServerPlayer player, float points, float rads) {
		if (RadiationConfig.get().thirst.enabled) {
			set(player, get(player) + points);
		}
		if (rads > 0 && RadiationConfig.get().food.enabled) {
			RadiationTracker.addRads(player, rads);
			player.sendOverlayMessage(Component.translatable("message.radiation.drank_contaminated", FoodContamination.format(rads))
					.withStyle(ChatFormatting.GOLD));
		}
	}

	/** Rad per serving of water taken at this water block: all the radiation there, times {@code thirst.waterUptake}. */
	public static float waterRads(ServerLevel level, BlockPos water) {
		RadiationConfig.Thirst cfg = RadiationConfig.get().thirst;
		if (!RadiationConfig.get().food.enabled || cfg.waterUptake <= 0) {
			return 0;
		}
		float rate = RadiationTracker.contaminationAt(level, Vec3.atCenterOf(water));
		float rads = FoodContamination.fine(rate * cfg.waterUptake);
		return Math.min(rads, cfg.maxRadsPerServing);
	}

	/**
	 * A sip straight from a water block (right-click with an empty hand). The server looks for the water itself; at most
	 * two sips a second.
	 */
	public static void sip(ServerPlayer player) {
		RadiationConfig.Thirst cfg = RadiationConfig.get().thirst;
		if (!cfg.enabled || !player.isAlive() || player.isSpectator() || !player.getMainHandItem().isEmpty()) {
			return;
		}
		State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
		long now = player.level().getGameTime();
		if (now - state.lastSip < 8) {
			return;
		}
		if (get(player) >= MAX) {
			return;
		}
		BlockPos water = WaterSources.lookingAtWater(player, net.minecraft.world.level.ClipContext.Fluid.ANY);
		if (water == null) {
			return;
		}
		state.lastSip = now;
		ServerLevel level = player.level();
		float rads = FoodContamination.fine(waterRads(level, water) * cfg.sipPoints / cfg.servingPoints);
		drink(player, cfg.sipPoints, rads);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.5f,
				level.getRandom().nextFloat() * 0.1f + 0.9f);
		Vec3 at = Vec3.atCenterOf(water);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, 6, 0.2, 0.05, 0.2, 0.05);
	}
}
