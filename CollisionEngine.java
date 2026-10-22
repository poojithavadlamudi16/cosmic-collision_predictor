import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Predicts collisions between moving circular objects over a time horizon.
 *
 * Pair test (constant velocity): relative position p = pb - pa, relative velocity v = vb - va.
 * Distance at time t is |p + v t|. A collision happens when that equals ra + rb:
 *      (v.v) t^2 + 2 (p.v) t + (p.p - R^2) = 0      -> smallest root t in [0, horizon]
 * The closest approach is at t* = -(p.v)/(v.v), clamped to [0, horizon].
 *
 * Candidate pairs are found with the KD-tree: if a pair collides within the horizon, their
 * *current* distance is at most ra + rb + (|va| + |vb|) * horizon, so a radius search with that
 * bound can never miss a collision. Events are ranked by a min-heap (earliest impact first).
 *
 * Impact energy: each object is an asteroid, planet or spaceship with its own mass
 * m = density * PI * r^2. For two bodies the energy that can be released in a collision is the
 * kinetic energy in the centre-of-mass frame:
 *      E = 1/2 * mu * |v_rel|^2,   mu = ma*mb / (ma+mb)   (reduced mass)
 * (the rest of the kinetic energy is just the pair moving together). So a planet hitting a ship
 * releases far more energy than two ships at the same relative speed, because mu grows with mass.
 */
public class CollisionEngine {

    public static class CollisionEvent implements Comparable<CollisionEvent> {
        public final SpaceObject a, b;
        public final double impactTime;      // first contact (0 = already overlapping)
        public final double closestTime;     // time of closest approach within the horizon
        public final double minDistance;     // centre distance at closest approach
        public final double relSpeed;        // |v_rel| = relative speed of the two bodies
        public final double impactEnergy;    // 1/2 * mu * |v_rel|^2 (reduced mass mu)

        // Time complexity: O(1)
        CollisionEvent(SpaceObject a, SpaceObject b, double impactTime, double closestTime, double minDistance,
                       double relSpeed, double impactEnergy) {
            this.a = a;
            this.b = b;
            this.impactTime = impactTime;
            this.closestTime = closestTime;
            this.minDistance = minDistance;
            this.relSpeed = relSpeed;
            this.impactEnergy = impactEnergy;
        }

        // Time complexity: O(1) - used by the min-heap to order events
        @Override
        public int compareTo(CollisionEvent o) {
            int c = Double.compare(impactTime, o.impactTime);
            return c != 0 ? c : Double.compare(minDistance, o.minDistance);
        }

        // Time complexity: O(1)
        @Override
        public String toString() {
            String when = impactTime <= 0 ? "OVERLAPPING NOW" : String.format("impact in %.2f s", impactTime);
            return String.format("#%d %s <-> #%d %s  %s  (closest %.1f at %.2f s, energy %.0f)",
                    a.id, a.type.label, b.id, b.type.label, when, minDistance, closestTime, impactEnergy);
        }
    }

    public static class Result {
        public final PriorityQueue<CollisionEvent> heap = new PriorityQueue<>();   // min-heap
        public long candidatePairs;      // pairs that went through the exact test
        public long nodesVisited;        // KD-tree nodes touched (0 for brute force)
        public long buildNanos;          // KD-tree construction time
        public long queryNanos;          // candidate search + pair tests
        public int objectCount;

        // Time complexity: O(1)
        public int eventCount() { return heap.size(); }

        /** The k earliest events (does not modify the heap). */
        // Time complexity: O(m + k log m) - copy the heap O(m), then k polls at O(log m) each
        // (m = events in the heap)
        public List<CollisionEvent> topEvents(int k) {
            PriorityQueue<CollisionEvent> copy = new PriorityQueue<>(heap);
            List<CollisionEvent> out = new ArrayList<>();
            while (out.size() < k && !copy.isEmpty()) out.add(copy.poll());
            return out;
        }
    }

