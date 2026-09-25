# API reference — Build Guide internals used by this fork

Scope: the `common` module APIs a shape author touches, plus the Fabric bridges. Written
from the code at branch `feat/cone-expanded`; update it whenever one of these contracts
changes. Paths are relative to the repo root.

## Module boundary

`common/` is loader-agnostic and **never imports `net.minecraft`**. Everything that needs
the game goes through a static handler on `BuildGuide` that Fabric implements:

| `BuildGuide.<field>` | Interface (common) | Fabric impl | Used for |
|---|---|---|---|
| `shapeHandler` | `shape/IShapeHandler` | `fabric/shape/ShapeHandler` | `newBuffer()`, `getPlayerPosition()` → `ShapeSet.Origin` (block coords, `floor`) |
| `widgetHandler` | `screen/widget/AbstractWidgetHandler` | `fabric/screen/widget/WidgetHandler` | creating buttons, text fields, sliders, lists |
| `screenHandler` | `screen/AbstractScreenHandler` | `fabric/screen/ScreenHandler` | translation (`translate(key, args)`), text drawing |
| `logHandler` | `ILogHandler` | `fabric/LogHandler` | `sendChatMessage(String)`, `error`, `debugOrHigher` |
| `renderHandler` | `AbstractRenderHandler` | `fabric/RenderHandler` | rendering; `validateShape(ShapeSet)` hook |
| `stateManager` | `AbstractStateManager` | `fabric/StateManager` | current `State`, shape sets, persistence |

`ShapeSet.Origin` is a plain `{int x, y, z}` in common — safe to pass around.

## Shapes (`common/shape/Shape`)

```java
public abstract class Shape {
    public ArrayList<Property<?>> properties;      // order = persistence order = default layout order
    protected ShapeSet shapeSet;                   // origin, colours, cube sizes
    protected abstract void updateShape(IShapeBuffer buffer) throws InterruptedException; // Catenary declares Exception
    protected void addShapeCube(IShapeBuffer, int x, int y, int z) throws InterruptedException;
    protected void setOriginOffset(double dx, double dy, double dz);
    protected ShapeSet.Origin getPlayerPositionLocal();   // player − shapeSet origin (fork addition)
    public void update();                          // schedules async regeneration
    public void onSelectedInGUI();                 // default layout: property i at y = 70 + i*20
    public void onDeselectedInGUI();
    public String toPersistence();
    public void restorePersistence(String data);
}
```

- Registration: `ShapeRegistry.registerShape(Class, "shape.buildguide.<name>")` in
  `BuildGuide.init()`. **Append only** — saved shapes reference the registry index.
- Local coordinates: everything a shape emits is relative to the shape set origin.
  `LocalPos.pack/unpackX/Y/Z` packs a local position into a `long` (21 signed bits/axis).
- Validation is built into `Shape` (Etapa 2.2c): the base class implements `IValidatable`
  (`getExpectedBlocks()` as packed longs, `triggerValidation()` / `consumeValidateRequest()`
  one-shot, `getValidationState()`). `addShapeCube` records every emitted position in
  `expectedBlocks` (the single emission funnel — no shape emits any other way), `doUpdate`
  does `validationState.invalidate(); expectedBlocks.clear()` before `updateShape` and
  `requestScan()` after. `addShapeCubeIfNew(buffer, x, y, z)` emits only if the position is
  new in this generation (Spline discs, Bridge sections) — one set serves dedup and
  validation. `addShapeCube` itself still emits duplicates (unchanged `nBlocks`/buffer for
  the other shapes); the validation total is the set size, i.e. distinct positions.
  `fabric/RenderHandler.validateShape` compares with the world, **fills the shape's
  `ValidationState`** and prints a chat summary read from it. Memory: the set costs
  ~62–76 B/block (sphere r=50 ≈ 1.9 MB, 100k ≈ 7 MB), about 10% of the vertex buffer the
  same shape already pays, so it is always kept.

### ValidationState (Etapa 2.1)

`common/shape/ValidationState` — live, **mutable, per-position, `synchronized`** state
held by every `Shape` in a `transient` field (never persisted):

- `Map<Long, Byte> status` (packed local position → `UNKNOWN 0 / OK 1 / MISSING 2 / IGNORED 4`;
  **byte 3 is reserved**, it was `WRONG` until 2.5 and is never reused), the IGNORED index, a
  `version` counter, ignored block names in a second map, and the structure errors
  `NearBlock{localPos, blockName, distance}` keyed by position.
- Scan protocol: `beginScan(expected)` (all `UNKNOWN`, not validated) → `setStatus(pos,
  status, name)` per position → `setNearBlocks(list)` → `endScan()` (validated = true).
- `setStatus` adjusts `ok/missing/ignored` **by the transition** (O(1)); positions not in the
  map are ignored. This is what incremental validation (2.2) will call per block event.
