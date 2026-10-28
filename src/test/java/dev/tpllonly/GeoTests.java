/*
 * tpll-only &mdash; standalone /tpll and /tpllcalc for modern Fabric.
 */
package dev.tpllonly;

import dev.tpllonly.geo.CoordinateParseUtils;
import dev.tpllonly.geo.LatLng;
import dev.tpllonly.projection.GeographicProjection;
import dev.tpllonly.projection.OutOfProjectionBoundsException;
import dev.tpllonly.projection.Projections;
import dev.tpllonly.projection.transform.ScaleProjectionTransform;

/**
 * Plain-JVM checks for the coordinate parser and the projections &mdash; no Minecraft, no Fabric
 * Loader, so it runs anywhere. Started by {@code ./gradlew geoTest}.
 *
 * <p>The parser cases are TerraPlusPlus' own test vectors
 * ({@code CoordinateParseUtilsTest}), ported over so that this implementation stays behaviour
 * compatible with the command players already know. The projection cases check the Dymaxion family
 * against the coordinates BuildTheEarth's live server reports for the same place.
 */
public final class GeoTests {

	private static final double PRECISION = 1.0E-7;

	private static int failures = 0;
	private static int checks = 0;

	public static void main(String[] args) throws Exception {
		validCoordinates();
		invalidCoordinates();
		projections();
		dymaxion();
		printReferencePositions();

		System.out.println();
		if (failures == 0) {
			System.out.println("geoTest: all " + checks + " checks passed");
			return;
		}

		System.out.println("geoTest: " + failures + " of " + checks + " checks FAILED");
		System.exit(1);
	}

	private static void validCoordinates() {
		// DMS format (degrees minutes seconds)
		valid("02°49'52\"N131°47'03\"E", 131.784167, 2.831111);
		valid("02°49'52''N 131°47'03''E", 131.784167, 2.831111);
		valid("02°49'52\"n 131°47'03\"E", 131.784167, 2.831111);
		valid("02°49'52\"N 131°47'03\"e", 131.784167, 2.831111);
		valid("02°49'52\"S 131°47'03\"E", 131.784167, -2.831111);
		valid("02°49'52\"N 131°47'03\"W", -131.784167, 2.831111);
		valid("02°49'52\"s 131°47'03\"w", -131.784167, -2.831111);

		// the shapes /tpll is used with most
		valid("48.858370 2.294481", 2.294481, 48.858370);
		valid("48.858370, 2.294481", 2.294481, 48.858370);
		valid("48°51'30\"N 2°21'42\"E", 2.361667, 48.858333);
		valid("48°51'30\"N;2°21'42\"E", 2.361667, 48.858333);
		valid("48d51m30sN 2d21m42sE", 2.361667, 48.858333);
		valid("48.858370N 2.294481E", 2.294481, 48.858370);
		valid("N48.858370 E2.294481", 2.294481, 48.858370);
		valid("48.858370 2.294481", 2.294481, 48.858370);
		valid("S 33.8651 W 151.2109", -151.2109, -33.8651);
		valid("33.8651S 151.2109W", -151.2109, -33.8651);

		// various decimal variations
		valid("2.831111s 131.784167w", -131.784167, -2.831111);
		valid("-2.831111 -131.784167", -131.784167, -2.831111);
		valid("2,831111s 131,784167w", -131.784167, -2.831111);
		valid("-2,831111 -131,784167", -131.784167, -2.831111);
		valid("33.452242° -112.070295°", -112.070295, 33.452242);

		// with various separators between lat and long
		valid("02°49'52\"N;131°47'03\"E", 131.784167, 2.831111);
		valid("2.831111s;131.784167w", -131.784167, -2.831111);
		valid("-2.831111;-131.784167", -131.784167, -2.831111);
		valid("02°49'52\"N,131°47'03\"E", 131.784167, 2.831111);
		valid("2.831111s,131.784167w", -131.784167, -2.831111);
		valid("-2.831111,-131.784167", -131.784167, -2.831111);
		valid("-2.831111, -131.784167", -131.784167, -2.831111);
		valid("41.86086476755931, -87.7630805146585", -87.763081, 41.860865);

		// variants of the above with a comma as the decimal separator
		valid("2,831111s;131,784167w", -131.784167, -2.831111);
		valid("-2,831111;-131,784167", -131.784167, -2.831111);
		valid("2,831111s 131,784167w", -131.784167, -2.831111);
		valid("-2,831111 -131,784167", -131.784167, -2.831111);

		valid("2 131", 131, 2);
		valid("-2 -131", -131, -2);
		valid("0. 0.", 0, 0);
		valid("0 0", 0, 0);
		valid("0.1 0", 0, 0.1);

		valid("0, 0,", 0, 0);
		valid("0,1 0", 0, 0.1);

		// more than one space in the middle
		valid("0,1  0", 0, 0.1);
	}

