package dev.radiation.world;

import dev.radiation.RadiationMod;
import dev.radiation.config.RadiationConfig;
import dev.radiation.network.RadiationSettingsPayload;
import dev.radiation.network.RadiationStatusPayload;
import dev.radiation.registry.ModRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Server-side heart of the mod: computes exposure, accumulates rads and applies sickness. */
public final class RadiationTracker {
	private static final Identifier SICKNESS_MODIFIER = RadiationMod.id("radiation_sickness");
	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final int VISUALIZE_RANGE = 64;

	private static @Nullable RadiationSources sources;
	private static final Map<UUID, PlayerState> STATES = new HashMap<>();
	private static final Map<UUID, Long> VISUALIZERS = new HashMap<>();
	private static long ticks;

	private RadiationTracker() {
	}

	private static final class PlayerState {
		int stage = -1;
	}

	// ------------------------------------------------------------------ lifecycle

	public static void onServerStarted(MinecraftServer server) {
		sources = RadiationSources.load(server);
		STATES.clear();
		VISUALIZERS.clear();
		RadiationMod.LOGGER.info("Loaded {} radiation zones, {} sources and {} barrels",
				sources.zones.size(), sources.sources.size(), sources.barrels.size());
	}

	public static void onServerStopping(MinecraftServer server) {
		if (sources != null) {
			sources.save();
		}
		sources = null;
		STATES.clear();
		VISUALIZERS.clear();
	}

	public static void saveSources() {
		if (sources != null) {
			sources.saveIfDirty();
		}
	}

	/** Re-reads radiation_sources.json, e.g. after it was edited by hand. */
	public static void reloadSources(MinecraftServer server) {
		sources = RadiationSources.load(server);
	}

	public static RadiationSources sources() {
		if (sources == null) {
			throw new IllegalStateException("Radiation sources accessed while no server is running");
		}
		return sources;
	}

	public static void onPlayerJoin(ServerPlayer player) {
		STATES.remove(player.getUUID());
		ServerPlayNetworking.send(player, RadiationSettingsPayload.fromConfig(RadiationConfig.get()));
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		STATES.remove(player.getUUID());
	}

	public static void onPlayerLeave(ServerPlayer player) {
		STATES.remove(player.getUUID());
		VISUALIZERS.remove(player.getUUID());
	}

