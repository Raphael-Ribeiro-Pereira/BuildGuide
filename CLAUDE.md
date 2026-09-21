# Build Guide — fork `feat/cone-expanded`

Fork of [brentmaas/BuildGuide](https://github.com/brentmaas/BuildGuide) (Minecraft
building-guide mod). Our work lives on branch `feat/cone-expanded`, based on upstream
`d4b846f` (mod version 0.4.8, Fabric 1.21.11).

## Permanent paths — never use temp folders

| What | Path |
|---|---|
| Repo | `C:\Users\Rapha\Documents\BuildGuide-src` |
| JDK 21 (`JAVA_HOME`) | `C:\Users\Rapha\Documents\BuildGuide-tools\jdk-21.0.12.1+1` |
| Vineflower decompiler | `C:\Users\Rapha\Documents\BuildGuide-tools\vineflower.jar` |
| Decompiled reference jars | `C:\Users\Rapha\Documents\BuildGuide-tools\decompiled\` |
| ~~Original jars (June 2026)~~ | ~~`C:\Users\Rapha\Documents\Buildguide\*.jar*`~~ — obsolete: every jar is reproducible from the branch history (one commit per jar) |
| Minecraft mods folder (Modrinth) | `C:\Users\Rapha\AppData\Roaming\ModrinthApp\profiles\Fabulously Optimized (1)\mods` |

Nothing project-related goes in a scratchpad or `%TEMP%`. The original source repo was
lost that way once; this branch was rebuilt from decompiled jars.

### Restoring on a fresh machine

Only the repo (this fork on GitHub) is irreplaceable; everything else is re-downloadable:

1. `git clone https://github.com/Raphael-Ribeiro-Pereira/BuildGuide BuildGuide-src` into
   `C:\Users\Rapha\Documents`, `git checkout feat/cone-expanded`,
   `git remote add upstream https://github.com/brentmaas/BuildGuide`,
   `git config core.autocrlf false`.
2. JDK 21 Temurin (`OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip` from
   adoptium.net) unzipped to `BuildGuide-tools\jdk-21.0.12.1+1`.
3. Vineflower (latest release jar from github.com/Vineflower/vineflower) as
   `BuildGuide-tools\vineflower.jar`.
4. Decompiled baselines are regenerated on demand: build the jar at the wanted commit and
   run `java -jar vineflower.jar <jar> decompiled/<NAME>`, normalising line endings
   (`sed -i 's/\r$//'`) before `diff -r`.
5. Harness: `tools/harness/README.md`.
6. Modrinth profile "Fabulously Optimized (1)" must be recreated (Fabric 1.21.11); copy the
   built jar into its `mods` folder.

## Build

```bash
export JAVA_HOME="C:/Users/Rapha/Documents/BuildGuide-tools/jdk-21.0.12.1+1"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :fabric1.21.11:build --configure-on-demand --no-daemon
```

- `--configure-on-demand` is required: upstream includes ~40 subprojects and the
  Forge ones demand JDK 25 at configuration time. We only build `common` +
  `fabric1.21.11`.
- Output: `fabric1.21.11/build/libs/BuildGuide-Fabric-0.4.8.jar`.
- First build downloads Gradle 9 + Loom + Minecraft (~10 min); later builds are fast.
- Only `fabric1.21.11` is maintained on this branch. Forge/NeoForge are untouched upstream code.

## Architecture rules (these are what made the recovery possible)

- **`common` never imports `net.minecraft`.** Anything loader-specific goes behind an
  interface or an abstract handler (`AbstractRenderHandler`, `ILogHandler`,
  `IShapeBuffer`…) implemented in `fabric1.21.11`. Because of this, `common` decompiles
  cleanly from a production jar; the Fabric side comes back with intermediary names.
- Shape base class is `Shape` (not `AbstractShape`). Override
  `protected void updateShape(IShapeBuffer buffer) throws InterruptedException`
  (`addShapeCube` throws `InterruptedException`; Catenary declares `throws Exception`).
- Register shapes in `BuildGuide.init()` via `ShapeRegistry.registerShape(Class, langKey)`
  — **always append at the end**. Saved shapes reference the registry index.
- New `Property` fields **always go at the end of `properties`** so shapes persisted by
  older versions still load (graceful degradation).
- Variable-count things (Spline points) are fixed slots + a count property appended last;
  never add/remove properties at runtime (persistence is by index).
- GUI-only properties (row owners, buttons) go through `Shape.addGuiOnly` so they take no
  persistence slot. Composed shapes: `ShapeBridge` is the model (sections, count, dedup set).
- Inert-but-persisted properties go through `Shape.hideFromGui` (never remove or reorder).
- Reset is screen-wide (`Shape.resetShownToDefaults`): defaults captured by `ShapeSet` at
  construction, `setValue` each shown property (no `onPress`), then one `update()`. Protect
  irrecoverable inputs (control points) with `protectFromReset`.
- Block events reach validation through `MixinClientLevel` → `IncrementalValidator` →
  `ValidationState.updateBlock`; filter cheap first (`isInRange`), never read a shape's
  `expectedBlocks` from the client thread, and call `invalidate()` before clearing it.
- Full scans are requested by `Shape.doUpdate` (`ValidationState.requestScan`) and run by
  `RenderHandler.validateShape` on the render thread, debounced 300 ms and gated on loaded
  chunks; never read the world from the generation executor. Next validation hook to add:
  `ClientChunkEvents.CHUNK_LOAD` → `requestScan()` for intersecting shapes.
