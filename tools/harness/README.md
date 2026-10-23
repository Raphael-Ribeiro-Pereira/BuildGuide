# Offline harness (no Minecraft)

Drives `common` classes directly (Bridge/Spline geometry, `ValidationState`,
`ValidationOverlay`, exclusion rules) and prints maps, counts and timings.
Run it before every in-game test; it caught the bridge bend holes before the game did.

`ShapeSet` needs a Minecraft-free instance, so the tests allocate it with
`sun.misc.Unsafe` and call `updateShape` by reflection. Property indices for
`ShapeBridge` are listed at the top of `BridgeTest.java`.

## Build and run

```bash
export JAVA_HOME="C:/Users/Rapha/Documents/BuildGuide-tools/jdk-21.0.12.1+1"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :common:build --configure-on-demand --no-daemon      # produces common/build/classes/java/main
CLASSES=common/build/classes/java/main
OUT=/c/Users/Rapha/Documents/BuildGuide-tools/bridgetest       # compiled classes stay out of the repo
javac -cp "$CLASSES" -d "$OUT" tools/harness/*.java
java  -cp "$CLASSES;$OUT" BridgeTest        # geometry: maps + hole counts (466/159/360 blocks expected)
java  -cp "$CLASSES;$OUT" StateTest         # ValidationState counters/transitions (12 asserts)
java  -cp "$CLASSES;$OUT" IncrementalTest   # updateBlock path (10 asserts)
java  -cp "$CLASSES;$OUT" ExclusionTest     # ignored types + exclusion boxes (11 asserts)
java  -cp "$CLASSES;$OUT" OverlayTest       # version/indices/highlight/cap/colours (10 asserts)
java  -cp "$CLASSES;$OUT" NearDiagTest      # structure errors: scan + incremental detection, shell geometry (16 asserts)
java  -cp "$CLASSES;$OUT" ClassifyTest      # 2.5 status table + hollow sphere/cone cavities (18 asserts)
java  -cp "$CLASSES;$OUT" PreviewTest       # preview: CubeMesh face order, shading, snapshot, fit (12 asserts)
java  -cp "$CLASSES;$OUT" PreviewColourTest # preview colours by status, exclusions white, refresh rule (16 asserts)
java  -cp "$CLASSES;$OUT" PreviewCameraTest # preview camera: drag, clamps, zoom, reset (12 asserts)
java  -cp "$CLASSES;$OUT" GenerationTest    # Shape.generation only on success, snapshot refuses errors (10 asserts)
java  -cp "$CLASSES;$OUT" PreviewControllerTest # refresh throttles, shape switch, shared camera, input (25 asserts)
java  -cp "$CLASSES;$OUT" ValidationListTest # error list tabs (Errors/Missing/Ignored), counts, debounce, tab switch, click/highlight (28 asserts)
java  -cp "$CLASSES;$OUT" AccordionTest     # accordion rows per section, every shape, vs capacity at 270 and 360 px (50 asserts)
java  -cp "$CLASSES;$OUT" PreviewViewTest   # preview filter and slice through PreviewMesh, view cache, empty view (25 asserts)
java  -cp "$CLASSES;$OUT" ShapeLayoutTest   # Shape-tab bands from the height: the preview down to the status line (2026-10-22), bottom row and accordion as in E6, D1 minimum check
java  -cp "$CLASSES;$OUT" CheckErrorsTest   # Check errors (off by default): the state drops them, stale scans, block events, a scan reading only the shape's positions with the same statuses, the Errors tab's message, State persistence and hand-over, the Visualisation topics' layout, texts
java  -cp "$CLASSES;$OUT" OriginScanTest    # P4 scan origin: results stay in the world, overlay offset exactly 0 when origins match, list rows, debounce, every ShapeSet origin path and the global origin (36 asserts)
java  -cp "$CLASSES;$OUT" PresetTest        # E8 presets: slot file (Windows-safe replace, bad lines), encoding, name= in the world save, two-click confirmation with an injected clock, preset data, load (reused/new instance, refusals leave the set untouched, full reset incl. Spline points) (63 asserts)
java  -cp "$CLASSES;$OUT" ReconcileTest     # ghost fix: safety net (stuck error before, gone after; chunk gate; scan/unvalidated gates; round robin with 50k positions; log limit), update idempotence, client model with and without the second hook, cost per pass (35 asserts)
java  -cp "$CLASSES;$OUT" GhostTest         # not an assert test: prints how a ghost error can arise in a model of the 1.21.11 client block-change code (ReconcileTest also drives it)
java  -cp "$CLASSES;$OUT" FocusTest         # build focus core: Island parts (Cap/Body/Spikes), Part x Layer filter, threshold, saved selectors, focusFrom in the cfg
java  -cp "$CLASSES;$OUT" FocusWorldTest    # build focus in the world: buffer only in focus, refocus without a generation or a scan, overlay, timing focus=
java  -cp "$CLASSES;$OUT" FocusGuiTest      # build focus panel: button texts in px, the row at 270 and 360, the preview's note
java  -cp "$CLASSES;$OUT" TerrainGeometryTest # Terrain geometry: seeds, Types, Dunes direction, the cap 3 x Scale, Edge fade, the skin without gaps (117 terrains), cost under 150k
java  -cp "$CLASSES;$OUT" TerrainTest       # Terrain shape: the cap in the panel, Randomize/Natural/Undo, focus, persistence, labels, accordion, registry, GOLDEN of 12 terrains
java  -cp "$CLASSES;$OUT" TerrainLayersTest # Terrain layers by height: cuts, column layer, palette truth table, sliced == instant scan, persistence, preview, focus by layer
java  -cp "$CLASSES;$OUT" SectionTest       # world buffer in 16-block sections: same vertices as one buffer, bounds, endPart budget, live apply over frames, drops, no leak, render/world-buffer lines
java  -cp "$CLASSES;$OUT" FillTest          # Fill with the block in hand: the cells (Area 3's rule, build height), exact box cover under 32768, by chunk section, the command, two clicks, refusals, 20 per tick, answers, Stop
java  -cp "$CLASSES;$OUT" PolygonRoomTest   # Polygon Floor / Ceiling: off = the old loop (40 random cases), floor and ceiling layers full, depth 1 and negative, persistence
java  -cp "$CLASSES;$OUT" PolygonBuildingTest # Polygon building: walls, windows, floor, roof, rim, ring, spokes, skylight, pillars, 90-degree symmetry of a square, persistence ("print" draws the layers)
java  -cp "$CLASSES;$OUT" FarmingLandTest   # Farming land (cane layout Cross, 2026-10-22): crops all within 4 of water (random outlines, every spacing, least water over grid offsets), sugar cane rows, parts, colours, persistence ("print" draws maps)
java  -cp "$CLASSES;$OUT" PerfStep4Test     # performance step 4: LongHashSet against a HashSet, the shape's set and the preview snapshot, the scan stopping inside a layer (same result, budget kept), the overlay's nearest cubes (same choice and order), the preview's frames= line
java  -cp "$CLASSES;$OUT" PerfStep2Test     # performance step 2: LongIndex, SliceScan.prepare and the bitmap/index paths equal to the scan as it was, getStatuses, same exclusion boxes, preview rebuild only on colour changes, prep=/build=/wait= lines
```

The whole suite is every `tools/harness/*.java` with a `main` except the probes listed below;
the commit helpers run it and require 0 failures and Bridge 466 / 159 / 360.

`CentreTest`, `Step4Test`, `BaseSetTest` are the Step 3/4 and 2.2c checks;
`ScanCostTest`, `MemTest`, `OverlayCost`, `OverlayCost2`, `WorldCost` are timing/memory probes.

Run the `java -cp` lines from PowerShell or cmd: Git Bash (MSYS) rewrites the `;`-separated
classpath and every test fails with `ClassNotFoundException`. `javac` and `gradlew` are fine
from either shell.

The canonical copy is this folder; `BuildGuide-tools\bridgetest` is only the
compile output directory.
