  **Mitigated in Step 0.5:** `Shape.declareSection`/`assignSection` (enum-style selector, one section at a time). Spline uses Shape/Points sections, max 8 rows. Bridge should do the same.
# Composition analysis — can `ShapeBridge` reuse existing shapes?

Investigation for Etapa B. No feature code was written. Line numbers refer to branch
`feat/cone-expanded` at the time of writing (Spline compaction included).

**Short answer: yes, composition is viable — but not by calling `updateShape()` of child
shapes. The clean path is a small refactor that separates "enumerate blocks" from "emit
into buffer / count / persist" in the four pillar shapes. That refactor is ~½ day and
makes the rest of the bridge straightforward.**

---

## 1. Can a shape generate geometry at an arbitrary offset, N times, into one buffer?

**Partly, and not the way the question assumes.**

- `updateShape(IShapeBuffer)` emits through `addShapeCube(buffer, x, y, z)` with local
  **integer** coordinates — [Shape.java:127-131](../common/src/brentmaas/buildguide/common/shape/Shape.java#L127).
  There is no translation parameter anywhere in the pipeline: `addCube` writes vertices at
  `x + 0.5 - cubeSize/2` directly ([Shape.java:87-125](../common/src/brentmaas/buildguide/common/shape/Shape.java#L87)).
- `setOriginOffset` does **not** move the shape. It only moves the small origin cube
  ([Shape.java:133-141](../common/src/brentmaas/buildguide/common/shape/Shape.java#L133));
  shapes use it for the 0.5 "even mode" shift of the marker
  ([ShapeCircle.java:36](../common/src/brentmaas/buildguide/common/shape/ShapeCircle.java#L36)).
- The shape itself assumes **one origin per generation**: Cone and Spline clear their
  `expectedBlocks` at the top of `updateShape`
  ([ShapeCone.java:60](../common/src/brentmaas/buildguide/common/shape/ShapeCone.java#L60)),
  and every `addShapeCube` increments the **callee's** private `nBlocks`
  ([Shape.java:18, 130](../common/src/brentmaas/buildguide/common/shape/Shape.java#L18)).

So "call `updateShape` N times with a translating `IShapeBuffer` decorator" is technically
possible (`IShapeBuffer` is a 4-method interface, [IShapeBuffer.java](../common/src/brentmaas/buildguide/common/shape/IShapeBuffer.java)),
but it leaves you with: the child's block count (not the bridge's), the child's
`expectedBlocks` overwritten each call, no dedup between overlapping parts, and the child's
cube size read from the child's `shapeSet` (see §2). It is a dead end for counting and
validation (§6).

**What does work:** the geometry loops in the four pillar shapes are already pure
functions of their properties; they just happen to call `addShapeCube` inline
([ShapeCircle.java:32-59](../common/src/brentmaas/buildguide/common/shape/ShapeCircle.java#L32),
[ShapeCuboid.java:38-…](../common/src/brentmaas/buildguide/common/shape/ShapeCuboid.java#L38),
[ShapeLine.java:30-38](../common/src/brentmaas/buildguide/common/shape/ShapeLine.java#L30),
[ShapeCone.java:59-170](../common/src/brentmaas/buildguide/common/shape/ShapeCone.java#L59)).
Replacing that inline call with a callback (`IBlockConsumer.accept(x, y, z)`) turns each
into a reusable **block enumerator** that the bridge can invoke N times with N different
consumers (`(x,y,z) -> emitAt(px + x, py + y, pz + z)`). The shape's own `updateShape`
becomes a one-liner that passes `(x,y,z) -> addShapeCube(buffer, x, y, z)`, so existing
behaviour is unchanged by construction.

## 2. How coupled are `Shape` and `ShapeSet`?

**Loosely, and only through one field — but that field matters.**

- `Shape.shapeSet` is `protected` and is set by `ShapeSet.initialiseShape`
  ([ShapeSet.java:51-56](../common/src/brentmaas/buildguide/common/shape/ShapeSet.java#L51))
  after `ShapeRegistry.getNewInstance` ([ShapeRegistry.java:23-33](../common/src/brentmaas/buildguide/common/shape/ShapeRegistry.java#L23)).
- Inside shapes it is used for: shape cube size in `addShapeCube`
  ([Shape.java:128](../common/src/brentmaas/buildguide/common/shape/Shape.java#L128)),
  colours in `doUpdate` ([Shape.java:80-84](../common/src/brentmaas/buildguide/common/shape/Shape.java#L80)),
  origin for "Set endpoint"/"Pos" buttons
  ([ShapeCatenary.java:22-25](../common/src/brentmaas/buildguide/common/shape/ShapeCatenary.java#L22),
  [Shape.java:144-147](../common/src/brentmaas/buildguide/common/shape/Shape.java#L144)).
  Nothing in the geometry loops reads `shapeSet`.
- A `ShapeBridge` in package `brentmaas.buildguide.common.shape` **can** do
  `new ShapeCircle()` and assign `child.shapeSet = this.shapeSet` without registering the
  child (all constructors are public no-arg; `ShapeRegistry` is only consulted by
  `ShapeSet`/GUI). Nothing breaks.
- **But** configuring the child through its properties is the real problem: property
  fields are `private` (e.g. `ShapeCircle.propertyRadius`), and `Property.setValue`
  lazily creates GUI widgets via `BuildGuide.widgetHandler`
  ([PropertyInt.java:49-54](../common/src/brentmaas/buildguide/common/property/PropertyInt.java#L49)
  → Fabric `EditBox`). `updateShape` runs on the generation executor thread
  ([Shape.java:46-63](../common/src/brentmaas/buildguide/common/shape/Shape.java#L46)),
  so setting child properties from there would construct Minecraft widgets off the
  render thread. Avoid entirely.

**Conclusion:** instantiate nothing. Call the enumerators from §1 with explicit
parameters (`ShapeCircle.enumerate(dir, radius, depth, evenMode, consumer)`). The bridge
never touches child `Property` objects or `shapeSet` wiring.

## 3. Orientation along the curve tangent

**Not possible today; and for this design it is mostly unnecessary.**

- Every shape generates on fixed axes through a `direction` enum and a `switch` that
  permutes coordinates ([ShapeCircle.java:44-54](../common/src/brentmaas/buildguide/common/shape/ShapeCircle.java#L44),
  [ShapeCone.java:172-191](../common/src/brentmaas/buildguide/common/shape/ShapeCone.java#L172)).
  There is no rotation anywhere in `common`.
- Arbitrary rotation of an integer block set is lossy: rotating each `(x,y,z)` and
  rounding leaves holes on diagonals and duplicates elsewhere. Doing it properly means
  either supersampling (generate at 2–4× resolution, downsample) or inverse mapping
  (iterate the target bounding box and test the rotated point against the analytic
  shape). Both are real work: **~1–2 days** for a generic `rotateY(angle)` block
  transform with dedup, plus per-shape analytic tests if you want it hole-free.
- 90° multiples around Y are exact and trivial (coordinate swap) — **~1 hour** as a
  consumer decorator.

**Why it barely matters here:**
- Pillars are vertical by definition (axis = Y). No orientation needed.
- Deck and handrail are *curves*, and the Spline already builds curves orientation-free:
  it samples the Catmull-Rom curve and stamps a disc at each sample
  ([ShapeSpline.java:135-180](../common/src/brentmaas/buildguide/common/shape/ShapeSpline.java#L135)).
  The deck is "stamp a `width × thickness` slab at each sample"; the handrail is the same
  curve **offset** by `lateral · n(t) + (0, vertical, 0)` where `n(t)` is the horizontal
  normal `normalize(tangent × up)`. That is 15 lines of vector math, no shape rotation.

**MVP recommendation:** cardinal axes only, plus the offset-curve handrail. Note that
even the *deck* is not tangent-aligned in that MVP: a wide deck on a sharp bend will show
the same stair-stepping the Spline disc shows today. Acceptable for a first version.

## 4. Height cut (large-radius cylinder, only a band of it)

**Do it in the composition layer as a generic block filter — not a property per shape,
not a buffer change.**

- A per-shape property means touching every shape (and its persistence order) for a
  bridge-only need.
- A filter in `IShapeBuffer` would work on vertices, not blocks; it cannot count and
  would break the 24-vertices-per-cube assumption of `end()`.
- A consumer decorator `clipAABB(min, max, next)` that drops blocks outside a box is
  ~10 lines, composes with the offset decorator from §1, and **is the same primitive
  Etapa 2.3 needs for exclusion AABBs** (just inverted: drop *inside*).

Cost check: a Circle with radius 50 iterates a 101×101 box per depth layer
([ShapeCircle.java:38-40](../common/src/brentmaas/buildguide/common/shape/ShapeCircle.java#L38))
≈ 10k tests × depth — negligible even before clipping.

## 5. Generation cost for ~8 sub-shapes per cycle

**Scales fine.**

- One `Shape` = one generation task on a cached thread pool
  ([Shape.java:27, 46](../common/src/brentmaas/buildguide/common/shape/Shape.java#L27)).
  A bridge is one shape, so one task; children are just loops inside it. Interrupt check
  is per cube ([Shape.java:88](../common/src/brentmaas/buildguide/common/shape/Shape.java#L88)),
  and `update()` cancels the previous task when async is on
  ([Shape.java:40-42, 74-76](../common/src/brentmaas/buildguide/common/shape/Shape.java#L40)),
  so rapid `-`/`+` clicks do not queue up.
- Memory: 24 vertices × 28 bytes ≈ 672 B per block
  ([ShapeBuffer.java:34](../fabric1.21.11/src/main/java/brentmaas/buildguide/fabric/shape/ShapeBuffer.java#L34)).
  A 200-block-long bridge with a 5-wide deck, two rails and five r=2 pillars is ~3–5k
  blocks ≈ 3 MB. A sphere of radius 50 already produces ~30k blocks today.
- The only real cost driver is a user setting many `steps per segment` with thick discs;
  the Spline dedups with a `HashSet<Long>` ([ShapeSpline.java:135](../common/src/brentmaas/buildguide/common/shape/ShapeSpline.java#L135)),
  which the bridge must keep (see §6). HashSet of ~10k longs is trivial.

Risk to watch: not CPU, but **GUI regeneration frequency** — every property tick
regenerates the whole bridge. Same as every shape today; acceptable.

## 6. Block count and validation with a union of parts

**Free, if the bridge emits through its own `addShapeCube` (the §1 design) and dedups.**

- Counter: `getNumberOfBlocks()` returns the shape's own `nBlocks`
  ([Shape.java:163-166](../common/src/brentmaas/buildguide/common/shape/Shape.java#L163));
  the GUI reads it per current shape and sums over the set
  ([BaseScreen.java:59-63](../common/src/brentmaas/buildguide/common/screen/BaseScreen.java#L59),
  [State.java:176-179](../common/src/brentmaas/buildguide/common/State.java#L176)).
  If the bridge calls its own `addShapeCube`, the count is the union automatically.
- Validation: `RenderHandler.validateShape` only needs `IValidatable.getExpectedBlocks()`
  as packed `LocalPos` longs ([RenderHandler.java:95-122](../fabric1.21.11/src/main/java/brentmaas/buildguide/fabric/RenderHandler.java#L95)).
  The bridge keeps one `Set<Long>` and it is the union.
- The one thing you must do: **dedup at the bridge** (deck ∩ pillar top, rail ∩ deck
  edge). Without it `nBlocks` double-counts and the buffer draws duplicate cubes
  (harmless visually, wrong count). The Spline's `emitted` set is the exact pattern —
  the bridge's emit becomes `if(emitted.add(key)) { addShapeCube(...); }`.

---

## Recommendation

**Composition as designed is viable.** Minimal architecture:

### Step 0 — refactor (prerequisite, ~½ day, zero behaviour change)

**Status: done (Step 0 commit on `feat/cone-expanded`).** Verified by decompile-diff:
only structural differences in Circle/Cuboid/Line/Cone/Spline; see `API_REFERENCE.md`
→ "Composition primitives".

```java
// common/shape/IBlockConsumer.java
public interface IBlockConsumer {
    void accept(int x, int y, int z) throws InterruptedException;
}
```

- `ShapeCircle`, `ShapeCuboid`, `ShapeLine`, `ShapeCone`: move the loop body into a
  `public static void enumerate(<params>, IBlockConsumer out)` (or instance method taking
  explicit params); `updateShape` calls it with `(x,y,z) -> addShapeCube(buffer,x,y,z)`.
  Cone keeps its `expectedBlocks` bookkeeping in `updateShape`, not in `enumerate`.
- `ShapeSpline`: extract `catmullRom` + segment table into `common/shape/CatmullRomCurve`
  (`sample(t)`, `tangent(t)`, arc-length table for even spacing). Spline uses it; Bridge
  reuses it.
- Two consumer decorators in `common/shape/BlockOps` (or similar): `offset(dx,dy,dz,next)`
  and `clipAABB(min,max,next)`. (`clipAABB` inverted = Etapa 2.3 exclusion.)
- Verify with the decompile-diff trick: the rebuilt jar's Circle/Cuboid/Line/Cone must
  differ only in structure, not in emitted blocks — plus the in-game regression list.

### Step 1 — Deck (~1 session)

`ShapeBridge implements IValidatable`: 5 control points (reuse `PropertyCompactInt` +
`PropertyPointRow`), `width`, `thickness`, `steps`. Sample the curve by arc length, stamp a
`width × thickness` horizontal slab (cuboid enumerator with `offset`) at each sample,
dedup. Register **last** in `BuildGuide.init()`.

### Step 2 — Handrails (~½ session)

`lateralOffset`, `verticalOffset`, `railThickness`. Same sampling; position =
`p(t) + n(t)·lateral + (0,vertical,0)`, stamp a 1×`railThickness` column, once per side.

### Step 3 — Pillars (~1 session)

`pillarCount`, `pillarType` (`PropertyEnum`: Cone / Cylinder / Cuboid / Line),
`pillarRadius`, `pillarDepth`. Positions at equal arc length; for each, run the chosen
enumerator with direction Y, height = `depth`, through `offset(px, py − depth, pz)`, then
`clipAABB` if a band is wanted. Pillar-specific params (cone top radius/taper) can be a
second enum or fixed defaults for the MVP.

### Risks

| Risk | Severity | Mitigation |
|---|---|---|
| **Property panel height.** Bridge ≈ 16 rows (5 points + 2 deck + 3 rail + 4 pillar + steps + validate) = 70 + 16×20 = 390 px. At GUI scale 4 on 1080p the GUI is 270 px tall; Spline's 9 rows (250) already brushes the limit. | High | Add a `PropertyEnum` "Section: Deck / Rails / Pillars" whose `onPress` toggles `setVisibility` on the other groups — `Property.setVisibility` already exists ([Property.java:65-70](../common/src/brentmaas/buildguide/common/property/Property.java#L65)), so this is cheap (~2 h) and avoids building scrolling. Do it in Step 1. |
| Refactor regressions in Circle/Cuboid/Line/Cone | Medium | Enumerators are moved, not rewritten; decompile-diff + in-game checklist. |
| Stair-stepping on bends (deck not tangent-aligned) | Low (MVP accepted) | Later: 90° snapping of the slab to the dominant tangent axis, or full rotation (§3). |
| Double counting / duplicate cubes across parts | Low | Bridge-level `emitted` set (Spline pattern). |
| Even spacing of pillars along a Catmull-Rom curve | Low | Arc-length table in `CatmullRomCurve` (cumulative chord lengths, binary search). |
| Persistence growth (many properties) | Low | Append-only rule already in place; bridge is a new shape so no legacy saves. |

### Plan B (not recommended)

Writing bespoke geometry inside `ShapeBridge` would avoid the Step 0 refactor but would
re-implement circle/cone/cuboid tests (~the same code the enumerators already contain) and
would not give Etapa 2.3 its `clipAABB`. Only worth it if Step 0 turns out to break
upstream shapes in-game, which the decompile-diff check should rule out before any bridge
code is written.
