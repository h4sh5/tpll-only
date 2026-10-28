/*
 * tpll-only &mdash; standalone /tpll and /tpllcalc for modern Fabric.
 */
package dev.tpllonly.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * Registers <code>/where</code>, the reverse of <code>/tpll</code>: instead of turning a place on
 * Earth into world coordinates, it turns the position you are standing on back into the place on
 * Earth that it corresponds to.
 *
 * <pre>
 *   /where                     where in the real world am I?
 *   /where Steve               ... and where is Steve?
 *   /where @a                  ... and everybody?
 *   /where 3235497 -4704415    ... or at this X/Z, if nobody is around to stand on it
 * </pre>
 *
 * <p>Nothing moves and no chunks are loaded, and looking at somebody else costs nothing either
 * &mdash; the command only reads a position the server already knows, so it asks for no
 * permissions. The answer is exactly the latitude/longitude pair that <code>/tpll</code> would put
 * you back where you started, in decimal (click to copy) and in degrees/minutes/seconds, with a
 * link that opens the spot on a map.
 */
public final class WhereCommand {

	/** a map of the reported place, centred and zoomed in close */
	private static final String MAP_URL = "https://www.openstreetmap.org/?mlat=%s&mlon=%s#map=15/%s/%s";

	private WhereCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("where")
				.executes(ctx -> where(ctx.getSource(), ""))
				.then(Commands.argument("target", StringArgumentType.greedyString())
						.executes(ctx -> where(ctx.getSource(), StringArgumentType.getString(ctx, "target")))));
	}

	// ------------------------------------------------------------------------

	private static int where(CommandSourceStack source, String raw) {
		String argument = raw.trim();

		if (argument.isEmpty()) {
			ServerPlayer self = source.getPlayer();
			if (self == null) {
				// the server console has no position of its own to look up
				usage(source, "There is nobody standing here to locate: give an X and a Z, or a player.");
				return 0;
			}

			report(source, self.level(), null, self.position());
			return 1;
		}

		// "Steve", "@a", "@p" - everybody the name or selector matches
		List<ServerPlayer> players = CoordinateInput.parsePlayersOnly(source, argument);
		if (players != null) {
			for (ServerPlayer player : players) {
				report(source, player.level(), player.getDisplayName(), player.position());
			}
			return players.size();
		}

		// "3235497 -4704415", a Y on the end optional - the position to look up by itself
		Vec3 position = parseWorldPosition(argument);
		if (position != null) {
			// positions given as numbers are taken to be overworld ones, which is what the map is
			report(source, source.getServer().overworld(),
					Component.literal(String.format(Locale.US, "X %.2f, Z %.2f", position.x, position.z)), position);
			return 1;
		}

		usage(source, "Nobody named \"" + argument + "\" is online, and that isn't an X and a Z either.");
		return 0;
	}

	/**
	 * Converts one position into latitude/longitude and prints it.
	 *
	 * @param level    the level {@code position} belongs to, for that dimension's coordinate scale
	 * @param subject  who or what the answer is about; null means "you", the source itself
	 * @param position where in that level, at whatever height
	 */
	private static void report(CommandSourceStack source, ServerLevel level, Component subject, Vec3 position) {
		// The projection maps the overworld, so in a scaled dimension (the Nether is 1:8) the block
		// you are standing on is nowhere near the matching overworld one. Convert to overworld
		// terms first, exactly like a portal does, and say so.
		double scale = level.dimensionType().coordinateScale();
		String scaleNote = Math.abs(scale - 1.0) <= 1.0E-9 ? null
				: String.format(Locale.US,
						"%s positions are scaled by x%s into the overworld first, like a portal does.",
						level.dimension().identifier().getPath(), trim(scale));

		GeographicProjection projection = TpllConfig.projection();

		double[] geo;
		try {
			geo = projection.toGeo(position.x * scale, position.z * scale);
		} catch (OutOfProjectionBoundsException e) {
			source.sendSystemMessage(subject(subject)
					.append(Component.literal(is(subject) + " outside the mapped part of this world.")
							.withStyle(ChatFormatting.RED)));
			if (scaleNote != null) {
				source.sendSystemMessage(Component.literal(scaleNote).withStyle(ChatFormatting.DARK_GRAY));
			}
			return;
		}

		LatLng coordinates;
		try {
			coordinates = new LatLng(geo[1], geo[0]);
		} catch (NumberFormatException e) {
			source.sendSystemMessage(subject(subject)
					.append(Component.literal(is(subject) + " somewhere this map projection can't describe.")
							.withStyle(ChatFormatting.RED)));
			return;
		}

		String decimal = GeoFormatter.decimal(coordinates);
		String dms = GeoFormatter.dms(coordinates);
		String xyz = String.format(Locale.US, "%.2f %.2f %.2f", position.x, position.y, position.z);

		source.sendSystemMessage(subject(subject)
				.append(Component.literal(is(subject) + " here on Earth:").withStyle(ChatFormatting.YELLOW)));

		source.sendSystemMessage(Component.literal("Real world: ").withStyle(ChatFormatting.GRAY)
				.append(Chat.copy(decimal, decimal).withStyle(ChatFormatting.AQUA))
				.append(Component.literal("   " + dms).withStyle(ChatFormatting.GRAY))
				.append(Component.literal("  ").append(Chat.bracketed(
						Chat.copy(Chat.actionable("copy"), decimal + "  " + dms)))
						.withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal("  ").append(Chat.bracketed(
						Chat.openUrl(Chat.actionable("map"), mapUrl(coordinates))))
						.withStyle(ChatFormatting.DARK_GRAY)));

		source.sendSystemMessage(Component.literal("In this world: ").withStyle(ChatFormatting.GRAY)
				.append(Chat.copy(xyz, xyz).withStyle(ChatFormatting.AQUA))
				.append(Component.literal("  (x y z in " + level.dimension().identifier() + ")")
						.withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal("  ").append(Chat.bracketed(
						Chat.suggest(Chat.actionable("go back"), "/tpll " + decimal)))
						.withStyle(ChatFormatting.DARK_GRAY)));

		if (scaleNote != null) {
			source.sendSystemMessage(Component.literal(scaleNote).withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	/** " are" when the answer is about whoever typed the command, " is" when it is about a target */
	private static String is(Component subject) {
		return subject == null ? " are" : " is";
	}

	/** "<name> " or "You ", ready for a verb to be appended */
	private static MutableComponent subject(Component subject) {
		return subject == null
				? Component.literal("You").withStyle(ChatFormatting.WHITE)
				: subject.copy().withStyle(ChatFormatting.WHITE);
	}

	private static String mapUrl(LatLng coordinates) {
		String lat = String.format(Locale.US, "%.6f", coordinates.getLatitude());
		String lon = String.format(Locale.US, "%.6f", coordinates.getLongitude());
		return String.format(Locale.US, MAP_URL, lat, lon, lat, lon);
	}

	private static String trim(double value) {
		return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.US, "%.3f", value);
	}

	/**
	 * Reads a bare world position: "3235497 -4704415", "3235497, -4704415, 73".
	 *
	 * @return the position (Y defaults to 0), or null if the text isn't one
	 */
	private static Vec3 parseWorldPosition(String raw) {
		String[] tokens = raw.split("[\\s,;]+");
		if (tokens.length < 2 || tokens.length > 3) {
			return null;
		}

		double[] values = new double[]{ 0, 0, 0 };
		for (int i = 0; i < tokens.length; i++) {
			double value;
			try {
				value = Double.parseDouble(tokens[i]);
			} catch (NumberFormatException e) {
				return null;
			}

			if (!Double.isFinite(value)) {
				return null;
			}

			// x and z go first, a trailing y goes last
			if (i == 0) {
				values[0] = value;
			} else if (i == 1) {
				values[2] = value;
			} else {
				values[1] = value;
			}
		}

		return new Vec3(values[0], values[1], values[2]);
	}

	private static void usage(CommandSourceStack source, String why) {
		source.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
		source.sendSystemMessage(Component.literal("Usage:").withStyle(ChatFormatting.YELLOW));
		source.sendSystemMessage(Component.literal("  /where").withStyle(ChatFormatting.GRAY)
				.append(Component.literal("  -  where in the real world you are standing")
						.withStyle(ChatFormatting.DARK_GRAY)));
		source.sendSystemMessage(Component.literal("  /where <player>")
				.withStyle(ChatFormatting.GRAY)
				.append(Component.literal("  -  the same for somebody else (names and selectors work)")
						.withStyle(ChatFormatting.DARK_GRAY)));
		source.sendSystemMessage(Component.literal("  /where <x> <z> [y]")
				.withStyle(ChatFormatting.GRAY)
				.append(Component.literal("  -  the same for a position, e.g. from the server console")
						.withStyle(ChatFormatting.DARK_GRAY)));
	}
}
