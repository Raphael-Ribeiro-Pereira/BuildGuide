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
- Validation opt-in: implement `IValidatable` (`getExpectedBlocks()` as packed longs,
  `consumeValidateRequest()` one-shot). `fabric/RenderHandler.validateShape` compares with
  the world and reports via `logHandler.sendChatMessage`.

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
  Ends are clamped (phantom points), matching the original Spline segment table.
- Rule: a composing shape never instantiates other shapes or touches their properties;
  it calls `enumerate` and emits through its own `addShapeCube` (keeps count/validation
  correct) with a `Set<Long>` for dedup.

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
| `PropertyRunnable` | Runnable | one 210-wide button at `x` | persists as `"Runnable"`; renders as a button (used for Validate, Set endpoint) |
| `PropertyCompactInt` (fork) | int | `-`(16) field(30) `+`(16) at `x+50+column*62` | no label, no Set; `commitTextField()` parses without `onPress` |
| `PropertyPointRow` (fork) | none | label +5, `Set`(26) at +236, `Pos`(26) at +264 | owns three `PropertyCompactInt`; Set commits all then one `onUpdate`; Pos fills from `IPositionSource` |

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
`stepspersegment`, `fromplayer`, `shape.buildguide.spline`.
