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

### Fill with the block in hand (on `wip/fill`, on top of `wip/perf`, waiting for the in-game test; not merged)

A **Fill** button at the right end of the preview's focus row (Part, Layer): it places the block in
the main hand into every empty cell the world shows, through `/fill` commands, so it needs commands
(cheats on, Open to LAN with cheats, or op). Raphael chose this over holding right click or a
printer (2026-10-16).

- Cells: exactly the ones Area 3 could target (`GuidelinePicker.qualifies`): expected, in the world
  buffer's focus, outside the exclusion boxes, loaded, empty (air, water, grass...), not inside the
  player; and inside the build height.
- Commands: `fill x1 y1 z1 x2 y2 z2 <block> replace #minecraft:replaceable`, one per box (greedy
  rectangles per layer, stacked up while they repeat, at most 32768 blocks each, the server's default
  limit): a solid 10^3 cuboid is one command, a solid sphere r = 20 (33,401 blocks) 583. The filter
  means a block placed meanwhile is never overwritten.
- Two clicks: the first shows `Fill N?` (N cells), a second within 5 s starts; while it runs the
  button reads Stop. 20 commands per client tick. The server's "Successfully filled" answers are
  counted and kept out of the chat; the chat gets one line at the start and one at the end
  (placed of N, commands, those that placed nothing). Errors other than /fill's still show.
- Refusals, each said in the chat: no block in the main hand, no right to run commands, the shape
  still generating, nothing to fill.
- Not in it: the palette (each layer's block) and undo. It uses only the block in hand.

In-game checklist for `fill-wip`:
1. Hand empty, then a block in hand without cheats: Fill says why and does nothing.
2. With cheats: the first click shows `Fill N?`; after 5 s it goes back to Fill; two clicks fill the
   shape with the block in hand, the chat shows only the start and the summary.
3. Blocks already in the shape, water and grass: the solid ones stay, water and grass are replaced.
4. The focus: Part Cap (Island) or Layer 2 fills only that part; Hidden says nothing to fill.
5. An exclusion box and the cell you stand in stay empty.
6. Stop in the middle of a big fill stops it; Validate afterwards shows the filled cells as OK.

Offline harness after Fill: 1262 asserts, 0 failures; Bridge 466 / 159 / 360.

### Performance, step 2: the single-frame costs (on `wip/perf2`, on top of `wip/fill`, waiting for the in-game test; not merged)

Raphael found `fill-wip` much heavier than `terrain-wip`. Its `latest.log` (2026-10-16, 01:52) did not
show the world drawing heavier (`phase=render avg=0.04` ms a frame) but four other costs, three
of them in single frames; the `terrain-wip` session had no Fill and no scan in its log, so the two
sessions do not compare directly.
- The scan: `start=26 publish=24` ms at 49,568 blocks, each in one frame, and a layer of 27 ms.
- The preview, with the menu open: rebuilt every 1 to 2 s for 11 to 32 ms (`reason=validation`)
  while the filled grass turned to dirt (a version change with no colour change).
- The Fill: about 19 FPS while it ran (its commands went layer by layer, so every chunk section was
  rebuilt once per layer; Sodium is off in this profile).
- Step 1's sections: sending a shape in sections costs about 2.7 times one buffer in total (7.8
  against 2.9 ms at 50k blocks), at most about 3 ms a frame. Left as it is for now.

