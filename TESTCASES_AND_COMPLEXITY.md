# Test Cases and Time Complexity - Cosmic Collision Predictor

## How to run the tests
```
javac *.java
java LabTests
```
Result when last run: **71 passed, 0 failed.**

## Test cases (in `LabTests.java`)

| Group | IDs | What they check |
|---|---|---|
| KDTree edge cases | KD-01 to 06 | Empty tree (nearest returns null, radius search returns nothing, height 0), single point, excluding the only point |
| KDTree known data | KD-07 to 14 | 6 hand-checked points: nearest (including distance 0 and with `exclude`), radius 3.2, radius 0, radius 5 (all points), far-away query |
| KDTree boundaries | KD-16 to 20 | Distance exactly equal to r is included, 50 duplicate points, 100 points on the same x, negative coordinates |
| KDTree balance | KD-21 | Height equals ceil(log2(n+1)) for n = 1, 7, 15, 100, 1000, 10000 |
| KDTree vs brute force | KD-22 to 24 | 2,000 random nearest and radius queries match brute force; nearest on 10,000 points visits only ~27 nodes (pruning works); trace size equals nodes visited |
| SpaceObject | SO-01 to 06 | Bounce off left and right walls, free movement, speed of (3,4) is 5, same seed gives same field, objects stay inside the world |
| evaluatePair | CE-01 to 11 | Head-on (impact 4.9 s, closest 5 s), horizon too short, same velocity, already overlapping, miss, grazing, moving apart, both stationary, diagonal, impact exactly at the horizon |
| Min-heap | HP-01 to 04 | Events come out in order 1.9, 4.9, 9.9; `topEvents` does not consume the heap |
| Object types | TY-01 to 10 | Mass = density x PI x r^2, planet > asteroid > ship for the same radius, doubling r quadruples mass, spawn mix about 70/3/27 %, radii and speeds inside each type's range, asteroid power-law size, planets slower than ships, kinetic energy |
| Impact energy | CE-12 to 16 | Relative speed 20 for a head-on pair, E = 100 m for equal masses, 0 when stationary, planet-ship releases more energy than asteroid-ship, energy never above 1/2 x lighter mass x v_rel^2 |
| Prediction | PR-01 to 07 | Empty list, single object, 90 random runs where KD-tree and brute force find identical collisions, 5 overlapping circles give C(5,2) = 10 events |

Manual GUI checks (not automated): generate 1,000 objects, click to run nearest and radius searches, press "Compare KD vs Brute" and confirm "Same collisions found: true".

## Object types: asteroids, planets and spaceships (math and reasoning)

Every object in the simulation is one of three bodies. The type decides its size, mass, speed and how common it is. The numbers are in `SpaceObject.Type`.

| | Asteroid | Planet | Spaceship |
|---|---|---|---|
| Share of objects | 70 % | 3 % | 27 % |
| Radius | 1.5 to 4 (power law) | 5 to 8 (uniform) | 1 to 2 (uniform) |
| Density | 2.5 | 5.5 | 0.5 |
| Speed (fraction of max speed) | 0.10 to 0.60 | 0.02 to 0.10 | 0.50 to 1.00 |
| Drawn as | grey dot | blue dot with ring | green triangle pointing along its velocity |

**1. Mass.** `m = density x PI x r^2`. The world is 2D, so the "volume" of a disc is its area and mass grows with r squared (r cubed in 3D). Doubling the radius makes the body 4 times heavier.

**2. Density.** Planet 5.5 > asteroid 2.5 > spaceship 0.5. Earth's mean density is about 5.5 g/cm^3 (iron core plus rock), rocky asteroids are about 2.5, and a spaceship is mostly hollow hull and fuel tanks.

**3. Size distribution.** Small bodies are far more common than large ones. Real asteroid counts follow a power law, N(> r) proportional to r^-2. The code samples this with the inverse-CDF (Pareto) method: `r = rMin x (1 - u)^(-1/2)`, where u is a uniform random number in [0,1). Test TY-08 checks that P(r > 3) is about (1.5/3)^2 = 0.25.

**4. Speed.** With the same energy `E = 1/2 m v^2`, a heavier body moves slower: `v = sqrt(2E / m)`. Momentum `p = m v` says the same: a heavy body changes speed very little. So planets are slowest, asteroids are in the middle, and spaceships, which have engines, are fastest.

**5. Impact energy.** For two bodies the energy that can be released in a collision is the kinetic energy in the centre-of-mass frame:
```
E = 1/2 x mu x |v_rel|^2,    mu = ma x mb / (ma + mb)    (reduced mass)
```
The rest of the kinetic energy is just the pair moving together. Because mu grows with mass, a planet hitting a ship releases far more energy than two ships at the same relative speed. Each collision alert in the GUI now shows both object types and this energy.

**6. Effect on the KD-tree.** The search radius uses the largest radius in the field: `searchR = ra + maxR + (va + vmax) x horizon`. A planet raises `maxR` for every search, which is why planets are kept rare (3 %). The KD-tree still finds exactly the same collisions as brute force (test PR-05).

## Time complexity of each function (grouped by the file it lives in)

n = number of objects, m = collision events found, k = results returned by a query, K = candidate pairs tested.

### File 1: `SpaceObject.java`
| Function | Complexity | Note |
|---|---|---|
| `SpaceObject(...)` constructors (6-argument and with type) | O(1) | Mass is one multiplication |
| `Type.pick(u)` | O(1) | At most 3 comparisons |
| `Type.randomRadius(u)` | O(1) | One `pow` for asteroids |
| `kineticEnergy()` | O(1) | |
| `coord(axis)` | O(1) | |
| `speed()` | O(1) | |
| `move(dt, w, h)` | O(1) | Position update + wall bounce |
| `randomField(n, w, h, seed, maxSpeed)` | **O(n)** | Creates n objects |
| `toString()` | O(1) | |

