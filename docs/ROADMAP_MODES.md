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