| What | Change |
|---|---|
| Scan start | the copy of the expected set and the whole preparation (`SliceScan.prepare`) run on a worker thread; the scan starts on the frame it is ready. A scan waiting for its chunks waits prepared (it was rebuilt every frame before). The tracked order is now the shape's own (it was a set of world positions): the error lists may list in another order |
| Scan layers | no boxing per cell: positions numbered by `LongIndex`, statuses and names in arrays, the near pass asks a bitmap of the box (z fastest, neighbours side by side), the exclusion boxes copied (no synchronized call per cell) |
| Scan publish | the status map is filled during the layers and swapped in (`ValidationState.publishScan`): the same version, counters and lists as before |
| Preview | statuses read under one lock (`getStatuses`); rebuilt only when a status or a structure error changes; the mesh written on a worker thread and sent to the GPU when ready (the previous one stays meanwhile) |
| Fill | boxes one chunk section at a time (no box crosses a 16^3 section, each section's boxes together): each section is rebuilt about once |

Measured offline (`ScanHitchProbe` in the session scratchpad, a built island with ground around,
warm JIT, the world a primitive set): at 59,717 blocks the render thread's part of the start went
from 7 to 30 ms to bookkeeping only (the copy, 1 to 5 ms, and the 5 to 7 ms after it are on the worker), the
slowest layer from about 10 to 5 ms, all layers from 243 to 130 ms, publish from 9 to 10 ms to
3 ms. The first scan of a session still costs more (the JIT is cold).

In-game checklist for `perf2-wip`:
1. Holding + on a big shape with the menu open: no tall isolated bar in F3 + 2 when the shape
   appears and when its scan ends; `phase=scan ... start= publish= prep=` with start and publish
   small.
2. The preview with the menu open during and after a Fill with grass: no rebuild every second
   (`preview-rebuild` lines only when colours change); the preview shows the new mesh a frame or two
   after a change, never empty once it showed something.
3. Fill of a big shape: the FPS during the fill (compare with the 19 FPS of 2026-10-16).
4. Validate, the error lists, the overlay and the counts are as before (only the order of the
   lists may differ).

Offline harness after step 2: 1285 asserts, 0 failures; Bridge 466 / 159 / 360.

### Performance, step 3: the error list (on `wip/perf3`, on top of `wip/perf2`, waiting for the in-game test; not merged)

Raphael (2026-10-17): still very laggy; the freezes are when the GUI key opens the menu and when a
big shape updates with a validation; with the Enabled checkbox off nothing freezes at all. The
`perf2-wip` log agreed: every logged phase was small, yet one frame took 36 ms, and with the
checkbox off only two frames drew in 70 s of editing. What only exists with a validation is the
error list (and the overlay), and the list was the cause:
- vanilla's `AbstractSelectionList.addEntry` places a row by summing the height of every row it
  already holds (`getNextY`), so filling n rows costs n^2: 9,000 rows about 0.2 s, 20,000 0.5 s,
  50,000 3 s (`ListCostProbe` in the session scratchpad);
- the list had one row per position (48,632 missing, 8,969 structure errors in that session), built
  with `String.format` per row, and opening the screen built and filled it three times
  (`setCategory` on the old list, `init`, the first `update`); with the menu open it was built and
  filled again at most every 100 ms while the validation changed.

What Litematica does instead (read from its decompiled jar, analysis only): its verifier groups the
mismatches by expected / found block, so its result list has a few rows with counts; malilib's
`WidgetListBase` makes widgets only for the rows that fit; only the nearest
`verifierErrorHilightMaxPositions` are highlighted; the verification runs once, chunk by chunk, in
the client tick with a time limit, not after every change. The schematic preview mod loads and
meshes its previews on other threads (`CompletableFuture`) and caches them.

| What | Change |
|---|---|
| List widget | `SelectorListImpl` overrides `getNextY` and `contentHeight` (every row has the slot height): filling is linear |
| Rows | at most `ValidationListComponent.maxRows` (1,000) per tab, the first by position, then "... and N more"; sorted by primitive keys; `String.format` only for the rows shown |
| Opening the screen | `selectCategory` before `init` (nothing built for the old list), `init` marks its rows current: one build instead of three |

Offline: 50,000 missing and 9,000 errors build 1,001 rows each in 5 to 7 ms warm (13 to 38 ms
before), and the fill no longer grows with the square.

In-game checklist for `perf3-wip`:
1. Open the GUI (the key) on a big validated shape, on each tab: no freeze; the long tabs end with
   "... and N more".
2. With the menu open, hold + on a big shape and let it validate: no freeze while the list updates.
3. Clicking a row still highlights its position; the "and N more" row clears the highlight.
4. Polygon, Floor and Ceiling (below).

### Polygon: Floor and Ceiling (on `wip/perf3`)

Raphael asked for the polygons to work as an X-sided house; of the options (roof, wall thickness,
floor and ceiling, straight corners) he chose floor and ceiling. Two checkboxes after Even mode:
Floor fills the inside of the first layer along the depth, Ceiling the inside of the last one (with
a depth of 1 both give one full polygon, no block twice; a negative depth has its floor at the
lowest layer). Off, the polygon is exactly as before (40 random cases against the old loop).
Persistence: 8 values, the two new ones last; older saves load with both off.

Offline harness after step 3 and the polygon room: 1303 asserts, 0 failures; Bridge 466 / 159 / 360.

### Polygon building and Farming land (on `wip/polygon` and `wip/farm`, on top of `wip/perf3`; not merged)

- Polygon building (`59b16d2`, `7a60f52`): an X-sided house on the Y axis (walls with windows, floor,
  roof with rim, inner ring, spokes and skylight, pillars, relief: corner columns and edge bands,
  the roof's pattern under the floor); build focus parts Walls, Floor, Roof, Details, Pillars.
  Raphael tested the first version (`polygon-wip`, 2026-10-18): "the test passed".
- Farming land (`90da321`): where the water goes in a farm of any outline (rectangle, polygon,
  circle); crops on the cheapest 9 x 9 grid with water added wherever the outline leaves farmland
  out of reach, sugar cane in rows every third line; parts Water, Field, Edge. Not tested in game.

### Performance, step 4: what Litematica does that we did not (on `wip/perf4`, on top of `wip/farm`, waiting for the in-game test; not merged)

Raphael (2026-10-20): the mod is still much heavier than Litematica, which draws more and more
detailed things. The game's own timing lines (Fabulously Optimized logs of 2026-10-17 and 18) say
where: drawing is cheap (`phase=render avg=0.03` to `0.04` ms a frame at about 580 FPS, so not the
GPU), the stutters are single frames on the render thread:
- the 3D preview sent to the GPU whole, every time the shape changed with the menu open:
  `preview-rebuild ms=8` to `25` at 20,000 to 70,000 blocks (one buffer of up to 36 MiB);
- the preview snapshot `9` to `11` ms (a walk over a `HashSet<Long>` of 50,000 boxed positions);
- the scan's `publish=12` ms in the same frame as its last layers, and layers of up to 9 ms at
  NORMAL's 6 ms budget (a layer could not be split);
- `phase=render ... max=36.2` and `22.1` ms frames.

What Litematica does instead (its decompiled 1.21.11 jar, analysis only): the schematic is meshed per
16^3 section on worker threads and only the sections that change are rebuilt; the uploads have a
time budget; the verifier's colours are part of the same section meshes; its world data is in
primitive palettes, never a boxed object per block; its overlay can skip the sides shared by two
blocks of the same kind (`OVERLAY_REDUCED_INNER_SIDES`).

| What | Change |
|---|---|
| Expected set | `LongHashSet` (primitive, emission order) instead of `HashSet<Long>`: no Long and no node per block per generation; the preview snapshot and the scan copy are one array copy (the scan's copy under the shape's lock, the boxing after it on the worker) |
| Preview | the mesh in 16-block sections, sent a few per frame within the world upload's 3 ms budget while the previous mesh stays on screen; `preview-rebuild` gets `frames=` and `maxframe=` |
| Scan | a unit is 512 positions of a layer or one x row of its near sweep (it was a whole layer), so a frame goes over the budget by one row at most; the result is published on the next frame, never together with the last layers |
| Ignored blocks | resolved to the blocks once per change of the setting and compared by identity: the scan built a `namespace:path` String for every solid block it read (and so did every block event) |
| Overlay | the 4,000 nearest errors found with one primitive sort of the distances instead of sorting every entry with a comparator; the same cubes in the same order |

Measured offline (`Perf4Probe` in the session scratchpad, an unbuilt island of 37,100 blocks with
ground around it, 19,293 structure errors, warm JIT): the slowest scan frame at 6 ms went from 8.6
to 16 ms to 6.0 to 7 ms, the overlay from 3.9 to 4.8 ms to 1.6 to 2.9 ms, the snapshot from up to
34 ms (cold) to under 1 ms once warm. Not measurable offline: the preview upload (GPU) and the
world reads (the ignored-block String was per block read in game).

Left as they were: the publish itself (3 to 10 ms offline, now alone in its frame); the world
buffer's sections (about 3 ms a frame while a new shape is sent); the chat line after every
automatic scan.

In-game checklist for `perf4-wip`:
1. With the menu open, hold + on a big shape (50,000 blocks or more) and let it validate: the
   preview follows a few frames later without a tall bar in F3 + 2; `preview-rebuild` lines show
   `frames=` and `maxframe=` about 3 ms or less.
2. A scan of a big shape: `phase=scan ... maxslice=` close to the Scan speed budget (6 at Normal),
   never 9 or more; the counts and lists as before.
3. `phase=render ... max=` over a few minutes of building next to a validated shape: lower than the
   36 and 22 ms of 2026-10-17.
4. Ignored blocks (Configuration, e.g. scaffolding) still count as IGNORED; changing the setting
   takes effect on the next scan.
5. The preview, the overlay (red, yellow, orange, the white highlight), Area 3, Fill and the
   focus work as before.

Offline harness after step 4: 1375 asserts, 0 failures; Bridge 466 / 159 / 360.

Offline harness at the close: 406 asserts, 0 failures; Bridge reference sizes 466 / 159 / 360.
E6 (after two fixes), E7, P4 and E8 passed their in-game checklists before being committed
(E7 7 of 7, P4 9 of 9, E8 9 of 9). The ghost fix was committed on Raphael's go-ahead after the
`ghost-fix-wip` test; the result of that test was not reported to the session that wrote this
file, so it is not claimed here (see the backlog).

### Geometric capsule, Check errors and Find blocks (on `wip/visual`, on top of `wip/perf4`, waiting for the in-game test; not merged)

Raphael (2026-10-22): rename the polygon building to Geometric capsule and give its depth options an inner
depth that copies the outer roof's pattern to the inner ceiling; the structure errors get in the way, so a
checkbox to turn them on; the coordinates of the missing / wrong / ignored blocks are not wanted all the time,
so the preview goes down to the bottom with only the missing count under it, and the list becomes a topic of
Visualisation.

| What | Change |
|---|---|
| Geometric capsule | the new display name (class and saved key unchanged); Relief has Inner depth: the roof's pattern under the roof, inside the walls, that many layers down (part Details, persisted last) |
| Check errors | a checkbox in Visualisation > Display, under Highlight errors, **off by default**: no structure errors (solid blocks around the shape); the scan then reads only the shape's own positions (no box around it), block events around the shape do nothing. Turning it on rescans validated shapes. Saved with the world (last key) when persistence is on |
| Shape tab | the preview reaches the bottom row (40..236 at 270, it ended at 175); under it one line: the progress and "Missing N   built ok / total (p%)" (errors and wrong layer only when there are any) |
| Find blocks | Visualisation has two topics (buttons on the right): Display (as before) and Find blocks: the tabs Errors / Missing / Ignored / Wrong layer with counts and the list by coordinate; clicking a row marks it in the world. The Errors tab says when errors are not checked |

In-game checklist for `visual-wip`:
1. The shape list shows Geometric capsule; an old polygon building (preset or world) still loads.
2. Capsule > Relief > Inner depth 1 and 2: the roof's pattern appears under the roof inside; the skylight
   stays open; windows stay open.
3. Visualisation > Display: Check errors off (new default): no red cubes, the error count is gone, a
   scan of a big shape is faster; on: the errors come back after the rescan.
4. Shape tab: the preview fills the right side down to the line "Missing N ..."; no tabs or list there.
5. Visualisation > Find blocks: the tabs with counts, the list of coordinates, a click marks the block
   (white), Display goes back.

Offline harness after this stage: 1408 asserts, 0 failures; Bridge 466 / 159 / 360.

### Farming land: Cane layout (on `wip/cane`, on top of `wip/visual`, waiting for the in-game test; not merged)

Raphael (2026-10-22): the sugar cane had only straight rows of water; one water with four canes around it
should fit more cane in the same space.

| What | Change |
|---|---|
| Cane layout | Farming land > Farm > Cane layout: **Cross** (new, default) or Lines (as before). Cross: a water block with a cane on each of its four sides, repeated so every cane touches one water: 4 cane in 5 (80 %) against 2 in 3 (67 %) for Lines. It is the densest pattern there can be: a water block serves four cells at most and the diagonal does not count |
| At the outline | The pattern is cut by the outline (a hexagon, a circle): both mirror images and all 5 offsets are tried and the one with the most cane kept; cells left dry get a water of their own where it wins cane. A square of 60 x 60 gets 78.7 % cane (722 water); a 5 x 5 gets 7 water and 18 cane (the fewest water that reach every cell) |
| Rows along | only used by Lines now (hidden for Cross) |

In-game checklist: Farming land > Crop Sugar cane: the Cane layout row; Cross: a plus of 4 cane around each water
(Show field on); cane beside water everywhere, also in a hexagon or circle; Lines as before (Rows along back); an
old save of Farming land still opens.

### Water in the farm, and no chat (on `wip/water`, on top of `wip/smartfill`, waiting for the in-game test; not merged)

Raphael (2026-10-22): Fill should fill the farming land's holes with water, the player standing in the water
they want and the backpack drawing on it; blocks such as leaves, stairs and slabs take water in, so it has to
adapt; and every chat message goes.

| What | Change |
|---|---|
| Holes count | A farm's water cell is built when it holds a water source: a water block, or a waterlogged slab, stairs, leaves (before, water never counted) |
| Fill with water | The holes are a colour of their own in the Fill panel, filled with water (no block to pick). A bucket's worth per hole, as a bucket emptied there: into a dry slab / stairs / leaves, over grass; nothing where a solid block is (dig first) |
| Where the water comes from | Standing in an endless pool (a 2 x 2 water source: two sources beside, water or a solid block below): endless, nothing taken. Else: the worn Traveler's Backpack's tanks (Tanks upgrade), water buckets and tanks carried, water buckets in shulker boxes, and the water block you stand in. The panel says which: endless, enough for N of M, or none |
| No chat | Nothing goes to the chat any more: the validation line after each scan is gone; Fill shows its progress and how it ended (for 20 s) in the Shape tab's preview; Hand with no block writes "Hold a block" in red in the field; preset problems show in the Save menu; persistence failures go to the log |

In-game checklist for `water-wip`:
1. A Farming land (sugar cane), holes dug: Fill shows a "Water" colour; standing in a 2 x 2 pool it says endless.
2. Start: the holes fill with water, nothing taken from the inventory. Out of the pool: buckets go empty, the
   backpack's tank goes down.
3. A slab or stairs (or leaves) in a hole: it gets waterlogged. Dirt in a hole: skipped.
4. The holes with water count as built (the missing count goes down).
5. No line in the chat after a validation or a fill; the Shape tab shows "Filling ..." and then "Fill done ...".

### Smart fill and one filter (on `wip/smartfill`, on top of `wip/relief`, waiting for the in-game test; not merged)

Raphael (2026-10-22): one filter for the world and the preview, and a fill for survival by that filter: it
reads the inventory, the backpacks and the shulker boxes and completes the shape with those blocks, one block
or a mix per part, split by the colours.

| What | Change |
|---|---|
| One filter | The Shape tab's Part and Layer buttons choose what the world **and** the preview show (the preview had a status filter of its own: gone; the colours still show missing / built, the list is Visualisation > Find blocks) |
| Fill | Opens "Fill with your blocks": left, the colours of what the filter shows and is still empty (a button each beside its colour, how many blocks, what is chosen); right, the blocks you carry, counted with what the shulker boxes and the Traveler's Backpack (worn or carried) hold. A click puts a block in the selected colour or takes it out; two or more mix (evenly, each cell always the same). "You carry enough for N of M". Start: 32 blocks per tick, section by section, bottom up; the button reads Stop (p%) |
| As in survival | Each block is taken out of the inventory, then the worn backpack, carried backpacks, shulker boxes (creative: nothing taken). Placed as the player would: facing like a click on the top, doors / beds / tall flowers whole, nothing where the block cannot stand or something is in the way. A colour out of blocks stops there; the chat sums up |
| Singleplayer only | The blocks are taken on the world's own server; on another server the panel says so |
| /fill | The fill of the block in hand with /fill commands is gone |

In-game checklist for `smartfill-wip`:
1. Part Roof (or a Layer): the world and the preview both show only that; Part All: everything.
2. Fill: the panel lists your colours and blocks; shulker contents and the backpack's are counted in.
3. Choose one block, Start: the roof fills from the bottom, the inventory goes down, then the shulker box.
4. Two blocks: a mix. Run out: it stops, the chat says how many had no block.
5. Stop mid-way; the button shows Stop (p%) while it runs.

### Geometric capsule: blue relief (on `wip/relief`, on top of `wip/cane`, waiting for the in-game test; not merged)

Raphael (2026-10-22): the relief in a simple blue, like the parts of Farming land. The cubes outside the walls
(columns and bands) are `0x5B8DD9`; the building keeps the shape's colour; a world buffer made again for the
focus keeps both (`colourCube`). Only the colour: the automatic Fill button stays; a smart Fill for survival
is for later.

### Minecraft 26.3 (on `wip/mc263`, on top of `wip/water`; compiled, not run in the game; not merged)

Raphael (2026-10-24): urgently needed for 26.3. New module `fabric26.3` (Fabric only, `include 'fabric26.3'` in
settings.gradle; `common` is shared and untouched, so the 1.21.11 jar is unchanged). Java 25, Loader 0.19.5,
Fabric API 0.161.0+26.3.

How it was made: every file of `fabric1.21.11` three-way merged (`git merge-file`) with upstream's 26.2 port
(base = upstream's 1.21.11 at `d4b846f`), then fixed against 26.3 until it compiled, using upstream's 26.3 release
(0.4.9, decompiled to `BuildGuide-tools\decompiled\upstream-26.3-0.4.9`) as the reference.

| What 26.3 changed | What was done |
|---|---|
| Rendering API moved from `com.mojang.blaze3d.*` to `com.mojang.renderpearl.api.*` (buffers, pipeline, textures, commands) | imports; `RenderSystem.getCompiledPipeline(pipeline)` for `setPipeline`; `setVertexBuffer(0, buffer.slice())`; `drawIndexed(count, 1, 0, 0, 0)`; `Optional` clear colour; `VertexFormat.Mode` is `PrimitiveTopology`; pipelines use `ColorTargetState` / `DepthStencilState` (depth is reversed: the preview pipeline is GREATER_THAN_OR_EQUAL with writes) |
| No model-view stack (`RenderSystem.getModelViewStack` is gone) | a small stack in `RenderHandler` (`pushModelView`, `translateModelView`, `popModelView`, `getModelView`) that every draw uses |
| No frame graph pass to add: `LevelRenderer.addWeatherPass` and `prepareCullFrustum` are gone | the world is drawn at the head of `LevelRenderer.executeOutline`, as upstream does. The terrain frustum is not captured any more: the world buffer's sections are culled by render distance only |
| Screens extract render state (`extractRenderState`, `GuiGraphicsExtractor`) | `ScreenWrapper` draws the panel fills, then the widgets, then the texts and the 3D view |
| 3D preview: the picture-in-picture renderer has no buffer source, draws submitted nodes into a texture it keeps private | `PreviewRenderer` takes the texture and depth views (reflection on `textureView` / `depthTextureView`) and draws its own sections there; registered with `PictureInPictureRendererRegistry` |
| `BlockStateBase.blocksMotion()` is gone | `WorldProbe.blocksMotion`: solid, but not cobweb or bamboo sapling (1.21.11's definition) |
| Mouse buttons are numbered from 1 (left 1, middle 2, right 3; GLFW and common: left 0, right 1, middle 2) | `ScreenWrapper.glfwButton` translates them: a left click reached the screens as a right one, so the Origin / property headers, the name and the preview's drag did nothing (Raphael's test, 2026-10-24; everything else worked) |
| `ItemContainerContents.stream()` is `itemCopies()` / `size()`; no GLFW on the classpath; `GuiGraphics.drawString` is `text`; `Gui.hud.isHidden()`; `Minecraft.gui.screen()`; key type `KEYBOARD` | adapted |

Not verifiable offline: everything that draws. The mixins were checked against the 26.3 bytecode (the targets exist).
In-game checklist for the 26.3 jar: it loads (no crash at the title screen); the shape shows in the world
(with and without the depth test); the 3D preview in the Shape tab and the expanded one; the shape list and
buttons look right; Fill, the guideline placement and the validation.

### Layers as strata (on `wip/strata`, on top of `wip/mc263`, waiting for the in-game test; not merged)

Raphael (2026-10-27): the layers were meant to show a piece of ground lifted out of the earth and floating
(grass, dirt, stone, deepslate), and the three cuts by height did not look like it. Agreed: fixed thicknesses
(option C), the old cuts removed; boundaries shaded both by mixing and by patches of other blocks, so it looks
natural; up to 5 layers with generic names; a choice of the layer the spikes take, blending with the last one;
the Terrain on the same model (one engine, two axes). Old saves change how they look; their cuts stay saved and
do nothing.

| What | Change |
|---|---|
| The model | `ShapeLayers` rewritten: 2 to 5 layers, 1 the top. One layer is the **rest** and takes what the others leave. Layers above it are a thickness in **blocks from the column's top**; layers below it a **% of the column from its bottom**, so the bottom follows the island's underside. Per column: a thin column serves the top and bottom bands first and the rest shrinks first |
| Mix | 0 to 6 blocks each side of every boundary, where the two layers interleave (smooth noise plus a per-block one): ragged boundaries, not lines |
| Patches | Per layer: patches % (0 to 100 by 5) and size (1 to 16): blobs of that layer inside the bands next to it, covering about that share |
| Spikes | Island: "Spike layer" at the end of Spike shape (default the last layer); spikes and loose pieces take it; with patches on, the bottom layer's blobs reach into the spikes too |
| Terrain | The same bands over Height, all in % (from the peaks, from the base); a column takes the band its surface falls in. `TerrainLayers` is gone |
| Panel | The Layers section, 7 rows at most (fits 270 px): Layers box; Layers (count) and rest; Mix; Layer (the one being edited, with its colour); its block; its thickness ("Blocks" / "% bottom" on the Island, "% top" / "% base" on the Terrain; "Fills the rest" for the rest); Patches % and size |
| Defaults | Island: 4 layers, rest 3, mix 2: 1 block, 3 blocks, the rest, 25 % from the bottom with 15 % patches; spikes on layer 4. Terrain: 4 layers, rest 2, mix 1: 15 % of the peaks, the rest, 30 %, 15 % of the base |
| Elsewhere | Legend (preview, coloured by layer), the layer's number then its thickness: "1 1b", "2 3b", "3 rest", "4 25%" ("5 spots" for a layer of patches only, "5 -" for none); the Layer focus button cycles over the layers used; palette of five ids (Block 5); validation, Fill and the focus all ask `layerAt`, so they agree |
| Saves | Island 64 values (44 as before, then 20), Terrain 49 (30, then 19); older saves load with the defaults |

Harness: `StrataTest` (new), the layer tests adapted; suite OK=1471 FAIL=0, Bridge 466 / 159 / 360.

In-game checklist for `strata-wip`:
1. An Island, Layers on, Colour: Layer in the preview: a thin top layer, a few blocks under it, the rest, and a
   bottom that follows the underside, with ragged boundaries and some blobs of the bottom layer.
2. Change Layer (the edited one): its block, thickness and patches rows change with it; Layers 5: a fifth colour.
3. Rest on another layer: that layer says "Fills the rest" and the others keep their thickness.
4. Mix 0: straight boundaries; Mix 6: wide interleaving. Patches 0: no blobs.
5. Spike layer (end of Spike shape): the spikes change colour; Validation by layer agrees with the colours.
6. A Terrain, Layers on: peaks, middle, lower band and base, with the same rows (in %).
7. An island saved before: it opens (it looks different: the new layers).

### New interface, stage 1: the Oficina (on `wip/ui`, on top of `wip/strata`, waiting for the in-game test; not merged)

Raphael (2026-10-27) chose variant 1 of five prototypes (the Design canvas "BuildGuide UI redesign"). Stage 1 is
the Shape tab; the other screens keep the old tab bar for now (Shape brings you back).

| What | Change |
|---|---|
| Layout | Rail of sections on the left (Shapes, Projects, Scan, Check, Config), a side panel beside it, the 3D preview always in the middle, the properties as the accordion at the right edge (the inspector), and a status bar under the preview with the progress and Fill |
| Header | The shape's name is a button ("Floating island v"): it opens the shape list; the instance selector < 1/3 > and its name after it |
| Shapes | The registered shapes in two columns; picking one closes the list, so the preview gets the room back |
| Projects | Origin and the presets in one panel: Set origin, X / Y / Z (- field +, Enter applies), this instance's name (Enter renames), All instances..., Presets... |
| Scan | A placeholder that lists the five modes of the next stage |
| Check | Progress, built, missing, errors, wrong layer; Errors and blocks... (Visualisation), Exclusions... |
| Config | Opens the configuration screen |
| Preview | Coloured **by layer by default** (by status, a shape with Layers on looked one colour: the cause of the confusing strata test). Colour, slice, Part and Layer on two rows at its top while it is at least 150 px wide (with a side panel open at the minimum width it shows only the 3D view); the legend at its foot |
| Code | `IWidget.shiftX` (Fabric widgets move sideways), `Property.setX` moves made widgets, `ShapeScreen.propertiesX` is the inspector's x for `Shape.placeRow`; `BaseScreen.hasTabs`; `State.sidePanel` replaces `originOpen`; `ShapeLayout` bands for the status bar |

At 480 x 270: rail 44, preview 244 (side panel closed), inspector 188 with 226 px of accordion (it was 206).
Texts shortened to fit: the focus note is "Focus Spikes, Layer 4: n of N", a patches-only legend entry "5 dots".

In-game checklist for `ui-wip`:
1. Open the GUI: the rail on the left, the preview in the middle, the properties on the right.
2. Click the shape's name: the list opens; pick a shape: the list closes, the properties change.
3. Projects: Set origin and the X / Y / Z fields move the shape; Presets... and All instances... open and come back.
4. An Island with Layers on: the preview is in layer colours at once; Colour switches to status and back.
5. Check shows the counts; Fill in the status bar works as before; resize the window: the properties follow the
   right edge.

Stage 1b, the flat look (`ui-wip`, 2026-10-27, after Raphael asked to drop the Minecraft style, in the spirit of
Axiom): `common/.../screen/Theme.java` holds the prototype's colours; the Fabric widgets draw themselves flat
(`FlatStyle`): buttons (`ButtonImpl.renderContents`), text fields (an unbordered EditBox inset in our own box, clicks
on the whole box), the slider (track, accent handle) and the checkbox (`FlatCheckbox`, vanilla's cannot be
subclassed). ShapeScreen's panels, top bar, section headers and progress bar use the Theme. Same code in 26.3 with
its renamed API (`GuiGraphicsExtractor`, `extractContents`, `extractWidgetRenderState`, `text`, `centeredText`).
Check in game: buttons, fields and checkboxes look flat, hover and focus show, typing and clicking in a field
still work (also on its edge), the Layers checkbox toggles.

Stage 2a, the other screens as pages of the Oficina (`ui-pages`, 2026-10-28): the old five tabs are gone. Every
screen but the Oficina has a page bar at y 20..40: "< Workshop" (back to the Oficina with the section's panel
open; on an overlay it acts as Escape), the pages of its section as tabs with the current one underlined in the
accent, the section's name on the right. Projects: Shape list, Presets. Check: Visualization, Exclusions.
Config: Configuration. `BaseScreen.section()` says where a screen belongs. The chrome (bars, content ground),
the header text, the bottom progress bar, the Fill and Preview panels, Visualisation's find tabs and both lists
(field ground, no separators, the selected row with an accent bar) use the Theme.
Check in game: from Check open Visualization, switch to Exclusions with its tab, "< Workshop" comes back with
Check open; the same from Projects (Shape list, Presets) and Config; the lists scroll and select.

Stage 2b, the left column as the prototype (`ui-proto`, 2026-10-28): button styles of the prototype
(`IButton.setStyle`: ghost rows, rail, soft, primary, outline), rounded boxes, text without the Minecraft shadow.
The rail has no boxes and lights the open section. Shapes: a search field, then one scrolled column in groups
(Solids, Flat, Lines and curves, Nature, Builds) with a slim scrollbar; Terrain and Farming land moved to the Scan
panel. Placement (was Projects): X / Y / Z cards with - and +, At my position, the name, up to four instances of
this world and All instances, Save this project and Open a saved one. Scan: the five mode cards (next stage) and
the ground shapes. Validation: the percentage, a bar, Built / Missing / Wrong layer / Errors, Validate now while
not validated, Errors and blocks and Exclusions. Portuguese texts in `pt_br.json`.
Check in game: the wheel scrolls the shape list, the search filters it, picking a shape closes it; the origin
cards change the origin; the instance rows switch instance; the Scan panel picks Terrain and Farming land.

Stage 2c, the rest of the prototype's frame (`ui-full`, 2026-10-28):
- Header: the BG mark on the rail, the shape button "Island v", "1 of 3" between arrows, and at the right the
  switches Preview | World and (with Layers) Status | Layers. The Enabled checkbox is Config's "Show in the world".
- See in the world (`WorldView`): the loaded ground around the shape from the top in map colours
  (`IShapeHandler.topColours`, read again every 2 s or when the shape or origin changes), the shape's footprint in
  blue, a note on the scale. Our own view; OrthoCamera stays an optional extra for later.
- Status bar: "N blocks", a short bar, "82% built", wrong layer and errors when there are some, Fill as the
  primary button. The legend as chips at the preview's foot, the hint at its top right.
- Inspector: tabs (one per section, two rows past three) instead of the accordion; one tab is always open.
- Placement: At my position, At the crosshair block (`IShapeHandler.crosshairBlock`, origin on top of it),
  Rotate 90 degrees (shown, not clickable: rotation is not in the generator), three instances, the saved projects
  as rows with Open (loads like the preset menu).
- Scan: the five modes are selectable, Under / Above ask for a Y, Region for a corner; Scan itself is the next stage.
- Validation: Nearest to you, the three closest problems (wrong blocks and errors first, then missing); a click
  lights the block in the world.
- Config is a side panel: Show in the world, Generate in background, Remember shapes, Random list colours as on /
  off pills, then All settings.
Not yet from the prototype: the Layers tab's own editor (layer chips, the block card, thickness and spots bars,
"Make this the rest", edge blend), the number rows with a bar under them and Randomize, the Spikes tab's
Points | Filled switch, the scan result view and its uses.
Check in game: World shows the ground and the shape in blue; the tabs switch sections; At the crosshair block
moves the origin; Nearest to you lights a block; the Config pills switch and stay after reopening.

## 2. Final jars

Build output: `fabric1.21.11/build/libs/BuildGuide-Fabric-0.4.8.jar` (mod version is still 0.4.8).

| Item | Value |
|---|---|
| Active jar in the mods folder | `BuildGuide-Fabric-0.4.8-visual-wip.jar` (deployed 2026-10-22) |
| SHA-256 | `DB4512123777A4142D080196274785347D7D953753EEA1EFBA037205B14CE16D` |
| Source | `wip/visual` at `524503d` (on top of `wip/perf4` and the branches below): the build output and the mods copy have the same SHA-256; `VisualisationScreen`, `ShapeLayout`, `ShapePolygonBuilding`, `ValidationState` and the new texts checked inside. Against `perf4-wip`, entry by entry: the 11 declared entries, and `RenderHandler$PendingScan`, `RenderHandler$IgnoredBlocks`, `State$ActiveScreen` by line numbers only |
| Tested | the offline harness (1408 asserts); not yet in game |
| Next jar | none built |
| Previous jars | `BuildGuide-Fabric-0.4.8-perf4-wip.jar` (SHA-256 `226D31F57C6575A7CDE23B43B015E61431204FFAFF2923B8D5DE7914B2B8E51D`, commit `43c9c6f`), `BuildGuide-Fabric-0.4.8-polygon-wip.jar` (SHA-256 `8C9FBB5A6E40BD592E84F948DC2134EC6DF4C33B453E9DA04AAFEB9A52041503`, commit `59b16d2`; the polygon building passed in game on 2026-10-18), `BuildGuide-Fabric-0.4.8-perf2-wip.jar` (SHA-256 `F12384911780D9CC4EF0FEA0493CFFCF58E423BE12D82E820C12C7FEB0125B04`, commit `a4758b6`), `BuildGuide-Fabric-0.4.8-fill-wip.jar` (SHA-256 `4828F694FD843595F17B1A3522B171A5E6920012C7345D8C20660B202A54E58F`, commit `c502556`), `BuildGuide-Fabric-0.4.8-terrain-wip.jar` (SHA-256 `8F8720A6C522676F562DEBBCAC85DBA1CBD7F8C6ACF0C2CE75F97971D2E96638`, commit `2724d58`), `BuildGuide-Fabric-0.4.8-focus-wip.jar` (SHA-256 `6923FAB6A40E250901C68746389327DA9FF8573498C920B5D86621CB9132B09C`, commit `0ff9af7`), `BuildGuide-Fabric-0.4.8-palette-wip.jar` (SHA-256 `F0C0A242D361BB0E73BC4EF477EE274AA905C7ED8681154FF004227B438B26B0`, commit `e35fad3`), `BuildGuide-Fabric-0.4.8-leakfix-wip.jar` (SHA-256 `D0089DE876C5855AFF509974DFA2400037F1B5DCAD05E63B45A0E6C4095EDABF`, commit `481d42a`), `BuildGuide-Fabric-0.4.8-layers-wip.jar` (SHA-256 `14B66C4E9A572D5224C01F5217BBB087BB48BB8BE908909CF67408824AE2F7CC`, commit `c8a8579`), `BuildGuide-Fabric-0.4.8-scan-slices-wip.jar` (SHA-256 `A280A6440BB423878D6AAFA316709E852D63EF2EEBB3FF3F643E826C4724A067`, commit `7762309`), `BuildGuide-Fabric-0.4.8-spikes2-wip.jar` (SHA-256 `89F6B80A37150060E598EF0988446699E8B43F5E050267E83A9398909BE5D87B`, commit `3e63cbd`), `BuildGuide-Fabric-0.4.8-spikes-wip.jar` (SHA-256 `C3564E26FD888E46E106724E051C27C19D74EE962365FD9BB90775E77DE03A6E`, commit `a6d1c23`), `BuildGuide-Fabric-0.4.8-live-apply-wip.jar` (SHA-256 `8DB5202C0AFEC1F4645D0E4052852B188BE4CA9CF35C9FC969DF5CC092994198`, commit `416b5e0`), `BuildGuide-Fabric-0.4.8-island-ux-wip.jar` (SHA-256 `DD5B7BD8ADF1714F0BC152A303B6ED3F2FB2EA5E9C8F79034B52BD27DAE76557`, commit `07d554c`), `BuildGuide-Fabric-0.4.8-island-wip.jar` (SHA-256 `556D80E49975B12802361D5CB8971EE632415A7E841C0EC71D8AF06826C92DE9`, commit `f7937be`), `BuildGuide-Fabric-0.4.8-area3-wip2.jar` (SHA-256 `B5AC963F068EFF59954521F29F24D994BBFE61CF9F55C2C1DE9256E055392A32`, commit `584674b`), `BuildGuide-Fabric-0.4.8-area3-wip.jar` (first Area 3 test) and `BuildGuide-Fabric-0.4.8-ghost-fix-wip.jar` (SHA-256 `1E2F5E96A659D474EF14D9CDDBF5930E6B695DF66F31C782A7C1E5654E55BDB6`, commit `9c3fbde`), kept as `.bak` |

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