- `exclude(pos)` removes a position: it leaves the total, it does not become missing (2.3).
- Readers: `isValidated()`, `getOk/Missing/Ignored/Total()`, `getProgress()` (ok/total),
  `getStatus(pos)` O(1) (for the coloured preview, 1.x), `getPositions(status)` and
  `getIgnoredBlockName(pos)` (error lists, 2.4), `getNearBlocks()` / `getNearCount()` (the
  structure errors, 2.5).
- Shapes call `validationState.invalidate()` right where they clear `expectedBlocks` in
  `updateShape`: a regenerated shape is "never validated" again.
- Threads: the scan and the incremental updates run on the client main thread,
  `invalidate()` on the generation executor. Hence `synchronized` from day one.

**Incremental validation (Etapa 2.2).** `fabric/mixin/MixinClientLevel` injects at the
return of `ClientLevel.setBlock(BlockPos, BlockState, int, int)` — the single point where
server block updates, section updates, local placement prediction and block destruction
converge (chunk loads do not: that is what the Validate scan is for). It calls
`fabric/validation/IncrementalValidator.onBlockChanged`, which for every shape set with an
instantiated `IValidatable` shape does, cheapest first: `isInRange(local)` (validated **and**
inside the expected bounding box expanded by `nearRadius` = 2, ~0.01 µs), then
`ValidationState.updateBlock(local, air, solid, ignoredType, name)`: an expected position
becomes OK / IGNORED / MISSING by transition (~0.1 µs); any other position runs the same
5×5×5 near test as the scan and adds or **removes** a structure error (keyed by position in
a `LinkedHashMap`; ~0.6 µs measured on a 50k-position state). Never touches the shape's
`expectedBlocks` set — shapes call `invalidate()` **before** `expectedBlocks.clear()` so a
regenerating shape is skipped. The block name is resolved for every non-air block (once
per event, only when some shape is in range), so structure errors placed after the scan are
named too. The scan log is one line: `[Build Guide] Validate - ok N, missing N, errors N`.

**Automatic scan (Etapa 2.2b).** Incremental updates only work on a validated state, so
the full scan now happens without a click: `Shape.doUpdate` calls
`ValidationState.requestScan()` right after `updateShape` returns (a cancelled or failed
generation throws before it), and `RenderHandler.validateShape` — which already runs on
the render thread, in the first rendered frame after `ready` — treats the request like a
button press, with two gates for automatic requests only: the shape must have been idle for
`autoScanIdleMillis` = 300 ms (`Shape.getHowLongAgoCompletedMillis`; holding `+` keeps
resetting it, so one scan runs after you let go) and every chunk under the shape's bounding
box must be loaded (`ClientLevel.hasChunksAt(min, max)`; otherwise the request stays pending
and is re-checked each frame — a scan of unloaded chunks would read everything as air).
The Validate button remains the manual, immediate rescan. Measured scan cost (hash work,
world reads excluded): cone r20 h40 ≈ 5–13 ms, 160-block bridge over ground ≈ 38 ms (the
near pass over solid non-expected cells dominates), 50k-block solid ≈ 70 ms — a single hitch
per completed regeneration; spread it over frames only if shapes above ~100k blocks appear.
Known gap: chunks that reload after flying away do not trigger a rescan (nothing changes the
state while they are unloaded, so counts only drift if the world changed meanwhile);
the cheap future hook is Fabric API `ClientChunkEvents.CHUNK_LOAD` → `requestScan()` for
shapes whose bounding box intersects the chunk.

**Validate button (Etapa 2.2c).** The fixed row under the validation block holds two 78 px
buttons: `Validate` at `(5, 238)` (manual rescan of the current shape via
`Shape.triggerValidation()`, for every shape — the `Validate` *property* that Cone, Spline
and Bridge still carry is kept only because it is persisted, and calls the same method) and
`Reset` at `(87, 238)`.

**Exclusion rules (Etapa 2.3).** Two mechanisms, both applied in *validation only* — the
shape keeps drawing everywhere (`BlockOps.excludeAABB` is geometry exclusion, a different
thing, and still has no caller):

- *Ignored block types* — global, in `buildguide.cfg` as `ignoredBlocks` (new
  `Config.StringConfigElement`: free text, comma-separated ids, `minecraft:` prefixed when
  missing, parsed into a set on `setValue`; default `minecraft:scaffolding`). Edited in the
  Configuration screen (text field + Set/Default at y 230; `State.requestRescanAll()` after
  a change). The Fabric side resolves `BuiltInRegistries.BLOCK.getKey(block)` in
  `RenderHandler.isIgnored(BlockState)` and passes a boolean — common stays Minecraft-free.
  Effect: an ignored block is never a structure error; on an expected position it
  gets the status **`IGNORED`** (byte 4), which `adjust()` counts as **missing**
  (the structure is not there) *and* in a separate `getIgnored()` counter; the block name is
  kept in `ignoredBlockNames` and `getPositions(IGNORED)` feeds the yellow tag of 2.4.
