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
 * Translates the wrapped projection's projected space, so that 0&deg;/0&deg; lands somewhere other
 * than the world origin. Handy for worlds where the real world has been built off-centre.
 */
public class OffsetProjectionTransform extends ProjectionTransform {

	private final double dx;
	private final double dy;

	public OffsetProjectionTransform(GeographicProjection delegate, double dx, double dy) {
		super(delegate);
		if (!Double.isFinite(dx) || !Double.isFinite(dy)) {
			throw new IllegalArgumentException("Projection offsets should be finite");
		}
		this.dx = dx;
		this.dy = dy;
	}

	@Override
	public double[] toGeo(double x, double y) throws OutOfProjectionBoundsException {
		return this.delegate.toGeo(x - this.dx, y - this.dy);
	}

	@Override
	public double[] fromGeo(double longitude, double latitude) throws OutOfProjectionBoundsException {
		double[] pos = this.delegate.fromGeo(longitude, latitude);
		pos[0] += this.dx;
		pos[1] += this.dy;
		return pos;
	}

	@Override
	public double metersPerUnit() {
		return this.delegate.metersPerUnit();
	}

	@Override
	public double[] bounds() {
		double[] b = this.delegate.bounds();
		b[0] += this.dx;
		b[1] += this.dy;
		b[2] += this.dx;
		b[3] += this.dy;
		return b;
	}

	@Override
	public String toString() {
		return "Offset (" + this.delegate + ") by " + this.dx + ", " + this.dy;
	}
}
