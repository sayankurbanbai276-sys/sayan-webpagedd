import java.util.Arrays;
import java.util.Random;

/** Kruskal vs Prim on the same seeded graphs. Times only the algorithm call (see ANALYSIS.md). */
public class Bench {
    static final long SEED = 20261025L;
    static final int RUNS = 7, WARMUP = 5;

    static int[][] graph(int n, int m) {
        Random rnd = new Random(SEED + n);
        int[][] e = new int[m][];
        for (int i = 0; i < m; i++)
            e[i] = new int[] {rnd.nextInt(n), rnd.nextInt(n), rnd.nextInt(2_000_001) - 1_000_000};
        return e;
    }

    /** Edge IDs must be distinct, acyclic, n-c in number, and sum to the reported cost. */
    static void verify(int n, int[][] e, GraphAlgos.MstResult r) {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) p[i] = i;
        long cost = 0;
        boolean[] used = new boolean[e.length];
        for (int id : r.edgeIds()) {
            if (used[id]) throw new AssertionError("repeated edge");
            used[id] = true;
            int a = find(p, e[id][0]), b = find(p, e[id][1]);
            if (a == b) throw new AssertionError("cycle in forest");
            p[a] = b;
            cost += e[id][2];
        }
        if (cost != r.cost() || r.edgeIds().length != n - r.components()) throw new AssertionError("cost/size");
        int[] q = new int[n];
        for (int i = 0; i < n; i++) q[i] = i;
        int comps = n;
        for (int[] x : e) {
            int a = find(q, x[0]), b = find(q, x[1]);
            if (a != b) { q[a] = b; comps--; }
        }
        if (comps != r.components()) throw new AssertionError("components");
    }

    static int find(int[] p, int x) {
        while (p[x] != x) { p[x] = p[p[x]]; x = p[x]; }
        return x;
    }

    static double median(double[] a) {
        double[] s = a.clone();
        Arrays.sort(s);
        return s[s.length / 2];
    }

    public static void main(String[] args) {
        int[][] sizes = {{1_000, 5_000}, {10_000, 50_000}, {100_000, 250_000}};
        System.out.println("seed=" + SEED + " warmup=" + WARMUP + " runs=" + RUNS);
        for (int[] s : sizes) {
            int n = s[0], m = s[1];
            int[][] e = graph(n, m);
            var k0 = GraphAlgos.kruskal(n, e);
            var p0 = GraphAlgos.prim(n, e);
            verify(n, e, k0);
            verify(n, e, p0);
            if (k0.cost() != p0.cost() || k0.components() != p0.components()) throw new AssertionError("disagree");
            for (int w = 0; w < WARMUP; w++) { GraphAlgos.kruskal(n, e); GraphAlgos.prim(n, e); }
            double[] kt = new double[RUNS], pt = new double[RUNS];
            for (int r = 0; r < RUNS; r++) {          // interleave to share any drift
                long t0 = System.nanoTime();
                GraphAlgos.kruskal(n, e);
                kt[r] = (System.nanoTime() - t0) / 1e6;
                t0 = System.nanoTime();
                GraphAlgos.prim(n, e);
                pt[r] = (System.nanoTime() - t0) / 1e6;
            }
            System.out.printf("n=%d m=%d cost=%d components=%d%n", n, m, k0.cost(), k0.components());
            System.out.println("  kruskal ms: " + fmt(kt) + "  median=" + String.format("%.2f", median(kt)));
            System.out.println("  prim    ms: " + fmt(pt) + "  median=" + String.format("%.2f", median(pt)));
        }
    }

    static String fmt(double[] a) {
        StringBuilder b = new StringBuilder();
        for (double x : a) b.append(String.format("%.2f ", x));
        return b.toString().trim();
    }
}