- *Exclusion boxes* — per **ShapeSet** (they are about *where in the world*, not shape
  parameters; switching shape keeps them; the 15 shapes stay untouched). Four fixed slots
  `ShapeSet.ExclusionBox {enabled, min/max XYZ}` in local coordinates, edited in the new
  `ExclusionScreen` (fifth top-bar tab; the tabs are now five 96-px buttons, 5..485, which
  also fits a 480-px GUI — the upstream four 120-px ones ended at 500). Each box has an
  `On` checkbox, min/max rows with X Y Z fields, a `Pos` button per corner (player position
  minus origin) and a `Set` button. Persisted as `exclusions=on,x1,y1,z1,x2,y2,z2,…;` in
  `ShapeSet.toPersistence`; older jars ignore it because unknown keys fall into the
  `ShapeRegistry.getShapeId(key) == -1` branch of `restorePersistence` (**compatible both
  ways**). `ValidationState.setExclusionBoxes(list)` stores them; `beginScan` does not
  track excluded positions (total is right from the start), the scan's near pass skips
  excluded cells **without reading the world**, and `updateBlock` ignores excluded
  positions. After a change `ShapeSet.onExclusionsChanged()` pushes the boxes to every
  instantiated shape — `setExclusionBoxes` immediately `exclude()`s tracked positions that
  fell inside (consistent counters, immediate feedback) — and `requestScan()`s so a shrunk
  box gets its positions back. `RenderHandler` also re-pushes the boxes at every scan.
  Measured: bridge over ground, near pass 32 ms → 4 ms with a box on the ground.

**Error list and world overlay (Etapa 2.4).** Everything reads the *live* `ValidationState`:
it now carries a `version` counter bumped on every mutation (`getVersion()`), per-status
**index** (`LinkedHashSet` for IGNORED — WRONG had one too until 2.5 — maintained on
transitions, so `getPositions(IGNORED)` is O(k) in a deterministic, stable insertion order;
other statuses still scan the map) and a `highlightedPos` (−1 = none).

- *List*: `ValidationScreen`, sixth top-bar tab (tabs are now six 80-px buttons, 5..485;
  "Configuration" is the tightest at 67 px + 8 padding). One `ISelectorList` (the existing
  Fabric `ObjectSelectionList`, given a new `setEntries(List<Translatable>)` that keeps the
  scroll position). Since 2.5 the headers are, most actionable first: `Structure errors (n)`,
  `Ignored (n)`, `Missing (n)` (count only, last); rows `[x, y, z] Name (d=1.4)` in **world
  coordinates** (local + set origin), sorted by (x, y, z). Rows are rebuilt when `version`
  (or the shape) changed, **at most every 100 ms**; clicking a row toggles `setHighlightedPos`.
- *Overlay*: `common/shape/ValidationOverlay.build(buffer, state, playerLocal)` fills one
  `IShapeBuffer` with `CubeMesh` cubes: since 2.5, red **shells** on structure errors,
  yellow inner cubes (0.7) on IGNORED, and the highlighted position white (shell or inner
  cube, following its kind), drawn last. Colours are per vertex, so it is **one
  buffer and one draw call** whatever the count. `Shape` holds `overlayBuffer /
  overlayVersion / overlayBuiltAt`; `AbstractRenderHandler.renderShapeSet` calls the
  `renderValidationOverlay(shapeSet)` hook right after the shape buffer (same translation)
  when `State.isHighlightErrors()`; Fabric rebuilds the buffer when the version changed, at
  most every 100 ms, and closes it when there is nothing to draw. Cap `maxCubes = 4000`:
  only when exceeded, entries are sorted by distance to the player (nearest kept; the list
  still shows real totals). Measured (warm, no-op buffer): 3000 errors 0.9 ms, 6000 with
  the sort 2.3 ms, plus ~5 ms of `BufferBuilder` for 96k vertices — per rebuild, not per
  frame. `CubeMesh.push(IShapeBuffer buffer, double x, double y, double z, double s)` is the
  24-vertex cube (min corner `x, y, z`, side `s`) extracted from `Shape.addCube` (which now
  delegates, identical order) and is what the 3D preview should reuse. **It takes no colour:**
  call `buffer.setColour(r, g, b, a)` first; the colour applies to every vertex pushed until
  the next `setColour`. There is no `push(buffer, x, y, z, r, g, b, a)` overload.
- *Toggle*: `State.highlightErrors` (persisted as `highlightErrors=`, default true),
  checkbox "Highlight errors" in the Visualisation screen at (5, 255).

**Error concept and visible errors (Etapa 2.5).** An *error* is a solid block that deforms
the geometric form, not a wrong block on the guideline:

