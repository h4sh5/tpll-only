/*
 * tpll-only &mdash; standalone /tpll and /tpllcalc for modern Fabric.
 *
 * Portions of this file are derived from TerraPlusPlus
 * (https://github.com/BuildTheEarth/terraplusplus), Copyright (c) 2020 BuildTheEarth.net,
 * distributed under the MIT license.
 */
package dev.tpllonly.projection.transform;

import dev.tpllonly.projection.GeographicProjection;
import dev.tpllonly.projection.OutOfProjectionBoundsException;

/**
 * Scales the wrapped projection's projected space, multiplying X and Y by their respective scale
 * factors. This is what turns "one map unit = one degree" into TerraPlusPlus'
 * "one block = 1/100000th of a degree".
 */
public class ScaleProjectionTransform extends ProjectionTransform {

	private final double x;
	private final double y;

	public ScaleProjectionTransform(GeographicProjection delegate, double x, double y) {
		super(delegate);
		if (!Double.isFinite(x) || !Double.isFinite(y)) {
			throw new IllegalArgumentException("Projection scales should be finite");
		}
		if (x == 0 || y == 0) {
			throw new IllegalArgumentException("Projection scale cannot be 0!");
		}
		this.x = x;
		this.y = y;
	}

	@Override
	public double[] toGeo(double x, double y) throws OutOfProjectionBoundsException {
		return this.delegate.toGeo(x / this.x, y / this.y);
	}

	@Override
	public double[] fromGeo(double longitude, double latitude) throws OutOfProjectionBoundsException {
		double[] p = this.delegate.fromGeo(longitude, latitude);
		p[0] *= this.x;
		p[1] *= this.y;
		return p;
	}

	@Override
	public boolean upright() {
		return (this.y < 0) ^ this.delegate.upright();
	}

	@Override
	public double[] bounds() {
		double[] b = this.delegate.bounds();
		double x0 = b[0] * this.x;
		double y0 = b[1] * this.y;
		double x1 = b[2] * this.x;
		double y1 = b[3] * this.y;
		return new double[]{
				Math.min(x0, x1), Math.min(y0, y1),
				Math.max(x0, x1), Math.max(y0, y1)
		};
	}

	@Override
	public double metersPerUnit() {
		return this.delegate.metersPerUnit() / Math.sqrt((this.x * this.x + this.y * this.y) / 2);
	}

	@Override
	public String toString() {
		return "Scale (" + this.delegate + ") by " + this.x + ", " + this.y;
	}
}
