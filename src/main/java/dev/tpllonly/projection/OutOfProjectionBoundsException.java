/*
 * tpll-only &mdash; standalone /tpll and /tpllcalc for modern Fabric.
 *
 * Portions of this file are derived from TerraPlusPlus
 * (https://github.com/BuildTheEarth/terraplusplus), Copyright (c) 2020 BuildTheEarth.net,
 * distributed under the MIT license.
 */
package dev.tpllonly.projection;

/**
 * Thrown when a point falls outside of the range a projection can represent.
 *
 * <p>Like the original, this is a stack-trace-less, shareable exception: it gets thrown for chunks
 * near the edges of the world, so allocating a stack trace every time would be wasteful.
 */
public final class OutOfProjectionBoundsException extends Exception {

	private static final OutOfProjectionBoundsException INSTANCE = new OutOfProjectionBoundsException();

	public static OutOfProjectionBoundsException get() {
		return INSTANCE;
	}

	/**
	 * @throws OutOfProjectionBoundsException if {@code |x| > maxX || |y| > maxY}
	 */
	public static void checkInRange(double x, double y, double maxX, double maxY)
			throws OutOfProjectionBoundsException {
		if (Math.abs(x) > maxX || Math.abs(y) > maxY) {
			throw OutOfProjectionBoundsException.get();
		}
	}

	/**
	 * @throws OutOfProjectionBoundsException if {@code |longitude| > 180 || |latitude| > 90}
	 */
	public static void checkLongitudeLatitudeInRange(double longitude, double latitude)
			throws OutOfProjectionBoundsException {
		checkInRange(longitude, latitude, 180, 90);
	}

	private OutOfProjectionBoundsException() {
		super(null, null, false, false);
	}
}
