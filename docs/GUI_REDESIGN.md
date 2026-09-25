# GUI redesign — layout investigation

Status: **investigation only, nothing implemented.** Written at `c1c40c5` (baseline
`decompiled/STEP_PREVIEW`). Every number below was read from the code (file:line in the
evidence column) or computed from it; nothing is estimated from screenshots.

Decisions already taken (input to this document): left panel 188 px + divider 4 px + right
panel 288 px = 480 px; five 96-px tabs (Shape, View, Shapes, Exclusions, Config) from x = 0;
Validation tab removed; error list always visible under the preview; accordion sections
(one open at a time) with clickable headers and a count; inline preview follows shape
regenerations; an "Enlarge" button opens the existing `PreviewScreen`.

---

## 0. Findings that change the brief

These were not in the brief and each one changes the work. They come first on purpose.

| # | Finding | Evidence | Consequence |
|---|---|---|---|
| F1 | **A property row is 210 px wide; the left panel is 188.** Label at `x+5`, controls from `x+90` to `x+210`. | `PropertyInt.java:21-41` (−, field 50, Set 30, +), `PropertyEnum.java:21,25`, `ShapeScreen.basePropertiesX = 180` | Every property type needs a compact row. It touches all 16 `Property*` classes (widgets only, values and persistence untouched). |
| F2 | **A point row (Spline, Bridge) is 290 px wide**, wider than either panel. | `PropertyPointRow.java:21-24`: columns at `x+50`, 3 × 62, then Set 26 + From player 26 | Point rows need a different design, not just narrower offsets (§3.4). |
| F3 | **480 × 270 is not the player's screen size.** Size = framebuffer ÷ GUI scale. Raphael's `options.txt` has `guiScale:3`: about **640 × 360** on a 1080p screen (626 wide measured earlier, windowed). Auto scale gives 480 × 270 on 1080p, **427 × 240 on 1440p** (scale 6), 456 × 256 on 1366×768. | `…/Fabulously Optimized (1)/options.txt`; vanilla auto scale = largest scale with width ≥ 320 and height ≥ 240 | 480 × 270 must be the **minimum design size**, not the size. The layout must stretch (§2.3). Below 480 × 270 something has to give (Risk R1). |
| F4 | **The current GUI already overflows 270 px.** `Visualisation` slider at y 255 ends at 275; the Validate/Reset/Preview row ends at 258 (fine) but the property list of Bridge Rails ends at 70 + 10 × 20 = 270 exactly. | `VisualisationScreen.java:30`, `ShapeScreen.java:36-41`, `Shape.java:231` | The redesign also fixes this; the other tabs need their y shifted anyway (§2.4). |
| F5 | **There is no generation counter in `Shape`.** Only `completedAt` (private, ms, exposed as "ms ago"), written in `finally` **also for cancelled or failed generations**. | `Shape.java:49,77-90,273` | Needs a small addition (§3.2). |
| F6 | **Latent bug in `PreviewModel.snapshot`:** it checks `ready` but not `error`. A generation cancelled by a newer `update()` ends with `ready = true, error = true` and a **partial** `expectedBlocks` until the next one takes the lock. | `Shape.java:77-90` (InterruptedException → `error = true`, `finally` → `ready = true`), `PreviewModel.snapshot` | Rare today (the preview opens once). With an inline preview re-snapshotting on every regeneration it becomes likely. Fix in stage E1: `ready && !error`. |
| F7 | `PropertySection` (the ‹ Section › selector) is **GUI-only, never persisted**. | `Shape.getGuiProperties()` adds `sectionSelector` outside `properties` (`Shape.java:211-217`); `getStringValue()` returns a constant | The accordion can replace its widgets freely: no save risk. |

---

## 1. Height table (current screen, measured)

Font line height 9 px (vanilla). Widget default height 20 (`AbstractWidgetHandler.defaultSize`).

### Header and tabs (all screens, `BaseScreen`)

| Element | y from | y to | px | Evidence |
|---|---|---|---|---|
| Enable checkbox | 5 | 25 | 20 | `BaseScreen.init` `createCheckbox(5, 5, …)` |
| Close `X` | 5 | 25 | 20 | `createButton(width − 25, 5, …)` |
| Title (centred text) | 10 | 19 | 9 | `render()` |
| Block counters (two lines each side) | 5 | 29 | 24 | `render()` y 5 and y 20 |
| Tab bar (6 × 80, x 5..485) | 30 | 50 | 20 | `BaseScreen.java:30-36` |
| **Total header + tabs** | 0 | 50 | **50** | |

