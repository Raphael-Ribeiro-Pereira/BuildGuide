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

Offline harness at the close: 406 asserts, 0 failures; Bridge reference sizes 466 / 159 / 360.
E6 (after two fixes), E7, P4 and E8 passed their in-game checklists before being committed
(E7 7 of 7, P4 9 of 9, E8 9 of 9). The ghost fix was committed on Raphael's go-ahead after the
`ghost-fix-wip` test; the result of that test was not reported to the session that wrote this
file, so it is not claimed here (see the backlog).

## 2. Final jars

Build output: `fabric1.21.11/build/libs/BuildGuide-Fabric-0.4.8.jar` (mod version is still 0.4.8).

| Item | Value |
|---|---|
| Active jar in the mods folder | `BuildGuide-Fabric-0.4.8-area3-wip2.jar` |
| SHA-256 | `B5AC963F068EFF59954521F29F24D994BBFE61CF9F55C2C1DE9256E055392A32` |
| Source | the Area 3 code of commit `584674b` (`99fa942` only adds docs): the build output and the mods copy have the same SHA-256; `TargetOutline` and `RenderHandler` are inside |
| Tested | the offline harness (466 asserts); the outline passed in game |
| Previous jars | `BuildGuide-Fabric-0.4.8-area3-wip.jar` (first Area 3 test) and `BuildGuide-Fabric-0.4.8-ghost-fix-wip.jar` (SHA-256 `1E2F5E96A659D474EF14D9CDDBF5930E6B695DF66F31C782A7C1E5654E55BDB6`, commit `9c3fbde`), kept as `.bak` |

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
   (`BuildGuide-tools\decompiled\<NAME>`, Vineflower, CRLF normalised). The baseline is now `GHOSTFIX`.
10. Deploy only with the game and the Modrinth App closed; abort if javaw, the Modrinth App, or a
    java whose command line contains fabric, knot, net.minecraft, .minecraft, ModrinthApp,
    `Fabulously Optimized (1)` or devlaunchinjector is running (any other java, such as an IDE
    build server, is allowed; an unreadable command line blocks); rename the old jar to `.bak`; prove the deploy with the
    SHA-256 of the mods copy against the build. (E6 once tested the old jar for this reason.)
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
