/*
 * tpll-only &mdash; standalone /tpll and /tpllcalc for modern Fabric.
 *
 * Portions of this file are derived from TerraPlusPlus
 * (https://github.com/BuildTheEarth/terraplusplus), Copyright (c) 2020 BuildTheEarth.net,
 * distributed under the MIT license.
 */
package dev.tpllonly.geo;

import dev.tpllonly.projection.OutOfProjectionBoundsException;

/**
 * A latitude/longitude pair on Earth.
 *
 * <p>Trimmed-down port of {@code net.buildtheearth.terraplusplus.util.geo.LatLng}: the
 * {@link net.buildtheearth.terraplusplus.projection.GeographicProjection} interop and the
 * {@code Comparable} bits TerraPlusPlus used for its map GUI are dropped, since this mod only ever
 * converts a point to and from world coordinates.
 */
public class LatLng {

	public static final double MIN_LAT = -90;
	public static final double MAX_LAT = 90;
	public static final double MIN_LNG = -180;
	public static final double MAX_LNG = 180;

	private final double latitude;
	private final double longitude;

	/**
	 * @throws NumberFormatException        if either part isn't a number
	 * @throws OutOfBoundsException if either part is outside the range of a real coordinate
	 */
	public LatLng(String lat, String lng) throws NumberFormatException {
		this(Double.parseDouble(lat), Double.parseDouble(lng));
	}

	/**
	 * @param lat latitude in degrees
	 * @param lng longitude in degrees
	 * @throws NumberFormatException if a part isn't a number
	 * @throws OutOfBoundsException  if a part is out of range
	 */
	public LatLng(double lat, double lng) throws NumberFormatException {
		if (Double.isNaN(lat) || Double.isNaN(lng)) {
			throw new NumberFormatException("Invalid coordinate: " + lat + ", " + lng);
		}

		if (lat < MIN_LAT || lat > MAX_LAT || lng < MIN_LNG || lng > MAX_LNG) {
			throw new OutOfBoundsException();
		}

		this.latitude = lat;
		this.longitude = lng;
	}

	public double getLatitude() {
		return this.latitude;
	}

	public double getLongitude() {
		return this.longitude;
	}

	/** kept under the TerraPlusPlus name too, since that's the API this mirrors */
	public double getLat() {
		return this.latitude;
	}

	/** kept under the TerraPlusPlus name too, since that's the API this mirrors */
	public double getLng() {
		return this.longitude;
	}

	/**
	 * @return the coordinate rounded to 6 decimals (~1&nbsp;m), which is how TerraPlusPlus shows and
	 * copies coordinates
	 */
	public String roundTo6decimals() {
		return String.format("%.6f, %.6f", this.latitude, this.longitude);
	}

	@Override
	public String toString() {
		return this.getLatitude() + ", " + this.getLongitude();
	}

	@Override
	public int hashCode() {
		long temp = Double.doubleToLongBits(this.latitude);
		int hash = (int) (temp ^ (temp >>> 32));

		temp = Double.doubleToLongBits(this.longitude);
		hash = 31 * hash + (int) (temp ^ (temp >>> 32));

		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}

		if (!(obj instanceof LatLng)) {
			return false;
		}

		LatLng a = (LatLng) obj;
		return Math.abs(this.latitude - a.latitude) < 1.0E-9
				&& Math.abs(this.longitude - a.longitude) < 1.0E-9;
	}

	/**
	 * @return true if the pair lies within the range of a real coordinate
	 */
	public static boolean inRange(double lat, double lng) {
		return lat <= MAX_LAT && lat >= MIN_LAT && lng <= MAX_LNG && lng >= MIN_LNG;
	}

	/** thrown when a coordinate is numerically fine but outside the globe */
	public static class OutOfBoundsException extends NumberFormatException {
		private static final long serialVersionUID = 1L;

		public OutOfBoundsException() {
			super("Coordinate out of range: latitude must be within [-90, 90] and longitude within [-180, 180]");
		}
	}
}
