/*
 * tpll-only &mdash; standalone /tpll and /tpllcalc for modern Fabric.
 */
package dev.tpllonly.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.tpllonly.geo.CoordinateParseUtils;
import dev.tpllonly.geo.LatLng;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns the free-form text after <code>/tpll</code> into a target list, a coordinate pair and an
 * optional altitude.
 *
 * <p>TerraPlusPlus did the same thing in 1.12 by brute force: the whole argument string is first
 * tried as a bare coordinate pair (so {@code /tpll 48.858 2.294} works), and only if that fails is
 * the first token re-interpreted as a player name or selector (so {@code /tpll Steve 48.858 2.294}
 * and {@code /tpll @a 48.858 2.294} work too), and then the same two options again with the last
 * token taken as an altitude. Keeping that behaviour means the command reads exactly like the one
 * players already know, and it avoids the ambiguity that a real {@code EntityArgument} node in
 * front of the coordinates would create.
 */
public record CoordinateInput(List<ServerPlayer> targets, LatLng coordinates, Double altitude) {

	/**
	 * @param source the command source, used to resolve player names/selectors and as the
	 *               implicit target
	 * @param raw    everything the player typed after the command name
	 * @return the parsed input, or {@code null} if it isn't a valid invocation
	 */
	public static CoordinateInput parse(CommandSourceStack source, String raw) {
		return parse(source, raw, false);
	}

	/**
	 * @param allowNoTargets true to also accept coordinates with nobody to act on them, which is
	 *                       how {@code /tpllcalc} behaves when run from the server console
	 */
	public static CoordinateInput parse(CommandSourceStack source, String raw, boolean allowNoTargets) {
		if (raw == null) {
			return null;
		}

		raw = raw.trim();
		if (raw.isEmpty()) {
			return null;
		}

		String[] tokens = raw.split("\\s+");
		int n = tokens.length;

		// 1. "48.858 2.294" - coordinates only, aimed at whoever ran the command
		CoordinateInput result = attempt(source, tokens, 0, n, null, allowNoTargets);
		if (result != null) {
			return result;
		}

		// 2. "Steve 48.858 2.294" - a player (or selector) in front
		if (n >= 2) {
			result = attempt(source, tokens, 1, n, tokens[0], allowNoTargets);
			if (result != null) {
				return result;
			}
		}

		// 3. "48.858 2.294 300" - and the same thing again with an altitude on the end
		if (n >= 3) {
			result = attempt(source, tokens, 0, n - 1, null, allowNoTargets);
			if (result != null) {
				return result;
			}

			// 4. "Steve 48.858 2.294 300"
			result = attempt(source, tokens, 1, n - 1, tokens[0], allowNoTargets);
			if (result != null) {
				return result;
			}
		}

		return null;
	}

	/**
	 * Parses one candidate reading of the argument list.
	 *
	 * @param from        first token of the coordinate
	 * @param to          token after the last token of the coordinate
	 * @param targetToken the token naming the player to teleport, or null for the command source
	 * @return the parsed input, or {@code null} if this reading doesn't work out
	 */
	private static CoordinateInput attempt(CommandSourceStack source, String[] tokens, int from, int to,
	                                       String targetToken, boolean allowNoTargets) {
		if (to - from < 1) {
			return null;
		}

		StringBuilder coordinate = new StringBuilder();
		for (int i = from; i < to; i++) {
			if (i > from) {
				coordinate.append(' ');
			}
			coordinate.append(tokens[i]);
		}

		// "48.858370, 2.294481, 150" reaches here as "48.858370, 2.294481," once the altitude has
		// been split off, and the parser (like TerraPlusPlus') won't read a coordinate with a
		// separator hanging off the end of it
		while (coordinate.length() > 0 && ",;/ ".indexOf(coordinate.charAt(coordinate.length() - 1)) >= 0) {
			coordinate.setLength(coordinate.length() - 1);
		}

		LatLng coordinates = parseVerbatim(coordinate.toString());
		if (coordinates == null) {
			return null;
		}

		// a trailing token is an altitude only if it really is a number
		Double altitude = null;
		if (to < tokens.length) {
			altitude = tryNumber(tokens[to]);
			if (altitude == null) {
				return null;
			}
		}

		List<ServerPlayer> targets;
		if (targetToken == null) {
			ServerPlayer self = source.getPlayer();
			if (self == null) {
				if (!allowNoTargets) {
					return null;
				}

				targets = List.of();
			} else {
				targets = List.of(self);
			}
		} else {
			targets = resolvePlayers(source, targetToken);
			if (targets == null) {
				return null;
			}
		}

		return new CoordinateInput(targets, coordinates, altitude);
	}

	private static LatLng parseVerbatim(String coordinates) {
		return CoordinateParseUtils.parseVerbatimCoordinates(coordinates);
	}

	/**
	 * Resolves a single token as a player name or entity selector, without any of the coordinate
	 * handling.
	 *
	 * @return the matching players, or {@code null} if the token isn't a valid target
	 */
	public static List<ServerPlayer> parsePlayersOnly(CommandSourceStack source, String token) {
		return resolvePlayers(source, token);
	}

	/**
	 * Resolves a single token as a player name or entity selector.
	 *
	 * @return the matching players, or {@code null} if the token isn't a valid target
	 */
	private static List<ServerPlayer> resolvePlayers(CommandSourceStack source, String token) {
		if (token == null || token.isEmpty()) {
			return null;
		}

		try {
			EntitySelector selector = EntityArgument.players().parse(new StringReader(token));
			List<ServerPlayer> found = selector.findPlayers(source);
			return found.isEmpty() ? null : List.copyOf(found);
		} catch (CommandSyntaxException e) {
			return null;
		}
	}

	private static Double tryNumber(String token) {
		try {
			double value = Double.parseDouble(token);
			return Double.isFinite(value) ? value : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * @return the coordinates in a form that can be pasted straight back into the command
	 */
	public String coordinatesAsString() {
		return String.format(Locale.US, "%.6f %.6f", this.coordinates.getLatitude(), this.coordinates.getLongitude());
	}
}
