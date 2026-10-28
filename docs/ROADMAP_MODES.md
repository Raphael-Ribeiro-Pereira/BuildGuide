# Roadmap: placement mode, island, terrain, tree, roof

Raphael's plan of 2026-10-05 for what comes after the GUI redesign. It is a decision record,
not a design: nothing below is implemented, each item gets its own investigation first
(`[PARE-1]`, javap on the mapped 1.21.11 jar, evidence before code) and its own approval.
Order is the order of work. Rules that apply to all of it are in `docs/STATUS.md`.

## The plan

1. **Area 3: place a block straight into the guideline.** See "Area 3 decisions" below.
   **Done** (2026-10-05, `229c3fe`..`99fa942`; see `docs/STATUS.md`).

2. **Island, phases 0-2: seeded noise, outline and body, organic edge (no colours).**
   - Noise with a seed that is saved.
   - *Outline* (the plan view): distance from the centre to the edge as a function of angle.
     Circle, square, polygon of N sides, and organic (through a Roundness parameter and noise).
   - *Body* (the depth profile): Bowl, Cone, Spike, Terraced.
   - Always a hollow shell, with the thickness as a parameter.
   - One column per position: no caves and no overhangs.
   - Flat top included (2026-10-05): a flat cap, the disc of the outline at depth 0, with no
     relief. Relief belongs to Terrain (item 5).
   - Block A (2026-10-05): shape `Island` registered at the end, sections Base, Body, Seed;
     Wall 1 to 3 (default 2); shapes Circle, Square, Polygon (3 to 12 sides), Organic;
     Roundness 0 (square) to 1 (circle); separate X and Z widths; rotation; edge noise amplitude
     and scale; Depth, Profile (Bowl, Cone, Terraced), Sharpness, Roughness; numeric seed saved
     with a `New seed` button.

3. **Island, spikes: focal points.**

4. **Palette per layer (its own stage, the riskiest), split in two (2026-10-05).**
   - **4C, visual only:** layers and their colours, in the world and in the preview. No
     validation change. **Done** (2026-10-07, `a981c8e`, `c8a8579`; see `docs/STATUS.md`).
   - **4D done** (2026-10-14, `1e3ef9c`..`e35fad3`; see `docs/STATUS.md`).
   - **4D, validation per block:** each layer's block is typed as an ID in the form
     `minecraft:stone` (no autocomplete); the field turns red if the ID is invalid; the text is
     persisted encoded (it must not break the comma-separated persistence).
   The rules below are the target of 4C and 4D together.
   - 4 layers by depth, with adjustable cuts; 1 block per colour; the layer colours are
     distinct from the status colours.
   - Rule: a block of the layer = green; a block from another layer's palette = orange; a solid
     block outside the palette = red; a layer with no block chosen accepts any solid block;
     air = missing.
   - Needs new statuses and a layer per position in `ValidationState`; status byte 3 stays
     reserved.
   - Generic infrastructure: the shape reports the layer.

   - **Build focus done** (2026-10-14, `8dd88d0`..`428c206`; see `docs/STATUS.md`): the world
     shows only a part (Cap, Body, Spikes, Hidden) and / or a layer; Part and Layer in the preview
     panel, Focus from (default 400) in Configuration.