| World | Status / result |
|---|---|
| solid block on an expected position | `OK` |
| air or any non-solid block on an expected position (torch, flower, water) | `MISSING`, no name, no colour |
| ignored block type on an expected position | `IGNORED` (still counts as missing), yellow |
| solid, non-ignored block **not** in the shape within `nearRadius` = 2 (Euclidean: face 1, straight 2, diagonal 1.41 yes, 2.83 no) | **structure error** (`NearBlock`), red |
| same, but inside the cavity of a hollow shape | structure error too — the inner wall is in `expectedBlocks`, so no special case |

The fixed distance 2 has no slider. `WRONG` was removed (byte 3 reserved), with its counter,
index and `getWrong()`. `NearBlock` / `getNearCount()` keep their names: they *are* the
structure errors. Scan (`RenderHandler.validateShape`) and `updateBlock` use the same table.

*Why errors were invisible before 2.5*: the overlay drew a 0.7 cube centred in the cell and
the depth test is on by default, so a cube inside an **opaque** block was hidden by the
block itself; old orange near blocks were never seen, while WRONG (torch) and IGNORED
(scaffolding) are see-through and showed. Rule: **a marker for a solid block must enclose
it** — `pushShell` draws a cube `shellInset` = 0.01 outside each face (side 1.02), like the
vanilla selection outline; raise `shellInset` (e.g. 0.05) if the faces z-fight at a distance.
Inner 0.7 cubes are only for see-through positions. The 3D preview must follow the same rule.

Bar: `ok / total (pct%)  errors N`, red when N > 0. Harness: `NearDiagTest` (detection by
scan and incremental at d = 1, 2, 1.41, 2.83, 3; shell geometry) and `ClassifyTest` (the
table above; hollow sphere r=6 and a real hollow cone filled with stone flag exactly the
interior cells within 2).

**Reset (Etapa 2.2).** One fixed `Reset` button in `ShapeScreen` (see above; 78 px wide
since 2.2c, below the validation block, above the 270 px limit). `ShapeSet.initialiseShape` calls
`Shape.captureDefaults()` right after construction (before `restorePersistence`), storing
every persisted property's constructor value in an `IdentityHashMap`.
`Shape.resetShownToDefaults()` restores the properties currently `isShown` (the selected
section with sections, all of them without), skipping `protectFromReset(...)` ones and
value-less ones, then calls `update()` **once** (`setValue` never runs `onPress`). Spline and
Bridge protect their control points and `Point count`. The per-section Reset buttons of
Step 4 are gone (Bridge `Rails` is back to 9 rows).

The old Fabric-only `validation/NearBlock` and `ValidationResult` were removed; the state
lives in `common` so the GUI can read it.

**Progress bar.** Drawn by `ShapeScreen.renderValidation()` in the **left column under
the origin** (y 205–230, which is free; the property rows and the Validate/Reset row are
untouched, so no section grows): title, a 160×7 bar (`BaseScreen.fillRect` →
`IScreenWrapper.fillRect` → `GuiGraphics.fill`, the one Fabric addition) and the text
`ok / total (pct%)`, plus `errors N` in red when there are structure errors (2.5; it was
`wrong N`). Shapes that are
not `IValidatable` show `-`; validatable but never scanned shows `- / total` (the total is
known from `getExpectedBlocks()` without a scan). Green bar when complete, blue otherwise.

### Composition primitives (Step 0)

- `IBlockConsumer` — `void accept(int x, int y, int z) throws InterruptedException`.
- `ShapeCircle.enumerate(direction, radius, depth, evenMode, out)`,
  `ShapeCuboid.enumerate(dx, dy, dz, walls, centredOrigin, out)`,
  `ShapeLine.enumerate(deltaX, deltaY, deltaZ, out)`,
  `ShapeCone.enumerate(direction, baseRadius, height, evenMode, topRadius, mode, taper, layerThickness, out)`
  — `public static`, pure geometry in local coordinates, no shape instance needed. Each
  shape's `updateShape` calls its own `enumerate` with `(x,y,z) -> addShapeCube(buffer,x,y,z)`
  (Cone also records `expectedBlocks`). The `direction`/`walls`/`Mode` enums are public.
- `BlockOps.offset(dx,dy,dz,next)`, `clipAABB(min…,max…,next)`, `excludeAABB(…)` — consumer decorators.
- `CatmullRomCurve(int[][] points)` — `sample(seg,t)`, `tangent(seg,t)`, `getLength()`,
  `parameterAtLength(s)`, `sampleAtLength(s)` (arc-length table, 32 samples/segment, lazy).
  Ends are clamped (phantom points), matching the original Spline segment table. Accepts
  N >= 2 (N=2 is a straight segment); degenerate/zero-length curves map every arc length
  to the start point (tested: coincident points, all-equal points, s<0, s>len, NaN).
- `Profiles.filledEllipse(width, height, out)` — the one profile no existing shape
  provides (Circle/Ellipse are hollow rings). Emits in **section space**: `u ∈ [0,width)`,
  `v ∈ [0,height)`, `w = 0` — the same frame as `ShapeCuboid.enumerate(width, height, 1, …)`,
  so one placing consumer serves every profile.
