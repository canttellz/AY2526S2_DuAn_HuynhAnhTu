package algorithm;

import data.Database;
import data.Transaction;

import java.util.*;

/**
 * ACO-Miner: Ant Colony Optimization for uncertain frequent itemset mining.
 *
 * Academic basis:
 *   Malipatil, S. & Hanumantha Reddy, T. (2023).
 *   "Discovery of interesting frequent item sets in an uncertain database
 *   using ant colony optimization."
 *   International Journal of Computers and Applications, 45(11), 673-679.
 *
 * Algorithm overview:
 *   Items are nodes. Ants build itemsets by selecting items guided by:
 *     - pheromone[item]  : learned reward from past iterations
 *     - heuristic[item]  : ExpSup({item}) from 1-itemset scan
 *
 *   Each ant extends its current itemset one item at a time.
 *   It stops when no candidate item keeps ExpSup >= minSup.
 *   After all ants finish, pheromone trails are updated.
 *
 * Selection probability for item i:
 *   P(i) = pheromone[i]^alpha * heuristic[i]^beta
 *          / SUM_j (pheromone[j]^alpha * heuristic[j]^beta)
 *
 * Pheromone update:
 *   evaporate : pheromone[i] *= (1 - evaporation)
 *   reinforce : pheromone[i] += ExpSup(itemset) for each item in found itemset
 *
 * Nature of results:
 *   APPROXIMATE — may miss some maximal frequent itemsets.
 *   Coverage improves with more ants and iterations.
 *   For exact results use uapriori or uhmine.
 *
 * Run command:
 *   java main.MaximalFrequentItemsets aco data.txt --minsup 0.05
 *   java main.MaximalFrequentItemsets aco data.txt --minsup 0.05
 *       --ants 30 --iterations 100 --evaporation 0.3 --alpha 1.0 --beta 2.0
 */
public class ACOMiner extends Algorithm {

    // --- ACO parameters ---
    private final int    numAnts;
    private final int    numIterations;
    private final double evaporation;   // pheromone decay rate (0 < e < 1)
    private final double alpha;         // pheromone influence
    private final double beta;          // heuristic (support) influence

    // --- Smart defaults ---
    private static final int    DEFAULT_ANTS        = 20;
    private static final int    DEFAULT_ITERATIONS  = 50;
    private static final double DEFAULT_EVAPORATION = 0.3;
    private static final double DEFAULT_ALPHA       = 1.0;
    private static final double DEFAULT_BETA        = 2.0;
    private static final double INITIAL_PHEROMONE   = 0.1;

    public ACOMiner(Database db, double minSup) {
        this(db, minSup,
             DEFAULT_ANTS, DEFAULT_ITERATIONS,
             DEFAULT_EVAPORATION, DEFAULT_ALPHA, DEFAULT_BETA);
    }

    public ACOMiner(Database db, double minSup,
                    int numAnts, int numIterations,
                    double evaporation, double alpha, double beta) {
        super(db, minSup);
        this.numAnts       = numAnts;
        this.numIterations = numIterations;
        this.evaporation   = evaporation;
        this.alpha         = alpha;
        this.beta          = beta;
    }

    @Override
    public List<Set<Integer>> mine() {
        List<Transaction> transactions = db.getTransactions();
        int n = transactions.size();

        // --- Step 1: Compute 1-itemset expected support (heuristic) ---
        Map<Integer, Double> heuristic = new HashMap<>();
        for (Transaction t : transactions) {
            for (int item : t.getItems()) {
                heuristic.merge(item, t.getProbability(item) / n, Double::sum);
            }
        }

        // Keep only frequent 1-itemsets as candidates
        List<Integer> freqItems = new ArrayList<>();
        for (Map.Entry<Integer, Double> e : heuristic.entrySet()) {
            if (e.getValue() >= minSup) {
                freqItems.add(e.getKey());
                System.out.println("  Frequent 1-itemset: [" + e.getKey()
                        + "]  support=" + String.format("%.4f", e.getValue()));
            }
        }

        if (freqItems.isEmpty()) return new ArrayList<>();

        // --- Step 2: Initialize pheromone trails ---
        Map<Integer, Double> pheromone = new HashMap<>();
        for (int item : freqItems) {
            pheromone.put(item, INITIAL_PHEROMONE);
        }

        // --- Step 3: Build UH-Struct for fast support computation ---
        // txnIndex -> probability for each item
        Map<Integer, Map<Integer, Double>> uhStruct = new HashMap<>();
        for (int i = 0; i < transactions.size(); i++) {
            Transaction t = transactions.get(i);
            for (int item : t.getItems()) {
                uhStruct.computeIfAbsent(item, k -> new HashMap<>())
                        .put(i, t.getProbability(item));
            }
        }

        // --- Step 4: ACO iterations ---
        Set<Set<Integer>> discovered = new HashSet<>();  // all found frequent itemsets
        Random random = new Random(42);                  // fixed seed for reproducibility

        for (int iter = 0; iter < numIterations; iter++) {
            List<Set<Integer>> iterFound = new ArrayList<>();

            // Each ant builds one itemset
            for (int ant = 0; ant < numAnts; ant++) {
                Set<Integer> itemset = buildItemset(
                        freqItems, pheromone, heuristic,
                        uhStruct, n, random);

                if (!itemset.isEmpty()) {
                    iterFound.add(itemset);
                    discovered.add(new HashSet<>(itemset));
                }
            }

            // --- Pheromone update ---
            // Evaporate all trails
            for (int item : freqItems) {
                pheromone.put(item, (1.0 - evaporation) * pheromone.get(item));
            }

            // Reinforce trails of items in itemsets found this iteration
            for (Set<Integer> itemset : iterFound) {
                double reward = computeExpSup(itemset, uhStruct, n);
                for (int item : itemset) {
                    pheromone.merge(item, reward, Double::sum);
                }
            }

            System.out.println("  Iteration " + (iter + 1)
                    + " — ants found " + iterFound.size()
                    + " frequent itemsets  [total unique: " + discovered.size() + "]");
        }

        // --- Step 5: Return all discovered frequent itemsets ---
        return new ArrayList<>(discovered);
    }