### Shape tab, left column (x 5..165)

| Element | y from | y to | px | Evidence |
|---|---|---|---|---|
| "Shape" title | 55 | 64 | 9 | `ShapeScreen.render` |
| Shape dropdown (160 wide) | 70 | 90 | 20 | `ShapeScreen.java:22` |
| "Origin" title | 100 | 109 | 9 | `render` |
| "Set origin" button | 115 | 135 | 20 | `ShapeScreen.java:23` |
| X / Y / Z rows (− field Set +) | 135 | 195 | 3 × 20 | `ShapeScreen.java:30-32` |
| "Validation" title | 205 | 214 | 9 | `renderValidation` |
| Progress bar | 217 | 224 | 7 | `renderValidation` |
| Progress text | 226 | 235 | 9 | `renderValidation` |
| Validate / Reset / Preview | 238 | 258 | 20 | `ShapeScreen.java:36-44` |

### Shape tab, properties (x 180..390)

| Element | Height | Width | Evidence |
|---|---|---|---|
| "Shape properties" title | 9 (y 55) | — | `render` |
| Any property row | 20, from y 70 | 210 | `Shape.placeRow`, `Shape.java:231` |
| `PropertyInt` / `Float` | 20 | 210: label 5..90, − 90..110, field 110..160, Set 160..190, + 190..210 | `PropertyInt.java:21-41` |
| `PropertyEnum` / `PropertySection` | 20 | 210: ‹ 90..110, name centred at 150, › 190..210 | `PropertyEnum.java:21-33` |
| `PropertyBoolean` | 20 | 160: checkbox 140..160 | `PropertyBoolean.java:20` |
| `PropertyRunnable` | 20 | per instance | `PropertyRunnable.java:27` |
| `PropertyCompactInt` column | 20 | 62: − 16, field 30, + 16 | `PropertyCompactInt.java` |
| `PropertyPointRow` | 20 | **290**: 3 columns from 50, Set 236..262, From player 264..290 | `PropertyPointRow.java:21-24` |

### Rows per shape (what the left panel must hold)

`properties.add` count per shape, minus hidden/GUI-only, plus the persisted `Validate` row:

| Shape | Rows today | Rows if `Validate` hidden (§3.5) |
|---|---|---|
| Cone | 9 | **8** (largest shape without sections) |
| Catenary | 7 | 7 |
| Grid, Parabola, Polygon, Polygonal pyramid | 6 | 6 |
| Others (Circle, Cuboid, Ellipse, Ellipsoid, Line, Paraboloid, Sphere, Torus) | 3–5 | 3–5 |
| Spline — Shape / Points | 5 / 8 (with selector and Validate) | 3 / 6 |
| Bridge — Shape / Deck / **Rails** / Supports | 8 / 5 / **10** / 8 | 6 / 3 / **8** / 6 |

### Validation tab (`ValidationScreen`)

| Element | y from | y to | px | Evidence |
|---|---|---|---|---|
| Title | 40 | 49 | 9 | `ValidationScreen.render` |
| List (x 5..475) | 50 | 265 | 215 | `listTop/listBottom` |
| Each row (headers and items) | — | — | **12** | `createSelectorList(…, 12, …)` |

---

## 2. Proposed layout (design size 480 × 270)

### 2.1 Vertical budget

| Block | y from | y to | px |
|---|---|---|---|
| Header row: enable checkbox, title, block counts (one line each side), close X | 0 | 20 | 20 |
| Tab bar: 5 × 96 at x 0, 96, 192, 288, 384 | 20 | 40 | 20 |
| Gap | 40 | 42 | 2 |
| **Content (both panels)** | 42 | 248 | **206** |
| Gap | 248 | 250 | 2 |
| Bottom bar: progress bar + text (x 4..284), Validate (288..380), Reset (384..476) | 250 | 270 | 20 |

The header needs the counts on one line each (today two lines, y 5 and y 20). That means
shorter labels ("Blocks: 1234", "Total: 5678"); the `x 64 + n` stack breakdown can move to a
tooltip or go. Decision D4.

### 2.2 Left panel (x 0..188, y 42..248 = 206 px)

Unit sizes: **section header 12 px** (clickable text row, `▸`/`▾` + name + count), **property
row 18 px** (vanilla buttons and edit boxes render correctly at 18; text is 9).