	/** Resends the settings to everyone, e.g. after /radiation reload. */
	public static void broadcastSettings(MinecraftServer server) {
		RadiationSettingsPayload payload = RadiationSettingsPayload.fromConfig(RadiationConfig.get());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, payload);
			STATES.remove(player.getUUID());
		}
	}

	public static void tick(MinecraftServer server) {
		if (sources == null) {
			return;
		}
		ticks++;
		RadiationConfig config = RadiationConfig.get();
		if (ticks % config.updateIntervalTicks == 0) {
			float seconds = config.updateIntervalTicks / 20f;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				updatePlayer(player, seconds, config);
			}
		}
		if (ticks % 10 == 0 && !VISUALIZERS.isEmpty()) {
			visualize(server);
		}
		if (ticks % 100 == 0) {
			validateBarrels(server);
			validateEmitters(server);
			sources.saveIfDirty();
		}
	}

	// ------------------------------------------------------------------ per-player update

	private static void updatePlayer(ServerPlayer player, float seconds, RadiationConfig config) {
		PlayerState state = STATES.computeIfAbsent(player.getUUID(), id -> new PlayerState());
		boolean immune = !config.affectCreativeAndSpectator && (player.isCreative() || player.isSpectator());

		float protection = protection(player, config);
		float exposure = player.isAlive() ? exposureAt(player.level(), bodyCenter(player), null) * (1 - protection) : 0;

		float rads = getRads(player);
		float gain = immune ? 0 : exposure * seconds;
		float decay = config.naturalDecayPerSecond * seconds;
		float newRads = Mth.clamp(rads + gain - decay, 0, config.maxRads);
		setRads(player, newRads);

		float netRate = (immune ? 0 : exposure) - config.naturalDecayPerSecond;
		MobEffectInstance radAway = player.getEffect(ModRegistry.RADAWAY);
		if (radAway != null) {
			netRate -= config.radAwayTotalRads / config.radAwayDurationSeconds * (radAway.getAmplifier() + 1);
		}

		updateStage(player, state, newRads, config);

		if (newRads >= config.maxRads && !immune && player.isAlive()) {
			ServerLevel level = player.level();
			player.hurtServer(level, level.damageSources().source(ModRegistry.RADIATION_DAMAGE), Float.MAX_VALUE);
		}

		boolean canMeasure = !config.requireGeigerCounter || hasGeigerCounter(player);
		ServerPlayNetworking.send(player, new RadiationStatusPayload(newRads, canMeasure ? exposure : -1f, netRate, protection));
	}

	private static void updateStage(ServerPlayer player, PlayerState state, float rads, RadiationConfig config) {
		int stage = player.isAlive() ? config.stageIndexFor(rads) : -1;

		if (stage != state.stage) {
			int previous = state.stage;
			state.stage = stage;
			applyMaxHealthModifier(player, stage >= 0 ? config.stages.get(stage).maxHealthModifier : 0);
			if (stage > previous) {
				RadiationConfig.Stage info = config.stages.get(stage);
				player.sendOverlayMessage(Component.translatable("message.radiation.stage_worse", info.name).withColor(info.argb() & 0xFFFFFF));
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModRegistry.RAD_WARNING, SoundSource.PLAYERS, 0.8f, 1f);
			} else if (stage >= 0) {
				RadiationConfig.Stage info = config.stages.get(stage);
				player.sendOverlayMessage(Component.translatable("message.radiation.stage_better", info.name).withStyle(ChatFormatting.GREEN));
			} else if (previous >= 0 && player.isAlive()) {
				player.sendOverlayMessage(Component.translatable("message.radiation.cured").withStyle(ChatFormatting.GREEN));
			}
		}

		if (stage >= 0) {
			int duration = config.updateIntervalTicks + 60;
			player.addEffect(new MobEffectInstance(ModRegistry.RADIATION_SICKNESS, duration, stage, true, false, true));
			for (RadiationConfig.EffectEntry entry : config.stages.get(stage).effects) {
				Holder<MobEffect> effect = lookupEffect(entry.effect);
				if (effect != null) {
					player.addEffect(new MobEffectInstance(effect, duration, Math.max(0, entry.amplifier), true, false, true));
				}
			}
		}
	}

	private static void applyMaxHealthModifier(ServerPlayer player, double amount) {
		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null) {
			return;
		}
		if (amount == 0) {
			maxHealth.removeModifier(SICKNESS_MODIFIER);
		} else {
			maxHealth.addOrUpdateTransientModifier(new AttributeModifier(SICKNESS_MODIFIER, amount, AttributeModifier.Operation.ADD_VALUE));
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
		}
	}

	private static final Map<String, Optional<Holder<MobEffect>>> EFFECT_CACHE = new HashMap<>();

	private static @Nullable Holder<MobEffect> lookupEffect(String id) {
		return EFFECT_CACHE.computeIfAbsent(id, key -> {
			Identifier identifier = Identifier.tryParse(key);
			Optional<Holder<MobEffect>> holder = identifier == null ? Optional.empty()
					: BuiltInRegistries.MOB_EFFECT.get(identifier).map(h -> h);
			if (holder.isEmpty()) {
				RadiationMod.LOGGER.warn("Unknown effect '{}' in radiation config", key);
			}
			return holder;
		}).orElse(null);
	}

	public static void clearCaches() {
		EFFECT_CACHE.clear();
	}

	// ------------------------------------------------------------------ rads

	public static float getRads(ServerPlayer player) {
		Float rads = player.getAttached(ModRegistry.RADS);
		return rads == null ? 0 : rads;
	}

	public static void setRads(ServerPlayer player, float rads) {
		player.setAttached(ModRegistry.RADS, Mth.clamp(rads, 0, RadiationConfig.get().maxRads));
	}

	public static void addRads(ServerPlayer player, float delta) {
		setRads(player, getRads(player) + delta);
	}

	// ------------------------------------------------------------------ exposure

	public static Vec3 bodyCenter(ServerPlayer player) {
		return player.position().add(0, player.getBbHeight() * 0.5, 0);
	}

	public static String dimensionId(Level level) {
		return level.dimension().identifier().toString();
	}

	/**
	 * Raw radiation (before personal protection) at a position.
	 *
	 * @param breakdown if not null, receives a human-readable line per contributing zone/source
	 */
	public static float exposureAt(ServerLevel level, Vec3 pos, @Nullable List<Component> breakdown) {
		if (sources == null) {
			return 0;
		}
		RadiationConfig config = RadiationConfig.get();
		String dimension = dimensionId(level);
		float total = 0;

		for (RadiationSources.Zone zone : sources.zones) {
			if (zone.dimension.equals(dimension) && zone.contains(pos.x, pos.y, pos.z)) {
				float value = zone.radsAt(pos.x, pos.y, pos.z);
				total += value;
				if (breakdown != null) {
					breakdown.add(Component.literal(String.format(Locale.ROOT, "  zone %s: %.2f rad/s", zone.name, value)));
				}
			}
		}

		for (RadiationSources.PointSource source : sources.sources) {
			if (!source.dimension.equals(dimension)) {
				continue;
			}
			Vec3 center = new Vec3(source.x, source.y, source.z);
			float value = pointExposure(level, center, null, pos, source.rads, source.radius, source.falloff, source.shielded, config);
			if (value > 0) {
				total += value;
				if (breakdown != null) {
					breakdown.add(Component.literal(String.format(Locale.ROOT, "  source %s: %.2f rad/s", source.name, value)));
				}
			}
		}

		for (RadiationSources.Emitter e : sources.emitters) {
			if (!e.dimension.equals(dimension) || e.rads <= 0) {
				continue;
			}
			double dx = e.x + 0.5 - pos.x, dy = e.y + 0.5 - pos.y, dz = e.z + 0.5 - pos.z;
			double d2 = dx * dx + dy * dy + dz * dz;
			if (d2 >= (double) e.radius * e.radius) {
				continue;
			}
			BlockPos blockPos = new BlockPos(e.x, e.y, e.z);
			double t2 = d2 / ((double) e.radius * e.radius);
			double value = e.rads / Math.max(0.25, d2) * (1 - t2);
			value *= transmission(level, Vec3.atCenterOf(blockPos), pos, blockPos, config);
			if (value > 0.0001) {
				total += (float) value;
				if (breakdown != null) {
					breakdown.add(Component.literal(String.format(Locale.ROOT, "  %s at %d %d %d: %.2f rad/s", e.block, e.x, e.y, e.z, value)));
				}
			}
		}

		if (config.barrelRads > 0 && config.barrelRadius > 0) {
			for (RadiationSources.Barrel barrel : sources.barrels) {
				if (!barrel.dimension.equals(dimension)) {
					continue;
				}
				BlockPos blockPos = new BlockPos(barrel.x, barrel.y, barrel.z);
				float value = pointExposure(level, Vec3.atCenterOf(blockPos), blockPos, pos, config.barrelRads, config.barrelRadius,
						RadiationSources.Falloff.LINEAR, true, config);
				if (value > 0) {
					total += value;
					if (breakdown != null) {
						breakdown.add(Component.literal(String.format(Locale.ROOT, "  waste barrel at %d %d %d: %.2f rad/s", barrel.x, barrel.y, barrel.z, value)));
					}
				}
			}
		}
		return total;
	}

	private static float pointExposure(ServerLevel level, Vec3 center, @Nullable BlockPos ownBlock, Vec3 target, float rads, float radius,
			RadiationSources.Falloff falloff, boolean shielded, RadiationConfig config) {
		double distanceSqr = center.distanceToSqr(target);
		if (distanceSqr >= radius * radius) {
			return 0;
		}
		double value = rads * falloff.apply(Math.sqrt(distanceSqr), radius);
		if (shielded && value > 0) {
			value *= transmission(level, center, target, ownBlock, config);
		}
		return (float) value;
	}

	/** Fraction of a block's radiation that is absorbed by it as a shield; 0 for air and things that do not shield. */
	public static float absorption(BlockState state, RadiationConfig config) {
		if (state.is(SHIELDING_HEAVY)) {
			return config.heavyShielding;
		}
		if (state.is(SHIELDING_CONCRETE)) {
			return config.concreteShielding;
		}
		if (state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
			return state.isSolidRender() ? Math.max(config.waterShielding, config.shieldingPerBlock) : config.waterShielding;
		}
		return state.isSolidRender() ? config.shieldingPerBlock : 0;
	}

	public static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> SHIELDING_HEAVY =
			net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, RadiationMod.id("shielding_heavy"));
	public static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> SHIELDING_CONCRETE =
			net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, RadiationMod.id("shielding_concrete"));

	/** Fraction of radiation that gets through every block on the line between two points (each block counted once, 48 at most). */
	public static double transmission(ServerLevel level, Vec3 from, Vec3 to, @Nullable BlockPos ignore, RadiationConfig config) {
		Vec3 delta = to.subtract(from);
		int steps = (int) Math.ceil(delta.length() * 4);
		BlockPos targetBlock = BlockPos.containing(to);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		long last = Long.MIN_VALUE;
		double through = 1;
		int count = 0;
		for (int i = 1; i < steps; i++) {
			double t = (double) i / steps;
			cursor.set(from.x + delta.x * t, from.y + delta.y * t, from.z + delta.z * t);
			long packed = cursor.asLong();
			if (packed == last || cursor.equals(ignore) || cursor.equals(targetBlock)) {
				continue;
			}
			last = packed;
			if (!level.isLoaded(cursor)) {
				continue;
			}
			float a = absorption(level.getBlockState(cursor), config);
			if (a > 0) {
				through *= 1 - a;
				if (++count >= 48 || through < 1e-6) {
					break;
				}
			}
		}
		return through;
	}

	/** Counts distinct full solid blocks on the line between two points (capped at 32). */
	private static int solidBlocksBetween(ServerLevel level, Vec3 from, Vec3 to, @Nullable BlockPos ignore) {
		Vec3 delta = to.subtract(from);
		int steps = (int) Math.ceil(delta.length() * 4);
		BlockPos targetBlock = BlockPos.containing(to);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		long last = Long.MIN_VALUE;
		int count = 0;
		for (int i = 1; i < steps; i++) {
			double t = (double) i / steps;
			cursor.set(from.x + delta.x * t, from.y + delta.y * t, from.z + delta.z * t);
			long packed = cursor.asLong();
			if (packed == last || cursor.equals(ignore) || cursor.equals(targetBlock)) {
				continue;
			}
			last = packed;
			if (level.isLoaded(cursor) && level.getBlockState(cursor).isSolidRender()) {
				if (++count >= 32) {
					break;
				}
			}
		}
		return count;
	}

	// ------------------------------------------------------------------ protection

	public static float protection(ServerPlayer player, RadiationConfig config) {
		float armor = 0;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack stack = player.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				Float value = config.protectiveItems.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
				if (value != null) {
					armor += value;
				}
			}
		}
		armor = Mth.clamp(armor, 0, 1);

		float radX = 0;
		MobEffectInstance resistance = player.getEffect(ModRegistry.RAD_RESISTANCE);
		if (resistance != null) {
			radX = (float) (1 - Math.pow(1 - config.radXResistance, resistance.getAmplifier() + 1));
		}
		float total = 1 - (1 - armor) * (1 - radX);
		return Mth.clamp(total, 0, config.maxProtection);
	}

	public static boolean hasGeigerCounter(ServerPlayer player) {
		return player.getInventory().contains(stack -> stack.is(ModRegistry.GEIGER_COUNTER));
	}

	// ------------------------------------------------------------------ barrels

	public static void addBarrel(ServerLevel level, BlockPos pos) {
		if (sources == null) {
			return;
		}
		String dimension = dimensionId(level);
		for (RadiationSources.Barrel barrel : sources.barrels) {
			if (barrel.is(dimension, pos.getX(), pos.getY(), pos.getZ())) {
				return;
			}
		}
		sources.barrels.add(new RadiationSources.Barrel(dimension, pos.getX(), pos.getY(), pos.getZ()));
		sources.markDirty();
	}

	/** Forgets emitters whose block was replaced by something else (only checked where the chunk is loaded). */
	private static void validateEmitters(MinecraftServer server) {
		List<String> gone = new ArrayList<>();
		for (RadiationSources.Emitter e : sources.emitters) {
			Identifier dimensionId = Identifier.tryParse(e.dimension);
			ServerLevel level = dimensionId == null ? null
					: server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionId));
			if (level == null) {
				continue;
			}
			BlockPos p = new BlockPos(e.x, e.y, e.z);
			if (!level.isLoaded(p)) {
				continue;
			}
			String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString();
			if (!id.equals(e.block)) {
				gone.add(e.key());
			}
		}
		for (String key : gone) {
			sources.removeEmitter(key);
		}
	}

	/** Forgets barrels whose block is gone (broken, exploded, replaced by commands...). */
	private static void validateBarrels(MinecraftServer server) {
		Iterator<RadiationSources.Barrel> iterator = sources.barrels.iterator();
		while (iterator.hasNext()) {
			RadiationSources.Barrel barrel = iterator.next();
			Identifier dimensionId = Identifier.tryParse(barrel.dimension);
			ServerLevel level = dimensionId == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
			if (level == null) {
				continue;
			}
			BlockPos pos = new BlockPos(barrel.x, barrel.y, barrel.z);
			if (level.isLoaded(pos) && !level.getBlockState(pos).is(ModRegistry.NUCLEAR_WASTE_BARREL)) {
				iterator.remove();
				sources.markDirty();
			}
		}
	}

	// ------------------------------------------------------------------ visualisation (/radiation show)

	public static void setVisualizing(ServerPlayer player, int seconds) {
		if (seconds <= 0) {
			VISUALIZERS.remove(player.getUUID());
		} else {
			VISUALIZERS.put(player.getUUID(), ticks + seconds * 20L);
		}
	}

	private static void visualize(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Long>> iterator = VISUALIZERS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Long> entry = iterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || entry.getValue() < ticks) {
				iterator.remove();
				continue;
			}
			ServerLevel level = player.level();
			String dimension = dimensionId(level);
			Vec3 eye = player.position();
			DustParticleOptions zoneDust = new DustParticleOptions(0x40FF40, 1.5f);
			DustParticleOptions sourceDust = new DustParticleOptions(0xFF3030, 1.5f);
			DustParticleOptions barrelDust = new DustParticleOptions(0xFFD020, 1.2f);

			for (RadiationSources.Zone zone : sources.zones) {
				if (!zone.dimension.equals(dimension)) {
					continue;
				}
				double x0 = zone.minX, y0 = zone.minY, z0 = zone.minZ, x1 = zone.maxX + 1, y1 = zone.maxY + 1, z1 = zone.maxZ + 1;
				double[][] edges = {
						{x0, y0, z0, x1, y0, z0}, {x0, y1, z0, x1, y1, z0}, {x0, y0, z1, x1, y0, z1}, {x0, y1, z1, x1, y1, z1},
						{x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x0, y0, z1, x0, y1, z1}, {x1, y0, z1, x1, y1, z1},
						{x0, y0, z0, x0, y0, z1}, {x1, y0, z0, x1, y0, z1}, {x0, y1, z0, x0, y1, z1}, {x1, y1, z0, x1, y1, z1}};
				for (double[] e : edges) {
					line(level, player, zoneDust, eye, e[0], e[1], e[2], e[3], e[4], e[5]);
				}
			}
			for (RadiationSources.PointSource source : sources.sources) {
				if (source.dimension.equals(dimension)) {
					ring(level, player, sourceDust, eye, source.x, source.y, source.z, source.radius);
					line(level, player, sourceDust, eye, source.x, source.y - 1, source.z, source.x, source.y + 2, source.z);
				}
			}
			RadiationConfig config = RadiationConfig.get();
			for (RadiationSources.Barrel barrel : sources.barrels) {
				if (barrel.dimension.equals(dimension)) {
					ring(level, player, barrelDust, eye, barrel.x + 0.5, barrel.y + 0.5, barrel.z + 0.5, config.barrelRadius);
				}
			}
		}
	}

	private static void line(ServerLevel level, ServerPlayer player, DustParticleOptions dust, Vec3 eye,
			double x0, double y0, double z0, double x1, double y1, double z1) {
		double length = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
		int points = (int) Math.min(256, Math.ceil(length));
		for (int i = 0; i <= points; i++) {
			double t = points == 0 ? 0 : (double) i / points;
			particle(level, player, dust, eye, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z0 + (z1 - z0) * t);
		}
	}

	private static void ring(ServerLevel level, ServerPlayer player, DustParticleOptions dust, Vec3 eye, double x, double y, double z, double radius) {
		int points = (int) Math.min(256, Math.max(16, radius * Math.PI * 2));
		for (int i = 0; i < points; i++) {
			double angle = Math.PI * 2 * i / points;
			particle(level, player, dust, eye, x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius);
		}
	}

	private static void particle(ServerLevel level, ServerPlayer player, DustParticleOptions dust, Vec3 eye, double x, double y, double z) {
		if (eye.distanceToSqr(x, y, z) < VISUALIZE_RANGE * VISUALIZE_RANGE) {
			level.sendParticles(player, dust, true, true, x, y, z, 1, 0, 0, 0, 0);
		}
	}
}
