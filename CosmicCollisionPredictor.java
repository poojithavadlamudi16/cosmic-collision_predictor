import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cosmic Collision Predictor: A KD-Tree-Based Spatial Search and Collision Analysis System.
 * Interactive Swing front end: moving objects, KD-tree partitions, search animation,
 * ranked collision alerts and a KD-tree vs brute-force benchmark.
 */
public class CosmicCollisionPredictor extends JFrame {

    static final double W = 1000, H = 1000;     // world size

    // ---- simulation state
    private List<SpaceObject> objects = new ArrayList<>();
    private final KDTree tree = new KDTree();
    private boolean treeStale = true;
    private final CollisionEngine engine = new CollisionEngine();
    private CollisionEngine.Result lastResult;
    private List<CollisionEngine.CollisionEvent> topEvents = new ArrayList<>();
    private Set<Integer> involved = new HashSet<>();

    // ---- settings
    private boolean running = true, showSplits = true, useBrute = false, predictRequested = true;
    private double horizon = 3.0, simSpeed = 1.0;
    private int splitDepth = 5, frame = 0;

    // ---- query (click) state
    private boolean queryActive, queryRadiusMode;
    private double qx, qy, qRadius = 100;
    private final List<double[]> traceSnap = new ArrayList<>();
    private final List<double[]> hitSnap = new ArrayList<>();
    private int traceShown;

    // ---- widgets
    private final SpacePanel space = new SpacePanel();
    private final JTextArea statsArea = area(5), eventsArea = area(10), queryArea = area(5);
    private JButton pauseBtn;
    private JComboBox<String> modeBox;
    private final javax.swing.Timer timer = new javax.swing.Timer(16, e -> step());

    // Time complexity: O(n) - buildUI is O(1), generate(500) is O(n)
    public CosmicCollisionPredictor() {
        super("Cosmic Collision Predictor - KD-Tree Spatial Search & Collision Analysis");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        buildUI();
        generate(500);
        pack();
        setLocationRelativeTo(null);
        timer.start();
    }

    // ================================================================= UI setup

    // Time complexity: O(1)
    private static JTextArea area(int rows) {
        JTextArea a = new JTextArea(rows, 30);
        a.setEditable(false);
        a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        return a;
    }