    /** Exact pair test. Returns null if the pair does not collide within the horizon. */
    // Time complexity: O(1) - fixed number of operations (quadratic formula for one pair)
    static CollisionEvent evaluatePair(SpaceObject a, SpaceObject b, double horizon) {
        double px = b.x - a.x, py = b.y - a.y;
        double vx = b.vx - a.vx, vy = b.vy - a.vy;
        double R = a.radius + b.radius;

        double qa = vx * vx + vy * vy;
        double qb = 2 * (px * vx + py * vy);
        double qc = px * px + py * py - R * R;

        double tClosest = qa < 1e-12 ? 0 : Math.max(0, Math.min(horizon, -qb / (2 * qa)));
        double cx = px + vx * tClosest, cy = py + vy * tClosest;
        double minDist = Math.sqrt(cx * cx + cy * cy);

        double tImpact;
        if (qc <= 0) {
            tImpact = 0;                                  // already overlapping
        } else {
            if (qa < 1e-12) return null;                  // same velocity: distance never changes
            double disc = qb * qb - 4 * qa * qc;
            if (disc < 0) return null;                    // paths never get close enough
            tImpact = (-qb - Math.sqrt(disc)) / (2 * qa);
            if (tImpact < 0 || tImpact > horizon) return null;
        }
        double mu = a.mass * b.mass / (a.mass + b.mass);   // reduced mass
        double energy = 0.5 * mu * qa;                     // qa = |v_rel|^2
        return new CollisionEvent(a, b, tImpact, tClosest, minDist, Math.sqrt(qa), energy);
    }

    /** KD-tree accelerated prediction. */
    // Time complexity: O(n log n + K + m log m) typical, O(n^2) worst case
    //   build tree O(n log n) + n radius searches O(n log n + K) + K pair tests O(K) + m heap inserts O(m log m)
    //   n = objects, K = candidate pairs tested, m = collisions found
    //   K is small when objects are spread out; it grows to n(n-1)/2 when they are clustered or the horizon/speeds are large.
    public Result predictWithKDTree(List<SpaceObject> objects, double horizon) {
        Result res = new Result();
        res.objectCount = objects.size();

        long t0 = System.nanoTime();
        KDTree tree = new KDTree();
        tree.build(objects);
        long t1 = System.nanoTime();

        double maxR = 0, maxSpeed = 0;
        for (SpaceObject o : objects) {
            maxR = Math.max(maxR, o.radius);
            maxSpeed = Math.max(maxSpeed, o.speed());
        }

        List<SpaceObject> found = new ArrayList<>();
        for (SpaceObject a : objects) {
            double searchR = a.radius + maxR + (a.speed() + maxSpeed) * horizon;
            found.clear();
            tree.radiusSearch(a.x, a.y, searchR, found);
            for (SpaceObject b : found) {
                if (b.id <= a.id) continue;               // test each pair only once
                res.candidatePairs++;
                CollisionEvent e = evaluatePair(a, b, horizon);
                if (e != null) res.heap.offer(e);
            }
        }
        long t2 = System.nanoTime();

        res.nodesVisited = tree.getNodesVisited();
        res.buildNanos = t1 - t0;
        res.queryNanos = t2 - t1;
        return res;
    }

    /** O(n^2) baseline: test every pair. */
    // Time complexity: O(n^2 + m log m) - n(n-1)/2 pair tests + m heap inserts
    public Result predictBruteForce(List<SpaceObject> objects, double horizon) {
        Result res = new Result();
        res.objectCount = objects.size();
        long t0 = System.nanoTime();
        int n = objects.size();
        for (int i = 0; i < n; i++) {
            SpaceObject a = objects.get(i);
            for (int j = i + 1; j < n; j++) {
                res.candidatePairs++;
                CollisionEvent e = evaluatePair(a, objects.get(j), horizon);
                if (e != null) res.heap.offer(e);
            }
        }
        res.queryNanos = System.nanoTime() - t0;
        return res;
    }
}
