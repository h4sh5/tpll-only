# tpll-only

`/tpll` and `/tpllcalc` for modern Fabric &mdash; the TerraPlusPlus teleport commands, without
TerraPlusPlus' world generation, datasets or dependencies.

Run against **Minecraft 26.3** or **1.21.11** from the same sources.

It works in singleplayer (the integrated server runs the same code) and on dedicated servers. On a
multiplayer server only the server needs the mod: plain clients get the commands too, because
everything the mod sends is a vanilla chat component.

```
/tpll 48.858370 2.294481            "take me to where Paris would be"
/tpll 48.858370 2.294481 200        ... at Y=200
/tpll @a 35.6895 139.6917           everyone to where Tokyo would be
/tpllcalc 48,858370 2,294481        just tell me the numbers, don't move me
```

Out of the box the mod uses the projection BuildTheEarth runs its own server on, so the coordinates
you get are the ones that command gives there &mdash; see [projection](#projection).

## What it does

`/tpll <latitude> <longitude> [altitude]` turns a geographic position into world coordinates using
the configured [projection](#projection) and teleports you there &mdash; into *your* world, whatever
terrain it has. Nothing is generated from a map: the command only moves you.

`/tpllcalc` does the same maths and reports the result **without teleporting**. It works from the
server console too, where it just prints the numbers. It is only about where on the map something
lands, so it doesn't go looking at the terrain for a Y either &mdash; it echoes the altitude you
gave it, or the height you are standing at. It answers with three links:

In chat the answer looks like this (a server console gets the same text, without the clickable
parts):

```
tpllcalc for Steve in minecraft:overworld
Latitude / longitude: 47.051939 8.306423  [ copy ]
World position: 3235497.40 73.00 -4704415.43  (x y z; /tpll picks the surface itself)
  [ teleport ]
```

| clicking | does |
|---|---|
| the coordinates | copies `47.051939 8.306423` |
| `[ copy ]` | copies the coordinates **and** the altitude |
| the world position | copies `3235497.40 73.00 -4704415.43` |
| `[ teleport ]` | runs `/tpll 47.051939 8.306423` |

Run without coordinates, `/tpllcalc` goes the other way:
`Steve is at 48.858370 2.294481 (X 2847558, Z -5051340) [ teleport here ]`, where the numbers copy
to the clipboard and `[ teleport here ]` fills the command into your chat box.

Both commands speak the coordinate syntax TerraPlusPlus always did
([`CoordinateParseUtils`](src/main/java/dev/tpllonly/geo/CoordinateParseUtils.java) is a port of
it), including things modern vanilla `/tp` won't accept:

```
48.858370 2.294481          decimal degrees
48.858370, 2.294481         with a separator between the halves
48,858370 2,294481          comma as the decimal separator
N 48.858370 E 2.294481      hemisphere letters, spaced or not
48°51'30"N 2°21'42"E        degrees / minutes / seconds
48°51'30.5''n 2°21'42.3''e  ... with seconds and lowercase letters
33.452242° -112.070295°     trailing degree signs
```

A player name or selector may go in front (`/tpll Steve 48.858 2.294`, `/tpll @a 48.858 2.294`),
and an altitude at the end. Teleporting *other* players requires the permission configured by
`teleport.target_permission`; teleporting yourself needs none.

## Elevation

By default `/tpll` puts you on **the highest block at that X/Z**, so you arrive standing on the
ground instead of buried in it &mdash; the height comes from the world's own heightmap, which makes
it exact for the world you are actually in. If the destination chunk isn't loaded it is requested
and the teleport happens a few ticks later (the same path vanilla's `/tp` into unloaded chunks
takes); if it can't be loaded you land at `elevation.default_altitude` (300, above even the tallest
vanilla mountains, so at least in open air) and the message says so.

Set `elevation.mode` to `default` for that fixed Y all the time &mdash; no chunk loads at all &mdash;
or to `require` if you want the altitude argument to be mandatory. An altitude given on the command
line always wins over the terrain.

## Configuration

`config/tpll-only.json`, written with defaults on first launch and reloaded when saved (the file is
watched, so a `/reload` isn't needed). Hand-written JSON with comments is fine: leading `//`
comments and trailing commas are accepted.

### projection

| key | default | meaning |
|---|---|---|
| `type` | `dymaxion` | `dymaxion`, `conformal_dymaxion`, `raw_dymaxion`, `equirectangular`, `mercator` or `sinusoidal` |
| `scale_x` | `7318261.522857145` | how far the projection's own units are stretched to make blocks |
| `scale_y` | `7318261.522857145` | the same, for the Z axis |
| `north_is_negative_z` | `true` | the projections point north towards **+Z**; `true` flips that to vanilla's north = &minus;Z |
| `offset_x`, `offset_z` | `0.0` | shift the world away from wherever the projection puts 0&deg;/0&deg; |

### The Dymaxion family

`dymaxion` is BuildTheEarth's own layout: the globe projected onto an icosahedron (Buckminster
Fuller's Dymaxion map) whose twenty faces are unfolded into one flat net, with the distortion
smoothed out per-face so angles are close to being preserved, then rotated so Eurasia and Africa
centre the map and the Bering Strait isn't torn in half. It is the projection the live BTE server
answers with, and the defaults above are its settings: the scale is the earth's radius
(40075017 / 2&pi;) times the projection's own 1.1473979730192934, which spreads the world over
about 35 million blocks and leaves one block worth about one metre of real ground.

```
/tpll 47.051939313728376, 8.306423216110339     (Lucerne)
        this mod         X 3235497.40  Z -4704415.43
        BTE's server     X 3235623.70  Z -4704424.71     0.004% apart

/tpll 48.858370 2.294481                        (Paris)   X 2847557.88  Z -5051340.18
/tpll -33.8651 151.2109                         (Sydney)  X 20000555.55  Z -2224370.26
```

The net isn't a rectangle, so most of the world's *bounding box* is empty: the bounds check tells
you when a place falls in one of the gaps rather than teleporting you to nonsense. `geo/` and
`projection/dymaxion/` are ports of TerraPlusPlus' own implementations, and the per-face distortion
field is the same 256&times;256 grid of offsets it ships (`conformal.bin`, 518&nbsp;KB, big-endian
doubles, decompressed out of TerraPlusPlus' `conformal.lzma` so that no LZMA decoder is needed).

`conformal_dymaxion` is that same net without BuildTheEarth's rotation, and `raw_dymaxion` the plain
icosahedron projection without the distortion correction.

```
"type": "equirectangular", "scale_x": 100000, "scale_y": 100000
```

is the classic TerraPlusPlus setup &mdash; one degree is 100&nbsp;000 blocks, so Paris lands at
X&nbsp;229448, Z&nbsp;4885837 (`north_is_negative_z` still puts north towards &minus;Z, as vanilla
expects). `mercator` (Web Mercator, the projection tile maps use) and `sinusoidal` are provided for
maps that were built with them, and `plate-carree` / `centered-mercator` are accepted as aliases.
All of them keep TerraPlusPlus' bounds checks: you get "those coordinates can't be mapped onto this
world" rather than a nonsense teleport, and Mercator refuses latitudes beyond &plusmn;85.05&deg;.

### elevation

| key | default | meaning |
|---|---|---|
| `mode` | `surface` | `surface` (land on the highest block), `default` / `fixed` (always `default_altitude`) or `require` (altitude mandatory) |
| `default_altitude` | `300.0` | the Y level used when the terrain isn't or can't be consulted |
| `surface_offset` | `0.0` | added to the sampled terrain height |
| `heightmap` | `motion_blocking` | what counts as "the highest block": `motion_blocking`, `ocean_floor`, `world_surface`, `*_wg`, `motion_blocking_no_particles` |
| `load_chunks` | `true` | allow loading the destination chunk to find its highest block |

### teleport

| key | default | meaning |
|---|---|---|
| `target_permission` | `gamemaster` | permission needed to teleport *other* players: `anyone`, `moderator`, `gamemaster`, `admin`, `owner`, `none` |
| `announce_target` | `true` | tell the destination ("You have arrived at 48.858370, 2.294481") |

## Building

Requires **JDK 25** and the Fabric Loom toolchain (Gradle downloads everything else, including the
matching JDK for 1.21.x):

```sh
./gradlew build -Pmc=26.3          # -> build/libs/tpll-only-26.3-*.jar
./gradlew build -Pmc=1.21.11       # -> build/libs/tpll-only-1.21.11-*.jar
./gradlew build                    # uses the mc= key in gradle.properties (26.3)
```

Loom remaps against the official Mojang mappings for each version; the per-version
`yarn_mappings`/`loader_version`/`fabric_commands_version`/`minecraft_range` values live in
[`versions/`](versions).

Supported Minecraft versions are declared per build as `>=26.3 <26.4` (respectively `>=1.21.11
<1.21.12`) &mdash; only what has actually been booted is claimed.

### Verifying

```sh
./gradlew geoTest             # coordinate parser + projections + config, plain JVM
./gradlew verifyNoClientRefs  # no net.minecraft.client references in the shipped classes
./gradlew check               # both of the above
tools/server-test.sh 26.3     # boots a dedicated server and drives both commands from its console
```

`tools/server-test.sh` boots a dedicated server through Loom's `runServer` in a throwaway world,
feeds the commands to the server console over a FIFO, and leaves the transcript in
`out/server-test-<version>.log`. (`runServer` rather than a bare `java` command: only Loom knows the
JVM properties that make Fabric load mods in a dev runtime &mdash; a hand-built classpath quietly
gives you a plain vanilla server with no commands in it.)

## Dependencies

At runtime the jar needs **Fabric Loader, Minecraft, and one Fabric API module** &mdash;
`fabric-command-api-v2`, which is part of the normal Fabric API bundle &mdash; and nothing else: no
TerraPlusPlus and no GeoAPI. Gson is used for the config file, but Minecraft already ships it, so it
isn't a dependency that has to be installed. Everything else is vendored:

| code | origin |
|---|---|
| `geo/CoordinateParseUtils`, `geo/LatLng` | TerraPlusPlus (which adapted it from GBIF's pipelines), MIT |
| `projection/*`, `projection/dymaxion/*` | TerraPlusPlus, MIT |
| `projection/dymaxion/conformal.bin` | TerraPlusPlus' `conformal.lzma`, decompressed, MIT |
| `config/TpllConfig` | written here |

TerraPlusPlus' own JUnit suite is kept under
[`src/test/java/net/buildtheearth/terraplusplus`](src/test/java/net/buildtheearth/terraplusplus) as
a reference for the behaviour the port has to match. The checks that run in CI live in
[`dev.tpllonly.GeoTests`](src/test/java/dev/tpllonly/GeoTests.java) so that the test task needs no
test framework.

## What this is not

* **No TerraPlusPlus world.** There is no terrain from a heightmap dataset, no biome lookup, no
  `TPLLBiomeProvider`. Teleporting to "Berlin" means the X/Z where Berlin would be, in a world that
  was generated normally.
* **No real-world elevation.** See [elevation](#elevation): the Y comes from *your* world's terrain,
  not from the altitude of the real location (TerraPlusPlus read a SRTM dataset for that).
* **No map, no colour/biome palettes, no `/tpllgen`, no chunk-cache commands.**
* **Projections are limited to the six above**; TerraPlusPlus' azimuthal projection isn't ported.
* **A fraction of a percent off, on purpose.** The Dymaxion defaults land within ~130 blocks of the
  same command on BuildTheEarth's live server at coordinates 3.2 million blocks out (0.004%), which
  is the difference between the distortion field shipped here and the one that server was built
  with. `./gradlew geoTest` keeps that gap from turning into a regression.

## Licence

Original code MIT. The coordinate parsers and projection classes are derived from
[TerraPlusPlus](https://github.com/BuildTheEarth/terraplusplus) (MIT, Copyright (c) 2020
BuildTheEarth.net), whose parsers are in turn adapted from
[GBIF pipelines](https://github.com/gbif/pipelines) (MIT); the relevant headers are preserved in
those files.