	private static void invalidCoordinates() {
		// too many values
		invalid("48.58, 85.26 75.2");
		invalid("48.58,  85.26 75.2");
		invalid("48.58, 85.26, 75.2");
		invalid("48.58/85.26/75.2");
		invalid("48.58, 85.26     75.2");
		invalid("48,58; 85,26 75,2");
		invalid("48,58; 85,26, 75,2");
		invalid("48,58/85,26/75,2");

		// too few values
		invalid("48.35");
		invalid("48.35,");
		invalid(",48.35");
		invalid("/48.35");
		invalid("/48,35");

		// invalid characters
		invalid("48.56@23.9");
		invalid("{48.56 23.9}");
		invalid("48,56@23.9");
		invalid("{48,56;23,9}");

		// mixed decimal separators
		invalid("78.52 45,8");
		invalid("78.52;45,8");

		// out of bounds
		invalid("45 -195");
		invalid("95 125");
		invalid("-97, -172");
		invalid("45, 223");

		// not coordinates at all
		invalid("hello world");
		invalid("N");
		invalid("");
		invalid(null);
	}

	private static void projections() throws OutOfProjectionBoundsException {
		// the classic TerraPlusPlus setup: equirectangular scaled to 100000 blocks per degree
		GeographicProjection defaultProjection =
				new ScaleProjectionTransform(Projections.create("equirectangular"), 100000, 100000);

		double[] paris = defaultProjection.fromGeo(2.294481, 48.858370);
		equals("equirectangular X", 229448.1, paris[0], 1.0E-3);
		equals("equirectangular Z", 4885837.0, paris[1], 1.0E-3);

		double[] back = defaultProjection.toGeo(paris[0], paris[1]);
		equals("equirectangular round trip longitude", 2.294481, back[0], 1.0E-6);
		equals("equirectangular round trip latitude", 48.858370, back[1], 1.0E-6);

		roundTrip("sinusoidal", Projections.create("sinusoidal"));
		roundTrip("mercator", Projections.create("mercator"));
		roundTrip("scaled sinusoidal",
				new ScaleProjectionTransform(Projections.create("sinusoidal"), 100000, 100000));

		// out-of-bounds handling
		throwsBounds("mercator past the poles", () -> Projections.create("mercator").fromGeo(0, 89.5));
		throwsBounds("beyond 180 degrees", () -> Projections.create("equirectangular").fromGeo(181, 0));
		throwsBounds("reverse beyond the poles", () -> Projections.create("equirectangular").toGeo(0, 95));

		// bounds() covers the whole world
		double[] bounds = Projections.create("equirectangular").bounds();
		equals("equirectangular min X", -180, bounds[0], 1.0E-9);
		equals("equirectangular max X", 180, bounds[2], 1.0E-9);
		equals("equirectangular min Z", -90, bounds[1], 1.0E-9);
		equals("equirectangular max Z", 90, bounds[3], 1.0E-9);
	}

	/**
	 * The default projection: the conformal Dymaxion net BuildTheEarth runs its server on.
	 *
	 * <p>The reference values are what <code>/tpll 47.051939313728376, 8.306423216110339</code>
	 * (Lucerne) reports on that live server, X 3235623.703 / Z -4704424.710. They are reproduced to
	 * well under a hundredth of a percent &mdash; the residue is the slightly different distortion
	 * field the newer build there uses, so the checks here are deliberately loose rather than
	 * pretending to be exact.
	 */
	private static void dymaxion() throws OutOfProjectionBoundsException {
		GeographicProjection net = Projections.create("dymaxion");

		// north runs along +Y on the raw net, which is what the config's north_is_negative_z flips
		equals("dymaxion net north is up", 1.0, Math.signum(net.fromGeo(0, 89)[1] - net.fromGeo(0, -89)[1]), 1.0E-9);

		GeographicProjection world = defaultWorldProjection();

		double[] lucerne = world.fromGeo(8.306423216110339, 47.051939313728376);
		equals("BTE dymaxion X (Lucerne)", 3235623.703, lucerne[0], 1000.0);
		equals("BTE dymaxion Z (Lucerne)", -4704424.710, lucerne[1], 100.0);

		double[] home = world.toGeo(lucerne[0], lucerne[1]);
		equals("BTE dymaxion round trip longitude", 8.306423216110339, home[0], 1.0E-6);
		equals("BTE dymaxion round trip latitude", 47.051939313728376, home[1], 1.0E-6);

		// with north_is_negative_z, teleporting north must move towards negative Z
		double[] equator = world.fromGeo(8.0, 0.0);
		double[] north = world.fromGeo(8.0, 45.0);
		equals("BTE dymaxion north is negative Z", -1.0, Math.signum(north[1] - equator[1]), 1.0E-9);

		roundTrip("dymaxion", net);
		roundTrip("conformal dymaxion", Projections.create("conformal_dymaxion"));
		roundTrip("raw dymaxion", Projections.create("raw_dymaxion"));
		roundTrip("BTE dymaxion world", world);

		// the whole world has to fit inside the map Minecraft allows
		double[] bounds = world.bounds();
		for (int i = 0; i < 4; i++) {
			equals("BTE dymaxion bounds within world limits", 0.0, Math.max(Math.abs(bounds[i]) - 2.9999999E7, 0.0), 1.0E-9);
		}

		// The scale isn't arbitrary: 7318261.522857145 is the earth's radius (40075017 / 2*pi) times
		// the projection's own 1.1473979730192934, so a block comes out at a metre of real ground.
		equals("BTE dymaxion metres per block", 1.0, world.metersPerUnit(), 1.0E-6);

		throwsBounds("dymaxion far outside the net", () -> net.toGeo(1000000, 1000000));
		throwsBounds("BTE dymaxion far outside the net", () -> world.toGeo(2.5E7, 2.5E7));
		throwsBounds("dymaxion beyond 90 degrees", () -> net.fromGeo(0, 91));
	}

