import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A circular object moving through 2D space with constant velocity.
 *
 * Every object is one of three kinds of body found in space. The kind decides its size,
 * mass, speed and how common it is. The mathematical reasoning for each choice:
 *
 * MASS (2D world):  m = density * area = density * PI * r^2
 *   In a 2D world the "volume" of a disc is its area, so mass grows with r squared
 *   (it would be r cubed in 3D). A bigger body is therefore much heavier than a small one.
 *
 * DENSITY (g/cm^3, scaled):  planet 5.5 > asteroid 2.5 > spaceship 0.5
 *   Earth's mean density is about 5.5 (iron core + rock). Rocky asteroids are about 2.5.
 *   A spaceship is mostly hollow hull and fuel tanks, so its average density is very low.
 *
 * SIZE:  asteroid 1.5..4 (power law), planet 5..8, spaceship 1..2
 *   Small bodies are far more common than large ones. Real asteroid counts follow a power
 *   law, N(> r) ~ r^-2. We sample it with the inverse-CDF (Pareto) method:
 *   r = rMin * (1 - u)^(-1/2), where u is a uniform random number in [0,1).
 *
 * SPEED (fraction of maxSpeed):  spaceship 0.50..1.00 > asteroid 0.10..0.60 > planet 0.02..0.10
 *   With the same energy E = 1/2 m v^2 a heavier body moves slower: v = sqrt(2E/m).
 *   Momentum p = m v tells the same story: a body with large m changes speed very little.
 *   A spaceship has an engine, so it is the fastest and lightest.
 *
 * HOW COMMON:  asteroid 70%, spaceship 27%, planet 3%
 *   Planets are rare, asteroids are everywhere, ships are in between.
 */
public class SpaceObject {

    /** The three kinds of body. Each constant carries the numbers described above. */
    public enum Type {
        //          label       density share  rMin rMax vLo   vHi   colour
        ASTEROID  ("Asteroid",  2.5,    0.70,  1.5, 4.0, 0.10, 0.60, 0xB4A58C),
        PLANET    ("Planet",    5.5,    0.03,  5.0, 8.0, 0.02, 0.10, 0x4FA3FF),
        SPACESHIP ("Ship",      0.5,    0.27,  1.0, 2.0, 0.50, 1.00, 0x7CFFB2);

        public final String label;
        public final double density;        // mass per unit area
        public final double share;          // fraction of all objects of this kind
        public final double rMin, rMax;     // radius range
        public final double vLo, vHi;       // speed range as a fraction of maxSpeed
        public final int rgb;               // colour used by the GUI

        Type(String label, double density, double share, double rMin, double rMax,
             double vLo, double vHi, int rgb) {
            this.label = label;
            this.density = density;
            this.share = share;
            this.rMin = rMin;
            this.rMax = rMax;
            this.vLo = vLo;
            this.vHi = vHi;
            this.rgb = rgb;
        }

        /** Choose a kind from a uniform random number u in [0,1) using the shares above. */
        // Time complexity: O(1) - at most 3 comparisons
        public static Type pick(double u) {
            double acc = 0;
            for (Type t : values()) {
                acc += t.share;
                if (u < acc) return t;
            }
            return SPACESHIP;
        }

        /**
         * Random radius for this kind from a uniform number u in [0,1).
         * Asteroids: power law (Pareto) r = rMin * (1-u)^(-1/2), capped at rMax.
         * Planets and ships: uniform between rMin and rMax.
         */
        // Time complexity: O(1)
        public double randomRadius(double u) {
            if (this == ASTEROID) return Math.min(rMax, rMin * Math.pow(1 - u, -0.5));
            return rMin + u * (rMax - rMin);
        }
    }

    public final int id;
    public final Type type;
    public double x, y;      // position
    public double vx, vy;    // velocity (units per second)
    public final double radius;
    public final double mass;   // density * PI * r^2

    // Time complexity: O(1)
    public SpaceObject(int id, double x, double y, double vx, double vy, double radius) {
        this(id, Type.ASTEROID, x, y, vx, vy, radius);
    }

    // Time complexity: O(1)
    public SpaceObject(int id, Type type, double x, double y, double vx, double vy, double radius) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.radius = radius;
        this.mass = type.density * Math.PI * radius * radius;
    }

    /** Coordinate on the given axis (0 = x, 1 = y). Used by the KD-tree. */
    // Time complexity: O(1)
    public double coord(int axis) {
        return axis == 0 ? x : y;
    }

    // Time complexity: O(1)
    public double speed() {
        return Math.hypot(vx, vy);
    }

    /** Kinetic energy E = 1/2 m v^2. */
    // Time complexity: O(1)
    public double kineticEnergy() {
        return 0.5 * mass * (vx * vx + vy * vy);
    }

    /** Advance by dt seconds; bounce off the walls of a w x h world. */
    // Time complexity: O(1) - position update plus wall bounce
    public void move(double dt, double w, double h) {
        x += vx * dt;
        y += vy * dt;
        if (x < radius) { x = radius; vx = Math.abs(vx); }
        else if (x > w - radius) { x = w - radius; vx = -Math.abs(vx); }
        if (y < radius) { y = radius; vy = Math.abs(vy); }
        else if (y > h - radius) { y = h - radius; vy = -Math.abs(vy); }
    }

    /** Generate n random asteroids, planets and spaceships inside a w x h world. */
    // Time complexity: O(n) - creates n objects
    public static List<SpaceObject> randomField(int n, double w, double h, long seed, double maxSpeed) {
        Random rnd = new Random(seed);
        List<SpaceObject> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Type t = Type.pick(rnd.nextDouble());
            double r = t.randomRadius(rnd.nextDouble());
            double px = r + rnd.nextDouble() * (w - 2 * r);
            double py = r + rnd.nextDouble() * (h - 2 * r);
            double angle = rnd.nextDouble() * 2 * Math.PI;
            double sp = maxSpeed * (t.vLo + rnd.nextDouble() * (t.vHi - t.vLo));
            list.add(new SpaceObject(i, t, px, py, Math.cos(angle) * sp, Math.sin(angle) * sp, r));
        }
        return list;
    }

    // Time complexity: O(1)
    @Override
    public String toString() {
        return String.format("#%d %s (%.1f, %.1f)", id, type.label, x, y);
    }
}
