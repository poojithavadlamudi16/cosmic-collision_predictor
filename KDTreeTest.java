import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Phase 1 check: KD-tree nearest/radius results must equal brute force on random data. Run: java KDTreeTest */
public class KDTreeTest {
    // Time complexity: O(Q x n) for each size n - Q random queries, each checked against an O(n) brute-force scan
    public static void main(String[] args) {
        Random rnd = new Random(7);
        int failures = 0, queries = 0;
        for (int n : new int[]{1, 2, 10, 100, 1000, 5000}) {
            List<SpaceObject> objs = SpaceObject.randomField(n, 1000, 1000, n, 20);
            KDTree tree = new KDTree();
            tree.build(objs);
            for (int q = 0; q < 200; q++) {
                double qx = rnd.nextDouble() * 1000, qy = rnd.nextDouble() * 1000, r = 5 + rnd.nextDouble() * 200;
                queries++;

                // nearest neighbour
                SpaceObject best = null;
                double bestD = Double.MAX_VALUE;
                for (SpaceObject o : objs) {
                    double d = Math.hypot(o.x - qx, o.y - qy);
                    if (d < bestD) { bestD = d; best = o; }
                }
                SpaceObject nn = tree.nearest(qx, qy, null);
                if (Math.abs(Math.hypot(nn.x - qx, nn.y - qy) - bestD) > 1e-9) {
                    failures++;
                    System.out.println("Nearest mismatch n=" + n + " q=" + q);
                }

                // radius search
                int expected = 0;
                for (SpaceObject o : objs) if (Math.hypot(o.x - qx, o.y - qy) <= r) expected++;
                List<SpaceObject> found = new ArrayList<>();
                tree.radiusSearch(qx, qy, r, found);
                if (found.size() != expected) {
                    failures++;
                    System.out.println("Radius mismatch n=" + n + " q=" + q + " expected " + expected + " got " + found.size());
                }
            }
            System.out.println("n=" + n + " ok, tree height = " + tree.height());
        }
        System.out.println(failures == 0 ? "ALL " + queries + " QUERIES PASSED" : failures + " FAILURES");
    }
}