| Block | y from | y to | px |
|---|---|---|---|
| Shape dropdown (188 wide) | 42 | 62 | 20 |
| Gap | 62 | 64 | 2 |
| Accordion (headers + the open section's rows) | 64 | 248 | **184** |

Capacity of the accordion, `184 = headers × 12 + rows × 18`:

| Shape | Sections (Origin is a section too) | Headers | Rows left | Largest open section | Fits |
|---|---|---|---|---|---|
| Cone (worst plain shape) | Origin, Properties | 24 | 160 → **8** | 8 | ✅ exactly |
| Catenary | Origin, Properties | 24 | 8 | 7 | ✅ |
| Spline | Origin, Shape, Points | 36 | 148 → 8 | Points 6 | ✅ |
| Bridge as today | Origin, Shape, Deck, Rails, Supports | 60 | 124 → 6 | **Rails 8** | ❌ |
| Bridge with Rails split (§3.5) | Origin, Shape, Deck, Rails, Posts, Supports | 72 | 112 → 6 | Shape 6, Supports 6, Rails 5, Posts 3 | ✅ |
| Origin section itself | X, Y, Z rows + "Set to player" | — | — | 4 | ✅ |

Rule for the future: **at most 8 rows in a section of a plain shape, 6 in Bridge.** The Cone is
at the limit; one more property there must go into a second section.

### 2.3 Right panel (x 192..480, y 42..248) and the divider (x 188..192)

| Block | y from | y to | px | Notes |
|---|---|---|---|---|
| Preview (288 × 130) | 42 | 172 | 130 | "Enlarge" button 16 × 16 in its top-right corner (x 462..478, y 44..60), drawn over the texture |
| Gap | 172 | 174 | 2 | |
| Error list (288 × 74) | 174 | 248 | 74 | 12-px rows → 6 visible, scrolls |

At zoom 1 the preview fits the model's framing sphere into 0.9 × 65 = 58 px of radius
(`PreviewCamera.fitScale`), readable for a shape; the list shows the three headers plus 3
rows without scrolling. Moving 10 px between the two is a constant.

**Larger screens (F3).** Anchor everything top-left and give the extra space to the right
panel: `rightWidth = width − 192`, the preview takes the extra width and 60 % of the extra
height, the list the rest; tabs stay 96 px (centred or left, decision D1); the left panel
stays 188 × 206 (its rows do not grow). On Raphael's 640 × 360: preview 448 × 184, list
448 × 110.

### 2.4 Other tabs

Their content starts at y 50–70 today with absolute coordinates; with the header at 40 they
shift up by 8–15 px each, and `Visualisation` (F4) must fit its bottom sliders under 248.
Not redesigned here: same widgets, new y.

---

## 3. Technical decisions, with evidence

### 3.1 Tabs from x = 0, no borders

`ButtonImpl extends Button.Plain` (`ButtonImpl.java:8,18`): the widget occupies exactly
`x..x+width`, there is no outer border or padding, so 5 × 96 ends at 480. Text is centred and,
when wider than the button, vanilla scrolls it instead of cutting it
(`AbstractButton.renderDefaultLabel` → `renderScrollingStringOverContents`; this also answers
whether "Validate" is cut at 52 px today: it scrolls). "Configuration" was measured at 67 px in
Etapa 2.4 — fits 96 with room. The enable checkbox and the close `X`, today at
y 5 on the tab row's left and right ends, move to the header row (they would otherwise
overlap tab 1 and tab 5).

### 3.2 Inline preview follows regenerations

- **What to observe:** add `private volatile long generation` to `Shape`, incremented in the
  `finally` of `update()` **only when the generation succeeded** (`!error`), under the lock, with
  a getter. Plus the shape instance (switching shape set or shape type gives another object).
  `completedAt` is not enough (F5): it also moves on cancelled generations and has ms
  resolution.
- **Snapshot rule:** `ready && !error` (fixes F6), geometry re-snapshot when
  `(shape, generation)` changes; colours keep the `getVersion()` rule.
- **Cost** (measured, sphere r = 50, 30,978 blocks): snapshot 4.8 ms + colours 1.5 ms + mesh
  fill 4.7 ms ≈ **11 ms CPU**, plus the GPU upload (not measurable offline). Holding `+`
  regenerates several times per second: re-snapshot **at most every 250 ms while generations
  keep coming, and always after the last one** (same idea as the 300-ms auto-scan idle
  gate). Worst case ≈ 44 ms CPU per second on r = 50; linear in block count.