    // Time complexity: O(1) - fixed number of UI components
    private void buildUI() {
        setLayout(new BorderLayout());
        add(space, BorderLayout.CENTER);

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(new EmptyBorder(8, 8, 8, 8));

        // object count + generate
        JSpinner countSpin = new JSpinner(new SpinnerNumberModel(500, 100, 10000, 100));
        JButton genBtn = new JButton("Generate");
        genBtn.addActionListener(e -> generate((Integer) countSpin.getValue()));
        side.add(row("Objects (100-10000)", countSpin, genBtn));

        // run controls
        pauseBtn = new JButton("Pause");
        pauseBtn.addActionListener(e -> setRunning(!running));
        JSlider speed = slider(1, 50, 10);
        speed.addChangeListener(e -> simSpeed = speed.getValue() / 10.0);
        side.add(row("Sim speed", speed, pauseBtn));

        JSlider hor = slider(1, 20, 3);
        hor.addChangeListener(e -> { horizon = hor.getValue(); predictRequested = true; });
        side.add(row("Horizon (s)", hor));

        // KD-tree display
        JCheckBox splitsBox = new JCheckBox("Show KD-tree splits", true);
        splitsBox.addActionListener(e -> showSplits = splitsBox.isSelected());
        JSpinner depthSpin = new JSpinner(new SpinnerNumberModel(5, 1, 14, 1));
        depthSpin.addChangeListener(e -> splitDepth = (Integer) depthSpin.getValue());
        side.add(row("Split depth", depthSpin, splitsBox));

        JCheckBox bruteBox = new JCheckBox("Predict with brute force");
        bruteBox.addActionListener(e -> { useBrute = bruteBox.isSelected(); predictRequested = true; });
        side.add(row("Algorithm", bruteBox));

        // click queries
        modeBox = new JComboBox<>(new String[]{"Nearest neighbor", "Radius search"});
        side.add(row("Click on map:", modeBox));
        JSlider rad = slider(10, 300, 100);
        rad.addChangeListener(e -> qRadius = rad.getValue());
        side.add(row("Search radius", rad));

        // actions
        JButton cmpBtn = new JButton("Compare KD vs Brute");
        cmpBtn.addActionListener(e -> compareNow());
        JButton benchBtn = new JButton("Run Benchmark + CSV");
        benchBtn.addActionListener(e -> runBenchmark(benchBtn));
        side.add(row("", cmpBtn, benchBtn));

        side.add(titled("Live statistics", statsArea));
        side.add(titled("Collision alerts (earliest first, min-heap)", eventsArea));
        side.add(titled("Last query / comparison / benchmark", queryArea));

        side.setPreferredSize(new Dimension(380, 780));
        add(new JScrollPane(side, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.EAST);
    }

    // Time complexity: O(1)
    private static JSlider slider(int min, int max, int val) {
        return new JSlider(min, max, val);
    }

    // Time complexity: O(1)
    private static JPanel row(String label, JComponent... comps) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        if (!label.isEmpty()) p.add(new JLabel(label));
        for (JComponent c : comps) p.add(c);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    // Time complexity: O(1)
    private static JComponent titled(String title, JTextArea a) {
        JScrollPane sp = new JScrollPane(a);
        sp.setBorder(BorderFactory.createTitledBorder(title));
        sp.setAlignmentX(Component.LEFT_ALIGNMENT);
        return sp;
    }

    // Time complexity: O(1)
    private void setRunning(boolean r) {
        running = r;
        pauseBtn.setText(r ? "Pause" : "Resume");
    }

    // ============================================================== simulation

    // Time complexity: O(n) - calls SpaceObject.randomField
    private void generate(int n) {
        objects = SpaceObject.randomField(n, W, H, System.nanoTime(), 20);
        queryActive = false;
        treeStale = true;
        predictRequested = true;
        eventsArea.setText("");
    }

    // Time complexity: O(1) if the tree is fresh, O(n log n) when it has to be rebuilt
    private void ensureTree() {
        if (treeStale) {
            tree.build(objects);
            treeStale = false;
        }
    }

    // Time complexity: O(n) to move objects, plus a prediction every few frames (see predict)
    private void step() {
        if (running) {
            double dt = 0.016 * simSpeed;
            for (SpaceObject o : objects) o.move(dt, W, H);
            treeStale = true;
        }
        frame++;
        int every = objects.size() > 2000 ? 30 : 10;
        if (predictRequested || (running && frame % every == 0)) {
            predict();
            predictRequested = false;
        }
        if (queryActive && traceShown < traceSnap.size()) {
            traceShown = Math.min(traceSnap.size(), traceShown + Math.max(1, traceSnap.size() / 60));
        }
        space.repaint();
    }

    // Time complexity: Cost of the prediction (see CollisionEngine) + O(n) to count object types + O(m) to build the 'involved' set and topEvents(10)
    private void predict() {
        if (objects.isEmpty()) return;
        lastResult = useBrute ? engine.predictBruteForce(objects, horizon)
                              : engine.predictWithKDTree(objects, horizon);
        topEvents = lastResult.topEvents(10);
        involved = new HashSet<>();
        for (CollisionEngine.CollisionEvent e : lastResult.heap) {
            involved.add(e.a.id);
            involved.add(e.b.id);
        }
        long n = objects.size();
        int cA = 0, cP = 0, cS = 0;
        for (SpaceObject o : objects) {
            if (o.type == SpaceObject.Type.ASTEROID) cA++;
            else if (o.type == SpaceObject.Type.PLANET) cP++;
            else cS++;
        }
        statsArea.setText(String.format(
                "Algorithm:        %s%nObjects:          %d (A:%d P:%d S:%d)%nHorizon:          %.0f s%n"
                        + "Tree build:       %.2f ms%nPrediction:       %.2f ms%n"
                        + "Pairs examined:   %d (brute force: %d)%nKD nodes visited: %d%nCollisions found: %d",
                useBrute ? "Brute force" : "KD-tree", n, cA, cP, cS, horizon,
                lastResult.buildNanos / 1e6, lastResult.queryNanos / 1e6,
                lastResult.candidatePairs, n * (n - 1) / 2, lastResult.nodesVisited, lastResult.eventCount()));
        StringBuilder sb = new StringBuilder();
        for (CollisionEngine.CollisionEvent e : topEvents) sb.append(e).append('\n');
        if (topEvents.isEmpty()) sb.append("No collisions predicted within the horizon.");
        eventsArea.setText(sb.toString());
        eventsArea.setCaretPosition(0);
    }

    // =================================================================== queries

    // Time complexity: Nearest mode: O(log n). Radius mode: O(sqrt(n) + k). Plus O(trace length) to copy the trace.
    private void handleClick(double wx, double wy) {
        if (objects.isEmpty()) return;
        setRunning(false);                       // freeze so the animation matches the data
        ensureTree();
        queryRadiusMode = modeBox.getSelectedIndex() == 1;
        qx = wx;
        qy = wy;
        tree.enableTrace(true);
        tree.resetStats();
        hitSnap.clear();
        String text;
        if (queryRadiusMode) {
            List<SpaceObject> out = new ArrayList<>();
            tree.radiusSearch(wx, wy, qRadius, out);
            for (SpaceObject o : out) hitSnap.add(new double[]{o.x, o.y});
            text = String.format("Radius search (r = %.0f)%nObjects found:   %d%nNodes visited:   %d of %d%nSubtrees pruned: %d%n(brute force would check %d)",
                    qRadius, out.size(), tree.getNodesVisited(), tree.size(), tree.getSubtreesPruned(), tree.size());
        } else {
            SpaceObject nn = tree.nearest(wx, wy, null);
            if (nn != null) hitSnap.add(new double[]{nn.x, nn.y});
            text = String.format("Nearest neighbor%nResult:          %s%nDistance:        %.2f%nNodes visited:   %d of %d%nSubtrees pruned: %d%n(brute force would check %d)",
                    nn, tree.lastNearestDistance(), tree.getNodesVisited(), tree.size(), tree.getSubtreesPruned(), tree.size());
        }
        traceSnap.clear();
        for (KDTree.Node nd : tree.getTrace()) traceSnap.add(new double[]{nd.obj.x, nd.obj.y});
        tree.enableTrace(false);
        traceShown = 0;
        queryActive = true;
        queryArea.setText(text);
    }

    // Time complexity: O(n^2) - runs brute force as well as the KD-tree
    private void compareNow() {
        if (objects.isEmpty()) return;
        CollisionEngine.Result kd = engine.predictWithKDTree(objects, horizon);
        CollisionEngine.Result bf = engine.predictBruteForce(objects, horizon);
        double kdMs = (kd.buildNanos + kd.queryNanos) / 1e6, bfMs = bf.queryNanos / 1e6;
        queryArea.setText(String.format(
                "Comparison (%d objects, %.0f s horizon)%nBrute force: %.2f ms, %d pair tests%nKD-tree:     %.2f ms, %d pair tests%nSpeed-up:     x%.1f%nSame collisions found: %b (%d)",
                objects.size(), horizon, bfMs, bf.candidatePairs, kdMs, kd.candidatePairs,
                bfMs / Math.max(1e-9, kdMs), kd.eventCount() == bf.eventCount(), kd.eventCount()));
    }

    // Time complexity: Same as PerformanceTester.run() - runs in a background thread
    private void runBenchmark(JButton btn) {
        btn.setEnabled(false);
        queryArea.setText("Running benchmark (this can take a minute)...\n");
        new SwingWorker<List<PerformanceTester.Row>, String>() {
            @Override
            protected List<PerformanceTester.Row> doInBackground() {
                return PerformanceTester.run(PerformanceTester.DEFAULT_SIZES, 3.0, 3, s -> publish(s));
            }

            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks) queryArea.append(s + "\n");
            }

            @Override
            protected void done() {
                btn.setEnabled(true);
                try {
                    List<PerformanceTester.Row> rows = get();
                    File f = new File("benchmark_results.csv");
                    PerformanceTester.writeCsv(rows, f);
                    queryArea.append("Saved " + f.getAbsolutePath() + "\n");
                    PerformanceTester.showChart(rows);
                } catch (Exception ex) {
                    queryArea.append("Benchmark failed: " + ex + "\n");
                }
            }
        }.execute();
    }

    // ================================================================== drawing

    private class SpacePanel extends JPanel {
        // Time complexity: O(1)
        SpacePanel() {
            setBackground(new Color(8, 10, 24));
            setPreferredSize(new Dimension(780, 780));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    handleClick((e.getX() - ox()) / scale(), (e.getY() - oy()) / scale());
                }
            });
        }

        // Time complexity: O(1)
        double scale() { return Math.min(getWidth() / W, getHeight() / H); }
        // Time complexity: O(1)
        double ox() { return (getWidth() - W * scale()) / 2; }
        // Time complexity: O(1)
        double oy() { return (getHeight() - H * scale()) / 2; }
        // Time complexity: O(1)
        int sx(double x) { return (int) Math.round(ox() + x * scale()); }
        // Time complexity: O(1)
        int sy(double y) { return (int) Math.round(oy() + y * scale()); }

        // Time complexity: O(n + trace length) per frame - draws n objects (HashSet lookup is O(1)),
        // plus split lines O(min(n, 2^splitDepth)) and at most 10 alert lines
        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double s = scale();

            g.setColor(new Color(40, 50, 90));
            g.drawRect(sx(0), sy(0), (int) (W * s), (int) (H * s));

            // KD-tree partitions
            if (showSplits && !objects.isEmpty()) {
                ensureTree();
                for (double[] l : tree.collectSplits(W, H, splitDepth)) {
                    int alpha = Math.max(40, 200 - (int) l[5] * 25);
                    g.setColor(l[4] == 0 ? new Color(80, 140, 255, alpha) : new Color(180, 100, 255, alpha));
                    g.drawLine(sx(l[0]), sy(l[1]), sx(l[2]), sy(l[3]));
                }
            }

            // objects
            Color hot = new Color(255, 150, 60);
            for (SpaceObject o : objects) {
                int r = Math.max(2, (int) Math.round(o.radius * s));
                int cx = sx(o.x), cy = sy(o.y);
                g.setColor(involved.contains(o.id) ? hot : new Color(o.type.rgb));
                if (o.type == SpaceObject.Type.SPACESHIP) {          // triangle pointing along its velocity
                    double ang = Math.atan2(o.vy, o.vx), k = Math.max(3, 2 * r);
                    int[] px = {(int) (cx + k * Math.cos(ang)), (int) (cx + 0.7 * k * Math.cos(ang + 2.5)),
                                (int) (cx + 0.7 * k * Math.cos(ang - 2.5))};
                    int[] py = {(int) (cy + k * Math.sin(ang)), (int) (cy + 0.7 * k * Math.sin(ang + 2.5)),
                                (int) (cy + 0.7 * k * Math.sin(ang - 2.5))};
                    g.fillPolygon(px, py, 3);
                } else {
                    g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
                    if (o.type == SpaceObject.Type.PLANET) {          // ring marks a planet
                        g.setColor(new Color(255, 255, 255, 150));
                        g.drawOval(cx - r - 2, cy - r - 2, 2 * r + 4, 2 * r + 4);
                    }
                }
            }

            // top collision alerts
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{5f, 4f}, 0f));
            for (CollisionEngine.CollisionEvent e : topEvents) {
                g.setColor(new Color(255, 70, 70));
                g.drawLine(sx(e.a.x), sy(e.a.y), sx(e.b.x), sy(e.b.y));
                int ra = Math.max(4, (int) (e.a.radius * s) + 3), rb = Math.max(4, (int) (e.b.radius * s) + 3);
                g.drawOval(sx(e.a.x) - ra, sy(e.a.y) - ra, 2 * ra, 2 * ra);
                g.drawOval(sx(e.b.x) - rb, sy(e.b.y) - rb, 2 * rb, 2 * rb);
                g.setColor(Color.WHITE);
                g.drawString(e.impactTime <= 0 ? "NOW" : String.format("%.1fs", e.impactTime),
                        (sx(e.a.x) + sx(e.b.x)) / 2 + 4, (sy(e.a.y) + sy(e.b.y)) / 2 - 4);
            }
            g.setStroke(new BasicStroke(1f));

            // query overlay
            if (queryActive) {
                int cx = sx(qx), cy = sy(qy);
                if (queryRadiusMode) {
                    int rr = (int) (qRadius * s);
                    g.setColor(new Color(255, 255, 255, 120));
                    g.drawOval(cx - rr, cy - rr, 2 * rr, 2 * rr);
                }
                g.setColor(new Color(255, 200, 0, 170));          // visited nodes
                for (int i = 0; i < traceShown && i < traceSnap.size(); i++) {
                    double[] p = traceSnap.get(i);
                    g.drawOval(sx(p[0]) - 5, sy(p[1]) - 5, 10, 10);
                }
                if (traceShown >= traceSnap.size()) {            // finished: show answer
                    g.setColor(new Color(60, 255, 120));
                    for (double[] p : hitSnap) {
                        g.fillOval(sx(p[0]) - 4, sy(p[1]) - 4, 8, 8);
                        if (!queryRadiusMode) g.drawLine(cx, cy, sx(p[0]), sy(p[1]));
                    }
                }
                g.setColor(Color.WHITE);
                g.drawLine(cx - 7, cy, cx + 7, cy);
                g.drawLine(cx, cy - 7, cx, cy + 7);
            }

            g.setColor(new Color(150, 160, 200));
            g.drawString("Grey dot: asteroid | Blue dot with ring: planet | Green triangle: spaceship", 10, getHeight() - 24);
            g.drawString("Blue/purple lines: KD-tree splits | Orange: objects on a collision course | "
                    + "Red: earliest predicted impacts | Yellow rings: nodes visited by search", 10, getHeight() - 8);
        }
    }

    // Time complexity: O(1) - opens the window
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CosmicCollisionPredictor().setVisible(true));
    }
}
