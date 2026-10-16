# Project status — Build Guide fork (`feat/cone-expanded`)

Closing summary written on 2026-10-04, after GUI redesign E8. It is a map, not a
replacement: the contracts are in `docs/API_REFERENCE.md`, the GUI plan, decisions and risks
in `docs/GUI_REDESIGN.md`, working conventions and paths in `CLAUDE.md`, the offline tests in
`tools/harness/README.md`.

Fork of [brentmaas/BuildGuide](https://github.com/brentmaas/BuildGuide), upstream base
`d4b846f` (mod 0.4.8). Only `common` and `fabric1.21.11` are built and tested; the Forge,
NeoForge and other Fabric modules are untouched upstream code (see the backlog).

## 1. What is done

Hashes are the commits on `feat/cone-expanded`. A stage's own "record commit" (a docs-only
follow-up that writes the hash into `CLAUDE.md`) is not listed.

### Shapes

| Stage | Commit | What |
|---|---|---|
| Cone | `bcfe640` | top radius (frustum), Hollow / Solid |
| Cone | `5a37018` | taper (curved slope) |
| Cone | `250b757` | layer thickness with smoothed radius steps |
| Catenary | `44febde` | thickness |
| Spline | `54467d8`, `da338be` | Catmull-Rom spline, compact point rows, Set from the player position |
| Step 0 / 0.5 | `b58f585`, `f2cbba3` | block enumerators and curve utility; panel sections and variable point count |
| Bridge, Step 1 | `e3d1f81`, `0978059` | `ShapeBridge`, arc-length sampled deck; adaptive subdivision on bends |
| Bridge, Step 2 | `7c90888` | handrails and posts |
| Bridge, Step 3 | `f45c0ce` | pillars |
| Bridge, Step 4 | `f0faad3` | pillars end under the deck, hollow round rails, layout and reset |

### Validation (world check)

| Stage | Commit | What |
|---|---|---|
| Validator | `54a0a00`, `e343d66` | compare the cone with the world, report in chat; Validate as a button |
| 2.1 | `5b30beb` | live `ValidationState` and progress bar |
| 2.2 | `76187a1` | incremental updates per block event, screen-wide Reset |
| 2.2b | `e85c090` | automatic full scan after generation (idle 300 ms, chunk gate) |
| 2.2c | `fa44267` | every shape validates |
| 2.3 | `b5da225` | exclusion rules: ignored block types and exclusion boxes |
| 2.4 | `628ebea` | error list and world overlay |
| 2.5 | `cffa2eb` | structure errors replace WRONG and become visible |

### 3D preview and GUI redesign

| Stage | Commit | What |
|---|---|---|
| Step 0 preview | `5dca445` | 3D preview (picture-in-picture, rotation, zoom, validation colours) |
| E1 | `8ef0e59` | `PreviewController`, `Shape.generation`, partial-snapshot fix |
| E2 | `a1ee0eb` | `ValidationListComponent`, the error list reusable in any rectangle |
| E3 | `850dcd0` | one-line header, six tabs, bottom bar in `BaseScreen` |
| E4 | `97ab2ae` | compact 18-px rows, Enter replaces Set |
| E5 | `17fc563` | accordion in the left panel |
| E6 | `f08d981` | Shape header, right panel (preview with filter and slice, list tabs, legend); fixes for the background under widgets and the empty-view crash. Committed under the message "docs: record E6 commit and baseline" (a failed commit command, see rules); `0dce4a8` corrected the hash in `CLAUDE.md`, no force-push |
| E7 | `30f14aa` | proportional layout (`ShapeLayout`), too-small message below 480 × 270 (D1), Visualisation third column (R10) |
| P4 | `98f6e4b` | validation state bound to the scan origin; origin changes request a debounced rescan |
| E8 | `acf4904` | presets: Save menu with 3 global slots, load replacing the shape, instance name saved with the world |
| Ghost fix | `9c3fbde` | validation error that outlived its block (found building a real cone): second hook on `setServerVerifiedBlockState`, which the first hook could not see, plus the `StateReconciler` safety net (every 250 ms re-reads the tracked errors and a round-robin slice of the shape) |

### Placement into the guideline (Area 3)

Built on `wip/area3` and fast-forwarded into `feat/cone-expanded` on 2026-10-05 after the
`area3-wip2` in-game test passed.

| Stage | Commit | What |
|---|---|---|
| Phase 2 | `229c3fe` | `GuidelinePicker` (voxel walk along the look ray) and `IBlockProbe.FLAG_REPLACEABLE` |
| Phase 3 | `2ad86f2` | target picked once per frame under the shape lock, and its outline |
| Phase 4 | `183c15e` | toggle key G, in-memory `State.placeMode`, HUD label below the crosshair |
| Phase 5 | `f7f4a42` | right click fills the targeted cell (`MixinMinecraft` swaps `hitResult` in `startUseItem`; one click, one block) |
| Outline | `584674b` | target outline in the error-list highlight white, `shapeCubeSize + 0.15` (at most 1.0), 12 black edge boxes, same buffer and pipeline |
| Rule | `99fa942` | deploy process guard (rule 10) |

