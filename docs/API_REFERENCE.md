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

- `Map<Long, Byte> status` (packed local position → `UNKNOWN/OK/MISSING/WRONG`), wrong
  block names in a second map, `List<NearBlock{localPos, blockName, distance}>`.
- Scan protocol: `beginScan(expected)` (all `UNKNOWN`, not validated) → `setStatus(pos,
  status, name)` per position → `setNearBlocks(list)` → `endScan()` (validated = true).
- `setStatus` adjusts `ok/missing/wrong` **by the transition** (O(1)); positions not in the
  map are ignored. This is what incremental validation (2.2) will call per block event.
- `exclude(pos)` removes a position: it leaves the total, it does not become missing (2.3).
- Readers: `isValidated()`, `getOk/Missing/Wrong/Total()`, `getProgress()` (ok/total),
  `getStatus(pos)` O(1) (for the coloured preview, 1.x), `getPositions(status)` and
  `getWrongBlockName(pos)` (error lists, 2.4), `getNearBlocks()`.
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
`ValidationState.updateBlock(local, air, solid, name)`: an expected position becomes
MISSING / OK / WRONG by transition (~0.1 µs); any other position runs the same 5×5×5 near
test as the scan and adds or **removes** a near block (near blocks are keyed by position in
a `LinkedHashMap`; ~0.6 µs measured on a 50k-position state). Never touches the shape's
`expectedBlocks` set — shapes call `invalidate()` **before** `expectedBlocks.clear()` so a
regenerating shape is skipped. The scan log is one line: `[Build Guide] Validate - ok N,
missing N, wrong N, near N`.

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
`ok / total (pct%)`, plus `wrong N` in red when there are wrong blocks. Shapes that are
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
`screen.buildguide.validation`; Etapa 2.2 added `screen.buildguide.reset` and removed `resetsection`.
