import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The six task methods. The algorithms live in GraphAlgos.java (same directory);
 * these methods read the request through Input and package the contract fields.
 * All algorithm state is local to a single call, so nothing survives between requests.
 */
public final class Algorithms {
    /** DSU with path compression and union by size; return states. */
    static Map<String, Object> dsu(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] operations = Input.operations(input);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("states", GraphAlgos.dsu(n, operations));
        return result;
    }

    /** Separate Kruskal implementation; return cost, edge_ids, components. */
    static Map<String, Object> kruskal(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);

        return mst(GraphAlgos.kruskal(n, edges));
    }

    /** Separate Prim implementation; restart for every unvisited component. */
    static Map<String, Object> prim(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);

        return mst(GraphAlgos.prim(n, edges));
    }

    /** Kosaraju's two DFS passes; return component and exact condensation. */
    static Map<String, Object> scc(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);

        GraphAlgos.SccResult r = GraphAlgos.scc(n, edges);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("component", r.component());
        result.put("condensation", r.condensation());
        return result;
    }

    /** Three-state DFS and reverse finish order; return acyclic and order. */
    static Map<String, Object> topo(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);

        GraphAlgos.TopoResult r = GraphAlgos.topo(n, edges);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("acyclic", r.acyclic());
        result.put("order", r.order());
        return result;
    }

    /** Whole-graph cycle check and DAG relaxation; return acyclic, distance, parent_edge. */
    static Map<String, Object> dag(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int source = Input.source(input);
        int[][] edges = Input.edges(input);

        GraphAlgos.DagResult r = GraphAlgos.dag(n, edges, source);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("acyclic", r.acyclic());
        result.put("distance", r.distance());
        result.put("parent_edge", r.parentEdge());
        return result;
    }

    private static Map<String, Object> mst(GraphAlgos.MstResult r) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cost", r.cost());
        result.put("edge_ids", r.edgeIds());
        result.put("components", r.components());
        return result;
    }
}