- **Shared logic:** `PreviewScreen` and the inline panel need the same model + camera +
  refresh code. Extract it into a common `PreviewController` (snapshot, colour refresh,
  camera, input) used by both; the inline panel and the enlarged window can keep separate
  cameras or share one (decision D5).
- The PIP renderer draws one preview per frame; both views are never on screen together.

### 3.3 Accordion sections

- **Model:** keep `PropertySection` as the state holder (`value` = open section index); replace
  its ‹ › widgets with N header rows. GUI-only (F7), so no persistence change.
- **Where the open-section state lives:** today `sectionSelector.value`, i.e. **per Shape
  instance** (transient). Keep it there: it survives tab switches and the preview (same
  instance) and resets on restart, like today. **Origin** is not a shape section; its
  "open" flag goes into `State` (transient, like `currentScreen`), because `ShapeScreen` is
  rebuilt on every tab switch (`State.createNewScreen`). Opening Origin collapses the shape's
  section and vice versa.
- **The count** ("Rails 5") must be **rows**, not properties: a point row is four properties
  in one row (`ShapeBridge.onSelectedInGUI`, `placeRow(row, rowProps)`). Compute it from the
  same placement pass that lays the rows out (a dry run of `onSelectedInGUI`), so it also
  follows dynamic counts such as Bridge's `Point count`.
- `Reset` keeps its meaning (the properties shown now = the open section).

### 3.4 Compact rows (188 px; F1, F2)

Proposed widths inside the panel (2-px margins, 184 usable):

| Type | Layout | Width |
|---|---|---|
| Int / Float | label 78 · − 14 · field 56 · + 14 | 162 |
| Enum | label 78 · ‹ 14 · name 76 · › 14 | 182 |
| Boolean | label 78 · checkbox 18 | 96 |
| Runnable | full-width button | 184 |
| Point row | `P1` 16 · x 46 · y 46 · z 46 · from-player 20 | 180 |

This drops two things that exist today (decision D2): the **Set** buttons (a value is applied
with Enter or when the field loses focus) and the **− / +** of the point-row coordinates (the
fields stay editable; scroll on a field could step it). Labels of 78 px hold ~13 characters;
longer ones ("Rail elevation") need shorter en_us strings or a scrolling label.

### 3.5 Shapes that must change (they appear in the decompile diff)

The brief's "no shapes in the diff" cannot hold for this redesign:
- **Cone, Spline, Bridge:** `hideFromGui(propertyValidate)`: the fixed Validate button in the
  bottom bar already does the same (Etapa 2.2c). Persisted order is unchanged (that is what
  `hideFromGui` is for).
- **Bridge:** split `Rails` into `Rails` (mode, sides, profile, width, height) and `Posts`
  (elevation, inset, post spacing). Sections are GUI-only (F7): no save impact.
- No geometry changes; the harness Bridge 466/159/360 must stay identical.

### 3.6 Error list in the right panel

`createSelectorList(left, right, top, bottom, itemHeight, …)` takes any rectangle;
`SelectorListImpl` places the scroll bar at `getRight() − 6` and rows at `width − 12`
(`SelectorListImpl.java`), so a 288 × 74 list works as is. Its `setYPosition` and
`setVisibility` are no-ops, fine for an always-visible block. The entry building
(`ValidationScreen.buildEntries`: headers, rows, highlight on click, 100-ms rebuild) moves
into a reusable component; the empty state gains a "No errors" line.

### 3.7 Removing the Validation tab

`grep` over `common` and `fabric1.21.11`: `ActiveScreen.Validation` is referenced only by
`BaseScreen.java:36` (the tab button) and `State.java:57` (`createNewScreen`). There is no
`valueOf`/`values()` on `ActiveScreen`, `currentScreen` is not among the persisted keys
(`State.java:25-30`: enabled, depthTest, highlightErrors, shapeSet, iShapeSet, iShapeNew),
and the profile's `config/buildguide.cfg` contains neither "validation" nor "currentScreen".
Removing the enum constant is safe for saves and config.

---

## 4. Risks

