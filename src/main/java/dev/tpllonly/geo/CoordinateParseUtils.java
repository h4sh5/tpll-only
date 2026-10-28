/*
 * tpll-only &mdash; standalone /tpll and /tpllcalc for modern Fabric.
 *
 * Portions of this file are derived from TerraPlusPlus
 * (https://github.com/BuildTheEarth/terraplusplus), Copyright (c) 2020 BuildTheEarth.net,
 * distributed under the MIT license. The original in turn adapted the coordinate parsers from
 * GBIF (https://github.com/gbif/pipelines), also MIT licensed.
 */
package dev.tpllonly.geo;

import dev.tpllonly.projection.OutOfProjectionBoundsException;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the free-form text players type into a {@link LatLng}.
 *
 * <p>This is a dependency-free port of TerraPlusPlus' {@code CoordinateParseUtils}, kept behaviour
 * compatible on purpose: {@code /tpll} accepts the same shapes it always has, including DMS with
 * hemisphere letters, comma-as-decimal-separator, and the various separators between the two
 * halves of a coordinate.
 *
 * <p>Understood, for example:
 * <pre>
 *   48.858370 2.294481
 *   48.858370, 2.294481
 *   48,858370 2,294481
 *   N 48.858370 E 2.294481
 *   48°51'30"N 2°21'42"E
 *   48°51'30''n 2°21'42''e
 *   33.452242° -112.070295°
 * </pre>
 */
public final class CoordinateParseUtils {

	/** degrees, minutes and optional seconds */
	private static final String DMS = "\\s*(\\d{1,3})\\s*(?:°|d|º| |g|o)"   // the degrees
			+ "\\s*([0-6]?\\d)\\s*(?:'|m| |´|’|′)"  // the minutes
			+ "\\s*(?:"                             // non-capturing group
			+ "([0-6]?\\d(?:[,.]\\d+)?)"            // seconds and optional decimal
			+ "\\s*(?:\"|''|s|´´|″)?"
			+ ")?\\s*";

	/** degrees and optional minutes */
	private static final String DM = "\\s*(\\d{1,3})\\s*(?:°|d|º| |g|o)" // the degrees
			+ "\\s*(?:"                          // non-capturing group
			+ "([0-6]?\\d(?:[,.]\\d+)?)"          // minutes and optional decimal
			+ "\\s*(?:'|m| |´|’|′)?"
			+ ")?\\s*";

	/** plain decimal degrees */
	private static final String D = "\\s*(\\d{1,3}(?:[,.]\\d+)?)\\s*(?:°|d|º| |g|o|)\\s*";

	private static final Pattern DMS_SINGLE = Pattern.compile("^" + DMS + "$", Pattern.CASE_INSENSITIVE);
	private static final Pattern DM_SINGLE = Pattern.compile("^" + DM + "$", Pattern.CASE_INSENSITIVE);
	private static final Pattern D_SINGLE = Pattern.compile("^" + D + "$", Pattern.CASE_INSENSITIVE);

	/** a full coordinate pair written with hemisphere letters and no separator between them */
	private static final Pattern DMS_COORD = Pattern.compile(
			"^" + DMS + "([NSEOW])" + "[ ,;/]?" + DMS + "([NSEOW])$", Pattern.CASE_INSENSITIVE);

	private static final String POSITIVE = "NEO";

	private static final char[] PART_DELIMITERS = new char[]{ ';', '/', ',' };

	/** tries a plain decimal first, then the same thing with a comma as the decimal separator */
	private static final List<Function<String, Double>> NUMBER_PARSERS = List.of(
			Double::parseDouble,
			CoordinateParseUtils::parseDoubleWithCommaDecimalSeparator
	);

	private CoordinateParseUtils() {
	}

	/**
	 * Parses a coordinate pair however it happens to be written.
	 *
	 * <p>TerraPlusPlus can read {@code N48.858 E2.294} but not {@code N 48.858 E 2.294}, because a
	 * blank between a hemisphere letter and its number leaves it with more than two parts. Players
	 * write the spaced form all the time, so if the strict parse fails the letters are closed up and
	 * the parse is retried - that only ever accepts more, never changes an answer.
	 *
	 * @param coordinates the text to parse
	 * @return the point it refers to, or {@code null} if it isn't a coordinate
	 */
	public static LatLng parseVerbatimCoordinates(final String coordinates) {
		LatLng parsed = parseStrict(coordinates);

		if (parsed == null && coordinates != null) {
			String closed = closeSpacedDirections(coordinates);
			if (!closed.equals(coordinates)) {
				parsed = parseStrict(closed);
			}
		}

		return parsed;
	}