### File 2: `KDTree.java`
| Function | Complexity | Note |
|---|---|---|
| `build(objects)` | **O(n log n)** average | Copies list O(n), then recursive build. Worst case O(n^2), very rare |
| `build(a, lo, hi, depth)` (private, recursive) | O(n log n) average | Quickselect on ~log n levels |
| `select(...)` (quickselect, private) | O(n) average, O(n^2) worst | Puts the median at index mid |
| `radiusSearch(qx, qy, r, out)` | **O(sqrt(n) + k)** worst for balanced 2D tree, about O(log n + k) for small radii | Prunes subtrees the circle does not touch |
| `radius(...)` (private, recursive) | same as above | |
| `nearest(qx, qy, exclude)` | **O(log n)** average, O(n) worst | Descend, then check far side only if needed |
| `nearest(...)` (private, recursive) | same as above | |
| `lastNearestDistance()` | O(1) | |
| `visit(n)` | O(1) | Counter + optional trace add |
| `resetStats()` | O(1), or O(trace length) when tracing | `trace.clear()` |
| `getNodesVisited()`, `getSubtreesPruned()`, `size()`, `isEmpty()` | O(1) | |
| `enableTrace(on)` | O(1) | |
| `getTrace()` | O(1) | |
| `height()` / `height(n)` | O(n) | Visits every node |
| `collectSplits(w, h, maxDepth)` / `splits(...)` | O(min(n, 2^maxDepth)) | Stops at maxDepth |

### File 3: `CollisionEngine.java`
| Function | Complexity | Note |
|---|---|---|
| `evaluatePair(a, b, horizon)` | O(1) | Quadratic formula for one pair |
| `predictWithKDTree(objects, horizon)` | **O(n log n + K + m log m)** typical, O(n^2) worst | Build + n radius searches + heap inserts |
| `predictBruteForce(objects, horizon)` | **O(n^2 + m log m)** | Tests every pair |
| `Result.eventCount()` | O(1) | |
| `Result.topEvents(k)` | O(m + k log m) | Copies heap, then k polls |
| `CollisionEvent` constructor | O(1) | |
| `CollisionEvent.compareTo(o)` | O(1) | Heap ordering |
| (inside `evaluatePair`) impact energy | O(1) | Reduced mass and 1/2 mu v^2 |
| `CollisionEvent.toString()` | O(1) | |

Heap insert (`offer`) is O(log m); peeking at the earliest event is O(1).

### File 4: `PerformanceTester.java`
| Function | Complexity | Note |
|---|---|---|
| `Row.kdTotalMs()`, `Row.speedup()` | O(1) | |
| `run(sizes, horizon, repeats, log)` | **O(repeats x n^2)** for each size n | Dominated by the brute-force run; KD-tree part is O(n log n + K) |
| `writeCsv(rows, file)` | O(rows) | |
| `showChart(rows)` | O(1) to open the window | |
| `ChartPanel.paintComponent(g)` | O(rows) | |
| `ChartPanel.drawSeries(...)` | O(rows) | |
| `main(args)` | same as `run` | Runs `run`, then `writeCsv` |

### File 5: `CosmicCollisionPredictor.java` (GUI)
| Function | Complexity | Note |
|---|---|---|
| `buildUI()`, `slider`, `row`, `titled`, `area`, `setRunning` | O(1) | Fixed number of UI components |
| `generate(n)` | O(n) | Calls `randomField` |
| `ensureTree()` | O(1) if tree is fresh, O(n log n) if stale | Rebuilds KD-tree |
| `step()` | O(n) move + prediction every 10 or 30 frames | |
| `predict()` | engine cost + O(n + m) | Counts object types O(n), builds the `involved` set from the heap and `topEvents(10)` |
| `handleClick(wx, wy)` | O(log n) nearest, O(sqrt(n) + k) radius | Plus O(trace length) to copy the trace |
| `compareNow()` | **O(n^2)** | Runs brute force as well as KD-tree |
| `runBenchmark(btn)` | same as `PerformanceTester.run` | Runs in a background thread |
| `SpacePanel.scale/ox/oy/sx/sy` | O(1) | |
| `SpacePanel.paintComponent(g)` | O(n + trace length) per frame | Draws n objects (HashSet lookup is O(1)); split lines add O(min(n, 2^splitDepth)); at most 10 alert lines |
| `main(args)` | O(1) | Opens the window |

### File 6: `KDTreeTest.java`
| Function | Complexity | Note |
|---|---|---|
| `main(args)` | O(Q x n) per size n | Q = 200 queries, each compared with an O(n) brute-force scan |

### File 7: `LabTests.java` (new)
| Function | Complexity | Note |
|---|---|---|
| `main(args)` | Dominated by the 90 KD-vs-brute runs (largest n = 3,000) | Runs in a few seconds |
| `check`, `o`, `mv`, `near` | O(1) | Helpers |

## Viva notes
- "KD-tree is O(n log n)" holds for constant density and a short horizon. The search radius is `a.radius + maxR + (a.speed + maxSpeed) * horizon`, so one very fast object or a large horizon pushes it toward O(n^2).
- Benchmark at 10,000 objects: KD-tree tests 97,499 pairs versus 49,995,000 for brute force (about 500x fewer).
- For small n (100 to 1,000) the KD-tree is no faster than brute force because of build overhead.
- Tree height matches ceil(log2(n+1)) in every test, which justifies the O(log n) claims.