- Rule: a composing shape never instantiates other shapes or touches their properties;
  it calls `enumerate` and emits through its own `addShapeCube` (keeps count/validation
  correct) with a `Set<Long>` for dedup.

### ShapeBridge (Step 1: curve + deck)

First composed shape. 2..5 control points (fixed slots + `Point count`), sampled **by
arc length** every `Sample step` blocks (default 1.0, always including the end); at each
sample a horizontal cross-section is placed. **Adaptive subdivision:** between two samples
the bridge measures how far the two lateral edges of the section moved
(`frameAt`/`edgeDistance`) and inserts intermediate sections until no edge jumps more
than `maxEdgeStep` (0.75) blocks — so a wide deck on a sharp bend has no holes whatever
the step (verified offline: L bend, width 9, identical 378 blocks at step 0.5 and 1.0).
Straight stretches cost nothing extra. Per sample:

- lateral normal from the horizontal tangent: `n = normalize(-tz, 0, tx)`; when the
  tangent has no horizontal component the previous `n` is reused (initial `(1,0,0)`) so a
  near-vertical stretch does not twist the deck;
- profile enumerated in section space and mapped with
  `x = round(cx + (u − (width−1)/2)·nx)`, `y = round(cy) − v`, `z = round(cz + (u − (width−1)/2)·nz)`;
- **the curve is the top row of the deck; thickness grows downward**;
- profiles: `Flat` = `ShapeCuboid.enumerate(w, t, 1, walls.ALL)` (filled), `Box` =
  `walls.NONE` (outline), `Disk` = `Profiles.filledEllipse(w, t)`;
- one `Set<Long>` dedups overlapping sections and is the `IValidatable` set.

**Vertical conventions, side by side:** the curve is the *floor line*. The deck's top row
is on the curve and `Thickness` grows **downward** (structure below the floor); rails start
`Rail elevation` rows **above** the curve and `Rail height` grows **upward** (protection
above the floor). Posts fill the rows between (`y0+1` up to the rail top).

