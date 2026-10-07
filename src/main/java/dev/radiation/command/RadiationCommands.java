package dev.radiation.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.radiation.config.RadiationConfig;
import dev.radiation.world.RadiationSources;
import dev.radiation.world.RadiationTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class RadiationCommands {
	private RadiationCommands() {
	}

	private static final SuggestionProvider<CommandSourceStack> ZONE_NAMES = (ctx, builder) -> {
		RadiationTracker.sources().zones.forEach(z -> builder.suggest(z.name));
		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSourceStack> SOURCE_NAMES = (ctx, builder) -> {
		RadiationTracker.sources().sources.forEach(s -> builder.suggest(s.name));
		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSourceStack> FALLOFFS = (ctx, builder) -> {
		for (RadiationSources.Falloff falloff : RadiationSources.Falloff.values()) {
			builder.suggest(falloff.name().toLowerCase(Locale.ROOT));
		}
		return builder.buildFuture();
	};

	private static int wind(CommandContext<CommandSourceStack> ctx) {
		String text = dev.radiation.world.Wind.describe(ctx.getSource().getLevel());
		ctx.getSource().sendSuccess(() -> Component.literal(Character.toUpperCase(text.charAt(0)) + text.substring(1)), false);
		return 1;
	}

	private static int releaseCloud(CommandContext<CommandSourceStack> ctx, float radius, float altitude) throws CommandSyntaxException {
		Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
		float rads = FloatArgumentType.getFloat(ctx, "rads");
		dev.radiation.world.Clouds.release(ctx.getSource().getLevel(), pos, rads, radius, altitude);
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Radioactive cloud released: %.2f rad/s, %.0f blocks, %.0f up", rads, radius, altitude)), true);
		return 1;
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("rads").executes(RadiationCommands::self));

		dispatcher.register(literal("radiation")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(literal("zone")
						.then(literal("add")
								.then(argument("name", StringArgumentType.word())
										.then(argument("from", BlockPosArgument.blockPos())
												.then(argument("to", BlockPosArgument.blockPos())
														.then(argument("rads", FloatArgumentType.floatArg(0))
																.executes(ctx -> addZone(ctx, 0))
																.then(argument("fade", FloatArgumentType.floatArg(0))
																		.executes(ctx -> addZone(ctx, FloatArgumentType.getFloat(ctx, "fade")))))))))
						.then(literal("remove")
								.then(argument("name", StringArgumentType.word()).suggests(ZONE_NAMES)
										.executes(RadiationCommands::removeZone)))
						.then(literal("list").executes(ctx -> list(ctx, true, false))))
				.then(literal("source")
						.then(literal("add")
								.then(argument("name", StringArgumentType.word())
										.then(argument("pos", Vec3Argument.vec3())
												.then(argument("rads", FloatArgumentType.floatArg(0))
														.then(argument("radius", FloatArgumentType.floatArg(0.5f, 1024f))
																.executes(ctx -> addSource(ctx, "linear", true))
																.then(argument("falloff", StringArgumentType.word()).suggests(FALLOFFS)
																		.executes(ctx -> addSource(ctx, StringArgumentType.getString(ctx, "falloff"), true))
																		.then(argument("shielded", BoolArgumentType.bool())
																				.executes(ctx -> addSource(ctx, StringArgumentType.getString(ctx, "falloff"),
																						BoolArgumentType.getBool(ctx, "shielded"))))))))))
						.then(literal("remove")
								.then(argument("name", StringArgumentType.word()).suggests(SOURCE_NAMES)
										.executes(RadiationCommands::removeSource)))
						.then(literal("list").executes(ctx -> list(ctx, false, true))))
				.then(literal("list").executes(ctx -> list(ctx, true, true)))
				.then(literal("check")
						.executes(ctx -> check(ctx, ctx.getSource().getPosition()))
						.then(argument("pos", Vec3Argument.vec3())
								.executes(ctx -> check(ctx, Vec3Argument.getVec3(ctx, "pos")))))
				.then(literal("show")
						.executes(ctx -> show(ctx, 60))
						.then(argument("seconds", IntegerArgumentType.integer(0, 3600))
								.executes(ctx -> show(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))))
				.then(literal("get")
						.then(argument("player", EntityArgument.player())
								.executes(RadiationCommands::get)))
				.then(literal("set")
						.then(argument("targets", EntityArgument.players())
								.then(argument("amount", FloatArgumentType.floatArg(0))
										.executes(ctx -> modify(ctx, FloatArgumentType.getFloat(ctx, "amount"), false)))))
				.then(literal("add")
						.then(argument("targets", EntityArgument.players())
								.then(argument("amount", FloatArgumentType.floatArg())
										.executes(ctx -> modify(ctx, FloatArgumentType.getFloat(ctx, "amount"), true)))))
				.then(literal("clear")
						.executes(ctx -> clear(ctx, List.of(ctx.getSource().getPlayerOrException())))
						.then(argument("targets", EntityArgument.players())
								.executes(ctx -> clear(ctx, EntityArgument.getPlayers(ctx, "targets")))))
				.then(literal("wind")
						.executes(RadiationCommands::wind)
						.then(literal("set").then(argument("towards", FloatArgumentType.floatArg(0, 360))
								.then(argument("speed", FloatArgumentType.floatArg(0, 40)).executes(ctx -> {
									dev.radiation.world.Wind.fix(FloatArgumentType.getFloat(ctx, "towards"), FloatArgumentType.getFloat(ctx, "speed"));
									dev.radiation.world.Clouds.save();
									return wind(ctx);
								}))))
						.then(literal("natural").executes(ctx -> {
							dev.radiation.world.Wind.release();
							dev.radiation.world.Clouds.save();
							return wind(ctx);
						})))
				.then(literal("clouds").executes(ctx -> {
					List<String> lines = dev.radiation.world.Clouds.describe();
					if (lines.isEmpty()) ctx.getSource().sendSuccess(() -> Component.literal("No radioactive clouds."), false);
					for (String line : lines) ctx.getSource().sendSuccess(() -> Component.literal(line), false);
					return lines.size();
				}))
				.then(literal("cloud").then(argument("pos", Vec3Argument.vec3()).then(argument("rads", FloatArgumentType.floatArg(0.001F, 1000))
						.executes(ctx -> releaseCloud(ctx, 12, 110))
						.then(argument("radius", FloatArgumentType.floatArg(2, 200))
								.executes(ctx -> releaseCloud(ctx, FloatArgumentType.getFloat(ctx, "radius"), 40))
								.then(argument("altitude", FloatArgumentType.floatArg(5, 300))
										.executes(ctx -> releaseCloud(ctx, FloatArgumentType.getFloat(ctx, "radius"), FloatArgumentType.getFloat(ctx, "altitude"))))))))
				.then(literal("reload").executes(RadiationCommands::reload)));
		// where a cloud would go, for everyone
		dispatcher.register(literal("wind").executes(RadiationCommands::wind));
	}

	private static int self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		RadiationConfig config = RadiationConfig.get();
		float rads = RadiationTracker.getRads(player);
		int stage = config.stageIndexFor(rads);
		Component condition = stage >= 0 ? Component.literal(config.stages.get(stage).name).withColor(config.stages.get(stage).argb() & 0xFFFFFF)
				: Component.translatable("message.radiation.healthy").withStyle(ChatFormatting.GREEN);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.radiation.self",
				fmt(rads), fmt(config.maxRads), condition), false);
		return (int) rads;
	}

	private static int addZone(CommandContext<CommandSourceStack> ctx, float fade) {
		String name = StringArgumentType.getString(ctx, "name");
		RadiationSources sources = RadiationTracker.sources();
		if (sources.zone(name) != null) {
			ctx.getSource().sendFailure(Component.translatable("commands.radiation.exists", name));
			return 0;
		}
		BlockPos a = BlockPosArgument.getBlockPos(ctx, "from");
		BlockPos b = BlockPosArgument.getBlockPos(ctx, "to");
		RadiationSources.Zone zone = new RadiationSources.Zone();
		zone.name = name;
		zone.dimension = RadiationTracker.dimensionId(ctx.getSource().getLevel());
		zone.minX = Math.min(a.getX(), b.getX());
		zone.minY = Math.min(a.getY(), b.getY());
		zone.minZ = Math.min(a.getZ(), b.getZ());
		zone.maxX = Math.max(a.getX(), b.getX());
		zone.maxY = Math.max(a.getY(), b.getY());
		zone.maxZ = Math.max(a.getZ(), b.getZ());
		zone.rads = FloatArgumentType.getFloat(ctx, "rads");
		zone.fade = fade;
		sources.zones.add(zone);
		sources.save();
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.radiation.zone.added", zone.describe()), true);
		return 1;
	}

	private static int removeZone(CommandContext<CommandSourceStack> ctx) {
		String name = StringArgumentType.getString(ctx, "name");
		RadiationSources sources = RadiationTracker.sources();
		RadiationSources.Zone zone = sources.zone(name);
		if (zone == null) {
			ctx.getSource().sendFailure(Component.translatable("commands.radiation.unknown", name));
			return 0;
		}
		sources.zones.remove(zone);
		sources.save();
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.radiation.removed", zone.name), true);
		return 1;
	}

	private static int addSource(CommandContext<CommandSourceStack> ctx, String falloffName, boolean shielded) {
		String name = StringArgumentType.getString(ctx, "name");
		RadiationSources sources = RadiationTracker.sources();
		if (sources.source(name) != null) {
			ctx.getSource().sendFailure(Component.translatable("commands.radiation.exists", name));
			return 0;
		}
		RadiationSources.Falloff falloff;
		try {
			falloff = RadiationSources.Falloff.valueOf(falloffName.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			ctx.getSource().sendFailure(Component.translatable("commands.radiation.bad_falloff", falloffName));
			return 0;
		}
		Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
		RadiationSources.PointSource source = new RadiationSources.PointSource();
		source.name = name;
		source.dimension = RadiationTracker.dimensionId(ctx.getSource().getLevel());
		source.x = pos.x;
		source.y = pos.y;
		source.z = pos.z;
		source.rads = FloatArgumentType.getFloat(ctx, "rads");
		source.radius = FloatArgumentType.getFloat(ctx, "radius");
		source.falloff = falloff;
		source.shielded = shielded;
		sources.sources.add(source);
		sources.save();
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.radiation.source.added", source.describe()), true);
		return 1;
	}

	private static int removeSource(CommandContext<CommandSourceStack> ctx) {
		String name = StringArgumentType.getString(ctx, "name");
		RadiationSources sources = RadiationTracker.sources();
		RadiationSources.PointSource source = sources.source(name);
		if (source == null) {
			ctx.getSource().sendFailure(Component.translatable("commands.radiation.unknown", name));
			return 0;
		}
		sources.sources.remove(source);
		sources.save();
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.radiation.removed", source.name), true);
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx, boolean zones, boolean points) {
		RadiationSources sources = RadiationTracker.sources();
		CommandSourceStack source = ctx.getSource();
		int count = 0;
		if (zones) {
			source.sendSuccess(() -> Component.translatable("commands.radiation.list.zones", sources.zones.size()).withStyle(ChatFormatting.GOLD), false);
			for (RadiationSources.Zone zone : sources.zones) {
				source.sendSuccess(() -> Component.literal(" - " + zone.describe()), false);
			}
			count += sources.zones.size();
		}
		if (points) {
			source.sendSuccess(() -> Component.translatable("commands.radiation.list.sources", sources.sources.size()).withStyle(ChatFormatting.GOLD), false);
			for (RadiationSources.PointSource point : sources.sources) {
				source.sendSuccess(() -> Component.literal(" - " + point.describe()), false);
			}
			count += sources.sources.size();
			if (zones) {
				source.sendSuccess(() -> Component.translatable("commands.radiation.list.barrels", sources.barrels.size()).withStyle(ChatFormatting.GOLD), false);
			}
		}
		return count;
	}

	private static int check(CommandContext<CommandSourceStack> ctx, Vec3 pos) {
		List<Component> breakdown = new ArrayList<>();
		float total = RadiationTracker.exposureAt(ctx.getSource().getLevel(), pos, breakdown);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.radiation.check",
				String.format(Locale.ROOT, "%.1f %.1f %.1f", pos.x, pos.y, pos.z), String.format(Locale.ROOT, "%.2f", total)).withStyle(ChatFormatting.YELLOW), false);
		for (Component line : breakdown) {
			ctx.getSource().sendSuccess(() -> line, false);
		}
		return Math.round(total);
	}

	private static int show(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		RadiationTracker.setVisualizing(player, seconds);
		ctx.getSource().sendSuccess(() -> seconds > 0
				? Component.translatable("commands.radiation.show.on", seconds)
				: Component.translatable("commands.radiation.show.off"), false);
		return 1;
	}

	private static int get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		float rads = RadiationTracker.getRads(player);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.radiation.get", player.getDisplayName(), fmt(rads)), false);
		return (int) rads;
	}

	private static int modify(CommandContext<CommandSourceStack> ctx, float amount, boolean relative) throws CommandSyntaxException {
		Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
		for (ServerPlayer player : players) {
			if (relative) {
				RadiationTracker.addRads(player, amount);
			} else {
				RadiationTracker.setRads(player, amount);
			}
		}
		if (players.size() == 1) {
			ServerPlayer player = players.iterator().next();
			ctx.getSource().sendSuccess(() -> Component.translatable("commands.radiation.set.single",
					player.getDisplayName(), fmt(RadiationTracker.getRads(player))), true);
		} else {
			ctx.getSource().sendSuccess(() -> Component.translatable("commands.radiation.set.multiple", players.size()), true);
		}
		return players.size();
	}

	private static int clear(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
		for (ServerPlayer player : players) {
			RadiationTracker.setRads(player, 0);
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.radiation.clear", players.size()), true);
		return players.size();
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		boolean ok = RadiationConfig.load();
		RadiationTracker.clearCaches();
		RadiationTracker.reloadSources(ctx.getSource().getServer());
		RadiationTracker.broadcastSettings(ctx.getSource().getServer());
		if (ok) {
			ctx.getSource().sendSuccess(() -> Component.translatable("commands.radiation.reload"), true);
		} else {
			ctx.getSource().sendFailure(Component.translatable("commands.radiation.reload.failed"));
		}
		return ok ? 1 : 0;
	}

	private static String fmt(float value) {
		return String.format(Locale.ROOT, "%.0f", value);
	}
}
