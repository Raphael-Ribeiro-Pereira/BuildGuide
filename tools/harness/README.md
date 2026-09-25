# Offline harness (no Minecraft)

Drives `common` classes directly (Bridge/Spline geometry, `ValidationState`,
`ValidationOverlay`, exclusion rules) and prints maps, counts and timings.
Run it before every in-game test; it caught the bridge bend holes before the game did.

`ShapeSet` needs a Minecraft-free instance, so the tests allocate it with
`sun.misc.Unsafe` and call `updateShape` by reflection. Property indices for
`ShapeBridge` are listed at the top of `BridgeTest.java`.

## Build and run

```bash
export JAVA_HOME="C:/Users/Rapha/Documents/BuildGuide-tools/jdk-21.0.12.1+1"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :common:build --configure-on-demand --no-daemon      # produces common/build/classes/java/main
CLASSES=common/build/classes/java/main
OUT=/c/Users/Rapha/Documents/BuildGuide-tools/bridgetest       # compiled classes stay out of the repo
javac -cp "$CLASSES" -d "$OUT" tools/harness/*.java
java  -cp "$CLASSES;$OUT" BridgeTest        # geometry: maps + hole counts (466/159/360 blocks expected)
java  -cp "$CLASSES;$OUT" StateTest         # ValidationState counters/transitions (12 asserts)
java  -cp "$CLASSES;$OUT" IncrementalTest   # updateBlock path (10 asserts)
java  -cp "$CLASSES;$OUT" ExclusionTest     # ignored types + exclusion boxes (11 asserts)
java  -cp "$CLASSES;$OUT" OverlayTest       # version/indices/highlight/cap/colours (10 asserts)
java  -cp "$CLASSES;$OUT" NearDiagTest      # structure errors: scan + incremental detection, shell geometry (16 asserts)
java  -cp "$CLASSES;$OUT" ClassifyTest      # 2.5 status table + hollow sphere/cone cavities (18 asserts)
```

`CentreTest`, `Step4Test`, `BaseSetTest` are the Step 3/4 and 2.2c checks;
`ScanCostTest`, `MemTest`, `OverlayCost`, `OverlayCost2` are timing/memory probes.

Run the `java -cp` lines from PowerShell or cmd: Git Bash (MSYS) rewrites the `;`-separated
classpath and every test fails with `ClassNotFoundException`. `javac` and `gradlew` are fine
from either shell.

The canonical copy is this folder; `BuildGuide-tools\bridgetest` is only the
compile output directory.
