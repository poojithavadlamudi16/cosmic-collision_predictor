# Cosmic Collision Predictor
**A KD-Tree-Based Spatial Search and Collision Analysis System** (Java 11+, Swing, no external libraries)

## Run
```
javac *.java
java CosmicCollisionPredictor      # GUI
java KDTreeTest                    # Phase 1: KD-tree vs brute-force correctness
java PerformanceTester             # Phase 4: benchmark -> benchmark_results.csv (+ chart if a display exists)
```

## Files
| File | Role |
|---|---|
| `SpaceObject.java` | id, position, velocity, radius; wall bounce; random field generator |
| `KDTree.java` | From-scratch 2D KD-tree: quickselect median build, radius search, nearest neighbour, pruning stats, visit trace, split lines |
| `CollisionEngine.java` | Candidate pairs via KD-tree, exact closest-approach / impact-time maths, min-heap ranking, brute-force baseline |
| `PerformanceTester.java` | Benchmarks 100 - 10,000 objects, CSV export, log-scale chart |
| `CosmicCollisionPredictor.java` | Swing GUI |
| `KDTreeTest.java` | Independent tests of the DSA core |

## Using the GUI
- **Generate**: 100 - 10,000 objects. **Pause/Resume**, sim speed, prediction horizon (seconds).
- **Show KD-tree splits** + depth: blue = vertical (x) splits, purple = horizontal (y) splits.
- **Click the map**: nearest-neighbour or radius search. The simulation pauses and the nodes the search visited light up as yellow rings; green = result. The side panel shows nodes visited vs. total and subtrees pruned.
- **Collision alerts**: orange = on a collision course, red dashed = the 10 earliest impacts (popped from the min-heap) with time labels.
- **Compare KD vs Brute** and **Run Benchmark + CSV** for the evaluation phase.

## How it works (viva notes)
1. **Build** - recursively pick the median on x, then y, then x... using quickselect: O(n log n), balanced, height ~ log2 n.
2. **Radius search** - visit a node; go into a child only if the search circle crosses the splitting line, otherwise prune that whole subtree.
3. **Nearest neighbour** - descend towards the query, then explore the far side only if the splitting line is closer than the best distance found so far.
4. **Collision maths** - relative position `p`, relative velocity `v`, radii sum `R`. Solve `|p + v t| = R`, i.e. `(v.v)t^2 + 2(p.v)t + (p.p - R^2) = 0`. Smallest root in `[0, horizon]` is the impact time; closest approach is `t* = -(p.v)/(v.v)` clamped to the horizon.
5. **Why KD-tree never misses a collision** - if two objects collide within the horizon, their *current* distance is at most `ra + rb + (|va| + |vb|) * horizon`. Searching with that radius gives a superset of all real collisions; the exact test then filters. `PerformanceTester` checks both methods find the same number of collisions (`results_match` column).
6. **Min-heap** - events go into a `PriorityQueue` ordered by impact time, so the most urgent collision is always on top (O(log m) insert).
7. **Moving objects** - the tree is rebuilt (cheap, ~2 ms for 10,000 objects) rather than updated in place.

## Measured results (constant density, 3 s horizon, best of 3)
| Objects | Brute force | KD-tree | Speed-up |
|---|---|---|---|
| 100 | 0.44 ms | 0.44 ms | x1.0 |
| 1,000 | 2.4 ms | 3.9 ms | x0.6 |
| 2,000 | 17.9 ms | 4.8 ms | x3.7 |
| 5,000 | 110.9 ms | 8.9 ms | x12.4 |
| 10,000 | 231.6 ms | 13.9 ms | x16.6 |

Brute force is O(n^2) and the KD-tree approach is roughly O(n log n) here. For small n the tree has no advantage, since build and traversal overhead cancel the savings. Your own machine will give different numbers.

## Limitations
- Predictions assume straight-line motion; wall bounces are not predicted.
- Objects pass through each other (no collision response).
- Above ~3,000 objects the GUI refreshes predictions less often to stay smooth.

## Object types (asteroids, planets, spaceships)
Each object has a type with its own radius, density, speed band and spawn share. Mass is `density x PI x r^2`.
Collision alerts also show the impact energy `1/2 x mu x |v_rel|^2` with reduced mass `mu = ma*mb/(ma+mb)`.
The mathematical reasoning is in `TESTCASES_AND_COMPLEXITY.md` and in the comment at the top of `SpaceObject.java`.
Run the tests with `javac *.java && java LabTests` (71 tests).
