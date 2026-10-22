import java.util.ArrayList;
import java.util.List;

/**
 * A 2D KD-tree written from scratch.
 *  - build:        median split using quickselect, alternating x / y axis  -> O(n log n)
 *  - radiusSearch: all objects within distance r of a point, pruning subtrees
 *  - nearest:      nearest neighbour with branch-and-bound pruning
 * Also records statistics (nodes visited, subtrees pruned) and an optional
 * visit trace so the GUI can animate the search.
 */
public class KDTree {

    public static class Node {
        public final SpaceObject obj;
        public final int axis;      // 0 = splits on x, 1 = splits on y
        Node left, right;           // left: coord <= split, right: coord >= split

        // Time complexity: O(1)
        Node(SpaceObject obj, int axis) {
            this.obj = obj;
            this.axis = axis;
        }
    }

    private Node root;
    private int size;

    // statistics
    private long nodesVisited;
    private long subtreesPruned;
    private List<Node> trace;       // null unless tracing is enabled

    // state for nearest-neighbour search
    private SpaceObject best;
    private double bestD2;

    // ---------------------------------------------------------------- build

    // Time complexity: O(n log n) average (copy O(n) + recursive build). Worst case O(n^2), very rare.
    // Space: O(n)
    public void build(List<SpaceObject> objects) {
        SpaceObject[] arr = objects.toArray(new SpaceObject[0]);
        size = arr.length;
        root = build(arr, 0, arr.length - 1, 0);
    }

    // Time complexity: O(n log n) average - quickselect O(n) per level over ~log n levels
    private Node build(SpaceObject[] a, int lo, int hi, int depth) {
        if (lo > hi) return null;
        int axis = depth % 2;
        int mid = (lo + hi) >>> 1;
        select(a, lo, hi, mid, axis);          // put the median at index mid
        Node node = new Node(a[mid], axis);
        node.left = build(a, lo, mid - 1, depth + 1);
        node.right = build(a, mid + 1, hi, depth + 1);
        return node;
    }

    /** Quickselect: rearrange a[lo..hi] so a[k] is the element that would be there if sorted by axis. */
    // Time complexity: O(n) average, O(n^2) worst case (quickselect)
    private void select(SpaceObject[] a, int lo, int hi, int k, int axis) {
        while (lo < hi) {
            double pivot = a[(lo + hi) >>> 1].coord(axis);
            int i = lo, j = hi;
            while (i <= j) {
                while (a[i].coord(axis) < pivot) i++;
                while (a[j].coord(axis) > pivot) j--;
                if (i <= j) {
                    SpaceObject t = a[i]; a[i] = a[j]; a[j] = t;
                    i++;
                    j--;
                }
            }
            if (k <= j) hi = j;
            else if (k >= i) lo = i;
            else return;
        }
    }

    // --------------------------------------------------------- radius search

    /** Adds every object within distance r of (qx, qy) to out. */
    // Time complexity: O(sqrt(n) + k) worst case for a balanced 2D tree, about O(log n + k) for small radii
    // (k = number of points returned; subtrees the circle cannot touch are pruned)
    public void radiusSearch(double qx, double qy, double r, List<SpaceObject> out) {
        radius(root, qx, qy, r, r * r, out);
    }

    // Time complexity: O(sqrt(n) + k) - same as radiusSearch
    private void radius(Node n, double qx, double qy, double r, double r2, List<SpaceObject> out) {
        if (n == null) return;
        visit(n);
        double dx = n.obj.x - qx, dy = n.obj.y - qy;
        if (dx * dx + dy * dy <= r2) out.add(n.obj);

        double diff = (n.axis == 0 ? qx : qy) - n.obj.coord(n.axis);   // query - split
        if (diff - r <= 0) radius(n.left, qx, qy, r, r2, out);
        else if (n.left != null) subtreesPruned++;
        if (diff + r >= 0) radius(n.right, qx, qy, r, r2, out);
        else if (n.right != null) subtreesPruned++;
    }

