import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Benchmarks KD-tree collision prediction against brute force for growing object counts.
 * Density is kept constant (world side = 40 * sqrt(n)) so the comparison is fair.
 * Run headless:  java PerformanceTester
 */
public class PerformanceTester {

    public static class Row {
        public int n;
        public double bruteMs, kdBuildMs, kdQueryMs;
        public long brutePairs, kdCandidates, nodesVisited;
        public int collisions;
        public boolean match;

        // Time complexity: O(1)
        public double kdTotalMs() { return kdBuildMs + kdQueryMs; }
        // Time complexity: O(1)
        public double speedup() { return bruteMs / Math.max(1e-9, kdTotalMs()); }
    }

    // Time complexity: O(repeats x n^2) for each size n - dominated by the brute-force run
    // (the KD-tree part is O(n log n + K) per repeat)
    public static List<Row> run(int[] sizes, double horizon, int repeats, Consumer<String> log) {
        CollisionEngine engine = new CollisionEngine();
        List<Row> rows = new ArrayList<>();

        // warm-up so the JIT does not distort the first measurement
        List<SpaceObject> warm = SpaceObject.randomField(300, 700, 700, 1, 20);
        engine.predictBruteForce(warm, horizon);
        engine.predictWithKDTree(warm, horizon);

        for (int n : sizes) {
            double side = 40 * Math.sqrt(n);
            List<SpaceObject> objs = SpaceObject.randomField(n, side, side, 42, 20);
            Row r = new Row();
            r.n = n;
            r.bruteMs = Double.MAX_VALUE;
            r.kdBuildMs = Double.MAX_VALUE;
            r.kdQueryMs = Double.MAX_VALUE;

            for (int i = 0; i < repeats; i++) {
                CollisionEngine.Result b = engine.predictBruteForce(objs, horizon);
                CollisionEngine.Result k = engine.predictWithKDTree(objs, horizon);
                r.bruteMs = Math.min(r.bruteMs, b.queryNanos / 1e6);
                if (k.buildNanos / 1e6 + k.queryNanos / 1e6 < r.kdBuildMs + r.kdQueryMs) {
                    r.kdBuildMs = k.buildNanos / 1e6;
                    r.kdQueryMs = k.queryNanos / 1e6;
                }
                r.brutePairs = b.candidatePairs;
                r.kdCandidates = k.candidatePairs;
                r.nodesVisited = k.nodesVisited;
                r.collisions = k.eventCount();
                r.match = b.eventCount() == k.eventCount();   // KD-tree must find exactly the same collisions
            }
            rows.add(r);
            if (log != null) {
                log.accept(String.format("n=%-6d brute=%9.2f ms | kd=%8.2f ms (build %.2f) | speedup x%.1f | collisions=%d | match=%b",
                        r.n, r.bruteMs, r.kdTotalMs(), r.kdBuildMs, r.speedup(), r.collisions, r.match));
            }
        }
        return rows;
    }

    // Time complexity: O(rows)
    public static void writeCsv(List<Row> rows, File file) throws IOException {
        try (PrintWriter w = new PrintWriter(file, "UTF-8")) {
            w.println("objects,brute_ms,kd_build_ms,kd_query_ms,kd_total_ms,speedup,brute_pair_checks,kd_candidate_pairs,kd_nodes_visited,collisions_found,results_match");
            for (Row r : rows) {
                w.printf(java.util.Locale.ROOT, "%d,%.3f,%.3f,%.3f,%.3f,%.2f,%d,%d,%d,%d,%b%n",
                        r.n, r.bruteMs, r.kdBuildMs, r.kdQueryMs, r.kdTotalMs(), r.speedup(),
                        r.brutePairs, r.kdCandidates, r.nodesVisited, r.collisions, r.match);
            }
        }
    }

