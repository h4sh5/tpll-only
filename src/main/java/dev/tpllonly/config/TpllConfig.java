/*
 * tpll-only &mdash; standalone /tpll and /tpllcalc for modern Fabric.
 */
package dev.tpllonly.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.tpllonly.projection.GeographicProjection;
import dev.tpllonly.projection.Projections;
import dev.tpllonly.projection.transform.OffsetProjectionTransform;
import dev.tpllonly.projection.transform.ScaleProjectionTransform;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * The mod's configuration, read from <code>config/tpll-only.json</code>.
 *
 * <p>The file is created with the built-in defaults &mdash; the setup BuildTheEarth's live server
 * uses: the conformal Dymaxion net scaled by 7318261.522857145 with north towards negative Z, so
 * <code>/tpll 47.051939 8.306423</code> lands within a few blocks of where the same command lands
 * there. The file is re-read automatically whenever it changes on disk, so edits apply to the next
 * <code>/tpll</code> without a restart.
 */
public final class TpllConfig {

	public static final String FILE_NAME = "tpll-only.json";

	private static final Logger LOGGER = LoggerFactory.getLogger("tpll-only/config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public ProjectionConfig projection = new ProjectionConfig();
	public ElevationConfig elevation = new ElevationConfig();
	public TeleportConfig teleport = new TeleportConfig();

	private TpllConfig() {
	}

	/** A config together with the projection built from it. */
	private record Snapshot(TpllConfig config, GeographicProjection projection, long modified) {
	}

	private static volatile Snapshot current = load();

	/**
	 * @return the current configuration, reloaded from disk if the file changed
	 */
	private static Snapshot snapshot() {
		Snapshot snapshot = current;
		Path file = configFile();
		long modified = lastModified(file);
		if (modified != snapshot.modified) {
			snapshot = load();
			current = snapshot;
		}
		return snapshot;
	}

	public static TpllConfig get() {
		return snapshot().config;
	}

	/**
	 * @return the projection to convert world positions with, matching the current configuration
	 */
	public static GeographicProjection projection() {
		return snapshot().projection;
	}

	public static Path configFile() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/**
	 * (Re)reads the configuration file, writing out the defaults if it doesn't exist yet. Any
	 * unreadable or nonsensical value falls back to the default rather than breaking the command.
	 */
	private static Snapshot load() {
		TpllConfig config = new TpllConfig();
		Path file = configFile();
		long modified = lastModified(file);

		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				TpllConfig parsed = GSON.fromJson(reader, TpllConfig.class);
				if (parsed != null) {
					if (parsed.projection == null) {
						parsed.projection = new ProjectionConfig();
					}
					if (parsed.elevation == null) {
						parsed.elevation = new ElevationConfig();
					}
					if (parsed.teleport == null) {
						parsed.teleport = new TeleportConfig();
					}
					config = parsed;
				}
			} catch (IOException | RuntimeException e) {
				LOGGER.error("Unable to read {}, falling back to defaults", file, e);
			}
		} else {
			try {
				Files.createDirectories(file.getParent());
				try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
					GSON.toJson(config, writer);
				}
				modified = lastModified(file);
				LOGGER.info("Wrote default configuration to {}", file);
			} catch (IOException e) {
				LOGGER.warn("Unable to write {}", file, e);
			}
		}

		return new Snapshot(config, buildProjection(config.projection), modified);
	}

	private static GeographicProjection buildProjection(ProjectionConfig config) {
		try {
			GeographicProjection projection = Projections.create(config.type);

			if (config.north_is_negative_z) {
				// equirectangular (and sinusoidal) point north towards +Z; flip the Z axis for
				// servers that would rather keep vanilla's "north is -Z".
				projection = new ScaleProjectionTransform(projection, 1.0, -1.0);
			}

			projection = new ScaleProjectionTransform(projection, config.scale_x, config.scale_y);

			if (config.offset_x != 0.0 || config.offset_z != 0.0) {
				projection = new OffsetProjectionTransform(projection, config.offset_x, config.offset_z);
			}

			LOGGER.info("Using projection {}", projection);
			return projection;
		} catch (RuntimeException | ExceptionInInitializerError e) {
			LOGGER.error("Invalid projection configuration, falling back to equirectangular x 100000", e);
			return new ScaleProjectionTransform(new EquirectangularFallback(), 100000.0, 100000.0);
		}
	}

	private static long lastModified(Path file) {
		try {
			return Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : -1L;
		} catch (IOException e) {
			return -1L;
		}
	}

	// ------------------------------------------------------------------------

	/**
	 * Stand-in used when the configured projection can't be built.
	 */
	private static final class EquirectangularFallback extends dev.tpllonly.projection.EquirectangularProjection {
		@Override
		public String toString() {
			return "Equirectangular (fallback)";
		}
	}

	/**
	 * How world coordinates map to latitude/longitude.
	 */
	public static final class ProjectionConfig {
		/**
		 * One of {@code dymaxion} (BuildTheEarth's own layout, the default),
		 * {@code conformal_dymaxion}, {@code raw_dymaxion}, {@code equirectangular},
		 * {@code mercator} or {@code sinusoidal}
		 */
		public String type = "dymaxion";

		/**
		 * How far the world is stretched: the projection's own units are multiplied by this to get
		 * blocks. The default is the value BuildTheEarth uses for its Dymaxion world &mdash; the
		 * earth's radius (40075017 / 2&pi;) times the projection's 1.1473979730192934 &mdash; which
		 * spreads the globe over about 35 million blocks and leaves a block worth roughly a metre of
		 * real ground.
		 */
		public double scale_x = 7318261.522857145;

		/** the same, for the Z axis */
		public double scale_y = 7318261.522857145;

		/**
		 * The Dymaxion net (like equirectangular and sinusoidal) points north towards positive Z.
		 * Set this to true to instead use Minecraft's own cardinal directions, where north is
		 * negative Z, which is what BuildTheEarth's server does.
		 */
		public boolean north_is_negative_z = true;

		/** added to the X coordinate; use this to shift the world away from 0&deg;/0&deg; */
		public double offset_x = 0.0;

		/** added to the Z coordinate */
		public double offset_z = 0.0;

		double scaleForAxis(boolean horizontal) {
			double scale = horizontal ? this.scale_x : this.scale_y;
			return Double.isFinite(scale) && scale != 0.0 ? scale : 100000.0;
		}
	}

	/**
	 * Where vertically the teleport lands.
	 */
	public static final class ElevationConfig {

		/**
		 * <ul>
		 *     <li>{@code surface}: arrive on top of the highest block at that X/Z (loading the chunk
		 *     first if it isn't loaded), falling back to {@link #default_altitude} when that isn't
		 *     possible &mdash; this is the default</li>
		 *     <li>{@code default} or {@code fixed}: always use {@link #default_altitude}, which is
		 *     high enough to be above any terrain</li>
		 *     <li>{@code require}: refuse to guess, the altitude argument is then mandatory</li>
		 * </ul>
		 */
		public String mode = "surface";

		/**
		 * Y level used when the terrain can't be consulted (or {@link #mode} says not to look). 300
		 * is above even the tallest vanilla mountains, so a teleport always arrives in open air
		 * rather than buried in stone.
		 */
		public double default_altitude = 300.0;

		/** added to the terrain height (e.g. 0.5 to hover slightly above tall grass) */
		public double surface_offset = 0.0;

		/** which heightmap decides "the surface", see {@link #heightmapType()} */
		public String heightmap = "motion_blocking";

		/**
		 * Whether {@code /tpll} may load unloaded chunks to find the surface. With this off,
		 * teleports into unloaded chunks use {@link #default_altitude} instead.
		 */
		public boolean load_chunks = true;

		public boolean usesSurface() {
			return !"default".equalsIgnoreCase(this.mode) && !"fixed".equalsIgnoreCase(this.mode);
		}

		public boolean requiresAltitude() {
			return "require".equalsIgnoreCase(this.mode) || "required".equalsIgnoreCase(this.mode);
		}

		/**
		 * @return the configured heightmap, or MOTION_BLOCKING if the name isn't recognised
		 */
		public Heightmap.Types heightmapType() {
			String name = this.heightmap == null ? "" : this.heightmap.toLowerCase(Locale.ROOT);
			switch (name) {
				case "ocean_floor":
					return Heightmap.Types.OCEAN_FLOOR;
				case "ocean_floor_wg":
					return Heightmap.Types.OCEAN_FLOOR_WG;
				case "world_surface":
					return Heightmap.Types.WORLD_SURFACE;
				case "world_surface_wg":
					return Heightmap.Types.WORLD_SURFACE_WG;
				case "motion_blocking_no_particles":
					return Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
				default:
					if (!name.isEmpty() && !"motion_blocking".equals(name)) {
						LOGGER.warn("Unknown heightmap \"{}\", using motion_blocking instead", this.heightmap);
					}
					return Heightmap.Types.MOTION_BLOCKING;
			}
		}
	}

	/**
	 * Who may teleport players other than themselves.
	 */
	public static final class TeleportConfig {
		/**
		 * One of {@code anyone}, {@code moderator} (level 1), {@code gamemaster} (level 2),
		 * {@code admin} (level 3) or {@code owner} (level 4).
		 */
		public String target_permission = "gamemaster";

		/** send teleported players a copy of the teleport message */
		public boolean announce_target = true;

		/**
		 * @return the permission needed to target other players, or {@code null} for everyone
		 */
		public PermissionCheck targetPermissionCheck() {
			switch (this.target_permission == null ? "" : this.target_permission.toLowerCase(Locale.ROOT)) {
				case "anyone":
				case "none":
					return null;
				case "moderator":
					return Commands.LEVEL_MODERATORS;
				case "admin":
					return Commands.LEVEL_ADMINS;
				case "owner":
					return Commands.LEVEL_OWNERS;
				case "gamemaster":
				default:
					return Commands.LEVEL_GAMEMASTERS;
			}
		}
	}

	/**
	 * @return {@code null} if the projection can map the given map coordinates, otherwise a
	 * player-readable explanation of why it can't
	 */
	public static String checkBounds(GeographicProjection projection, double x, double z) {
		double[] bounds = projection.bounds();
		if (x < bounds[0] || x > bounds[2] || z < bounds[1] || z > bounds[3]) {
			return ChatFormatting.YELLOW + "That location is outside the map this world covers"
					+ ChatFormatting.GRAY + " (X " + fmt(bounds[0]) + " to " + fmt(bounds[2])
					+ ", Z " + fmt(bounds[1]) + " to " + fmt(bounds[3]) + ").";
		}
		return null;
	}

	private static String fmt(double value) {
		return Long.toString((long) value);
	}
}