	/**
	 * Assembles the projection {@code TpllConfig} builds from the built-in defaults, without needing
	 * Minecraft or Fabric Loader on the classpath.
	 */
	private static GeographicProjection defaultWorldProjection() {
		GeographicProjection projection = new ScaleProjectionTransform(Projections.create("dymaxion"), 1.0, -1.0);
		return new ScaleProjectionTransform(projection, 7318261.522857145, 7318261.522857145);
	}

	private static void roundTrip(String name, GeographicProjection projection) {
		double[][] points = {
				{ 0, 0 }, { 2.294481, 48.858370 }, { -151.2109, -33.8651 }, { 139.6917, 35.6895 },
				{ -74.006, 40.7128 }, { 179.5, -80.0 }, { -179.5, 80.0 }, { 8.306423216, 47.051939314 },
				{ -65.0, -3.0 }, { 170.0, 65.0 }, { -170.0, -65.0 }, { 37.6173, 55.7558 }, { -0.1276, 51.5072 },
		};

		for (double[] point : points) {
			try {
				double[] projected = projection.fromGeo(point[0], point[1]);
				double[] geo = projection.toGeo(projected[0], projected[1]);
				equals(name + " round trip longitude " + point[0], point[0], geo[0], 1.0E-5);
				equals(name + " round trip latitude " + point[1], point[1], geo[1], 1.0E-5);
			} catch (OutOfProjectionBoundsException e) {
				fail(name + " round trip " + point[0] + " " + point[1] + ": " + e.getMessage());
			}
		}
	}

	// ------------------------------------------------------------------------

	private static void valid(String input, double longitude, double latitude) {
		checks++;
		LatLng parsed = CoordinateParseUtils.parseVerbatimCoordinates(input);
		if (parsed == null) {
			fail("failed to parse a valid coordinate: " + input);
			return;
		}

		if (Math.abs(parsed.getLongitude() - longitude) > PRECISION) {
			fail("wrong longitude for \"" + input + "\": expected " + longitude + ", got " + parsed.getLongitude());
		}

		if (Math.abs(parsed.getLatitude() - latitude) > PRECISION) {
			fail("wrong latitude for \"" + input + "\": expected " + latitude + ", got " + parsed.getLatitude());
		}
	}

	/**
	 * Prints the numbers the {@code /tpll} docs quote, so that they can be checked against a live
	 * server (see the {@code dymaxion()} checks for the reference values).
	 */
	private static void printReferencePositions() throws OutOfProjectionBoundsException {
		GeographicProjection world = defaultWorldProjection();
		double[][] places = {
				{ 47.051939313728376, 8.306423216110339 },  // Lucerne, the BTE server's reference
				{ 48.858370, 2.294481 },                     // the Eiffel Tower
				{ -33.8651, 151.2109 },                      // Sydney
		};

		for (double[] place : places) {
			double[] at = world.fromGeo(place[1], place[0]);
			double[] back = world.toGeo(at[0], at[1]);
			System.out.printf("  %.6f, %.6f -> X %.2f Z %.2f (back to %.6f, %.6f)%n",
					place[0], place[1], at[0], at[1], back[0], back[1]);
		}
	}

	private static void invalid(String input) {
		checks++;
		LatLng parsed = CoordinateParseUtils.parseVerbatimCoordinates(input);
		if (parsed != null) {
			fail("parsed an invalid coordinate \"" + input + "\": " + parsed);
		}
	}

	private static void equals(String what, double expected, double actual, double tolerance) {
		checks++;
		if (Math.abs(expected - actual) > tolerance) {
			fail(what + ": expected " + expected + ", got " + actual);
		}
	}

	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private static void throwsBounds(String what, ThrowingRunnable runnable) {
		checks++;
		try {
			runnable.run();
			fail(what + ": expected OutOfProjectionBoundsException");
		} catch (OutOfProjectionBoundsException expected) {
			// good
		} catch (Exception e) {
			fail(what + ": expected OutOfProjectionBoundsException, got " + e);
		}
	}

	private static void fail(String message) {
		failures++;
		System.out.println("FAIL: " + message);
	}
}
