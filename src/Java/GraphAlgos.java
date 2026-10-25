import java.util.Arrays;
import java.util.PriorityQueue;

/** Assignment 3: graph algorithms. Iterative (no recursion), 64-bit totals. */
public final class GraphAlgos {
    private GraphAlgos() {}

    private static final int SHIFT = 18;                 // m <= 250000 < 2^18
    private static final long MASK = (1L << SHIFT) - 1;
    private static final long OFF = 1_000_000_000L;      // makes weights non-negative

    public record MstResult(long cost, int[] edgeIds, int components) {}
    public record SccResult(int[] component, int[][] condensation) {}
    public record TopoResult(boolean acyclic, int[] order) {}
    public record DagResult(boolean acyclic, Long[] distance, int[] parentEdge) {}

    private static long key(long w, int id) {
        return ((w + OFF) << SHIFT) | id;
    }

    /** mode 0: u->v, mode 1: both directions, mode 2: reversed (v->u). Returns {head, to, id}. */
    private static int[][] csr(int n, int[][] edges, int mode) {
        int[] head = new int[n + 1];
        for (int[] e : edges) {
            if (mode == 0) head[e[0] + 1]++;
            else if (mode == 2) head[e[1] + 1]++;
            else { head[e[0] + 1]++; head[e[1] + 1]++; }
        }
        for (int i = 0; i < n; i++) head[i + 1] += head[i];
        int[] pos = Arrays.copyOf(head, n + 1);
        int[] to = new int[head[n]];
        int[] id = new int[head[n]];
        for (int i = 0; i < edges.length; i++) {
            int u = edges[i][0], v = edges[i][1];
            if (mode == 0) { to[pos[u]] = v; id[pos[u]++] = i; }
            else if (mode == 2) { to[pos[v]] = u; id[pos[v]++] = i; }
            else {
                to[pos[u]] = v; id[pos[u]++] = i;
                to[pos[v]] = u; id[pos[v]++] = i;
            }
        }
        return new int[][] {head, to, id};
    }

    // ---------------------------------------------------------------- DSU
    private static final class Dsu {
        final int[] parent, size;
        int components, largest;

        Dsu(int n) {
            parent = new int[n];
            size = new int[n];
            for (int i = 0; i < n; i++) { parent[i] = i; size[i] = 1; }
            components = n;
            largest = n > 0 ? 1 : 0;
        }

        int find(int x) {
            int root = x;
            while (parent[root] != root) root = parent[root];
            while (parent[x] != root) { int nx = parent[x]; parent[x] = root; x = nx; } // path compression
            return root;
        }

        boolean union(int a, int b) {
            int ra = find(a), rb = find(b);
            if (ra == rb) return false;
            if (size[ra] < size[rb]) { int t = ra; ra = rb; rb = t; } // union by size
            parent[rb] = ra;
            size[ra] += size[rb];
            components--;
            if (size[ra] > largest) largest = size[ra];
            return true;
        }
    }

    /** operations rows are [u, v]; returns [components, largest] after each union. */
    public static int[][] dsu(int n, int[][] operations) {
        Dsu d = new Dsu(n);
        int[][] states = new int[operations.length][];
        for (int i = 0; i < operations.length; i++) {
            d.union(operations[i][0], operations[i][1]);
            states[i] = new int[] {d.components, d.largest};
        }
        return states;
    }

    // ------------------------------------------------------------ Kruskal
    /** edges rows are [u, v, w]. */
    public static MstResult kruskal(int n, int[][] edges) {
        long[] keys = new long[edges.length];
        for (int i = 0; i < edges.length; i++) keys[i] = key(edges[i][2], i);
        Arrays.sort(keys);
        Dsu d = new Dsu(n);
        int[] ids = new int[Math.max(0, n)];
        int cnt = 0;
        long cost = 0;
        for (long k : keys) {
            int id = (int) (k & MASK);
            if (d.union(edges[id][0], edges[id][1])) {
                cost += edges[id][2];
                ids[cnt++] = id;
            }
        }
        return new MstResult(cost, Arrays.copyOf(ids, cnt), n - cnt);
    }

    // --------------------------------------------------------------- Prim
    public static MstResult prim(int n, int[][] edges) {
        int[][] g = csr(n, edges, 1);
        int[] head = g[0], to = g[1], eid = g[2];
        boolean[] seen = new boolean[n];
        PriorityQueue<Long> pq = new PriorityQueue<>();
        int[] ids = new int[Math.max(0, n)];
        int cnt = 0, comps = 0;
        long cost = 0;
        for (int s = 0; s < n; s++) {          // restart in every component
            if (seen[s]) continue;
            comps++;
            seen[s] = true;
            for (int k = head[s]; k < head[s + 1]; k++)
                if (!seen[to[k]]) pq.add(key(edges[eid[k]][2], eid[k]));
            while (!pq.isEmpty()) {
                int id = (int) (pq.poll() & MASK);
                int v = seen[edges[id][0]] ? edges[id][1] : edges[id][0];
                if (seen[v]) continue;
                seen[v] = true;
                cost += edges[id][2];
                ids[cnt++] = id;
                for (int k = head[v]; k < head[v + 1]; k++)
                    if (!seen[to[k]]) pq.add(key(edges[eid[k]][2], eid[k]));
            }
        }
        return new MstResult(cost, Arrays.copyOf(ids, cnt), comps);
    }