5. **Terrain: a new shape, relief (mountains)** with its own outline and seed, positioned by the
   origin. One column per position. **Built, waiting for the in-game test** (2026-10-15,
   `wip/terrain`, `45abc73`..`f2943e0`; see `docs/STATUS.md`): a skin of 1 to 3 blocks over a plan
   (the Island's four Outlines, copied, never shared), Hills, Mountains or Dunes, Height <= 3 x
   Scale; layers and the palette by height.

6. **Tree: an experiment with a deadline** (round canopy and bonsai before the willow).

7. **Roof: last.**

Satellites (loose rocks) only if Raphael asks for them.

## Island: pending from the block A test (2026-10-06)

Block A (`3fe71f7`..`f7937be`) passed in game with `island-wip`. Raphael's feedback, not yet
implemented:

1. **Only the controls that apply.** Sides serves only Polygon: show only the controls that apply
   to the chosen Outline.
2. **Neutral defaults, clearer labels.** Defaults become a smooth circle with no noise and no
   Roughness; labels get clearer. A `Naturalize` action creates the natural island.
3. **More than one cone underneath** is block B (spikes, item 3 of the plan).
4. **Wall is unclear:** rename it to `Thickness` and explain its effect.
5. **Seed becomes Randomize,** with a percentage for Base and one for Body, plus Naturalize.
6. **Apply values without Enter and without freezing:** debounce on the fields; 3 modes for
   updating the world; measure before optimising. "Reveal in sequence" goes to the backlog.

## Area 3 decisions (already taken)

- A right click aimed at an empty position of the guideline places the block in hand there. One
  click, one block.
- It consumes from the inventory and respects the reach, as in survival, in any game mode.
- Orientation is always the default. It never breaks blocks.
- A key turns it on and off, with an indicator on the HUD.

Settled after the `[PARE-1]` investigation (2026-10-05):

- Orientation is what vanilla gives for a click on the top face of the cell; blocks that face
  the player (stairs, furnaces) still face the player.
- Strictly one block per key press: it places only on the falling edge of the use key (a press
  that follows a tick with the key up), not while the key is held.
- Cells inside an exclusion box are not targets.
- Reach is the player's `blockInteractionRange` attribute.
- The on/off state lives in memory in `State` (not persisted); the default key is `G`.
- The click is replaced by substituting the value of `Minecraft.hitResult` inside
  `startUseItem` (a value substitution, not a cancel), so other mods' hooks on the same
  method keep working. Test case for `[PARE-5]`: Click Through Plus assigns `hitResult` in
  that same method.

Phases: `[PARE-1]` investigation (no code); `[PARE-2]` voxel raycast (DDA) in `common`, pure
and tested; `[PARE-3]` outline of the target position in the world; `[PARE-4]` toggle key and
HUD indicator; `[PARE-5]` replacing the click at the approved point; `[PARE-6]` decompile-diff
declared beforehand and deploy `area3-wip`; `[PARE-7]` commit and push.

## Backlog: findings from the live-apply timing log (2026-10-07)

From Raphael's `latest.log` with `live-apply-wip`. Not implemented; measure again after each fix.

1. **preview-rebuild (render thread): 13 ms at 7k blocks, 36 ms at 49k, 81 ms at 107k, and twice
   per change**, the first time over the old model (eight cases in a row). Likely cause, to
   confirm: a generation starts by invalidating the validation state, so `PreviewController` makes
   a new instance of the old model with fresh colours (`withValidation`), and the renderer
   rebuilds its mesh for it before the new geometry's snapshot arrives. Rebuilding once per
   generation would halve the cost.
2. **scan (render thread): 15-18 ms at 45k blocks with 3 errors; 99-128 ms at 72-75k blocks with
   thousands of errors.** The cost grows with the error count, not only the block count.
3. **generation (its own thread): 23-90 ms.** No problem: it does not run on the render thread.
4. **world-buffer and preview-snapshot never went above 8 ms.** Checked in the code (2026-10-07):
   both are recorded every time they run (`AbstractRenderHandler.renderShapeSetDeferred` around
   the buffer upload, `PreviewController.update` around `PreviewModel.snapshot`), and both run on
   every applied change, so they did run and stayed at 8 ms or below. A lower threshold for one
   session would show their real values.
5. **107 518 blocks seen, above the ~84k worst case in `STATUS.md`.** Confirmed: the old figure
   was Wall 2 only. At radius 60, depth 80, Wall 3 gives 92 139 (Circle) to 121 636 (Square)
   blocks; the limits table in `STATUS.md` is corrected.
6. **The 50-line cap of the timing log was reached in about 5 minutes:** raise
   `TimingLog.maxLines` to 200.

## Island spikes 2: requests after the block B test (2026-10-07)

Raphael approved `spikes-wip` (mark 9). Requests for the next round, in three phases (each its own
commits, so a merge can stop at the last phase that passes):

1. **More spikes:** Count up to 64.
2. **Third mode, Fill:** a regular sunflower spiral (golden angle) covering the whole outline, with
   a seeded Jitter %.
3. **Dripstone style** (checkbox): a thin needle on a thick base, with a 1-block tail.
4. **Taper** (the curve of the narrowing) and **Edge falloff %** (longer in the middle, shorter
   towards the edge).
5. **Naturalize spikes** button and a **Spikes %** slider for Randomize (the spikes' shape).
6. **Break:** Attached or Segmented (a part falling), 2 to 4 pieces with a gap.

## Findings from the spikes-wip timing log (2026-10-07)

- **The double preview rebuild is gone:** one rebuild per generation, plus one for the colours
  when its scan lands (`reason=validation`, by design).
- **Scan:** with 0 errors it took 24-44 ms at 22-53k blocks, so the earlier hypothesis that the
  scan's cost depends mostly on the error count is weak: the block count (and the structure-error
  sweep around the shape) matters as much.
- **world-buffer:** 12-15 ms, now above the 8 ms threshold (it never was before).

## Spikes 2: outcome and what stays pending (2026-10-07)

Built on `wip/spikes2` in three phases (`73fcd08` cap 64, Fill, Jitter, edge falloff; `b23952d`
Taper, Dripstone, Spike shape, Spikes %, Naturalize spikes; `3e63cbd` Segmented), after the
GOLDEN-SPIKES capture `ceb4479`; installed as `spikes2-wip` for Raphael's test, not merged yet.

- **Spike cap: 64.** Worst case measured (121 x 121, depth 80, Wall 3, 64 spikes 40 long with base
  8, Taper 0.5, Segmented with 4 pieces and gap 1, Fill): **278 151 blocks, about 178 MB** of vertex
  buffer, 119 ms of geometry, under the 400k-block / 260 MB limit, so the cap was kept.

**Not done in this cycle, still pending:**

1. **Scan speed: the scan in Y layers.** Spreading the validation scan over layers of Y (so a
   large shape is scanned in slices instead of in one frame) was not started.
2. **Preview investigation:** the remaining preview cost (one rebuild per generation plus one for
   the colours) was not measured further.
3. **world-buffer investigation:** the 12-15 ms upload seen in the spikes-wip log was not
   investigated.

Item 1 was done right after (`6802ac9`..`7762309`, merged 2026-10-07; see `docs/STATUS.md`).

## Backlog: the world stutter (2026-10-07)

Reported by Raphael after the `scan-slices-wip` test, not investigated yet:

- **The stutter comes when the blocks enter the world.** With World update mode On close, editing
  runs smoothly; the stutter comes when the menu is **closed**, which is when the new shape is
  applied to the world.
- **The preview is not the culprit.**
- **The timing log has shown no world-buffer line since `spikes2-wip`** (the `spikes-wip` log had
  12-15 ms at 22k-51k blocks).

Investigated in the 4C stage (phase 1, `487fbe6`):

- **Where the buffer is built and sent:** the vertices are written on the generation thread into a
  native `ByteBufferBuilder`; on the render thread `ShapeBuffer.end()` sends them to the GPU (the
  driver copies them) and, when a larger shape appears, vanilla regrows its shared QUADS index buffer
  to twice the need, generated on the CPU.
- **world-buffer did not disappear:** nothing in its timing path changed between `spikes-wip` and
  `spikes2-wip`; it stayed under 8 ms. The 12-15 ms lines were the first two applies of that session
  at new peak sizes, which fits the one-time index buffer growth.
- **What else runs in the frame that closes the menu (On close):** the scan starts in that same
  frame (its request is already due) and its preparation (world positions, tracked list, `SliceScan`)
  is not budgeted; the last frame of the scan publishes the result, also unbudgeted. Offline
  (`tools/harness/WorldCost.java`): preparation 7-9 ms at 35k blocks warm, 15-48 ms at 122k, 24-65 ms
  at 177k; publish 4-5, 22-39 and 28-45 ms; one copy of the vertices 1.2, 4 and 6 ms.
- **So the band upload (phase 2) was skipped.** Next candidates, not done: budget the scan's
  preparation and its publish; the scan preparation is also rebuilt every frame while the chunks
  under the shape are not loaded.
- **Native memory leak** found on the way: `ShapeBuffer` never closed its `ByteBufferBuilder`, so
  every generation (also the discarded ones), preview mesh and overlay left about 384 bytes per block
  behind. Fixed in `481d42a` (see `docs/STATUS.md`).
- Vertex size, for the figures in `docs/STATUS.md`: 24 vertices x 16 bytes = 384 bytes per block of
  vertex buffer; with the shared int index buffer (36 indices, allocated at twice the need) the GPU
  peak is up to 672 bytes per block, the figure used there.

## Pending: Raphael's full test (after `leakfix-wip`, 2026-10-14)

`leakfix-wip` was approved in normal use; the deep test is left for the end:

1. **Memory:** the javaw memory before and after 20 to 30 edits.
2. **native= stays level** in the timing log.
3. **Stress test:** Randomize / Undo, opening and closing the menu, switching World update mode,
   removing a shape set, resizing the window, the G key.
4. **Test 10:** the worst case with 64 spikes.
5. **11-px accordion headers** in Cone and Bridge, at heights 270 and 360.
6. **The stutter when closing the menu** in the 3 modes, with the world-buffer lines and the scan's
   `start=` / `publish=`.

Open decisions:
- Spike cap: 64 or 40.
- The empty Spike shape header (Count 0).
- The hidden Seed row.
- The meaning of Pieces 2.

## Backlog (2026-10-14)

- **Take the scan's preparation and its publish out of a single frame**, after the numbers of the
  full test.
- **A possible pull request of the leak fix to the original repository** (brentmaas/BuildGuide).

## Pending after 4D (2026-10-14)

- **The scan's preparation and publish out of a single frame:** waiting for the log of Raphael's full
  test (the world-buffer lines and the scan's `start=` / `publish=`).
- **Raphael's full test:** the checklist is in his own document (the list above, "Pending: Raphael's
  full test", is what it covers so far).

## Pending after the build focus (2026-10-14)

- **The scan's preparation and publish out of a single frame** (as above, waiting for the full
  test's log).
- **Raphael's full test.**
- **Idea: scan only the focus.** Today the scan, the totals and Validate always cover the whole
  shape; scanning only the blocks in focus would cut the scan's cost too. Not decided.

## Next: measure, then optimise the world rendering (2026-10-15)

Why the mod is heavier than Litematica (read from Litematica's own code, decompiled locally for
analysis only; details in the session notes):
1. Every cube draws its 6 faces; Litematica drops the face between two neighbours. A 107k-block
   Island (Wall 3) has 645k faces (39 MB); only 16% are exposed. Our cubes are 0.6 wide by
   default, so the inner faces show: dropping them changes the look (a decision for Raphael).
2. One buffer per shape, drawn whole every frame and remade whole on any change; Litematica uses
   16^3 sections with frustum and render-distance culling, rebuilds only what changed on worker
   threads and uploads with a time budget per frame.
3. The scan prepares and publishes in one frame on the render thread with boxed per-block
   collections (several `HashSet<Long>` / `HashMap<Long, Byte>` of ~6 MB each at 107k).
4. The preview is a second full mesh, rebuilt every 100 ms while the validation changes.

First step (no code): measure in the same profile with JFR and `modlab report`, following
`docs/TUTORIAL_DESEMPENHO.md`, with the shape visible, with Part Hidden, and while editing; then
the same with Litematica. Then choose between 16^3 sections, face culling and the scan.

Step 1 (2026-10-16, `wip/perf`, on top of `wip/terrain`, waiting for the in-game test): point 2.
The world buffer in 16^3 sections, drawn only within the render distance and the terrain's
frustum, in one render pass; a new shape sent a few sections per frame (3 ms) and swapped in
whole; the `phase=render` line every 30 s and `sections=`, `frames=`, `maxframe=`, `gpu=` on
`world-buffer` to measure it. Next, in this order unless the measurements say otherwise:
- Point 3: the scan's preparation and publishing spread over frames, with `long` collections
  instead of boxed ones (no decision needed).
- Point 4: the preview not rebuilt whole every 100 ms while the validation changes.
- Point 1: dropping hidden faces, after Raphael chooses the look (only the surface cubes, or a
  full-cube option).

Step 2 (2026-10-16, `wip/perf2`, on top of `wip/fill`, waiting for the in-game test): points 3
and 4 plus the Fill, from the `fill-wip` log. The scan prepared on a worker thread, its layers
without boxing (an index and a bitmap), its result swapped into the state; the preview's statuses
read under one lock, rebuilt only when a colour changes, its mesh written on a worker thread; the
Fill one chunk section at a time. Still open: step 1's sections cost about 2.7 times one buffer to
send in total; point 1 (hidden faces) waits for the look.

Step 3 (2026-10-17, `wip/perf3`): the freezes Raphael still had (opening the GUI, big updates
with a validation; none with Enabled off) were the error list: one row per position in a vanilla
list whose fill is quadratic (50,000 rows about 3 s), built three times on opening and again every
100 ms while the validation changed. Now linear, at most 1,000 rows a tab, built once on opening.
Litematica's verifier, for comparison, lists mismatches grouped by block with counts. Grouping our
rows by block the same way is a possible next step.

## Plan of 2026-10-27: validation, new interface, Scan and its uses

Raphael's order. Each step gets its own investigation and approval, and nothing is merged without his in-game test.

0. **STOP: validate the layers (strata) in the game.** `strata-wip` is installed on the teste profile (2026-10-27,
   `b13ecd2`), and its checklist is in `docs/STATUS.md` ("Layers as strata"). No step below starts until it passes
   or its fixes are in. Then the same change goes to 26.3 (it already compiles there: `common` is shared).

1. **New interface, "Oficina" (variant 1 of the prototypes).** Prototype: the Design canvas "BuildGuide UI
   redesign" (the interactive board "1 · Oficina — protótipo interativo").
   - Three columns: section rail, side panel, the preview always on screen (never behind a button), and a
     property inspector with tabs (Forma / Camadas / Espinhos as the shape has them). A status bar with the
     progress and Fill.
   - The shape list collapses once a shape is chosen (the preview gets the room). The shape's name in the top
     bar opens it again.
   - Origin and Presets become one module, working name **Projetos**: where the shape stands (X Y Z, my
     position, the block in the crosshair, rotate), the instances in this world, and the saved ones.
   - Rail: Formas, Projetos, Scan, Validar, Config.
   - **Ver no mundo:** shows how the structure sits in the real place.
     - Our own view first, without any dependency: the loaded chunks, drawn top-down in map colours, with the
       shape over them.
     - **OrthoCamera** is an optional extra when it is installed (DimasKama, MIT, Fabric, branches up to 1.21.11
       and 26.3). It has no API: its state is the public static `OrthoCamera.CONFIG` (`enabled`, `fixed`,
       `fixed_yaw`, `fixed_pitch`, `scale_x`, `scale_y`), and it changes the game's own camera. So the button
       would set those fields by reflection, a soft dependency (yaw and pitch aimed at the shape, the scale
       fitted to it), close our screen while the ortho view shows, and restore the user's settings afterwards.
       Fields may change between its versions, so the integration checks them and falls back to our view.

2. **Scan (new rail button).** Reads the world around you into the preview, with real blocks.
   - Modes:
     - Scan island: the connected structure you stand on.
     - Scan under me: from you down to a Y.
     - Scan above me: from you up to a Y.
     - Scan region: X, Y, Z of a box.
     - Scan hole: the empty volume under water or inside the ground.
   - Singleplayer or loaded chunks only (client side); kept apart from the shapes, saved to its own file.
   - Shares its capture with the structure identifier (its Part 1); the recognition (Part 2) stays after.

3. **Uses of the scan** (later, one at a time):
   1. Relief under buildings: a construction sits on the scanned ground; the gap below becomes foundation.
   2. Farming land on the real ground: from the scanned floor, the water pattern that grows the most of the
      chosen crop.
   3. Terraform: brushes (raise, smooth, dig) over the scanned terrain, in the spirit of Axiom's.
   4. Lighting: where to put torches for the fewest torches and no dead spot (light 0). With "Slime chunk"
      checked, the minimum becomes light 2, since slimes spawn at light 1 (to confirm against the 1.21.11
      spawn rules before coding).

4. **Name and publication** (with care, not before Raphael chooses).
   - A name mixing "build helper" with Caroline.
   - The README says the mod derives from BuildGuide by Brentmaas, links it, and lists the mods that inspired
     it.
   - The licence stays CC BY-NC 3.0: credit and no commercial use.
   - Supported versions table, jars in GitHub Releases. Changing the mod id keeps old saves and configs
     readable.

### Additions of 2026-10-27 (after the first strata test)

- **Strata test inconclusive.** In the game the layers looked strange, and Raphael could not test them properly
  because the interface was unclear. In particular the preview stayed one colour with Layers on. Before the new
  UI, check whether that is a bug (the preview not recolouring) or the hidden "Colour: Status | Layer" toggle.
  The new UI must make the layer colouring obvious, and then the test of step 0 is repeated.
- **Fill in creative:** pick only the block(s). No quantity is needed, nothing is counted or taken, and Start is
  never blocked by "not enough".
- **"+" beside the shape name in the header:** add another shape relative to the current one: on top, below,
  inside, outside, or beside. This is composition (see `docs/COMPOSITION_ANALYSIS.md`).
- **World cache:** the shapes, their origins and these choices persist per world, even after the game is closed.
- **Presets:** up to 10 saved (from the current limit).

### Feedback of 2026-10-27 (the `ui-wip` test)

- **Strata: validated.** The layers come out right in the preview (coloured by layer). Step 0 passes on the
  system itself; what is left is how it is presented.
- **Labels with a comma are confusing.** "Layers, rest" and "Patches %, size" put two numbers on one row and
  say neither what they do. Each number gets its own row with a plain name, and every row a tooltip that says
  what it changes. Proposed:
  - "Layers, rest" -> "Layer count" and "Filler layer" (the layer that takes what the others leave).
  - "Mix" -> "Edge blend" (how far two layers mix at their border).
  - "% bottom" -> "Thickness %" (with "from the bottom" / "from the top" in the tooltip).
  - "Patches %, size" -> "Spots: amount %" and "Spots: size".
- **A "Naturalise" button is missing** in the Layers section: one click gives natural-looking values (blend,
  spots, an uneven border). What exactly it sets is for Raphael to confirm.
- **Look: drop the vanilla Minecraft style.** The layout is right; the widgets should look like the prototype,
  in the spirit of Axiom, which draws its own interface instead of Minecraft's stone buttons.
  - Flat buttons and fields drawn by us (`ButtonImpl`, `TextFieldImpl`, `SliderImpl`, checkbox), dark panels,
    one accent colour, a hover and a pressed state, the open section highlighted.
  - The tokens (colours, spacing) in one class in `common`, so 1.21.11 and 26.3 look the same.
  - A smoother font (a TTF font provider in the resource pack) is a possible second step, after the widgets.
  - This comes before the other stage 2 items, since every later screen is built on it.
