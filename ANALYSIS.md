# Assignment 3: Graph Algorithms - Analysis

Language: **Java 17** (`submission.json`: `{"language":"Java"}`).
Code: `src/Java/Algorithms.java` (six task methods, packaging of the JSON fields) and `src/Java/GraphAlgos.java` (the algorithms and the route helper). `Main.java`, `Input.java` and `Json.java` are the supplied adapters and are unchanged.

## 1. Build, run and test commands

Run from the repository root.

```sh
python tools/run.py --setup                     # launch check (ping)
python tools/run.py --check                     # 36 public cases + state-reset batch
python tools/run.py --input tests/my_cases.jsonl > tests/my_cases_actual.jsonl
python tests/check_my_cases.py                  # semantic comparison with hand-justified answers

# own unit tests, route helper, full limits, benchmark (compilation is a separate step)
mkdir -p .build/tests
javac --release 17 -d .build/tests src/Java/GraphAlgos.java tests/java/Tests.java tests/java/Bench.java
java -Xmx384m -cp .build/tests Tests
java -Xmx384m -cp .build/tests Bench
```

## 2. Common design

- **Edge IDs.** `edges[i]` has ID `i`. Sorting and adjacency lists store IDs, never copies of rows. Adjacency is a CSR layout (`head[]`, `to[]`, `id[]`), built in `O(n+m)`.
- **No recursion.** Every DFS keeps an explicit `int[]` stack plus a per-vertex cursor `it[u]` (the next unread slot of `u`'s adjacency range). A suspended vertex resumes exactly where it stopped, so no neighbour is scanned twice. A chain of 100000 vertices therefore cannot overflow the call stack (tested, section 5).
- **64-bit arithmetic.** Costs and distances are `long`; the largest magnitude is `250000 * 1e9 = 2.5e14`. Vertex/edge IDs are `int`.
- **State reset.** `Algorithms` methods are static and keep no fields. Each call allocates its own arrays (`Dsu`, `visited`, `stack`, heap) so nothing survives between requests. This is checked by the repeated mixed requests in `tests/my_cases.jsonl` (cases 2/11 are identical, separated by other tasks) and by the public "two complete batches in one process" check.
- **Packed sort keys.** Kruskal and Prim order edges by the `long` key `((w + 1e9) << 18) | id`. `w + 1e9` lies in `[0, 2e9]` (< 2^31), and `id < 250000 < 2^18`, so the key fits in 49 bits, preserves weight order (ties by ID), and recovers the ID with `key & (2^18 - 1)`.

## 3. Methods

### 3.1 `dsu`
- **Steps.** `parent[i]=i`, `size[i]=1`, `components=n`, `largest = n>0 ? 1 : 0`. For each `[u,v]`: `find` both roots; if different, attach the smaller tree to the larger, add sizes, `components--`, `largest=max(largest,size)`. Append `[components, largest]` after every operation (also for repeated or self unions).
- **Invariant.** `parent` encodes a forest whose trees are exactly the current components; `size[root]` equals the number of vertices of that tree; `components` is the number of roots; `largest` is the maximum of root sizes (sizes only grow, so a running maximum is exact).
- **Correctness.** A union of two different roots merges exactly two components; a union of equal roots changes nothing, so repeated and self unions keep the state. Path compression only rewires vertices directly to their root, which preserves the partition.
- **Boundaries.** `n=0` gives `largest=0` and `states=[]`; self-union; repeated union; `q=0`.
- **Complexity.** `O(n + q * alpha(n))` time, `O(n + q)` memory (the states are `q` pairs).

### 3.2 `kruskal`
- **Steps.** Pack every edge into a key, sort the `long[]`, scan in order, and accept an edge when `union(u,v)` merges two different components. Accumulate `cost` (long) and the original IDs. `components = n - accepted`.
- **Invariant.** After each scan step the accepted edges form a minimum spanning forest of the edges examined so far (cut/cycle property); the DSU partition equals the components of the accepted edges.
- **Correctness.** The lightest edge crossing any cut of the current partition is safe; the sorted scan meets such an edge before any heavier one. A self-loop has `find(u)==find(u)` and is never taken. Negative weights need no special case because the order, not the sign, matters. For `c` components exactly `n-c` edges are accepted.
- **Boundaries.** `n=0` gives cost 0, no IDs, 0 components; isolates count as components; parallel edges (the lighter/earlier one wins); totals above 2^31 (tested: `3000000000`).
- **Complexity.** `O(n + m log(m+1))` time (sorting dominates), `O(n+m)` memory.

### 3.3 `prim`
- **Steps.** Build an undirected CSR. For every unvisited `s` (this is the restart): count a component, mark `s`, push its edges to unvisited neighbours into a lazy min-heap of packed keys. Pop the smallest key; recover the edge; its unvisited endpoint `v` is added (if both endpoints are visited, the entry is stale and is skipped); add `w` to the cost, record the ID, push `v`'s edges to unvisited neighbours.
- **Invariant.** The visited set of the current component with the chosen edges is a minimum spanning tree of the subgraph it induces among visited vertices (as a prefix of Prim's run); the heap contains the edges that leave the visited set (plus stale entries).
- **Correctness.** The heap minimum is the lightest edge crossing the cut (visited, unvisited), which is safe. When the heap is empty, the component is complete; the outer loop restarts so isolates and other components are covered, and each restart yields one component.
- **Boundaries.** Self-loops are never pushed (the other endpoint is already visited); parallel edges; negative weights; `n=0`; isolates.
- **Complexity.** `O(n + m log(m+1))` time with the lazy heap (at most `2m` pushes), `O(n+m)` memory.

### 3.4 `scc` (Kosaraju)
- **Steps.** Pass 1: iterative DFS over the original graph; record vertices in finish order. Build the transpose CSR. Pass 2: take vertices in decreasing finish order; every unlabeled vertex starts a new label and an iterative DFS/stack traversal over the transpose labels everything it reaches. Condensation: for every edge with different labels, pack `label(u)*c + label(v)` into a `long`, sort, drop duplicates.
- **Invariant.** After pass 1, the vertex finishing last lies in a source SCC of the condensation. In pass 2, each traversal from the unlabeled vertex with the largest finish time stays inside one SCC of the transpose (all SCCs it could leave are already labeled).
- **Correctness.** Standard Kosaraju argument: reverse finish order processes SCCs in a topological order of the condensation, and the transpose traversal cannot escape into an unlabeled SCC. Condensation pairs are exactly the distinct cross-label edges.
- **Boundaries.** `n=0` (empty arrays); isolates (own SCC); duplicate edges and self-loops (self-loops are internal, so they are excluded from the condensation; duplicates collapse).
- **Complexity.** Two DFS passes `O(n+m)`. The deduplication uses sorting, so the total is `O(n + m log(m+1))`, slightly above the `O(n+m)` expected target with hashing; memory is `O(n+m)`.

### 3.5 `topo`
- **Steps.** Three states: 0 unvisited, 1 active (on the stack), 2 finished. Iterative DFS from every unvisited vertex. Reading an edge to an active vertex means a directed cycle: return `acyclic=false`, `order=[]` immediately. When a vertex exhausts its edges, mark it finished and append it to the post-order. The answer is the reversed post-order.
- **Invariant.** Active vertices form the current DFS path; a finished vertex never reaches an active one (otherwise a cycle would already have been reported).
- **Correctness.** A directed cycle exists iff DFS sees an edge into an active vertex. With no such edge, finish times satisfy `finish(u) > finish(v)` for every edge `u->v`, so the reversed post-order has every edge going forward. An edge to a finished vertex is not a cycle (parallel edges are exercised by the public case "topo isolated and parallel").
- **Boundaries.** Self-loop (`u` is active when its own edge is read: cycle); cycles in components that are not reached first; `n=0`; parallel edges.
- **Complexity.** `O(n+m)` time and memory.

### 3.6 `dag`
- **Steps.** Run `topo` on the whole graph first; any cycle (even one unreachable from the source) returns `acyclic=false`, `distance=[]`, `parent_edge=[]`. Otherwise `reached[source]=true`, `dist[source]=0`. Walk vertices in topological order; skip a vertex that is not reached; for every outgoing edge relax `dist[v] = dist[u] + w` when `v` is unreached or the candidate is smaller, and store the edge ID in `parent[v]`. Unreached vertices get `null` distance and `-1` parent.
- **Invariant.** When vertex `u` is processed in topological order, `dist[u]` is final, because all predecessors of `u` precede it in the order and have already relaxed their edges into `u`.
- **Correctness.** Shortest walks in a DAG are paths and a path visits vertices in topological order, so one pass in this order is sufficient. Negative weights are harmless because no vertex is revisited. A separate `reached[]` flag is used so that `-1` or any other long value stays a legal finite distance. Following `parent` IDs, each edge ends at the vertex it is stored for and starts at a reached vertex that is earlier in the topological order, so the chain strictly moves backwards and ends at the source.
- **Boundaries.** `n=1`; unreachable cycle; self-loop (cyclic); distance exactly `-1`; `-1e9`/`+1e9` edges (tested, the two cancel); parallel edges (first strictly better candidate is kept).
- **Complexity.** `O(n+m)` time and memory.

## 4. Route helper (`GraphAlgos.route`)

`route(parentEdge, edges, source, target)` returns the **vertex sequence** `source..target`, built backwards from `parent_edge` IDs (`v = edges[parentEdge[v]][0]`). The route to the source is `[source]`. The no-route result for an unreachable target is an **empty array** (the JSON `distance` of that vertex is still `null`). Time and output space are `O(L)` for a route with `L` vertices. `routeEdges` returns the selected edge IDs.

Calls actually executed in `tests/java/Tests.java` (all pass; direction and total weight verified against `distance`):

| Graph (`edges` as `[u,v,w]`) | Call | Result | Check |
|---|---|---|---|
| README example, `n=5`, source 0: `[[0,1,3],[0,2,8],[1,2,-7],[2,3,4]]` | `route(parent, edges, 0, 3)` | `[0,1,2,3]`, edge IDs `[0,2,3]` | `3 + (-7) + 4 = 0 = distance[3]`; each edge starts at the previous vertex |
| same | `route(parent, edges, 0, 0)` | `[0]` | the source |
| `n=4`, source 0: `[[0,1,5],[0,1,2],[1,2,1]]` | `route(parent, edges, 0, 3)` | `[]` | vertex 3 unreachable (`distance=null`, `parent_edge=-1`) |
| same (parallel edges 0 and 1 between 0 and 1) | `route(parent, edges, 0, 2)` | `[0,1,2]`, edge IDs `[1,2]` | the weight-2 edge (ID 1) beats the weight-5 edge (ID 0): `2 + 1 = 3 = distance[2]` |

The random test additionally calls the helper for every reachable vertex of 2000 random DAGs and checks that the edge sequence is contiguous, directed, and sums to `distance`.

## 5. Tests (`tests/`)

- `tests/java/Tests.java` - 2000 random graphs (n up to 8, loops, parallel edges, negative weights): Kruskal vs Prim cost and component count; SCC membership against Floyd-Warshall reachability; topological precedence and the cycle flag; DAG distances against Bellman-Ford; valid parent paths. It also covers DSU examples, chains and a ring of 100000 vertices (deep DFS), a cycle unreachable from the source, the route helper, and the full limits (`n=100000`, `m=250000`, weights in `[-1e9, 1e9]`, Kruskal = Prim).
- Witness checker: `validParents` accepts both optimal parent arrays of a tied/parallel graph and rejects a parent edge ID out of range and an edge in the wrong direction.
- `tests/my_cases.jsonl` (inputs), `tests/my_cases_expected.jsonl` (answers derived by hand, below), `tests/my_cases_actual.jsonl` (actual output of the command above), `tests/check_my_cases.py` (semantic comparison; all 14 cases pass).

Hand justification of the expected answers:

1. `ping` -> `{"status":"ready"}`.
2. `dsu` `n=5`: `[0,1]` -> 4 components, largest 2; `[1,0]` repeats; `[2,3]` -> 3, 2; `[1,3]` joins `{0,1}` and `{2,3}` -> 2, 4; `[4,4]` self-union -> unchanged.
3. `kruskal`/`prim` `n=5`: components `{0,1,2}`, `{3}`, `{4}` = 3. The loop (ID 3, `-100`) is never used. Cheapest edge is ID 1 (`-3`); then the cheaper of ID 2 (`1`) and ID 0/4 (`2`) connects vertex 0: cost `-3+1 = -2`, IDs `{1,2}`.
4. `n=4` chain of three `1e9` edges: cost `3e9` (above 2^31), one component.
5. `scc` `n=6`: cycles `0->1->2->0` and `3<->4`, isolated `5` with a self-loop. Edges `2->3` and `0->3` give one distinct condensation pair `{0,1,2} -> {3,4}`.
6. `topo`: `1<->2` is a cycle -> `false`, `[]`.
7. `dag` with the unreachable cycle `3<->4` -> `acyclic=false`, empty arrays.
8. `dag` `n=5`, source 1: dist(2) = `min(-4,-1) = -4` via ID 1; dist(3) = `-4 + (-1e9) = -1000000004` via ID 3 (the `+1e9` edge gives `999999996`); vertices 0 and 4 are unreachable (`null`, `-1`).
9. Cases 11-14 repeat DSU and empty inputs after other tasks to confirm state reset and `n=0` outputs.

## 6. Benchmark: Kruskal vs Prim

**Method.** Same graph for both algorithms at each size, generated by `new Random(20261025 + n)` with endpoints uniform in `[0,n)` and weights uniform in `[-1000000, 1000000]` (self-loops and parallel edges may occur). Before timing, both results were verified: distinct IDs, acyclic, `n-c` edges, sum equals the reported cost, the same component count as the input graph, and Kruskal cost = Prim cost.
**Warm-up:** 5 untimed runs of each method per size. **Timed runs:** 7 per method per size, interleaved (Kruskal, Prim, Kruskal, ...). **Timer:** `System.nanoTime()` around the single algorithm call.
**Boundary:** only the call `GraphAlgos.kruskal(n, edges)` / `GraphAlgos.prim(n, edges)` on an already built `int[][]` is timed. This includes the sort or the CSR/heap construction inside each method. Excluded: compilation, graph generation, JSON parsing, JSON output, JVM start-up.
**Environment:** Intel Xeon @ 2.10 GHz, 2 vCPUs, 8 GB RAM, Linux 6.18, OpenJDK 21.0.12 run with `-Xmx384m`, code compiled with `--release 17`. These numbers were measured in a cloud sandbox, not on a student laptop; re-run `Bench` on your own machine and replace the table if you want your hardware reported.

| n | m | Kruskal timings, ms | Kruskal median | Prim timings, ms | Prim median |
|---:|---:|---|---:|---|---:|
| 1 000 | 5 000 | 1.92, 0.55, 0.62, 0.54, 0.58, 0.52, 0.45 | **0.55** | 1.74, 2.19, 1.07, 1.12, 1.53, 1.15, 1.14 | **1.15** |
| 10 000 | 50 000 | 4.40, 4.48, 4.66, 4.21, 4.34, 4.17, 4.62 | **4.40** | 19.86, 18.51, 18.52, 17.46, 17.98, 24.66, 17.25 | **18.51** |
| 100 000 | 250 000 | 41.83, 41.25, 46.49, 57.23, 37.45, 45.93, 34.76 | **41.83** | 262.94, 304.72, 241.05, 247.83, 238.07, 216.63, 200.43 | **241.05** |

Costs/components of the three graphs: `-761691264 / 1`, `-7593149072 / 1`, `-52744406754 / 645`. Raw output: `tests/benchmark_output.txt`.

**Observations (measured).** Kruskal was faster at all three sizes: about 2x at the smallest, about 4x at 10 000 vertices and about 6x at 100 000 vertices. **Asymptotics (argument, separate from the measurements).** Both algorithms are `O(n + m log m)`; the benchmark does not change that bound. **Likely reasons for the gap (not separately measured):** Kruskal sorts a primitive `long[]` and runs a DSU with near-constant operations over arrays, while the lazy Prim pushes up to `2m` boxed `Long` objects into a `PriorityQueue`, which costs allocation and pointer chasing. **Limitations:** one random graph family and one seed per size; a shared 2-vCPU machine with timing noise (see the spread of the timings); JIT effects at the smallest size (first timing is slower); the last size is a forest with 645 components, so Prim restarts often; Prim memory/time would change with an indexed heap, which was not implemented.

## 7. Sources and AI assistance

- Sources: the assignment text and README in this repository (contracts, limits, examples); the standard textbook algorithms (DSU with path compression and union by size, Kruskal, Prim, Kosaraju, three-state DFS topological sort, DAG relaxation).
- AI assistance: the code in `src/Java/GraphAlgos.java` and `Algorithms.java`, the tests in `tests/`, the benchmark and the first draft of this document were produced with Claude (Anthropic). Verification actually performed: the 36 public checks and the state-reset check (`python tools/run.py --check`), the unit/random tests and the full-limit test, the 14 own JSONL cases with hand-derived answers, and the benchmark correctness checks described above. I am responsible for the submission; I should read and understand every method before submitting.