Known limit, off hand: placement into the guideline only acts when the **main hand** holds a
block item. A block held only in the off hand (main hand empty or holding a non-block) is
placed the vanilla way, at the block looked at, not into the guideline.

Offline harness after Area 3: 466 asserts, 0 failures; Bridge 466 / 159 / 360.

### Island, block A (plan, body, organic edge; no colours)

Built on `wip/island` and fast-forwarded into `feat/cone-expanded` on 2026-10-06 after the
`island-wip` in-game test passed. Decisions and the test feedback: `docs/ROADMAP_MODES.md`.

| Stage | Commit | What |
|---|---|---|
| Roadmap | `5398f84` | flat top included, block A decisions, stage 4 split into 4C and 4D |
| Core | `3fe71f7` | `IslandNoise` (pure seeded gradient noise) and `IslandGeometry`: star-shaped plan (Circle, Square with Roundness, Polygon 3-12, Organic), body Bowl / Cone / Terraced, flat cap, hollow shell = solid cells within Manhattan distance Wall (1-3) of a non-solid cell, closed in 6-connectivity |
| Shape | `643d540`  | `ShapeIsland` registered last, sections Base / Body / Seed, 15 persisted properties, New seed |
| Float step | `f7937be` | `PropertyFloat` gains an optional -/+ step (default 1.0, the old path unchanged; a fractional step uses decimal arithmetic, so 0.05 x 3 reads 0.15); Island's Roundness, Edge noise, Sharpness and Roughness step 0.05 and the shape holds them in [0, 1] (no float property type has a maximum) |

Limits: width 3 to 121 (radius 60), depth 0 to 80, Wall 1 to 3. Block counts at radius 60, depth 80 (roughness 0.3, measured 2026-10-07; the block A text quoted
only Wall 2, which is not the worst case):

| Outline | Wall 1 | Wall 2 | Wall 3 |
|---|---|---|---|
| Circle | 32 576 | 63 262 | 92 139 |
| Square | 43 015 | 83 657 | **121 636** |

The true worst case is Square, Wall 3: 121 636 blocks, about 78 MB of vertex buffer (672 B per
block) and about 7.5 MB of expected-block set (62 B per entry). The 107 518 blocks seen in the
live-apply log fit a Wall 3 island between these two rows. Diagonal (26-connected) leaks: Wall 1 and 2 have some, Wall 3
none; the 6-connected shell is always closed.

Offline harness after Island block A: 571 asserts, 0 failures; Bridge 466 / 159 / 360.

### Island, block A2 (clarity, Randomize / Naturalize)

Built on `wip/island-ux` and fast-forwarded into `feat/cone-expanded` on 2026-10-06 after the
`island-ux-wip` in-game test passed (7 of 7, with the two finishing tweaks of `cddf0c9`).

