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
     validation change.
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

5. **Terrain: a new shape, relief (mountains)** with its own outline and seed, positioned by the
   origin. One column per position.

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
