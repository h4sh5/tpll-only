/*
 * tpll-only &mdash; standalone /tpll and /tpllcalc for modern Fabric.
 *
 * Portions of this file are derived from TerraPlusPlus
 * (https://github.com/BuildTheEarth/terraplusplus), Copyright (c) 2020 BuildTheEarth.net,
 * distributed under the MIT license.
 */
package dev.tpllonly.projection;

/**
 * The handful of physical constants the projections need.
 */
public final class TerraConstants {

	/** the earth's circumference along the equator, in meters */
	public static final double EARTH_CIRCUMFERENCE = 40075017;

	/** the earth's circumference through the poles, in meters */
	public static final double EARTH_POLAR_CIRCUMFERENCE = 40008000;

	private TerraConstants() {
	}
}