	/** hemisphere letters with a space on either side of them */
	private static final Pattern SPACED_LEADING_DIRECTION =
			Pattern.compile("(?i)\\b([NSEW])\\s+(?=[+-]?\\d)");

	private static final Pattern SPACED_TRAILING_DIRECTION =
			Pattern.compile("(?i)(?<=\\d)\\s+([NSEW])(?=\\s|$)");

	private static String closeSpacedDirections(String coordinates) {
		return SPACED_TRAILING_DIRECTION
				.matcher(SPACED_LEADING_DIRECTION.matcher(coordinates).replaceAll("$1"))
				.replaceAll("$1");
	}

	/**
	 * The TerraPlusPlus parser, unchanged.
	 */
	private static LatLng parseStrict(final String coordinates) {
		if (coordinates == null || coordinates.isEmpty()) {
			return null;
		}

		try {
			// first try a pair written back-to-back, e.g. 02°49'52"N131°47'03"E
			Matcher matcher = DMS_COORD.matcher(coordinates);
			if (matcher.find()) {
				String direction1 = matcher.group(4);
				String direction2 = matcher.group(8);

				for (Function<String, Double> format : NUMBER_PARSERS) {
					try {
						double coordinate1 = coordFromMatcher(matcher, 1, 2, 3, direction1, format);
						double coordinate2 = coordFromMatcher(matcher, 5, 6, 7, direction2, format);

						// now work out which half is which
						if (isLat(direction1) && !isLat(direction2)) {
							OutOfProjectionBoundsException.checkLongitudeLatitudeInRange(coordinate2, coordinate1);
							return validateAndRound(coordinate1, coordinate2);
						} else if (!isLat(direction1) && isLat(direction2)) {
							return validateAndRound(coordinate2, coordinate1);
						}

						return null;
					} catch (Exception ignored) {
					}
				}

				return null;
			}

			// otherwise split the string in two and parse each half
			String[] latlon = separateParts(coordinates);

			for (Function<String, Double> format : NUMBER_PARSERS) {
				try {
					double lat = parseDMS(latlon[0], true, format);
					double lon = parseDMS(latlon[1], false, format);
					return validateAndRound(lat, lon);
				} catch (IllegalArgumentException | OutOfProjectionBoundsException ignored) {
				}
			}

			for (Function<String, Double> format : NUMBER_PARSERS) {
				try {
					double lat = parseDecimal(latlon[0], format);
					double lon = parseDecimal(latlon[1], format);
					return validateAndRound(lat, lon);
				} catch (Exception ignored) {
				}
			}

			// no luck
			return null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Rounds to 6 decimal places (~1&nbsp;m of precision) and checks that the result is a real
	 * place on Earth, swapping the halves if that's what it takes.
	 */
	private static LatLng validateAndRound(double lat, double lon) throws OutOfProjectionBoundsException {
		lat = roundTo6decimals(lat);
		lon = roundTo6decimals(lon);

		if (Double.compare(lat, 0) == 0 && Double.compare(lon, 0) == 0) {
			return new LatLng(0, 0);
		}

		if (LatLng.inRange(lat, lon)) {
			return new LatLng(lat, lon);
		}

		if (Double.compare(lat, 90) > 0 || Double.compare(lat, -90) < 0) {
			// the input was probably longitude-first
			if (LatLng.inRange(lon, lat)) {
				return new LatLng(lat, lon);
			}
		}

		throw OutOfProjectionBoundsException.get();
	}

	/**
	 * Splits a coordinate pair into its two halves, the same way TerraPlusPlus does: a single blank
	 * wins, and if there isn't one, a delimiter that appears exactly once.
	 *
	 * @throws NumberFormatException if the string can't be split in two
	 */
	private static String[] separateParts(String coordinates) throws NumberFormatException {
		coordinates = coordinates.trim();

		int firstBlankStart = coordinates.indexOf(' ');
		if (firstBlankStart > 0) {
			int firstBlankEnd = firstBlankStart;
			while (coordinates.charAt(firstBlankEnd) == ' ') {
				firstBlankEnd++;
			}

			// more than one blank means the input has too many parts
			if (coordinates.indexOf(' ', firstBlankEnd) >= 0) {
				throw new NumberFormatException();
			}

			// "48.58, 85.26" - the comma right before the blank belongs with the blank
			char charBeforeBlank = coordinates.charAt(firstBlankStart - 1);
			if (firstBlankStart > 1
					&& indexOf(PART_DELIMITERS, charBeforeBlank) >= 0
					&& coordinates.indexOf(charBeforeBlank, firstBlankEnd) < 0) {
				firstBlankStart--;
			}

			return new String[]{ coordinates.substring(0, firstBlankStart), coordinates.substring(firstBlankEnd) };
		}

		for (char delimiter : PART_DELIMITERS) {
			int count = countMatches(coordinates, delimiter);
			int at = coordinates.indexOf(delimiter);
			if (count == 1 && at > 0 && at < coordinates.length() - 1) {
				return new String[]{ coordinates.substring(0, at), coordinates.substring(at + 1) };
			}
		}

		throw new NumberFormatException();
	}

	/**
	 * Parses one half of a coordinate in DMS, DM or decimal-degrees form.
	 *
	 * @param coord the text for one half
	 * @param lat   true for latitude (N/S), false for longitude (E/O/W)
	 */
	private static double parseDMS(String coord, boolean lat, Function<String, Double> format) {
		final String dirs = lat ? "NS" : "EOW";
		coord = coord.trim().toUpperCase(java.util.Locale.ROOT);

		if (coord.length() > 3) {
			// pull the hemisphere letter off first so the regexes stay simple
			char dir = 'n';
			if (dirs.indexOf(coord.charAt(0)) >= 0) {
				dir = coord.charAt(0);
				coord = coord.substring(1);
			} else if (dirs.indexOf(coord.charAt(coord.length() - 1)) >= 0) {
				dir = coord.charAt(coord.length() - 1);
				coord = coord.substring(0, coord.length() - 1);
			}

			Matcher matcher = DMS_SINGLE.matcher(coord);
			if (matcher.find()) {
				return coordFromMatcher(matcher, 1, 2, 3, String.valueOf(dir), format);
			}

			matcher = DM_SINGLE.matcher(coord);
			if (matcher.find()) {
				return coordFromMatcher(matcher, 1, 2, String.valueOf(dir), format);
			}

			matcher = D_SINGLE.matcher(coord);
			if (matcher.find()) {
				return coordFromMatcher(matcher, 1, String.valueOf(dir), format);
			}
		}

		throw new IllegalArgumentException();
	}

	private static double parseDecimal(String coord, Function<String, Double> format) {
		if (coord.endsWith("°")) {
			coord = coord.substring(0, coord.length() - 1).trim();
		}

		return format.apply(coord);
	}

	private static double coordFromMatcher(Matcher matcher, int degrees, int minutes, int seconds,
	                                       String sign, Function<String, Double> format) {
		return roundTo6decimals(coordSign(sign)
				* dmsToDecimal(format.apply(matcher.group(degrees)),
				format.apply(matcher.group(minutes)),
				format.apply(matcher.group(seconds))));
	}

	private static double coordFromMatcher(Matcher matcher, int degrees, int minutes,
	                                       String sign, Function<String, Double> format) {
		return roundTo6decimals(coordSign(sign)
				* dmsToDecimal(format.apply(matcher.group(degrees)), format.apply(matcher.group(minutes)), 0.0));
	}

	private static double coordFromMatcher(Matcher matcher, int degrees,
	                                       String sign, Function<String, Double> format) {
		return roundTo6decimals(coordSign(sign)
				* dmsToDecimal(format.apply(matcher.group(degrees)), 0.0, 0.0));
	}

	private static double dmsToDecimal(double degrees, double minutes, double seconds) {
		return degrees + (minutes / 60) + (seconds / 3600);
	}

	/** rounds to 6 decimals (~1&nbsp;m), because nothing more precise is meaningful here */
	private static double roundTo6decimals(double value) {
		return Math.round(value * Math.pow(10, 6)) / Math.pow(10, 6);
	}

	private static boolean isLat(String direction) {
		return "NS".contains(direction.toUpperCase(java.util.Locale.ROOT));
	}

	private static int coordSign(String direction) {
		return POSITIVE.contains(direction.toUpperCase(java.util.Locale.ROOT)) ? 1 : -1;
	}

	private static double parseDoubleWithCommaDecimalSeparator(String str) {
		if (str.indexOf('.') > 0) {
			throw new NumberFormatException("Illegal char in comma number: .");
		}

		return Double.parseDouble(str.replace(',', '.'));
	}

	private static int indexOf(char[] array, char value) {
		for (int i = 0; i < array.length; i++) {
			if (array[i] == value) {
				return i;
			}
		}

		return -1;
	}

	private static int countMatches(String string, char value) {
		int count = 0;
		for (int i = 0; i < string.length(); i++) {
			if (string.charAt(i) == value) {
				count++;
			}
		}

		return count;
	}
}