    // ---------------------------------------------------------------- SCC
    /** Kosaraju: DFS for finishing order, transpose, DFS in reverse finishing order. */
    public static SccResult scc(int n, int[][] edges) {
        int[][] g = csr(n, edges, 0);
        int[] head = g[0], to = g[1];
        int[] it = Arrays.copyOf(head, n);
        int[] stack = new int[n];
        int[] finish = new int[n];
        boolean[] seen = new boolean[n];
        int fc = 0;
        for (int s = 0; s < n; s++) {
            if (seen[s]) continue;
            int sp = 0;
            stack[sp++] = s;
            seen[s] = true;
            while (sp > 0) {
                int u = stack[sp - 1];
                if (it[u] < head[u + 1]) {
                    int v = to[it[u]++];
                    if (!seen[v]) { seen[v] = true; stack[sp++] = v; }
                } else {
                    sp--;
                    finish[fc++] = u;
                }
            }
        }
        int[][] r = csr(n, edges, 2);
        int[] rh = r[0], rt = r[1];
        int[] comp = new int[n];
        Arrays.fill(comp, -1);
        int c = 0;
        for (int i = fc - 1; i >= 0; i--) {
            int s = finish[i];
            if (comp[s] != -1) continue;
            comp[s] = c;
            int sp = 0;
            stack[sp++] = s;
            while (sp > 0) {
                int u = stack[--sp];
                for (int k = rh[u]; k < rh[u + 1]; k++) {
                    int v = rt[k];
                    if (comp[v] == -1) { comp[v] = c; stack[sp++] = v; }
                }
            }
            c++;
        }
        long[] pairs = new long[edges.length];
        int pc = 0;
        for (int[] e : edges)
            if (comp[e[0]] != comp[e[1]]) pairs[pc++] = (long) comp[e[0]] * c + comp[e[1]];
        Arrays.sort(pairs, 0, pc);
        int[][] cond = new int[pc][];
        int cc = 0;
        for (int i = 0; i < pc; i++) {
            if (i > 0 && pairs[i] == pairs[i - 1]) continue;
            cond[cc++] = new int[] {(int) (pairs[i] / c), (int) (pairs[i] % c)};
        }
        return new SccResult(comp, Arrays.copyOf(cond, cc));
    }

    // --------------------------------------------------------------- Topo
    /** Three-state DFS (0 white, 1 gray, 2 black). Gray neighbour means a directed cycle. */
    public static TopoResult topo(int n, int[][] edges) {
        int[][] g = csr(n, edges, 0);
        int[] head = g[0], to = g[1];
        int[] it = Arrays.copyOf(head, n);
        int[] state = new int[n];
        int[] stack = new int[n];
        int[] post = new int[n];
        int pc = 0;
        for (int s = 0; s < n; s++) {
            if (state[s] != 0) continue;
            int sp = 0;
            stack[sp++] = s;
            state[s] = 1;
            while (sp > 0) {
                int u = stack[sp - 1];
                if (it[u] < head[u + 1]) {
                    int v = to[it[u]++];
                    if (state[v] == 1) return new TopoResult(false, new int[0]);
                    if (state[v] == 0) { state[v] = 1; stack[sp++] = v; }
                } else {
                    state[u] = 2;
                    sp--;
                    post[pc++] = u;
                }
            }
        }
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = post[n - 1 - i];
        return new TopoResult(true, order);
    }

    // ---------------------------------------------------------------- DAG
    /** edges rows are [u, v, w]. Cycle anywhere in the graph => acyclic=false and empty arrays. */
    public static DagResult dag(int n, int[][] edges, int source) {
        TopoResult t = topo(n, edges);
        if (!t.acyclic()) return new DagResult(false, new Long[0], new int[0]);
        int[][] g = csr(n, edges, 0);
        int[] head = g[0], to = g[1], eid = g[2];
        long[] dist = new long[n];
        boolean[] reached = new boolean[n];
        int[] parent = new int[n];
        Arrays.fill(parent, -1);
        reached[source] = true;
        for (int u : t.order()) {
            if (!reached[u]) continue;
            for (int k = head[u]; k < head[u + 1]; k++) {
                int v = to[k];
                long nd = dist[u] + edges[eid[k]][2];
                if (!reached[v] || nd < dist[v]) {
                    reached[v] = true;
                    dist[v] = nd;
                    parent[v] = eid[k];
                }
            }
        }
        Long[] out = new Long[n];
        for (int i = 0; i < n; i++) out[i] = reached[i] ? Long.valueOf(dist[i]) : null;
        return new DagResult(true, out, parent);
    }

    /** Edge IDs from source to target in path order; empty for target==source; null if unreachable. */
    public static int[] routeEdges(int[] parentEdge, int[][] edges, int source, int target) {
        if (target == source) return new int[0];
        if (parentEdge[target] == -1) return null;
        int len = 0;
        for (int v = target; v != source; v = edges[parentEdge[v]][0]) len++;
        int[] path = new int[len];
        int v = target;
        for (int i = len - 1; i >= 0; i--) {
            path[i] = parentEdge[v];
            v = edges[parentEdge[v]][0];
        }
        return path;
    }

    /**
     * Route-reconstruction helper: vertex sequence source..target built from parent_edge IDs.
     * The route to the source is [source]. No-route result (unreachable target): an EMPTY array.
     * Time and output space O(L) for a route with L vertices.
     */
    public static int[] route(int[] parentEdge, int[][] edges, int source, int target) {
        if (target == source) return new int[] {source};
        if (parentEdge[target] == -1) return new int[0];
        int len = 1;
        for (int v = target; v != source; v = edges[parentEdge[v]][0]) len++;
        int[] path = new int[len];
        int v = target;
        for (int i = len - 1; i >= 0; i--) {
            path[i] = v;
            if (v != source) v = edges[parentEdge[v]][0];
        }
        return path;
    }
}