    // Time complexity: O(1) to open the window
    public static void showChart(List<Row> rows) {
        JFrame f = new JFrame("Performance: KD-tree vs Brute Force (log scale)");
        f.add(new ChartPanel(rows));
        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    public static final int[] DEFAULT_SIZES = {100, 500, 1000, 2000, 5000, 10000};

    // Time complexity: Same as run() - runs the benchmark, then writeCsv()
    public static void main(String[] args) throws Exception {
        List<Row> rows = run(DEFAULT_SIZES, 3.0, 3, System.out::println);
        File out = new File("benchmark_results.csv");
        writeCsv(rows, out);
        System.out.println("Saved " + out.getAbsolutePath());
        if (!GraphicsEnvironment.isHeadless()) showChart(rows);
    }

    // ------------------------------------------------------------------ chart

    private static class ChartPanel extends JPanel {
        private final List<Row> rows;

        // Time complexity: O(1)
        ChartPanel(List<Row> rows) {
            this.rows = rows;
            setPreferredSize(new Dimension(760, 500));
            setBackground(Color.WHITE);
        }

        // Time complexity: O(rows) per repaint
        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int L = 80, R = 30, T = 50, B = 60;
            int w = getWidth() - L - R, h = getHeight() - T - B;
            if (rows.isEmpty()) return;

            double min = Double.MAX_VALUE, max = 0;
            int maxN = 0;
            for (Row r : rows) {
                min = Math.min(min, Math.min(r.bruteMs, r.kdTotalMs()));
                max = Math.max(max, Math.max(r.bruteMs, r.kdTotalMs()));
                maxN = Math.max(maxN, r.n);
            }
            double lo = Math.floor(Math.log10(Math.max(min, 1e-3)));
            double hi = Math.ceil(Math.log10(Math.max(max, 1e-2)));
            if (hi <= lo) hi = lo + 1;

            g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, 14f));
            g.setColor(Color.BLACK);
            g.drawString("Collision prediction time vs number of objects", L, 28);

            // horizontal grid: one line per decade
            g.setFont(g.getFont().deriveFont(java.awt.Font.PLAIN, 11f));
            for (double d = lo; d <= hi; d++) {
                int y = T + (int) (h - (d - lo) / (hi - lo) * h);
                g.setColor(new Color(225, 225, 225));
                g.drawLine(L, y, L + w, y);
                g.setColor(Color.DARK_GRAY);
                g.drawString(String.format("%.3g ms", Math.pow(10, d)), 8, y + 4);
            }
            // x ticks
            for (Row r : rows) {
                int x = L + (int) ((double) r.n / maxN * w);
                g.setColor(Color.DARK_GRAY);
                g.drawLine(x, T + h, x, T + h + 4);
                g.drawString(String.valueOf(r.n), x - 14, T + h + 18);
            }
            g.drawString("Number of space objects", L + w / 2 - 60, T + h + 42);
            g.setColor(Color.BLACK);
            g.drawRect(L, T, w, h);

            drawSeries(g, L, T, w, h, lo, hi, maxN, new Color(210, 50, 50), true);
            drawSeries(g, L, T, w, h, lo, hi, maxN, new Color(30, 110, 210), false);

            g.setColor(new Color(210, 50, 50));
            g.fillRect(L + 12, T + 10, 12, 12);
            g.setColor(Color.BLACK);
            g.drawString("Brute force O(n^2)", L + 30, T + 21);
            g.setColor(new Color(30, 110, 210));
            g.fillRect(L + 12, T + 30, 12, 12);
            g.setColor(Color.BLACK);
            g.drawString("KD-tree (build + search)", L + 30, T + 41);
        }

        // Time complexity: O(rows)
        private void drawSeries(Graphics2D g, int L, int T, int w, int h, double lo, double hi, int maxN, Color c, boolean brute) {
            g.setColor(c);
            g.setStroke(new BasicStroke(2.2f));
            int px = -1, py = -1;
            for (Row r : rows) {
                double v = brute ? r.bruteMs : r.kdTotalMs();
                int x = L + (int) ((double) r.n / maxN * w);
                int y = T + (int) (h - (Math.log10(Math.max(v, 1e-3)) - lo) / (hi - lo) * h);
                if (px >= 0) g.drawLine(px, py, x, y);
                g.fillOval(x - 4, y - 4, 8, 8);
                px = x;
                py = y;
            }
            g.setStroke(new BasicStroke(1f));
        }
    }
}