- Exclusions live in validation, never in generation: ignored block types are a global
  config (`Config.ignoredBlocks`, resolved to a boolean on the Fabric side), exclusion boxes
  belong to the `ShapeSet` (`ExclusionScreen`, persisted as `exclusions=`), and both are
  applied by `ValidationState` (`IGNORED` status counts as missing; excluded positions are
  untracked). `BlockOps.excludeAABB` is geometry exclusion and intentionally unused.
- GUI and overlay never snapshot validation: they compare `ValidationState.getVersion()` and
  rebuild (debounced 100 ms). Coloured world cubes go through `ValidationOverlay` +
  `CubeMesh` (one buffer, per-vertex colours, one draw call); the 3D preview must reuse them.
- Top bar: six 80-px tabs; there is no room for a seventh — new screens must hang off an
  existing one.
- `ShapeCuboid.enumerate(w, h, d, walls.ALL)` with `d > 1` is a hollow box (six faces), not a
  solid; stamp a `w × h × 1` footprint per row when you need a solid volume.
- `PropertyRunnable` renders as a button (used for `Validate`, `Set endpoint`).
- Block solidity: `BlockState.getMaterial().isSolid()` does not exist on 1.21.11 — use
  `isAir()` / `blocksMotion()`.
- Validation is in the `Shape` base for every shape: `addShapeCube` records the position in
  `expectedBlocks`, `doUpdate` invalidates/clears before and requests a scan after; use
  `addShapeCubeIfNew` when a shape needs dedup (never a second local set). Fabric
  `RenderHandler.validateShape` reads the world and fills the transient `ValidationState`
  (mutable, per-position, synchronized; see `docs/API_REFERENCE.md`). Do not add a Validate
  property to shapes: the screen has a fixed Validate button.
- `IScreenWrapper.fillRect` is the only drawing primitive besides text; the validation bar
  lives in `ShapeScreen.renderValidation()` (left column, y 205–230), not in a property row.
- Geometry lives in `public static enumerate(..., IBlockConsumer)` on Circle/Cuboid/Line/Cone;
  `updateShape` only wires it to `addShapeCube`. Compose with `BlockOps` decorators, never by
  instantiating other shapes (see `docs/API_REFERENCE.md` → Composition primitives).
- Translation keys live in `common/resources/assets/buildguide/lang/en_us.json`, kept
  alphabetical. Only `en_us` is maintained for our keys.
- Line endings are mixed upstream: `common/**` and `en_us.json` are CRLF,
  `fabric1.21.11/**` is LF. `core.autocrlf=false` is set locally; match the file's existing
  ending so diffs stay minimal.

## Branch history (one commit per original jar)

| Commit | Original jar | Change |
|---|---|---|
| top radius + mode | `cone-expanded.jar.bak` | frustum, Hollow/Solid |
| taper | `cone-taper.jar.bak` | curved slope `(1-t)^taper` |
| layer thickness | `cone-layers.jar.bak` | stepped layers, [.25 .5 .25] smoothing, `PropertyMinimumInt` `>=` fix |
| catenary thickness | `catenary-thick.jar.bak` | disc per point + dedup |
| validator | `validator.jar.bak` | world check, near-block scan (d ≤ 2) |
| validate button | `validate-button.jar.bak` | `PropertyRunnable` |
| spline | `spline.jar` | Catmull-Rom, `IValidatable`, `LocalPos` |

## Workflow

- **Rule zero:** read `docs/API_REFERENCE.md` before touching properties, widgets,
  persistence or handlers, and update it in the same change when a contract moves.
- `git push` at the end of every session. The remote is the only backup.
- Use the `decompiled/` reference when in doubt about original behaviour: decompile the
  new jar with the same Vineflower and `diff -r` against `decompiled/Fabric-0.4.8-spline`
  — only ordering/style differences should appear.
- To test in-game, copy the built jar over the one in the Modrinth mods folder.
- Offline harness (geometry + validation, no Minecraft): sources live in the repo at
  `tools/harness/` (see its README for build/run). Compiled classes go to
  `C:\Users\Rapha\Documents\BuildGuide-tools\bridgetest` (not versioned). Run it before
  every in-game test; it caught the bridge bend holes before the game did.

## Known issues

- **Tech debt (conscious):** `ShapeSpline` keeps its five `PropertyPointRow` in
  `properties`, persisting as 5 useless `"Row"` entries, because they were appended before
  `Shape.addGuiOnly` existed. Changing it would break saves; live with it. New shapes use
  `addGuiOnly`.
- Uniform Catmull-Rom overshoots on sharp corners (an L of control points bulges past the
  corner by ~25% of the segment length). Inherent to the curve type; centripetal
  parameterisation would reduce it if it ever matters.
- No scrolling in the property panel. Shapes with many properties use panel sections
  (`declareSection`/`assignSection`, see `docs/API_REFERENCE.md`) and/or a custom
  `onSelectedInGUI`; Spline uses both (max 8 rows).

## Verified in-game (2026-09-17, rebuilt jar)

Cone (top radius, solid, taper, layer thickness), Catenary thickness, Validate button
(chat report), persistence reload with `Layer thickness = 1` (the `PropertyMinimumInt`
fix) — all OK.
