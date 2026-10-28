/*
 * tpll-only &mdash; standalone /tpll and /tpllcalc for modern Fabric.
 *
 * Portions of this file are derived from TerraPlusPlus
 * (https://github.com/BuildTheEarth/terraplusplus), Copyright (c) 2020 BuildTheEarth.net,
 * distributed under the MIT license.
 */
package dev.tpllonly.projection.transform;

import dev.tpllonly.projection.GeographicProjection;

/**
 * Base class for projections that wrap another projection.
 */
public abstract class ProjectionTransform implements GeographicProjection {

	protected final GeographicProjection delegate;

	protected ProjectionTransform(GeographicProjection delegate) {
		if (delegate == null) {
			throw new IllegalArgumentException("Delegate projection cannot be null");
		}
		this.delegate = delegate;
	}

	public GeographicProjection delegate() {
		return this.delegate;
	}
}