| Stage | Commit | What |
|---|---|---|
| Core | `5a30b97` | `IslandControls` (pure): which plan controls apply to each Outline, Randomize (new = clamp(current + U(-1, 1) x % x range), never Outline or Profile), the Naturalize recipe in named constants, `Values` for one-step Undo; `PropertyRangeInt` optional -/+ step (default 1, unchanged) |
| Shape | `07d554c` | Base shows only the controls of the Outline (Circle: widths, rotation; Square: + Corner round; Polygon: + Sides; Organic: + Wobble, Wobble size); neutral defaults for a new island and Reset; section Random (Base %, Body %, Randomize, Naturalize, Undo, Seed); labels Wobble, Wobble size, Corner round, Rotation (deg) (own key `islandrotation`: the shared `rotation` key labels other shapes' enum); the 15 block A positions unchanged, 4 new ones at the end |
| Tweaks | `cddf0c9` | Base % and Body % default 15 (new island and Reset; saved values kept); Wall reads "Shell (blocks)" (70 px; key unchanged) |

Offline harness after block A2: 633 asserts, 0 failures; Bridge 466 / 159 / 360.

### Live apply (all shapes)

Built on `wip/live-apply` and fast-forwarded into `feat/cone-expanded` on 2026-10-07 after the
`live-apply-wip` in-game test passed (9 of 9).

| Stage | Commit | What |
|---|---|---|
| Core | `4b94cf4` | `FieldDebounce`, `WorldUpdateGate`, `TimingLog` (pure, injectable clocks); `ITextField.markApplied` (default) so a point row applies its three fields with one regeneration |
| Fabric | `8a9c810` | text fields apply 500 ms after the last keystroke or on focus loss (code changes and text equal to the applied text never fire; Enter and -/+ stay immediate); the world keeps the last applied buffer (`Shape.shownBuffer`) until the gate allows the new one; scan, error overlay and Area 3 target wait for it; "World update pending" in the preview corner; timing lines |
| Config | `416b5e0` | World update mode in Configuration, saved in `buildguide.cfg`: **Live** (as soon as the generation is ready), **Idle** (default: 1 s after the last change), **On close** (when the menu closes). Closing the menu always applies at once |
| Docs | `d1988dc` | `CLAUDE.md` baseline `ISLANDUX2` |

The deferred path is opt-in (`AbstractRenderHandler.deferredWorldUpdates`, default false): only
`fabric1.21.11` uses it; the other loaders keep the original render path. Timing lines go to the
profile's `logs/latest.log` as `[Build Guide] timing: phase=... ms=... blocks=...`.

Offline harness after live apply: 658 asserts, 0 failures; Bridge 466 / 159 / 360.

### Preview performance and Island spikes (block B)

Built on `wip/spikes` and fast-forwarded into `feat/cone-expanded` on 2026-10-07 after the
`spikes-wip` in-game test passed (Raphael's mark: 9); the timing log then showed one preview
rebuild per change.

| Phase | Commit | What |
|---|---|---|
| 1, preview | `27ee102` | the colour refresh waits while the shape regenerates (the generation starts by invalidating the validation state, which recoloured and rebuilt the old model); `PreviewRenderer` rebuilds only when `PreviewModel.meshChange` gives a reason (generation, filter, slice, validation, view); timing line with `reason=`, cap 200 lines |
| 2, GOLDEN | `b7b372e` | block-set hashes of 15 islands without spikes, from the untouched code |
| 2, core | `e999988` | spikes: vertical cones, tip down, under the body; Random or Ring roots pulled inside the outline; the union is taken column by column and keeps the body's shell definition; Count 0 = the GOLDEN body |
| 2, shape | `a6d1c23` | six controls (Spikes, Spike mode, Spike length, Spike base, Length var %, Spread %) after the 19 existing ones, in a Spikes section between Body and Random |

Spikes worst case (121 x 121, depth 80, Wall 3, 12 spikes 40 long with base 8, every Outline,
mode, Profile and spread): **142 675 blocks**, about **91 MB** of vertex buffer, **41 ms** of
geometry.

Offline harness after block B: 719 asserts, 0 failures; Bridge 466 / 159 / 360.

### Island spikes 2 and the scan in Y layers

Built on `wip/spikes2` and `wip/scan-slices` (which grew from it) and fast-forwarded into
`feat/cone-expanded` on 2026-10-07 after the `scan-slices-wip` in-game test passed.

| Stage | Commit | What |
|---|---|---|
| GOLDEN-SPIKES | `ceb4479` | block-set hashes of 16 islands with spikes, from the block B code; spikes 2 keeps every one at its defaults |
| Spikes 2, phase 1 | `73fcd08` | spike cap 64; Spike mode Fill (sunflower spiral, golden angle) with Jitter %; edge falloff in the geometry; "Large shape: N blocks" in the preview corner above 200 000 blocks |
| Spikes 2, phase 2 | `b23952d` | Taper (0.5 to 3.0), Dripstone, Edge falloff %, in a new Spike shape section between Spikes and Random; Spikes % for Randomize; Natural spikes (Naturalize spikes); the Seed row is no longer shown (still saved) |
| Spikes 2, phase 3 | `3e63cbd` | Break Attached / Segmented (a part falling), Pieces 2 to 4, Gap 1 to 6 |
| Scan slices, core | `6802ac9` | `SliceScan` (pure): the validation scan in Y layers, bottom to top, within a time budget per frame (at least one layer), published in one go and identical to the instantaneous scan; blocks changed meanwhile are read again; scan epoch in `ValidationState` (a scan whose inputs changed starts over) |
| Scan slices, Fabric | `76ca97d` | `RenderHandler.validateShape` runs the scan in layers; "Scanning N%" in the preview corner; timing line `phase=scan ms= slices= maxslice= blocks=` |
| Scan slices, Config | `7762309` | Scan speed in Configuration, saved in `buildguide.cfg` after `worldUpdateMode`: Instant, Fast 12 ms, **Normal 6 ms** (default), Slow 3 ms |

Spike cap 64, worst case (121 x 121, depth 80, Wall 3, 64 spikes 40 long with base 8, Taper 0.5,
Segmented with 4 pieces and gap 1, Fill): **278 151 blocks**, about **178 MB**, **119 ms** of
geometry.

Pending from this stage:
- Test 10 (the worst case with 64 spikes) was not tested in game.
- With Count 0 the Spike shape section shows an empty header (accordion sections are fixed per
  shape): no answer from Raphael yet.
- The Seed row is hidden (Random would need 8 rows and 7 fit at height 270; the value is still
  saved, in presets and in Randomize): no answer yet.
- The meaning of Pieces 2 (Pieces counts the attached top piece: 2 is the top piece plus one loose
  piece): no answer yet.

Offline harness after the scan in Y layers: 846 asserts, 0 failures; Bridge 466 / 159 / 360.

### Layers by depth (4C) and the world update measured

Built on `wip/layers` and fast-forwarded into `feat/cone-expanded` on 2026-10-07 after the
`layers-wip` in-game test passed.

| Phase | Commit | What |
|---|---|---|
| 1, measure | `487fbe6` | the frame that applies a new world buffer is timed every time (threshold 4 ms): `phase=world-buffer ms= blocks= reason=apply\|idle\|close world-end= world-close= world-other=`; the scan line gains `start=` and `publish=` (its two single-frame steps); no behaviour change |
| 2, bands | - | **skipped**: phase 1 showed the stutter is not the upload (see `docs/ROADMAP_MODES.md`), so there is no Build speed |
| 3, core | `a981c8e` | `ShapeLayers` (pure): four layers below the top, cut at three percentages of Depth, in integers; Island: Layers (box) and Cut 1 %, Cut 2 %, Cut 3 % (5 to 95 by 5, 25/50/75, kept at least 5 apart) after Gap, in a Layers section between Spike shape and Random; with Layers on the world cubes take the layer colour and the shape's alpha (same blocks); `Shape.hasLayers` / `getLayers` (defaults: none) |
| 3, preview | `c8a8579` | "Colour: Status \| Layer" right of the filter while Layers is on; the rebuild key holds the colour mode and the cuts; coloured by layer, the legend shows each layer's depths below the top (`0-5`, `6-11`, `12-17`, `18+`) |

Layer colours: cyan `00C8DC`, magenta `DC32C8`, brown `8C5A2D`, strong blue `2846DC` (none is a
status colour). The 31 GOLDEN islands hash the same with Layers on and off.

Accordion headers went from 12 to 11 px for every shape: with the Island's sixth section, 12-px
headers left room for 6 rows at height 270 and Base (Organic), Spikes (Fill) and Random have 7;
at 11 px Origin and six sections leave room for 7 (77 + 126 of 206 px).

Offline harness after 4C: 922 asserts, 0 failures; Bridge 466 / 159 / 360.

### Native memory leak fix

Built on `wip/leakfix` (`481d42a`) and fast-forwarded into `feat/cone-expanded` on 2026-10-14
after Raphael approved `leakfix-wip` in normal use (the full test is pending, see
`docs/ROADMAP_MODES.md`).

`ShapeBuffer` never closed its native `ByteBufferBuilder`: every generation (also the discarded
ones), preview mesh, overlay and Area 3 outline left about 384 bytes per block behind.

- **`VertexMemory` (pure):** one account per builder, following vanilla's growth rule; it is released
  exactly once, and a write after the release throws instead of touching freed memory.
- **`ShapeBuffer`:** `end()` frees the native copy right after `createBuffer` copies it to the GPU
  (as vanilla's SkyRenderer and CubeMap do); `close()` frees the GPU buffer only.
- **Ownership in `Shape`, under its lock:** a cancelled or failed generation frees its own buffer when
  its task ends; a finished generation replaced before it was shown is freed when the next one starts.
- **`dispose` on removing a shape set** (`State.removeShapeSet` -> `ShapeSet.dispose` ->
  `Shape.dispose`): GPU buffers closed, the last generation's data freed after a running one stops.
- **Counter:** the world-buffer and preview-rebuild timing lines end with `native=<MiB>`, the vertex
  memory still held; it must stay level while editing.

Offline harness after the leak fix: 946 asserts, 0 failures; Bridge 466 / 159 / 360.

### Palette per layer (4D)

Built on `wip/palette` and fast-forwarded into `feat/cone-expanded` on 2026-10-14 after the
`palette-wip` in-game test passed.

| Phase | Commit | What |
|---|---|---|
| Core | `1e3ef9c` | `LayerPalette` (pure): one block id per layer of the 4C `ShapeLayers` and `classify`, the one rule every path uses (scan, `SliceScan`, block events, safety net); `WRONG_LAYER = 5` and `WRONG_BLOCK = 6` in `ValidationState` (3 stays reserved; statuses are not persisted) |
| Integration | `a63a95d` | Island Block 1 to Block 4 (`PropertyBlockId`, URL-encoded, empty = any solid) after Cut 3 %; ids resolved once per scan to raw registry ids, read as ints (no string per block); `IShapeHandler.normalizeBlockId` / `heldBlockId` (defaults) |
| GUI | `e35fad3` | Layers section in 6 compact rows (the three cuts on one row, then an id field and Hand per layer); orange `FF8C1A`; Wrong layer filter, tab and legend entry only with a palette |
| Rule 10 | `f2fc46a` | the deploy guard never prints a full command line (docs) |

The rule for an expected position: air is MISSING; the layer's chosen block is OK (a block chosen for
two layers is right on both); the block of another layer is WRONG_LAYER (orange); an ignored type
is IGNORED; another non-solid block is MISSING; a solid block in no palette where the layer has
one is WRONG_BLOCK (red); a layer with no block accepts any solid block. Only the block id is
compared, not its state. Layers off, or no block chosen: the validation of before.

**Errors now add up two things:** the solid blocks near the shape but outside it (structure
errors, as before), and the wrong blocks on the shape's own positions. Both are red. The list shows
the first with their distance and the second with their layer.

Ids are typed (`stone` becomes `minecraft:stone`; an id that is not a block, or air, turns the
field red and is not applied) or taken from the main hand with Hand ("Hold a block" otherwise).

Offline harness after 4D: 1028 asserts, 0 failures; Bridge 466 / 159 / 360.

### Build focus

Built on `wip/focus` and fast-forwarded into `feat/cone-expanded` on 2026-10-14 after Raphael
validated `focus-wip`. The world shows only the part of a shape one is working on; the preview,
the expected set, the scan, the totals and Validate always cover all of it.

| Phase | Commit | What |
|---|---|---|
| Core | `8dd88d0` | `FocusFilter` (Part x Layer, the threshold), `IFocusParts` (default: one part); Island parts from the position and the body's column bottoms (nothing per block): Spikes below the body's bottom, Cap the top Wall rows, Body the rest; Focus part and Focus layer saved after Block 4 (44 values; older islands load with All, All); `focusFrom` last in `buildguide.cfg` |
| World | `3628e78` | the world buffer holds only the blocks in focus; a focus change makes only that buffer again from the expected set (no generation, the validation and its scan untouched), through the WorldUpdateGate; the overlay keeps near and ignored positions and filters wrong block / wrong layer; the Area 3 target only in focus; `world-buffer ... focus=<n>/<total>` |
| Panel | `0ff9af7` | Part and Layer buttons on a second row of the preview panel (Layer only with Layers on), the note "Focus: ...: N of M blocks" / "Focus off: < N blocks", Focus from in Configuration |
| Default | `428c206` | Focus from defaults to 400 blocks instead of 30000 (with Part and Layer on All the focus does nothing; 0 = always) |

- Part: All, Cap, Body, Spikes, Hidden on the Island; All, Hidden on every other shape (not
  saved there: the persistence of the other shapes does not change). Layer: All, 1 to 4.
- Hidden keeps the origin cube. The threshold applies to Hidden too.
- The installed `focus-wip` jar still has the 30000 default; 400 arrives with the next deploy
  (a `buildguide.cfg` that already has `focusFrom` keeps its value).

Offline harness after the focus: 1096 asserts, 0 failures; Bridge 466 / 159 / 360.

### Terrain (on `wip/terrain`, waiting for the in-game test; not merged)

A new shape, registered after the Island: a seeded relief **skin** over a plan, never solid (Skin 1
to 3 blocks, no floor, no wall at the edge). It shares no class with the Island (the plan code is a
copy); the decompile diff against `FOCUS` shows `ShapeIsland`, `IslandGeometry`, `IslandNoise` and
`IslandControls` identical.

| Phase | Commit | What |
|---|---|---|
| Geometry | `45abc73` | `TerrainNoise`, `TerrainGeometry`: the Island's four Outlines; Hills (fractal), Mountains (ridged), Dunes (waves along the Rotation); a column goes down to its lowest neighbour inside the plan minus Skin - 1 (no gaps); the cap Height <= min(60, 3 x Scale) |
| Shape | `147d831` | `ShapeTerrain` (sections Base, Relief, Random with a visible Seed), `TerrainControls` (Randomize, Natural, Undo), the cap in the panel, the focus (Part All / Hidden), GOLDEN of 12 terrains |
| Layers | `f2943e0` | layers and the palette by **height** (`TerrainLayers`: a column's layer from its surface, t = h / Height); `ShapeLayers.layerAt` and `LayerPalette.classify` by position (the Island's layers unchanged) |

- The cap came from a measured cost: without it the worst case was 381,733 blocks. With it, at
  121 x 121, Roughness 1, Skin 3, every Scale at its cap: Hills 93,703, Mountains 129,882, Dunes
  126,210 blocks (at most 83 MB of vertex buffer). Defaults on 121 x 121: 36,649 blocks.
- Dunes: lee 0.35 of a wave and 0.20 of light noise (the lee alone hardly changes the count; the
  noise's share does).
- Natural (drawn from the current seed, which stays): Organic, Corner round 0.75 +- 0.15, Wobble
  0.25 +- 0.08, Wobble size 6 +- 2, Roughness 0.40 +- 0.15, Edge fade 35 +- 10.
- Known behaviour: a Height typed above 60 turns the field red like every range field (above
  3 x Scale it is brought to the cap); Randomize moves Corner round and Wobble even where the
  Outline hides them.

In-game checklist for `terrain-wip` (jar SHA-256 `8F8720A6…6638`, commit `f2943e0`):
1. Terrain is last in the shape list; each Type and Outline draws a skin with no holes, also on steep
   Mountains (Scale 4, Height 12).
2. Lowering Scale lowers Height in its field; a typed Height above 3 x Scale comes back as the cap.
3. Randomize, Natural and Undo: one regeneration each, Outline and Type never change, Undo restores
   the seed.
4. Layers on: four colours by height (low to peaks), the legend in blocks above the base, Colour:
   Layer in the preview, a block per layer validates (wrong layer orange, wrong block red).
5. The focus: Part Hidden hides the terrain in the world only; Layer 1..4 shows only those columns.
6. An Island saved before still loads and looks the same.

Offline harness after the Terrain: 1178 asserts, 0 failures; Bridge 466 / 159 / 360.

### Performance, step 1: the world buffer in sections (on `wip/perf`, waiting for the in-game test; not merged)

Branched from `wip/terrain` (`2724d58`), so its jar has the Terrain too. It changes how the world
draws a shape, not the shapes: the decompile diff against `TERRAIN` (declared beforehand) is 4 new
classes (`SectionedBuffer`, `WorldUpload`, `RenderStats`, `GpuMemory`) and 8 changed
(`AbstractRenderHandler`, `TimingLog`, `IShapeBuffer`, `Shape`, the Fabric `RenderHandler`,
`ShapeBuffer`, `ShapeHandler`, `MixinLevelRenderer`); `en_us.json` showed up only through the
line-ending normalisation (the same bytes in both jars).

| Phase | Commit | What |
|---|---|---|
| Sections | `b86d7fe` | the world buffer in 16 x 16 x 16 sections, one `ShapeBuffer` each (the same vertices and colours as one buffer); drawn only within the render distance + 1 section and inside the terrain's frustum (captured at `prepareCullFrustum`), all in one render pass; a new shape sent a few sections per frame (3 ms) and swapped in whole; `phase=render` every 30 s and `sections=`, `frames=`, `maxframe=`, `gpu=` on `world-buffer` |

- Why: Litematica draws only the 16^3 sections in view and sends changes with a time budget; we drew
  the whole shape every frame and sent a big one in a single frame (the hitch when it appears).
- Offline: an Island 81 x 81 with spikes and layers (31,319 blocks) is 123 sections that hold exactly
  the cubes of one buffer; 245 sections over generations, focus changes and drops, all freed once.
- Not measurable offline: the FPS and the GPU side. The `phase=render` line is there for that.
- Known: a big shape now appears a few frames after it is generated (whole, never half).

In-game checklist for `perf-wip`:
1. Every shape looks as before (Island with layers and spikes, Terrain, Sphere, Bridge), also with
   Cube size 1 (Visualisation): no hole or missing block along the 16-block section edges.
2. Turning around: nothing appears late at the screen edges; `phase=render ... sections=n/total`
   drops while looking away from a big shape, and the FPS (F3) goes up.
3. Flying beyond the render distance: the far sections go away with the terrain and come back.
4. Holding + on a big shape with Live: the world keeps the old shape until the new one is complete;
   `world-buffer ... frames=N maxframe=` about 3 ms; no isolated tall bar in F3 + 2.
5. Validate, the error overlay, Area 3 placing and the focus (Part, Layer) work as before.
6. `gpu=` stays level while editing (it must not grow) and goes down when a shape is removed.

Offline harness after step 1: 1229 asserts, 0 failures; Bridge 466 / 159 / 360.

Offline harness at the close: 406 asserts, 0 failures; Bridge reference sizes 466 / 159 / 360.
E6 (after two fixes), E7, P4 and E8 passed their in-game checklists before being committed
(E7 7 of 7, P4 9 of 9, E8 9 of 9). The ghost fix was committed on Raphael's go-ahead after the
`ghost-fix-wip` test; the result of that test was not reported to the session that wrote this
file, so it is not claimed here (see the backlog).

## 2. Final jars

Build output: `fabric1.21.11/build/libs/BuildGuide-Fabric-0.4.8.jar` (mod version is still 0.4.8).

| Item | Value |
|---|---|
| Active jar in the mods folder | `BuildGuide-Fabric-0.4.8-terrain-wip.jar` (deployed 2026-10-15) |
| SHA-256 | `8F8720A6C522676F562DEBBCAC85DBA1CBD7F8C6ACF0C2CE75F97971D2E96638` |
| Source | `wip/terrain` at `2724d58` (code identical to `f2943e0`, only docs after it; it includes `428c206`, Focus from 400 for a `buildguide.cfg` without `focusFrom`): the build output and the mods copy have the same SHA-256; `ShapeTerrain`, `TerrainGeometry`, `TerrainControls`, `TerrainNoise` and `TerrainLayers` are inside |
| Tested | the offline harness (1178 asserts); in game: Raphael's test pending |
| Next jar | `perf-wip` (`wip/perf`, `b86d7fe`): built and checked offline, not deployed yet (the game was open) |
| Previous jars | `BuildGuide-Fabric-0.4.8-focus-wip.jar` (SHA-256 `6923FAB6A40E250901C68746389327DA9FF8573498C920B5D86621CB9132B09C`, commit `0ff9af7`), `BuildGuide-Fabric-0.4.8-palette-wip.jar` (SHA-256 `F0C0A242D361BB0E73BC4EF477EE274AA905C7ED8681154FF004227B438B26B0`, commit `e35fad3`), `BuildGuide-Fabric-0.4.8-leakfix-wip.jar` (SHA-256 `D0089DE876C5855AFF509974DFA2400037F1B5DCAD05E63B45A0E6C4095EDABF`, commit `481d42a`), `BuildGuide-Fabric-0.4.8-layers-wip.jar` (SHA-256 `14B66C4E9A572D5224C01F5217BBB087BB48BB8BE908909CF67408824AE2F7CC`, commit `c8a8579`), `BuildGuide-Fabric-0.4.8-scan-slices-wip.jar` (SHA-256 `A280A6440BB423878D6AAFA316709E852D63EF2EEBB3FF3F643E826C4724A067`, commit `7762309`), `BuildGuide-Fabric-0.4.8-spikes2-wip.jar` (SHA-256 `89F6B80A37150060E598EF0988446699E8B43F5E050267E83A9398909BE5D87B`, commit `3e63cbd`), `BuildGuide-Fabric-0.4.8-spikes-wip.jar` (SHA-256 `C3564E26FD888E46E106724E051C27C19D74EE962365FD9BB90775E77DE03A6E`, commit `a6d1c23`), `BuildGuide-Fabric-0.4.8-live-apply-wip.jar` (SHA-256 `8DB5202C0AFEC1F4645D0E4052852B188BE4CA9CF35C9FC969DF5CC092994198`, commit `416b5e0`), `BuildGuide-Fabric-0.4.8-island-ux-wip.jar` (SHA-256 `DD5B7BD8ADF1714F0BC152A303B6ED3F2FB2EA5E9C8F79034B52BD27DAE76557`, commit `07d554c`), `BuildGuide-Fabric-0.4.8-island-wip.jar` (SHA-256 `556D80E49975B12802361D5CB8971EE632415A7E841C0EC71D8AF06826C92DE9`, commit `f7937be`), `BuildGuide-Fabric-0.4.8-area3-wip2.jar` (SHA-256 `B5AC963F068EFF59954521F29F24D994BBFE61CF9F55C2C1DE9256E055392A32`, commit `584674b`), `BuildGuide-Fabric-0.4.8-area3-wip.jar` (first Area 3 test) and `BuildGuide-Fabric-0.4.8-ghost-fix-wip.jar` (SHA-256 `1E2F5E96A659D474EF14D9CDDBF5930E6B695DF66F31C782A7C1E5654E55BDB6`, commit `9c3fbde`), kept as `.bak` |

- The file name still says `wip`: it was never renamed after the test. Renaming is a choice
  for Raphael (nothing depends on the name); a rebuild of `acf4904` gives the same classes.
- The mods folder also keeps 25 older jars as `.bak` (one per stage, `e1-wip` .. `e8-wip`,
  plus the original feature jars). They are history, not needed: each is reproducible from
  the branch (one commit per jar), as `CLAUDE.md` says.
- Mods folder: `C:\Users\Rapha\AppData\Roaming\ModrinthApp\profiles\Fabulously Optimized (1)\mods`.
- Presets are kept outside the jar in `buildguide_presets.txt` in the profile's `config`
  folder; replacing the jar does not touch them.

## 3. Rules that hold from here on

Code
1. `common` never imports `net.minecraft`; loader code sits behind interfaces / abstract handlers.
2. Read `docs/API_REFERENCE.md` before touching properties, widgets, persistence or handlers,
   and update it in the same change when a contract moves.
3. Persistence only grows at the end: never reorder or remove a shipped property, new world-save
   entries go last (as `name=` and `exclusions=` did), unknown keys must be skipped.
4. A new method on an interface that every loader implements (`IButton` has 37
   implementations, `ITextField` as many) is a `default` no-op; only the built Fabric class
   overrides it.
5. A widget kept as a field is the same object on every `init()` (it reruns on resize and GUI
   scale change): reposition it there (`setYPosition`).
6. Rows per accordion section: at most 8 (6 in Bridge). A GUI stage never changes shape
   geometry: no shape subclass, `Property*` or geometry file in its decompile diff (E8 touched
   `Shape` only to add `loadPresetValues`, declared beforehand).
7. Free text inside `key=value;` data is URL-encoded (`PresetStore.encode`).

Process
8. `[PARE-N]` stops: report and wait before the next phase. Nothing is committed until the
   user has confirmed the stage in-game; the user runs the in-game tests.
9. Before every in-game test: build green, harness green. Declare the expected decompile diff
   *before* running it, then diff the new jar against the previous baseline
   (`BuildGuide-tools\decompiled\<NAME>`, Vineflower, CRLF normalised). The baseline is now `FOCUS`
   for `feat/cone-expanded`, `TERRAIN` for `wip/terrain` and `PERF` for `wip/perf`.
10. Deploy only with the game and the Modrinth App closed; abort if javaw, the Modrinth App, or a
    java whose command line contains fabric, knot, net.minecraft, .minecraft, ModrinthApp,
    `Fabulously Optimized (1)` or devlaunchinjector is running (any other java, such as an IDE
    build server, is allowed; an unreadable command line blocks); rename the old jar to `.bak`; prove the deploy with the
    SHA-256 of the mods copy against the build. (E6 once tested the old jar for this reason.)
    **Never print or log a process's full command line:** the game's holds the Minecraft session
    (`--accessToken`). Match the markers internally and show only the process name, the PID and the first
    60 characters, with `--accessToken`, `--uuid`, `--xuid`, `--clientId` and `--username` masked
    (2026-10-14: a listing once printed a token into a session log; it never reached the repo or GitHub).
11. Git scripts are fail-fast: message in a file and `git commit -F` (never `-m` with quotes in
    PowerShell 5.1), `$LASTEXITCODE` checked after every git call, hash read only after the
    commit exists. Push at the end of every session; the remote is the only backup.
12. Verification checks must fail closed: a check that passes when it cannot verify is worse
    than none (the `Select-String -SimpleMatch` without `-Pattern` case). Compare the hash in
    `CLAUDE.md` with `git log`, and require exactly one matching row.
13. Line endings: `common/**`, `en_us.json` and `docs` are CRLF, `fabric1.21.11/**` and
    `tools/harness/**` are LF; `BaseScreen.java` and `en_us.json` end with a lone CR (keep it).
    Edit a mixed file on its original bytes. Check with PowerShell or `git ls-files --eol`,
    not Git Bash `cat -A`.
14. PowerShell traps: no helper named `R` (alias of `Invoke-History`); no
    `$ErrorActionPreference='Stop'` around gradle (stderr notes become errors); count harness
    failures by `^FAIL`, unhandled exceptions and exit codes, not by the word "Exception".
15. Be honest when a premise is wrong, and report what failed with the output.

## 4. Backlog: what was left out

Known limitations, each already written down in the docs (R numbers are in `docs/GUI_REDESIGN.md`)

| Item | Where | Note |
|---|---|---|
| Own GUI scale for the mod | R14 | Auto scale gives 456 × 256 at 1366 × 768 and 427 × 240 at 1280 × 720, below the 480 × 270 minimum: only the "lower the GUI scale" message shows; GUI scale 2 fixes it. Needs a draw / hit-test scaling layer in the Fabric wrapper |
| Name editing takes two clicks | R11 | add `ITextField.setFocused` (default no-op) |
| Slice slider has no change callback | R12 | `ISlider` limit; workaround in place |
| Preview rebuild cost on very large shapes | R4 | 250-ms throttle in place; the r = 50 sphere (31k blocks) cost is measured in the design doc; no measurement with the largest torus or ≥ 100k blocks is recorded |
| GPU mesh stays resident after the preview closes | `CLAUDE.md` tech debt, R5 | sphere r = 50 ≈ 20 MB |
| Chunks that reload after flying away do not rescan | `API_REFERENCE` (2.2b) | hook idea: Fabric API `ClientChunkEvents.CHUNK_LOAD` → `requestScan()` |
| Pillar ground detection | `COMPOSITION_ANALYSIS` | needs a two-pass world bridge like the validator |
| Spline persists 5 useless `"Row"` entries | `CLAUDE.md` tech debt | changing it would break saves |
| Uniform Catmull-Rom overshoots on sharp corners | `CLAUDE.md` | centripetal parameterisation if it ever matters |
| No scrolling in the property panel | `CLAUDE.md` | sections and the 8-row rule instead |
| Only `en_us` is maintained | R7 | other languages fall back or overflow |
| Divergence from upstream | R8 | already diverged, nothing planned |

From the last stages (no earlier doc lists them)
- **Other loaders.** Forge, NeoForge and the other Fabric versions are not built or tested on
  this branch. E8 changed shared `common` code; on those loaders `IButton.setTitle` is the
  default no-op, so a preset's confirmation still arms and needs the second click, but the
  button keeps its old label ("Load" instead of "Replace?").
- **Presets by design:** colours and cube sizes are not in a preset; the world label has no
  dimension; exclusion boxes are included, so in another world they can hide blocks of a
  different terrain (visible and editable in the Exclusions tab). Only 3 slots, no naming of
  the slot itself (the label is type - instance name - world).
- **Instance name and the rest of the world state** are saved only when `persistenceEnabled`
  is on in Configuration, which is off by default.
- **P4, accepted:** an exclusion corner captured from the player position inside the 300 ms
  window is converted with the new origin and applied to the old state; the rescan it requests
  corrects it. Marks, list and progress show the previous scan until the rescan finishes.
- **Not covered offline:** the cancel-then-lock ordering of `Shape.loadPresetValues` while a
  generation runs (it needs the loader's executor; the harness covers values, refusals and
  instance reuse); the widgets and screens themselves (checked only in-game).
- **"To check in game" from 2.5, never recorded as checked:** the validation bar text with
  five-digit totals, and the error shells z-fighting with the block faces
  (`shellInset` 0.01, raise to 0.05 if it flickers).
- **Ghost fix, open points:** (1) the exact server/client sequence behind Raphael's ghost error
  was not proven, only a confirmed hole (`setServerVerifiedBlockState` skips the first hook) and a
  net that corrects whatever escapes; the first `safety net corrected` line in `latest.log`
  (`...\ModrinthApp\profiles\Fabulously Optimized (1)\logs\latest.log`) names the next culprit if
  there is one. (2) The net only removes errors and corrects expected positions: a structure
  error that appears without an event is not added until the next scan. (3) The two mixin
  targets were checked against the game jar and remapped by the build, but only the game proves
  they apply. (4) The real `getBlockState` cost per pass was estimated, not measured in game.
- **The `wip` file name** of the final jar (section 2).
