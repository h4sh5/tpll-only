/*
 * tpll-only &mdash; standalone /tpll and /tpllcalc for modern Fabric.
 *
 * Portions of this file are derived from TerraPlusPlus
 * (https://github.com/BuildTheEarth/terraplusplus), Copyright (c) 2020 BuildTheEarth.net,
 * distributed under the MIT license.
 */
package dev.tpllonly.projection.mercator;

import dev.tpllonly.projection.GeographicProjection;
import dev.tpllonly.projection.OutOfProjectionBoundsException;
import dev.tpllonly.projection.TerraConstants;

/**
 * The Mercator projection, normalized to the range -1..1 on both axes.
 *
 * <p>Unlike {@link EquirectangularProjection}, north points towards negative Z here, and latitudes
 * beyond &plusmn;85.05&deg; (the Web Mercator limit) cannot be represented.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Mercator_projection">Wikipedia's article on the
 * Mercator projection</a>
 */
public class CenteredMercatorProjection implements GeographicProjection {

	/** the highest latitude Web Mercator can represent, in degrees (~85.0511) */
	public static final double LIMIT_LATITUDE =
			Math.toDegrees(2 * Math.atan(Math.pow(Math.E, Math.PI)) - Math.PI / 2);

	@Override
	public double[] toGeo(double x, double y) throws OutOfProjectionBoundsException {
		OutOfProjectionBoundsException.checkInRange(x, y, 1, 1);
		return new double[]{
				x * 180.0,
				Math.toDegrees(Math.atan(Math.exp(-y * Math.PI)) * 2 - Math.PI / 2)
		};
	}

	@Override
	public double[] fromGeo(double longitude, double latitude) throws OutOfProjectionBoundsException {
		OutOfProjectionBoundsException.checkInRange(longitude, latitude, 180, LIMIT_LATITUDE);
		return new double[]{
				longitude / 180.0,
				-(Math.log(Math.tan((Math.PI / 2 + Math.toRadians(latitude)) / 2))) / Math.PI
		};
	}

	@Override
	public double[] bounds() {
		return new double[]{ -1, -1, 1, 1 };
	}

	@Override
	public double metersPerUnit() {
		// accurate at about 30 degrees
		return Math.cos(Math.toRadians(30)) * TerraConstants.EARTH_CIRCUMFERENCE / 2;
	}

	@Override
	public boolean upright() {
		return true;
	}

	@Override
	public String toString() {
		return "Mercator";
	}
}
