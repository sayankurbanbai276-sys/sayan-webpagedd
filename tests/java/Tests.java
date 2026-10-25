import java.util.Arrays;
import java.util.Random;

public class Tests {
    static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    /** Semantic check of parent_edge: ends at vertex, tight on distance, leads to the source. */
    static boolean validParents(int[][] e, int n, int src, Long[] dist, int[] par) {
        for (int v = 0; v < n; v++) {
            if (v == src || dist[v] == null) { if (par[v] != -1) return false; continue; }
            int id = par[v];
            if (id < 0 || id >= e.length || e[id][1] != v) return false;
            if (dist[e[id][0]] == null || dist[e[id][0]] + e[id][2] != dist[v]) return false;
        }
        return true;
    }

    static boolean[][] reach(int n, int[][] e) {
        boolean[][] r = new boolean[n][n];
        for (int i = 0; i < n; i++) r[i][i] = true;
        for (int[] x : e) r[x[0]][x[1]] = true;
        for (int k = 0; k < n; k++)
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++)
                    if (r[i][k] && r[k][j]) r[i][j] = true;
        return r;
    }

    static Long[] bellman(int n, int[][] e, int s) {
        Long[] d = new Long[n];
        d[s] = 0L;
        for (int it = 0; it < n; it++)
            for (int[] x : e)
                if (d[x[0]] != null && (d[x[1]] == null || d[x[0]] + x[2] < d[x[1]])) d[x[1]] = d[x[0]] + x[2];
        return d;
    }

    static void randomTests() {
        Random rnd = new Random(7);
        for (int iter = 0; iter < 2000; iter++) {
            int n = 1 + rnd.nextInt(8), m = rnd.nextInt(15);
            int[][] w = new int[m][];
            int[][] d = new int[m][];
            for (int i = 0; i < m; i++) {
                int u = rnd.nextInt(n), v = rnd.nextInt(n);
                w[i] = new int[] {u, v, rnd.nextInt(15) - 5};
                d[i] = new int[] {u, v};
            }
            var k = GraphAlgos.kruskal(n, w);
            var p = GraphAlgos.prim(n, w);
            check(k.cost() == p.cost() && k.components() == p.components()
                    && k.edgeIds().length == p.edgeIds().length, "mst mismatch");

            boolean[][] r = reach(n, d);
            var s = GraphAlgos.scc(n, d);
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++)
                    check((s.component()[i] == s.component()[j]) == (r[i][j] && r[j][i]), "scc");
            boolean cyc = false;
            for (int[] x : d) if (r[x[1]][x[0]]) cyc = true;
            var t = GraphAlgos.topo(n, d);
            check(t.acyclic() == !cyc, "topo flag");
            if (t.acyclic()) {
                int[] pos = new int[n];
                check(t.order().length == n, "order len");
                for (int i = 0; i < n; i++) pos[t.order()[i]] = i;
                for (int[] x : d) check(pos[x[0]] < pos[x[1]], "order invalid");
            } else check(t.order().length == 0, "order must be empty");

            int src = rnd.nextInt(n);
            var res = GraphAlgos.dag(n, w, src);
            check(res.acyclic() == !cyc, "dag flag");
            if (res.acyclic()) {
                check(Arrays.equals(res.distance(), bellman(n, w, src)), "dag dist");
                check(res.parentEdge()[src] == -1, "src parent");
                for (int v = 0; v < n; v++) {
                    if (res.distance()[v] == null) { check(res.parentEdge()[v] == -1, "unreach parent"); continue; }
                    int[] path = GraphAlgos.routeEdges(res.parentEdge(), w, src, v);
                    int[] verts = GraphAlgos.route(res.parentEdge(), w, src, v);
                    check(verts[0] == src && verts[verts.length - 1] == v && verts.length == path.length + 1, "route vertices");
                    for (int i = 0; i < path.length; i++) check(w[path[i]][0] == verts[i] && w[path[i]][1] == verts[i + 1], "route direction");
                    long sum = 0;
                    for (int id : path) sum += w[id][2];
                    check(sum == res.distance()[v], "route sum");
                }
            } else check(res.distance().length == 0 && res.parentEdge().length == 0, "cyclic dag arrays");
        }
    }

    public static void main(String[] a) {
        randomTests();
        System.out.println("ok random");

        check(Arrays.deepEquals(GraphAlgos.dsu(5, new int[][] {{0, 1}, {1, 0}, {2, 3}, {1, 3}}),
                new int[][] {{4, 2}, {4, 2}, {3, 2}, {2, 4}}), "dsu");
        check(GraphAlgos.dsu(0, new int[0][]).length == 0, "dsu empty");
        System.out.println("ok dsu");

        int n = 100000;
        int[][] chain = new int[n - 1][];
        for (int i = 0; i < n - 1; i++) chain[i] = new int[] {i, i + 1};
        check(GraphAlgos.topo(n, chain).acyclic(), "chain topo");
        check(GraphAlgos.scc(n, chain).component().length == n, "chain scc");
        int[][] ring = Arrays.copyOf(chain, n);
        ring[n - 1] = new int[] {n - 1, 0};
        check(!GraphAlgos.topo(n, ring).acyclic(), "ring topo");
        check(Arrays.stream(GraphAlgos.scc(n, ring).component()).max().getAsInt() == 0, "ring scc");
        System.out.println("ok deep chain");

        int[][] two = {{0, 1, 1}, {2, 3, 1}, {3, 2, 1}};
        var r = GraphAlgos.dag(4, two, 0);
        check(!r.acyclic() && r.distance().length == 0 && r.parentEdge().length == 0, "unreachable cycle");
        System.out.println("ok cycle unreachable from source");

        int[][] par = {{0, 1, 5}, {0, 1, 2}, {1, 2, 1}};
        var q = GraphAlgos.dag(4, par, 0);
        check(Arrays.equals(GraphAlgos.routeEdges(q.parentEdge(), par, 0, 2), new int[] {1, 2}), "route tied edge ids");
        check(Arrays.equals(GraphAlgos.route(q.parentEdge(), par, 0, 2), new int[] {0, 1, 2}), "route tied vertices");
        check(Arrays.equals(GraphAlgos.route(q.parentEdge(), par, 0, 0), new int[] {0}), "route source");
        check(GraphAlgos.route(q.parentEdge(), par, 0, 3).length == 0, "route unreachable");
        int[][] ex = {{0, 1, 3}, {0, 2, 8}, {1, 2, -7}, {2, 3, 4}};
        var e5 = GraphAlgos.dag(5, ex, 0);
        check(Arrays.equals(GraphAlgos.route(e5.parentEdge(), ex, 0, 3), new int[] {0, 1, 2, 3}), "README example route");
        System.out.println("ok route");

        // A checker must accept another valid witness and reject an invalid one.
        int[][] tie = {{0, 1, 1}, {0, 1, 1}};
        check(validParents(tie, 2, 0, new Long[] {0L, 1L}, new int[] {-1, 0}), "alt witness 0");
        check(validParents(tie, 2, 0, new Long[] {0L, 1L}, new int[] {-1, 1}), "alt witness 1");
        check(!validParents(tie, 2, 0, new Long[] {0L, 1L}, new int[] {-1, 5}), "bad witness id");
        check(!validParents(new int[][] {{1, 0, 1}}, 2, 0, new Long[] {0L, 1L}, new int[] {-1, 0}), "bad witness direction");
        System.out.println("ok witness checker accepts alternative, rejects invalid");

        int m = 250000;
        int[][] big = new int[m][];
        Random rnd = new Random(1);
        for (int i = 0; i < m; i++) big[i] = new int[] {rnd.nextInt(n), rnd.nextInt(n), rnd.nextInt(2_000_000_001) - 1_000_000_000};
        var k = GraphAlgos.kruskal(n, big);
        var p = GraphAlgos.prim(n, big);
        check(k.cost() == p.cost() && k.components() == p.components(), "big mst");
        System.out.println("ok full limits: cost=" + k.cost() + " components=" + k.components());
    }
}
