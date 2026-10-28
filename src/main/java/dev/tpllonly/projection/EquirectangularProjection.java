/*
 * tpll-only &mdash; standalone /tpll and /tpllcalc for modern Fabric.
 *
 * Portions of this file are derived from TerraPlusPlus
 * (https://github.com/BuildTheEarth/terraplusplus), Copyright (c) 2020 BuildTheEarth.net,
 * distributed under the MIT license.
 */
package dev.tpllonly.projection;

/**
 * The equirectangular projection: map coordinates are simply longitude and latitude, so the world
 * is a flat, unscaled rectangle. This is TerraPlusPlus' default projection (scaled by 100000).
 *
 * @see <a href="https://en.wikipedia.org/wiki/Equirectangular_projection">Wikipedia's article on
 * the equirectangular projection</a>
 */
public class EquirectangularProjection implements GeographicProjection {

	@Override
	public double[] toGeo(double x, double y) throws OutOfProjectionBoundsException {
		OutOfProjectionBoundsException.checkLongitudeLatitudeInRange(x, y);
		return new double[]{ x, y };
	}

	@Override
	public double[] fromGeo(double longitude, double latitude) throws OutOfProjectionBoundsException {
		OutOfProjectionBoundsException.checkLongitudeLatitudeInRange(longitude, latitude);
		return new double[]{ longitude, latitude };
	}

	@Override
	public double metersPerUnit() {
		return 100000;
	}

	@Override
	public String toString() {
		return "Equirectangular";
	}
}
