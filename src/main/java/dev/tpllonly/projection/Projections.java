/*
 * tpll-only &mdash; standalone /tpll and /tpllcalc for modern Fabric.
 */
package dev.tpllonly.projection;

import dev.tpllonly.projection.dymaxion.BTEDymaxionProjection;
import dev.tpllonly.projection.dymaxion.ConformalDynmaxionProjection;
import dev.tpllonly.projection.dymaxion.DymaxionProjection;
import dev.tpllonly.projection.mercator.CenteredMercatorProjection;

import java.util.Locale;

/**
 * Creates the base projections that {@link dev.tpllonly.config.TpllConfig} can name.
 */
public final class Projections {

	private Projections() {
	}

	/**
	 * @param type one of {@code dymaxion} (BuildTheEarth's layout), {@code conformal_dymaxion},
	 *             {@code raw_dymaxion}, {@code equirectangular}, {@code mercator} or
	 *             {@code sinusoidal}
	 * @throws IllegalArgumentException if {@code type} isn't a known projection
	 */
	public static GeographicProjection create(String type) {
		switch (type.toLowerCase(Locale.ROOT).replace('_', '-')) {
			case "equirectangular":
			case "plate-carree":
			case "none":
				return new EquirectangularProjection();
			case "mercator":
			case "centered-mercator":
				return new CenteredMercatorProjection();
			case "sinusoidal":
				return new SinusoidalProjection();
			case "dymaxion":
			case "bte":
			case "bte-dymaxion":
			case "bte-conformal-dymaxion":
			case "airocean":
				// what BuildTheEarth's live server uses: the conformal Dymaxion net, rotated so that
				// Eurasia is centred, with north along +Y
				return new BTEDymaxionProjection();
			case "conformal-dymaxion":
				return new ConformalDynmaxionProjection();
			case "raw-dymaxion":
			case "dymaxion-raw":
				return new DymaxionProjection();
			default:
				throw new IllegalArgumentException("Unknown projection type \"" + type + "\" (expected "
						+ "dymaxion, conformal_dymaxion, raw_dymaxion, equirectangular, mercator or sinusoidal)");
		}
	}
}
