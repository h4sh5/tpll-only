/*
 * tpll-only &mdash; standalone /tpll and /tpllcalc for modern Fabric.
 */
package dev.tpllonly.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.tpllonly.config.TpllConfig;
import dev.tpllonly.geo.GeoFormatter;
import dev.tpllonly.geo.LatLng;
import dev.tpllonly.projection.GeographicProjection;
import dev.tpllonly.projection.OutOfProjectionBoundsException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * Registers <code>/tpll</code> and <code>/tpllcalc</code>.
 *
 * <p><code>/tpll</code> teleports by latitude and longitude, exactly like TerraPlusPlus:
 * <pre>
 *   /tpll 48.858370 2.294481          teleport yourself
 *   /tpll 48°51'N 2°21'E 300          DMS input, at altitude 300
 *   /tpll Steve @e 48.858370 2.294    teleport other players
 * </pre>
 *
 * <p><code>/tpllcalc</code> does all of the same maths and shows the resulting coordinates
 * (click to copy) without moving anybody, and without loading any chunks: only the X and Z it works
 * out are meaningful. With no arguments it works in reverse and reports the latitude/longitude of
 * where you (or someone else) are standing &mdash; <code>/where</code> ({@link WhereCommand}) is
 * that reverse direction as a command of its own, with DMS and a map link in the answer.
 */
public final class TpllCommands {

	/** the largest coordinate Minecraft accepts */
	private static final double WORLD_LIMIT = 2.9999999E7;

	private TpllCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(build("tpll", false));
		dispatcher.register(build("tpllcalc", true));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> build(String name, boolean calc) {
		return Commands.literal(name)
				.executes(ctx -> run(ctx.getSource(), "", calc))
				.then(Commands.argument("coordinates", StringArgumentType.greedyString())
						.executes(ctx -> run(ctx.getSource(), StringArgumentType.getString(ctx, "coordinates"), calc)));
	}

	// ------------------------------------------------------------------------

	private static int run(CommandSourceStack source, String raw, boolean calc) {
		CoordinateInput input = CoordinateInput.parse(source, raw, calc);

		if (input == null) {
			if (calc) {
				return reverseLookup(source, raw);
			}
			sendUsage(source);
			return 0;
		}

		if (!mayTarget(input, source)) {
			source.sendSystemMessage(Component.literal("You need permission to teleport other players.")
					.withStyle(ChatFormatting.RED));
			return 0;
		}

		if (input.altitude() == null && TpllConfig.get().elevation.requiresAltitude()) {
			source.sendSystemMessage(Component
					.literal("This server requires an altitude: /tpll <latitude> <longitude> <altitude>")
					.withStyle(ChatFormatting.RED));
			return 0;
		}

		GeographicProjection projection = TpllConfig.projection();
		LatLng coordinates = input.coordinates();

		double[] position;
		try {
			position = projection.fromGeo(coordinates.getLongitude(), coordinates.getLatitude());
		} catch (OutOfProjectionBoundsException e) {
			source.sendSystemMessage(Component.literal("Those coordinates can't be mapped onto this world.")
					.withStyle(ChatFormatting.RED));
			return 0;
		}

		double x = position[0];
		double z = position[1];

		if (Math.abs(x) > WORLD_LIMIT || Math.abs(z) > WORLD_LIMIT) {
			source.sendSystemMessage(Component.literal(
							String.format(Locale.US, "That lands at X %.0f, Z %.0f, outside the world's limits.", x, z))
					.withStyle(ChatFormatting.RED));
			return 0;
		}

		if (input.targets().isEmpty()) {
			// only /tpllcalc can do anything when nobody is around to be teleported: it just
			// reports where the coordinates land in the overworld
			String consoleBounds = TpllConfig.checkBounds(projection, x, z);
			if (consoleBounds != null) {
				source.sendSystemMessage(Component.literal(consoleBounds));
				return 0;
			}

			sendCalcResult(source, null, coordinates, x, previewAltitude(input.altitude(), null), z, null);
			return 1;
		}

		int count = 0;
		for (ServerPlayer target : input.targets()) {
			ServerLevel level = target.level();
			String outOfBounds = TpllConfig.checkBounds(projection, x, z);
			if (outOfBounds != null) {
				source.sendSystemMessage(Component.literal(outOfBounds));
				return 0;
			}

			if (calc) {
				// A preview only needs to say where on the map something lands, so unlike the real
				// teleport it doesn't go near the heightmaps
				sendCalcResult(source, target, coordinates, x, previewAltitude(input.altitude(), target), z, null);
			} else {
				resolveAltitude(level, x, z, input.altitude(),
						(y, note) -> teleport(source, target, coordinates, x, y, z, note));
			}
			count++;
		}

		return count;
	}