**Rails (Step 2).** `Rail mode` None / Continuous / Posts only / Both; `Rail sides` Left /
Right / Both — *left* is `−n`, i.e. the left-hand side when walking from point 1 towards
the last point (`n = normalize(−tz, 0, tx)` is the right-hand side). `Rail profile` Square
(`ShapeCuboid` filled) / Round (`Profiles.filledEllipse`); `Rail width` × `Rail height` is
the rail cross-section; `Rail inset` moves the rail centre from the deck edge inward
(negative = outward): lateral centre `±((width−1)/2 − inset)`. Rails are emitted in the
same curve walk as the deck; the subdivision radius is the outermost element,
`ext = max(halfWidth, |halfWidth − inset| + (railWidth−1)/2)`. Posts: `Post spacing` is a
target — `n = max(2, round(length/spacing) + 1)` posts at `s = k·length/(n−1)`, always at
both ends; a post is a `railWidth × 1` column from `y0+1` to `y0 + elevation + railHeight − 1`
(so in *Posts only* the posts include the rail's height and read as a fence). All placement
goes through one `placeSection(f, lateral, yBase, ySign, w, h, kind)`.

**Pillars (Step 3).** `Pillar mode` None / On; `Pillar shape` Square / Round / Line /
Taper; `Pillar width`, `Pillar depth`, `Pillar spacing` (own spacing, same even spread as
posts), `Pillar taper`. Pillars are **volumes**, not 1-deep sections: `placeColumn(f, yTop,
depth, w, shape, taper)` maps a `w × w` horizontal footprint `world = c + u·n + v·t̂ − (0,row,0)`
where `t̂ = tangentOf(n) = (nz, 0, −nx)` is the horizontal unit tangent (verified in the
harness for 6 directions and visually on a diagonal bridge). They start at `yTop =
round(cy) − thickness` (the row under the deck) and go down `depth` rows, centred on the
curve. Square = `ShapeCuboid.enumerate(w, w, 1, ALL)` **per row** and Round =
`Profiles.filledEllipse(w, w)` per row — note `ShapeCuboid.enumerate(w, depth, w, ALL)`
would be a *hollow box* (with `dz > 1`, `ALL` means the six faces), which is why the
footprint is stamped row by row. Line = one block per row. **Taper is a round frustum
only** (a cone, not a square pyramid): `ShapeCone.enumerate(Y, r, −(depth−1), evenMode,
r·taper, SOLID, 1.0, 1)` with `r = (w−1)/2`, `evenMode = w % 2 == 0` (the cone's 0.5
offset is subtracted back to centre the footprint); `Pillar taper` is the base/top width
ratio — 2.0 = base twice as wide (the reference bridges), 0.5 = obelisk. A square taper
would be a new `Taper square` shape interpolating `w` per row.

**Step 4 fixes (after the first in-game test).**
- End pillars are pulled inward by `min(length/2, (w−1)/2)` along the curve so the whole
  footprint stays under the deck (harness: 100% under deck in +x/−x/+z/diagonal, even and
  odd widths). A pillar wider than the bridge is long still overhangs — unavoidable.
- `Rail profile: Round` is **hollow** (`Profiles.hollowEllipse` = filled ellipse minus the
  filled ellipse inscribed one block in; for `w ≤ 2` or `h ≤ 2` it equals the filled one, so
  it never goes empty). Deck `Disk` and pillar `Round` stay filled.
- `Sample step` is inert (adaptive subdivision caps section spacing at 0.75 whatever the
  step) and is hidden with `Shape.hideFromGui` — still in `properties`, so persistence
  stays aligned.
- `Post spacing` moved to the `Rails` section (layout only; persistence order unchanged).
- ~~Per-section Reset buttons~~ — replaced in Etapa 2.2 by the screen-wide Reset (see
  ValidationState section); `PropertyRunnable(run, name, xOffset, width)` remains available.

Persistence order: `p1x..p5z` (15), `Point count`, `Sample step` (hidden), `Profile`,
`Width`, `Thickness`, `Validate`, then Step 2: `Rail mode`, `Rail sides`, `Rail profile`,
`Rail width`, `Rail height`, `Rail elevation`, `Rail inset`, `Post spacing`, then Step 3:
`Pillar mode`, `Pillar shape`, `Pillar width`, `Pillar depth`, `Pillar spacing`,
`Pillar taper` (35 entries). Point rows and Reset buttons are GUI-only (see below), so
they take no persistence slots. Sections and row counts (selector + properties + last row
with Validate/Reset): `Shape` (count + up to 5 point rows) = 7; `Deck` (3) = 5; `Rails`
(7 rail + post spacing = 8) = **10 rows = 270 px, exactly the GUI height at scale 4** —
anything more must split (the Step 4 reset button that shared the Validate row is gone); `Supports` (6 pillar) = 8; Validate global. Registered last.

Offline harness: `BuildGuide-tools/bridgetest/BridgeTest.java` (property indices in its
header) — run it before any in-game test of Bridge changes.

## Properties (`common/property`)

`Property<T>` owns a value and its widgets. Widgets are created **lazily** on the first
`getWidgetList()` (triggered by `setY`/`setVisibility`/`addToScreen`) using the property's
current `x`/`y`. `IWidget` only has `setYPosition` and `setVisibility`, so `x` must be
final before the first layout.

| Class | Value | Widgets (offsets from `x`) | Notes |
|---|---|---|---|
| `PropertyInt` | int | label +5, `-` +90, field(50) +110, `Set` +160, `+` +190 | `-`/`+`/`Set` run `onPress`; typing alone does nothing |
| `PropertyMinimumInt` / `PositiveInt` / `NonzeroInt` | int | same | value guards; `setValueFromString` uses `>=` minimum (fixed in this fork) |
| `PropertyFloat` / `PositiveFloat` / `NonzeroFloat` | float | same layout | |
| `PropertyBoolean` | bool | checkbox | |
| `PropertyEnum<E>` | enum | `<-` name `->` | needs display names array |
| `PropertyRunnable` | Runnable | 210-wide button at `x`, or `(run, name, xOffset, width)` for a narrower one sharing a row | persists as `"Runnable"`; renders as a button (Validate, Set endpoint, Reset) |
| `PropertyCompactInt` (fork) | int | `-`(16) field(30) `+`(16) at `x+50+column*62` | no label, no Set; `commitTextField()` parses without `onPress` |
| `PropertyPointRow` (fork) | none | label +5, `Set`(26) at +236, `Pos`(26) at +264 | owns three `PropertyCompactInt`; Set commits all then one `onUpdate`; Pos fills from `IPositionSource` |
| `PropertyRangeInt` (fork) | int | same as `PropertyInt` | clamped to [min,max]; `-`/`+` disabled at the bounds via `IButton.setActive` |
| `PropertySection` (fork) | int | `<-` name `->` like `PropertyEnum` | panel section selector; lives in `Shape.sectionSelector`, **not** in `properties`, never persisted |

Row width budget: base `x = 180`; a `PropertyInt` row ends at 390, a point row at 470.
Keep rows ≤ 480 so they fit at GUI scale 4 on 1080p.

### Persistence contract

`Shape.toPersistence()` = `getStringValue()` of every property in list order, joined by
`,`. `restorePersistence()` splits on `,` and applies by index up to
`min(properties.size(), entries)`. Therefore:

- never reorder or remove properties of a shipped shape;
- new properties go at the end (older saves load with defaults for the tail; newer saves
  load in older jars by ignoring the tail);
- `getStringValue()` must never contain a comma;
- non-value properties (`PropertyRunnable`, `PropertyPointRow`) return a constant and
  accept anything in `setValueFromString`.

### Panel sections

A shape with many properties splits its panel with `Shape.declareSection(name)` (returns
an index; the first call creates the selector) and `assignSection(index, props...)`.
Properties never assigned are shown in every section (that is how Validate stays
visible). `getGuiProperties()` = `properties` + selector, and is what `ShapeScreen` adds
as widgets. The default `onSelectedInGUI` lays out: selector on row 0, then each property
of the current section or unassigned, in list order; shapes with no section keep the
original layout untouched. Changing the section re-runs `onSelectedInGUI`. Helpers for
custom layouts: `placeSectionSelector()`, `placeRow(row, props...)`, `isShown(p)`.

**GUI-only properties.** `Shape.addGuiOnly(p)` registers a property that gets widgets,
layout and visibility handling but is **not** in `properties` and therefore never
persisted. Use it for row owners and buttons (`PropertyPointRow` in Bridge). Spline still
keeps its rows in `properties` (they were appended before this existed and persist as
`"Row"`); do not change that.

**Hidden properties.** `Shape.hideFromGui(p)` keeps a property in `properties` (persistence
slot preserved) but out of `getGuiProperties()` and of the layout (`isShown` is false). Use
it when a property becomes inert (Bridge `Sample step`). Requires the shape to have
sections or a custom layout: the plain no-section loop in `onSelectedInGUI` is left
untouched on purpose.

### Variable point count (Spline pattern)

Persistence is by index, so a shape cannot add or remove properties at runtime. The
pattern is fixed slots (5 point rows) plus a `Point count` `PropertyRangeInt` appended at
the end: layout hides rows beyond the count, `updateShape` uses only the first N. New
splines default to 2; `ShapeSpline.restorePersistence` forces 5 when the save predates the
property, so old saves load unchanged.

### Custom layout

Override `onSelectedInGUI()` and call `setX`, `setY`, `setVisibility(true)` on each
property yourself. Layout is independent of list order — this is how `ShapeSpline` shows
15 point properties on 5 rows while keeping their persistence order. Row height is
`AbstractWidgetHandler.defaultSize` (20); base is `ShapeScreen.basePropertiesX/Y` (180, 70).

## Screens

- `ShapeScreen` draws the left column (shape selector, origin) and calls
  `addProperty(p)` for every property of the current shape; `BaseScreen.addProperty`
  just adds the widgets. No scrolling exists.
- `BaseScreen.shouldUpdatePersistence = true` marks state dirty; persistence is written
  on screen close when `config.persistenceEnabled` is on (off by default — enable it in
  the Configuration screen to test save/load).
- **One screen at a time.** `showScreen(x)` replaces the game's `Screen`; there is no stack.
  A sub-screen (`DropdownOverlayScreen`, `PreviewScreen`) keeps its `parent` and returns
  with `showScreen(parent)`, which runs `parent.init()` again on the same object (state
  kept). `BaseScreen.init()` starts with `properties.clear()` — before Step 0 every re-init
  appended the properties again and their labels were drawn twice.
- **Input hooks** (Step 0), neutral by default, called by the Fabric `ScreenWrapper` only when
  no widget handled the event (GUI coordinates, GLFW buttons `MOUSE_LEFT` 0 / `MOUSE_MIDDLE` 2):
  `onEscape()` (true = handled; default closes the GUI), `onMouseClicked(x, y, button,
  doubleClick)`, `onMouseDragged(dx, dy)`, `onMouseReleased()` (always called, so a drag
  state can end), `onMouseScrolled(x, y, amount)`. 1.21.11 signatures behind them:
  `keyPressed(KeyEvent)`, `mouseClicked(MouseButtonEvent, boolean)`,
  `mouseDragged(MouseButtonEvent, double, double)`, `mouseReleased(MouseButtonEvent)`,
  `mouseScrolled(double, double, double, double)` (there is no `MouseScrollEvent`).
- `ShapeScreen` fixed row at y 238: `Validate` 5..57, `Reset` 59..111, `Preview` 113..165
  (three 52-px buttons; the next row would pass the 270-px limit). Preview is inactive only
  when there is no shape set (`State.isShapeAvailable()`), not while a shape generates.

### 3D preview (Step 0)

A panel over the GUI showing the shape selected when it opened, coloured by validation.

- **Why picture-in-picture.** Since 1.21.6 the GUI is deferred and `GuiGraphics.pose()` is a
  2D `Matrix3x2fStack`: there is no 3D camera inside a screen. Vanilla draws 3D in the GUI
  (inventory entity, book, skins) with `PictureInPictureRenderer`: it renders into its own
  texture + depth (`RenderSystem.outputColor/DepthTextureOverride`), orthographic projection
  with **Y down** and z in [−1000, 1000], texture = area × GUI scale, pose
  `translate(w/2, getTranslateY) · scale(guiScale, guiScale, −guiScale)`, then blits the
  texture. `textureIsReadyToBlit(state)` = true reuses the last texture.
- **Registration.** `SpecialGuiElementRegistry.register(ctx -> new PreviewRenderer(ctx.vertexConsumers()))`
  (fabric-rendering-v1) in `onInitializeClient`; after the `GuiRenderer` exists it throws
  "Too late to register". States are routed by `getRenderStateClass()`.
- **common:** `PreviewScreen` (panel, snapshot, camera, input) → `IScreenWrapper.drawShapePreview(x1,
  y1, x2, y2, PreviewModel, PreviewCamera)` (default no-op, so other loaders compile).
  `PreviewModel` = immutable `long[] positions` + bounds, copied under `shape.lock` (`tryLock` +
  `ready`, "Generating..." otherwise); `withValidation(state)` returns a new instance sharing
  the positions with `byte[] status` (null when not validated → white), `long[] errors`
  (structure errors) and `stateVersion` (read **before** the statuses). Framing `radius()` is
  the box grown by `nearRadius` on every side, so the camera does not move when an error
  appears. `PreviewColours`: white E1E1E1 (not validated / UNKNOWN, i.e. excluded positions),
  OK 3CC850, MISSING AAB4C8 (blue-grey: plain 180 grey equals a white side face × 0.8),
  IGNORED E6C832, ERROR FF3C3C. `PreviewMesh.fill(buffer, model)`: cubes 0.92, shape positions
  by status then errors in red, one buffer, through `FaceShadedBuffer` (per-face factors in
  `CubeMesh` order −X −Y −Z +X +Y +Z: 0.6 0.5 0.8 0.6 1.0 0.8; relies on that order).
  `PreviewCamera` (mutable): yaw 45, pitch 30, zoom 1; `rotate(dx, dy)` 0.5°/GUI px, yaw in
  [0, 360), pitch clamped to ±89; `zoomBy(notches)` ×1.1 per notch in [0.25, 8]; `reset()`;
  `fitScale` = 0.9 × half the smaller side / radius × zoom.
- **Fabric:** `PreviewRenderState` (record: model, yaw, pitch, zoom, area, scissor from
  `guiGraphics.scissorStack.peek()`), submitted with `guiGraphics.guiRenderState.submitPicturesInPictureState`.
  `PreviewRenderer`: `getTranslateY` = h/2; `renderToTexture` composes
  `scale(1, −1, 1)` (Y up) · `scale(s, s, sz)` · `rotateX(−pitch)` · `rotateY(yaw)` ·
  `translate(−centre)`; `sz` keeps depth within ±900. Yaw 0 = viewer north (−Z); 45 = north-east,
  north face left, east face right. Mesh rebuilt only when the model instance changes; texture
  re-rendered only when model or camera changed. Pipeline `BUILD_GUIDE_PREVIEW`: depth write on,
  **cull off** (the flipped projection makes winding unpredictable).
  `ShapeBuffer.render(colour, depth, modelView, pipeline)` is the variant it uses; the world's
  `render()` delegates to it with the main target, `getModelViewMatrix()` and
  `getRenderPipeline()` — unchanged behaviour.
- **Refresh.** Geometry frozen at open; colours follow `getVersion()`, at most every 100 ms
  (same rule as the error list and the overlay). Measured, sphere r=50 (30,978 blocks): snapshot
  4.8 ms, colour snapshot 1.5 ms, mesh fill 4.7 ms CPU.
- Harness: `PreviewTest`, `PreviewColourTest`, `PreviewCameraTest`.

## Translation

`common/resources/assets/buildguide/lang/en_us.json`, alphabetical. `Translatable(key,
args...)` formats with `%s`. Keys added by this fork: `mode`, `topradius`, `taper`,
`layerthickness`, `thickness`, `validate`, `diameter`, `point`, `pointrow`,
`stepspersegment`, `fromplayer`, `shape.buildguide.spline`; Step 0.5 added `section`,
`section.shape`, `section.points`, `pointcount`; Step 1 added `shape.buildguide.bridge`,
`section.deck`, `samplestep`, `profile`; Step 2 added `railmode`, `railsides`, `railprofile`,
`railwidth`, `railheight`, `railelevation`, `railinset`, `postspacing`, `section.rails`,
`section.supports`; Step 3 added `pillarmode`, `pillarshape`, `pillarwidth`, `pillardepth`,
`pillarspacing`, `pillartaper`; Step 4 added `resetsection`; Etapa 2.1 added
`screen.buildguide.validation`; Etapa 2.2 added `screen.buildguide.reset` and removed `resetsection`;
Etapa 2.3 added `config.buildguide.ignoredBlocks(+Comment)`, `screen.buildguide.exclusions`,
`exclusionbox`, `exclusionshint`; Etapa 2.4 added `screen.buildguide.errors.{missing,wrong,ignored,near}`,
`highlighterrors`, `notvalidated`, `novalidation`; Etapa 2.5 added `screen.buildguide.errors.structure`
and removed `errors.wrong` and `errors.near`; Step 0 (preview) added `screen.buildguide.preview`,
`previewhint`, `previewgenerating`, `previewempty`.
