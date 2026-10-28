/*
 * tpll-only &mdash; standalone /tpll and /tpllcalc for modern Fabric.
 */
package dev.tpllonly.geo;

import java.util.Locale;

/**
 * The two ways this mod shows a geographic coordinate.
 *
 * <p>{@link #decimal(LatLng)} is what <code>/tpll</code> itself wants and what the copy-buttons
 * hand back; {@link #dms(LatLng)} is the degrees/minutes/seconds form, for people who read
 * coordinates off a paper map. Both are understood by {@link CoordinateParseUtils}, so anything the
 * mod prints can be pasted straight back into <code>/tpll</code>.
 *
 * <p>Lives in <code>geo</code> rather than next to the commands because it needs nothing but the
 * JDK, which keeps it testable from {@code GeoTests}.
 */
public final class GeoFormatter {

	/** six decimals is about a metre, which is how TerraPlusPlus shows coordinates */
	private static final int DECIMAL_PLACES = 6;

	private GeoFormatter() {
	}

	/**
	 * @return <code>"48.858370 2.294481"</code>, i.e. latitude first, ready to paste into /tpll
	 */
	public static String decimal(LatLng coordinates) {
		return String.format(Locale.US, "%." + DECIMAL_PLACES + "f %." + DECIMAL_PLACES + "f",
				coordinates.getLatitude(), coordinates.getLongitude());
	}

	/**
	 * @return <code>"48°51'30.13\"N 2°17'40.13\"E"</code>
	 */
	public static String dms(LatLng coordinates) {
		return dms(coordinates.getLatitude(), 'N', 'S') + " " + dms(coordinates.getLongitude(), 'E', 'W');
	}

	private static String dms(double degrees, char positive, char negative) {
		char hemisphere = degrees < 0 ? negative : positive;

		double value = Math.abs(degrees);
		int d = (int) Math.floor(value);
		double minutes = (value - d) * 60;
		int m = (int) Math.floor(minutes);
		double seconds = (minutes - m) * 60;

		// 59.999" prints as 60.00" otherwise, which no parser on earth accepts
		if (seconds >= 59.995) {
			seconds = 0;
			if (++m == 60) {
				m = 0;
				d++;
			}
		}

		return String.format(Locale.US, "%d°%02d'%05.2f\"%c", d, m, seconds, hemisphere);
	}
}