| # | Risk | Impact | Mitigation |
|---|---|---|---|
| R1 | **Screens smaller than 480 × 270** (1440p auto = 427 × 240, 768p = 456 × 256). The current GUI already breaks there (F4). | High for those players; none for Raphael (640 × 360) | Decision D1: minimum size + a "lower the GUI scale" message, or a compressed layout. |
| R2 | Compact rows touch **all 16 `Property*` classes** and every shape's look. No offline test covers widgets. | High (widest regression surface) | Its own stage (E4), per-shape in-game checklist, no other change in that stage. |
| R3 | Dropping Set / point-row ± changes how values are entered (Enter/focus). | Medium (habit) | Decision D2 before E4. |
| R4 | Inline preview rebuilds on every regeneration: large shapes (≥ 100k blocks) could hitch while holding `+`. | Medium | 250-ms throttle (§3.2); measure in E6 with the r = 50 sphere and the largest torus. |
| R5 | GPU mesh now resident whenever the Shape tab is open (tech debt listed in `CLAUDE.md`). | Low | Free it when no preview was drawn for N frames. |
| R6 | Cone is at exactly 8 rows: any new property overflows. | Low now | Rule in `CLAUDE.md` (§2.2). |
| R7 | Label lengths in 78 px; only `en_us` is maintained, other languages fall back or overflow. | Low | Shorter en_us strings; scrolling labels if needed. |
| R8 | Divergence from upstream grows (GUI was upstream code). | Low (already diverged) | None needed. |
| R9 | F6 (partial snapshot) is live today in `PreviewScreen`. | Low (rare) | Fixed in E1. |
| R10 | **Debt from E3:** `VisualisationScreen` only moved up 10 px and has no bottom bar (`hasBottomBar() = false`): its content goes down to y 305, off screen at 480 × 270 (the cube-size Set/Default buttons). Visible at Raphael's scale 3 (~640 × 360). | Medium at the minimum size | Re-layout (third column for cube size) in E7. |

---

## 5. Implementation order (safest first)

Each stage: build, harness, decompile-diff against the previous baseline, in-game check, commit.

| Stage | Content | Visible change | Offline testable |
|---|---|---|---|
| **E1** | `Shape.generation` + snapshot `ready && !error` (F6) + extract `PreviewController` from `PreviewScreen` | none | yes (harness: generation, cancelled generation, controller refresh rules) |
| **E2** | Error list as a reusable component; `ValidationScreen` uses it | none | partly (entry building) |
| **E3** ✅ | Header row + 6-tab bar (80 px, Validation kept until E6) + bottom bar (progress in `BaseScreen`, Validate/Reset/Preview on the Shape tab); other tabs shifted (Visualisation only −10, R10). Done, 8/8 in-game checks passed | header, tabs, bottom bar | no (in-game) |
| **E4** | Compact 18-px, 188-px rows for every property type; point row redesign | every shape's panel | no (per-shape in-game list) |
| **E5** | Accordion (`PropertySection` headers, Origin as a section, counts from the placement pass); Bridge Rails/Posts split; Validate rows hidden | left panel | partly (row counts per shape in the harness) |
| **E6** | Right panel: inline preview (following generations, 250-ms throttle) + error list; Enlarge button; remove the Validation tab and `ActiveScreen.Validation` | right panel | partly |
| **E7** | Larger-screen stretching (§2.3) and the small-screen behaviour chosen in D1 | at other sizes | no |

## 6. Decisions (taken 2026-09-25)

| # | Question | Decision |
|---|---|---|
| D1 | Below 480 × 270 | **Message asking for a lower GUI scale** (option a). Proportional scaling does not exist for vanilla widgets (integer GUI pixels); cutting was rejected. Tabs left-aligned on wider screens. |
| D2 | Set buttons | **Dropped**: a value is applied with Enter. |
| D2b | Point rows | **One row per point, ± kept**: `P1` 14 · 3 × (− 12 · field 28 · + 12) · from-player 14 = 184 px. Two rows per point were rejected: 5 points + count = 11 rows, capacity is 8 (Spline) / 6 (Bridge). Fields of 28 px hold 4–5 digits, enough for origin-relative coordinates. |
| D2c | Long labels | **Shortened in en_us** (no label scrolling: property names are plain text, `Property.render` → `drawShadowLeft`, they do not scroll like button text). E4 proposes abbreviations for every label over 12 characters. |
| D3 | Row height | **18 px** (20 does not fit the Cone plus two headers). |
| D4 | Header counts | **One line each**, shorter labels, no `x 64 + n` breakdown. |
| D5 | Camera | **Shared** between the inline preview and Enlarge: one `PreviewCamera` owned by the `PreviewController`; reopening Enlarge continues where the view was. |

Approved order: E1 infra → E2 list component → E3 header/footer → E4 compact widgets →
E5 accordion → E6 right panel → E7 scale.