	private static void teleport(CommandSourceStack source, ServerPlayer target, LatLng coordinates,
	                             double x, double y, double z, String note) {
		if (target.isRemoved()) {
			return;
		}

		ServerLevel level = target.level();
		target.teleport(new TeleportTransition(level, new Vec3(x, y, z), Vec3.ZERO,
				target.getYRot(), target.getXRot(), TeleportTransition.DO_NOTHING));

		MutableComponent message = Component.literal("Teleported ").withStyle(ChatFormatting.YELLOW)
				.append(target.getDisplayName().copy().withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" to ").withStyle(ChatFormatting.YELLOW))
				.append(Chat.copy(formatGeo(coordinates), formatGeo(coordinates) + " " + formatAltitude(y))
						.withStyle(ChatFormatting.AQUA));

		source.sendSystemMessage(message);

		if (note != null) {
			source.sendSystemMessage(Component.literal(note).withStyle(ChatFormatting.GRAY));
		}

		if (TpllConfig.get().teleport.announce_target && source.getEntity() != target) {
			target.sendSystemMessage(Component.literal("You were teleported to ")
					.withStyle(ChatFormatting.YELLOW)
					.append(Chat.copy(formatGeo(coordinates), formatGeo(coordinates))
							.withStyle(ChatFormatting.AQUA))
					.append(Component.literal(".").withStyle(ChatFormatting.YELLOW)));
		}
	}

	/**
	 * <code>/tpllcalc</code> with no coordinates: report where a player currently is.
	 */
	private static int reverseLookup(CommandSourceStack source, String raw) {
		ServerPlayer target;
		String token = raw.trim();

		if (token.isEmpty()) {
			target = source.getPlayer();
			if (target == null) {
				source.sendSystemMessage(Component.literal("Nothing to look up: /tpllcalc <latitude> <longitude> [altitude]")
						.withStyle(ChatFormatting.RED));
				return 0;
			}
		} else {
			List<ServerPlayer> found = CoordinateInput.parsePlayersOnly(source, token);
			if (found == null) {
				sendUsage(source);
				return 0;
			}
			for (ServerPlayer player : found) {
				printReverse(source, player);
			}
			return found.size();
		}

		printReverse(source, target);
		return 1;
	}

	private static void printReverse(CommandSourceStack source, ServerPlayer target) {
		GeographicProjection projection = TpllConfig.projection();
		Vec3 pos = target.position();

		double[] geo;
		try {
			geo = projection.toGeo(pos.x, pos.z);
		} catch (OutOfProjectionBoundsException e) {
			source.sendSystemMessage(Component.literal("This position is outside the world's map projection.")
					.withStyle(ChatFormatting.RED));
			return;
		}

		LatLng coordinates = new LatLng(geo[1], geo[0]);
		String geoText = formatGeo(coordinates);

		source.sendSystemMessage(Component.literal(target.getDisplayName().getString() + " is at ")
				.withStyle(ChatFormatting.YELLOW)
				.append(Chat.copy(geoText, geoText).withStyle(ChatFormatting.AQUA))
				.append(Component.literal(String.format(Locale.US, " (X %.0f, Z %.0f)", pos.x, pos.z))
						.withStyle(ChatFormatting.GRAY))
				.append(Component.literal("  ").append(Chat.bracketed(
						Chat.suggest(Chat.actionable("teleport here"), "/tpll " + geoText)))
						.withStyle(ChatFormatting.DARK_GRAY)));
	}

	private static void sendCalcResult(CommandSourceStack source, ServerPlayer target, LatLng coordinates,
	                                   double x, double y, double z, String note) {
		String geoText = formatGeo(coordinates);
		String xyzText = String.format(Locale.US, "%.2f %.2f %.2f", x, y, z);

		MutableComponent header = Component.literal("tpllcalc").withStyle(ChatFormatting.LIGHT_PURPLE);

		if (target != null) {
			header.append(Component.literal(" for ").withStyle(ChatFormatting.GRAY))
					.append(target.getDisplayName().copy().withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" in ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(target.level().dimension().identifier().toString())
							.withStyle(ChatFormatting.GRAY));
		} else {
			header.append(Component.literal(" (no player, showing the overworld)")
					.withStyle(ChatFormatting.GRAY));
		}

		source.sendSystemMessage(header);

		source.sendSystemMessage(Component.literal("Latitude / longitude: ").withStyle(ChatFormatting.GRAY)
				.append(Chat.copy(geoText, geoText).withStyle(ChatFormatting.AQUA))
				.append(Component.literal("  ").append(Chat.bracketed(
						Chat.copy(Chat.actionable("copy"), geoText + " " + formatAltitude(y))))
						.withStyle(ChatFormatting.DARK_GRAY)));

		source.sendSystemMessage(Component.literal("World position: ").withStyle(ChatFormatting.GRAY)
				.append(Chat.copy(xyzText, xyzText).withStyle(ChatFormatting.AQUA))
				.append(Component.literal("  (x y z; /tpll picks the surface itself)").withStyle(ChatFormatting.DARK_GRAY)));

		source.sendSystemMessage(Component.literal("  ").append(Chat.bracketed(
						Chat.run(Chat.actionable("teleport"),
								"/tpll " + (target == null ? "" : target.getScoreboardName() + " ") + geoText)))
				.withStyle(ChatFormatting.DARK_GRAY));

		if (note != null) {
			source.sendSystemMessage(Component.literal(note).withStyle(ChatFormatting.GRAY));
		}
	}

	// ------------------------------------------------------------------------

	/**
	 * The Y level a <code>/tpllcalc</code> preview shows. Finding where the terrain actually is
	 * means loading chunks, which a preview has no reason to do, so the altitude argument is echoed
	 * back if there was one and otherwise the height being stood on is reported.
	 */
	private static double previewAltitude(Double explicit, ServerPlayer target) {
		if (explicit != null) {
			return explicit;
		}

		if (target != null) {
			return target.getY();
		}

		return TpllConfig.get().elevation.default_altitude;
	}

	/**
	 * Works out the Y level to teleport to: by default the top of the highest block at that X/Z, so
	 * that you arrive standing on the terrain instead of buried in it. The height comes from the
	 * world's own heightmap, which means it is exact for the world you are actually in
	 * (TerraPlusPlus read a real-world elevation dataset instead). If the chunk isn't loaded yet it
	 * is requested and the teleport happens on a later tick, falling back to
	 * {@code elevation.default_altitude} when the terrain can't be reached.
	 */
	private static void resolveAltitude(ServerLevel level, double x, double z, Double explicit,
	                                    AltitudeCallback callback) {
		TpllConfig.ElevationConfig config = TpllConfig.get().elevation;

		if (explicit != null) {
			callback.accept(explicit, null);
			return;
		}

		if (!config.usesSurface()) {
			callback.accept(config.default_altitude, null);
			return;
		}

		int blockX = (int) Math.floor(x);
		int blockZ = (int) Math.floor(z);
		int chunkX = blockX >> 4;
		int chunkZ = blockZ >> 4;

		if (level.hasChunk(chunkX, chunkZ)) {
			callback.accept(surfaceHeight(level, config, blockX, blockZ), null);
			return;
		}

		if (!config.load_chunks) {
			callback.accept(config.default_altitude,
					"That chunk isn't loaded, so the configured default altitude was used.");
			return;
		}

		MinecraftServer server = level.getServer();
		level.getChunkSource().getChunkFuture(chunkX, chunkZ, ChunkStatus.FULL, true)
				.thenAccept(result -> server.executeIfPossible(() -> {
					if (result != null && result.isSuccess()) {
						callback.accept(surfaceHeight(level, config, blockX, blockZ),
								"The chunk had to be generated to work out the elevation.");
					} else {
						callback.accept(config.default_altitude,
								"That chunk couldn't be loaded, so the configured default altitude was used.");
					}
				}))
				.exceptionally(error -> {
					server.executeIfPossible(() -> callback.accept(config.default_altitude,
							"The elevation couldn't be calculated, so the configured default altitude was used."));
					return null;
				});
	}

	private static double surfaceHeight(ServerLevel level, TpllConfig.ElevationConfig config, int blockX, int blockZ) {
		return level.getHeight(config.heightmapType(), blockX, blockZ) + config.surface_offset;
	}

	/** @return true if the source is allowed to teleport players other than itself */
	private static boolean mayTarget(CoordinateInput input, CommandSourceStack source) {
		ServerPlayer self = source.getPlayer();
		boolean others = false;
		for (ServerPlayer target : input.targets()) {
			if (self == null || !target.getUUID().equals(self.getUUID())) {
				others = true;
				break;
			}
		}

		if (!others) {
			return true;
		}

		var check = TpllConfig.get().teleport.targetPermissionCheck();
		return check == null || check.check(source.permissions());
	}

	private static void sendUsage(CommandSourceStack source) {
		source.sendSystemMessage(Component.literal("Usage:").withStyle(ChatFormatting.YELLOW));
		source.sendSystemMessage(Component.literal("  /tpll <latitude> <longitude> [altitude]")
				.withStyle(ChatFormatting.GRAY)
				.append(Component.literal("  -  teleport yourself").withStyle(ChatFormatting.DARK_GRAY)));
		source.sendSystemMessage(Component.literal("  /tpll <player> <latitude> <longitude> [altitude]")
				.withStyle(ChatFormatting.GRAY)
				.append(Component.literal("  -  teleport someone else").withStyle(ChatFormatting.DARK_GRAY)));
		source.sendSystemMessage(Component.literal("  /tpllcalc ...")
				.withStyle(ChatFormatting.GRAY)
				.append(Component.literal("  -  same thing, but only shows the coordinates")
						.withStyle(ChatFormatting.DARK_GRAY)));
		source.sendSystemMessage(Component.literal("  /tpllcalc [player]")
				.withStyle(ChatFormatting.GRAY)
				.append(Component.literal("  -  show the coordinates of where you are standing")
						.withStyle(ChatFormatting.DARK_GRAY)));
		source.sendSystemMessage(Component.literal("  /where [player | x z]")
				.withStyle(ChatFormatting.GRAY)
				.append(Component.literal("  -  the same reverse lookup, with a map link and DMS as well")
						.withStyle(ChatFormatting.DARK_GRAY)));
		source.sendSystemMessage(Component.literal("Coordinates can be decimal (48.858370 2.294481), "
						+ "DMS (48°51'30\"N 2°21'42\"E) or use hemisphere letters (N 48.858 E 2.294).")
				.withStyle(ChatFormatting.DARK_GRAY)
				.withStyle(style -> style.withClickEvent(
						new net.minecraft.network.chat.ClickEvent.SuggestCommand("/tpll "))));
	}

	@FunctionalInterface
	private interface AltitudeCallback {
		/**
		 * @param y    the Y level to teleport to
		 * @param note an explanation to show alongside the result, or null
		 */
		void accept(double y, String note);
	}

	// ------------------------------------------------------------------------

	private static String formatGeo(LatLng coordinates) {
		return GeoFormatter.decimal(coordinates);
	}

	private static String formatAltitude(double y) {
		return String.format(Locale.US, "%.1f", y);
	}
}