    // ---------------------------------------------------------------
    // Ant itemset construction
    // ---------------------------------------------------------------

    /**
     * One ant builds one itemset.
     *
     * Starting from an empty set, the ant repeatedly selects an item
     * using the probability formula until no item can be added without
     * dropping ExpSup below minSup.
     *
     * @return The frequent itemset built by this ant (may be empty if
     *         no starting item is frequent — should not happen since
     *         freqItems is pre-filtered)
     */
    private Set<Integer> buildItemset(List<Integer> freqItems,
                                      Map<Integer, Double> pheromone,
                                      Map<Integer, Double> heuristic,
                                      Map<Integer, Map<Integer, Double>> uhStruct,
                                      int n, Random random) {
        Set<Integer> itemset   = new HashSet<>();
        List<Integer> candidates = new ArrayList<>(freqItems);

        // Current projected transactions: txnIdx -> joint probability so far
        Map<Integer, Double> projTxns = null;

        while (!candidates.isEmpty()) {
            // Select next item using pheromone + heuristic probability
            int selected = selectItem(candidates, pheromone, heuristic, random);
            if (selected == -1) break;

            // Compute new projected transactions if we add this item
            Map<Integer, Double> newProjTxns =
                    projectTransactions(projTxns, selected, uhStruct);

            // Check expected support of (itemset + selected)
            double expSup = sumProjectedSupport(newProjTxns) / n;

            if (expSup >= minSup) {
                itemset.add(selected);
                projTxns = newProjTxns;
                candidates.remove((Integer) selected);
            } else {
                // This item doesn't help — remove from candidates for this ant
                candidates.remove((Integer) selected);
            }
        }

        return itemset;
    }

    /**
     * Select an item from candidates using roulette wheel selection.
     *
     * P(item i) = pheromone[i]^alpha * heuristic[i]^beta
     *             / SUM_j (pheromone[j]^alpha * heuristic[j]^beta)
     */
    private int selectItem(List<Integer> candidates,
                           Map<Integer, Double> pheromone,
                           Map<Integer, Double> heuristic,
                           Random random) {
        if (candidates.isEmpty()) return -1;

        // Compute selection weights
        double[] weights = new double[candidates.size()];
        double   total   = 0.0;

        for (int i = 0; i < candidates.size(); i++) {
            int item = candidates.get(i);
            double ph = Math.pow(pheromone.getOrDefault(item, INITIAL_PHEROMONE), alpha);
            double he = Math.pow(heuristic.getOrDefault(item, 0.0), beta);
            weights[i] = ph * he;
            total += weights[i];
        }

        if (total == 0.0) return candidates.get(random.nextInt(candidates.size()));

        // Roulette wheel selection
        double spin = random.nextDouble() * total;
        double cumulative = 0.0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += weights[i];
            if (cumulative >= spin) return candidates.get(i);
        }
        return candidates.get(candidates.size() - 1);
    }

    // ---------------------------------------------------------------
    // Support computation via projected transactions
    // ---------------------------------------------------------------

    /**
     * Project current transactions by intersecting with the new item.
     *
     * If projTxns is null (empty itemset so far), initialize from
     * the new item's transaction list directly.
     *
     * For each shared transaction:
     *   newProb = currentJointProb * p(newItem, t)
     */
    private Map<Integer, Double> projectTransactions(
            Map<Integer, Double> projTxns,
            int item,
            Map<Integer, Map<Integer, Double>> uhStruct) {

        Map<Integer, Double> itemTxns =
                uhStruct.getOrDefault(item, Collections.emptyMap());
        Map<Integer, Double> result = new HashMap<>();

        if (projTxns == null) {
            // First item — initialize projected transactions
            result.putAll(itemTxns);
        } else {
            // Intersect with existing projected transactions
            for (Map.Entry<Integer, Double> e : projTxns.entrySet()) {
                int    txnIdx  = e.getKey();
                double curProb = e.getValue();
                Double itemProb = itemTxns.get(txnIdx);
                if (itemProb != null) {
                    result.put(txnIdx, curProb * itemProb);
                }
            }
        }
        return result;
    }

    /** Sum all joint probabilities in the projected transaction list. */
    private double sumProjectedSupport(Map<Integer, Double> projTxns) {
        double sum = 0.0;
        for (double prob : projTxns.values()) sum += prob;
        return sum;
    }

    /**
     * Compute ExpSup of a full itemset using the UH-Struct.
     * Used for pheromone reinforcement.
     */
    private double computeExpSup(Set<Integer> itemset,
                                 Map<Integer, Map<Integer, Double>> uhStruct,
                                 int n) {
        Map<Integer, Double> proj = null;
        for (int item : itemset) {
            proj = projectTransactions(proj, item, uhStruct);
            if (proj.isEmpty()) return 0.0;
        }
        return proj == null ? 0.0 : sumProjectedSupport(proj) / n;
    }
}