    // ------------------------------------------------------ nearest neighbour

    /** Nearest object to (qx, qy), ignoring 'exclude' (may be null). Returns null for an empty tree. */
    // Time complexity: O(log n) average, O(n) worst case
    public SpaceObject nearest(double qx, double qy, SpaceObject exclude) {
        best = null;
        bestD2 = Double.POSITIVE_INFINITY;
        nearest(root, qx, qy, exclude);
        return best;
    }

    // Time complexity: O(1)
    public double lastNearestDistance() {
        return Math.sqrt(bestD2);
    }

    // Time complexity: O(log n) average, O(n) worst case - far side is searched only if the split line is closer than the best so far
    private void nearest(Node n, double qx, double qy, SpaceObject exclude) {
        if (n == null) return;
        visit(n);
        if (n.obj != exclude) {
            double dx = n.obj.x - qx, dy = n.obj.y - qy;
            double d2 = dx * dx + dy * dy;
            if (d2 < bestD2) {
                bestD2 = d2;
                best = n.obj;
            }
        }
        double diff = (n.axis == 0 ? qx : qy) - n.obj.coord(n.axis);
        Node near = diff <= 0 ? n.left : n.right;
        Node far = diff <= 0 ? n.right : n.left;
        nearest(near, qx, qy, exclude);
        if (diff * diff < bestD2) nearest(far, qx, qy, exclude);   // far side might hold something closer
        else if (far != null) subtreesPruned++;                    // pruned!
    }

    // ------------------------------------------------- stats / visualisation

    // Time complexity: O(1)
    private void visit(Node n) {
        nodesVisited++;
        if (trace != null) trace.add(n);
    }

    // Time complexity: O(1) (O(trace length) when tracing is on, because of trace.clear())
    public void resetStats() {
        nodesVisited = 0;
        subtreesPruned = 0;
        if (trace != null) trace.clear();
    }

    // Time complexity: O(1)
    public long getNodesVisited() { return nodesVisited; }
    // Time complexity: O(1)
    public long getSubtreesPruned() { return subtreesPruned; }
    // Time complexity: O(1)
    public int size() { return size; }
    // Time complexity: O(1)
    public boolean isEmpty() { return root == null; }

    // Time complexity: O(1)
    public void enableTrace(boolean on) {
        trace = on ? new ArrayList<>() : null;
    }

    // Time complexity: O(1)
    public List<Node> getTrace() {
        return trace == null ? new ArrayList<>() : trace;
    }

    // Time complexity: O(n) - visits every node
    public int height() {
        return height(root);
    }

    // Time complexity: O(n) - visits every node
    private int height(Node n) {
        return n == null ? 0 : 1 + Math.max(height(n.left), height(n.right));
    }

    /**
     * Split lines for drawing the partitions, down to maxDepth.
     * Each entry: {x1, y1, x2, y2, axis, depth}.
     */
    // Time complexity: O(min(n, 2^maxDepth)) - stops at maxDepth
    public List<double[]> collectSplits(double w, double h, int maxDepth) {
        List<double[]> out = new ArrayList<>();
        splits(root, 0, 0, w, h, 0, maxDepth, out);
        return out;
    }

    // Time complexity: O(min(n, 2^maxDepth))
    private void splits(Node n, double x0, double y0, double x1, double y1, int depth, int maxDepth, List<double[]> out) {
        if (n == null || depth >= maxDepth) return;
        if (n.axis == 0) {
            double s = n.obj.x;
            out.add(new double[]{s, y0, s, y1, 0, depth});
            splits(n.left, x0, y0, s, y1, depth + 1, maxDepth, out);
            splits(n.right, s, y0, x1, y1, depth + 1, maxDepth, out);
        } else {
            double s = n.obj.y;
            out.add(new double[]{x0, s, x1, s, 1, depth});
            splits(n.left, x0, y0, x1, s, depth + 1, maxDepth, out);
            splits(n.right, x0, s, x1, y1, depth + 1, maxDepth, out);
        }
    }
}
