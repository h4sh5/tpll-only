/*
 * tpll-only &mdash; standalone /tpll and /tpllcalc for modern Fabric.
 *
 * Portions of this file are derived from TerraPlusPlus
 * (https://github.com/BuildTheEarth/terraplusplus), Copyright (c) 2020 BuildTheEarth.net,
 * distributed under the MIT license.
 */
package dev.tpllonly.projection;

/**
 * A mapping between the geographic space (latitude/longitude, in degrees) and the projected space
 * (the flat Minecraft world, in block coordinates).
 *
 * <p>Trimmed-down port of {@code net.buildtheearth.terraplusplus.projection.GeographicProjection}:
 * only the parts needed to convert positions are kept (no map/tissot/azimuth helpers, no Jackson
 * (de)serialization).
 */
public interface GeographicProjection {

	/**
	 * Converts map coordinates to geographic coordinates.
	 *
	 * @param x map coordinate along the world's X axis
	 * @param y map coordinate along the world's Z axis
	 * @return {@code {longitude, latitude}} in degrees
	 */
	double[] toGeo(double x, double y) throws OutOfProjectionBoundsException;

	/**
	 * Converts geographic coordinates to map coordinates.
	 *
	 * @param longitude longitude, in degrees
	 * @param latitude  latitude, in degrees
	 * @return {@code {x, y}} map coordinates, where {@code y} is used for the world's Z axis
	 */
	double[] fromGeo(double longitude, double latitude) throws OutOfProjectionBoundsException;

	/**
	 * @return an estimation of the scale of this projection, in meters per map unit (block)
	 */
	double metersPerUnit();

	/**
	 * @return {@code {min X, min Y, max X, max Y}} of the projected space
	 */
	default double[] bounds() {
		try {
			double[] bounds = {
					this.fromGeo(-180, 0)[0],
					this.fromGeo(0, -90)[1],
					this.fromGeo(180, 0)[0],
					this.fromGeo(0, 90)[1]
			};

			if (bounds[0] > bounds[2]) {
				double t = bounds[0];
				bounds[0] = bounds[2];
				bounds[2] = t;
			}

			if (bounds[1] > bounds[3]) {
				double t = bounds[1];
				bounds[1] = bounds[3];
				bounds[3] = t;
			}

			return bounds;
		} catch (OutOfProjectionBoundsException e) {
			return new double[]{ 0, 0, 1, 1 };
		}
	}

	/**
	 * @return whether north points towards negative Z in the projected space
	 */
	default boolean upright() {
		try {
			return this.fromGeo(0, 90)[1] <= this.fromGeo(0, -90)[1];
		} catch (OutOfProjectionBoundsException e) {
			return false;
		}
	}
}
