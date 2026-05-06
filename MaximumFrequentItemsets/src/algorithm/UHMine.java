package algorithm;

import data.Database;
import data.Transaction;

import java.util.*;

/**
 * UH-Mine: Uncertain H-Mine algorithm for uncertain databases.
 *
 * Based on: Leung et al. / Tong et al. (VLDB 2012)
 * Extended from H-Mine (Pei et al.) for uncertain data.
 *
 * Core idea — UH-Struct (Hyperlinked Structure):
 *   Instead of a compressed tree, build a projected database for each
 *   item: a list of (transactionId, probability) pairs for every
 *   transaction containing that item.
 *
 *   UH-Struct:
 *     item 40 → [(t1, 0.9), (t3, 0.7), (t5, 0.8), ...]
 *     item 49 → [(t1, 0.8), (t3, 0.5), (t7, 0.9), ...]
 *
 * Mining (DFS):
 *   To compute ExpSup({40, 49}):
 *     - Intersect transaction lists of 40 and 49
 *     - For each shared transaction t: contribution = p(40,t) × p(49,t)
 *     - ExpSup = SUM contributions / n
 *
 *   This is IDENTICAL to U-Apriori's formula — guaranteed correct.
 *   The advantage over U-Apriori: we only look at transactions in the
 *   intersection, not all n transactions.
 *
 * Advantages:
 *   - Exact same results as U-Apriori (mathematically identical)
 *   - DFS traversal — no candidate generation
 *   - No tree construction — simpler and correct
 *   - Efficient on sparse databases (small intersection lists)
 *
 * Run command:
 *   java main.MaximalFrequentItemsets uhmine data.txt --minsup 0.05
 */
public class UHMine extends Algorithm {

    public UHMine(Database db, double minSup) {
        super(db, minSup);
    }

    @Override
    public List<Set<Integer>> mine() {
        List<Transaction> transactions = db.getTransactions();
        int n = transactions.size();

        // --- Step 1: Build UH-Struct ---
        // For each item: map transactionIndex -> probability
        // Using LinkedHashMap to preserve insertion order
        Map<Integer, Map<Integer, Double>> uhStruct = new LinkedHashMap<>();

        for (int i = 0; i < transactions.size(); i++) {
            Transaction t = transactions.get(i);
            for (int item : t.getItems()) {
                uhStruct
                    .computeIfAbsent(item, k -> new LinkedHashMap<>())
                    .put(i, t.getProbability(item));
            }
        }

        // --- Step 2: Compute 1-itemset expected support ---
        // ExpSup({x}) = SUM p(x,t) / n
        Map<Integer, Double> sup1 = new LinkedHashMap<>();
        for (Map.Entry<Integer, Map<Integer, Double>> e : uhStruct.entrySet()) {
            double sum = 0.0;
            for (double prob : e.getValue().values()) sum += prob;
            sup1.put(e.getKey(), sum / n);
        }

        // Filter frequent 1-itemsets, sort by descending support
        List<Integer> freqItems = new ArrayList<>();
        for (Map.Entry<Integer, Double> e : sup1.entrySet()) {
            if (e.getValue() >= minSup) {
                freqItems.add(e.getKey());
                System.out.println("  Frequent 1-itemset: [" + e.getKey()
                        + "]  support=" + String.format("%.4f", e.getValue()));
            }
        }
        freqItems.sort((a, b) -> Double.compare(sup1.get(b), sup1.get(a)));

        if (freqItems.isEmpty()) return new ArrayList<>();

        // --- Step 3: Collect results ---
        List<Set<Integer>> allFrequent = new ArrayList<>();

        // Add all frequent 1-itemsets
        for (int item : freqItems) {
            Set<Integer> s = new HashSet<>();
            s.add(item);
            allFrequent.add(s);
        }

        // --- Step 4: DFS mining for 2+ itemsets ---
        // For each frequent item, project the database and recurse
        for (int i = 0; i < freqItems.size(); i++) {
            int item = freqItems.get(i);
            Map<Integer, Double> itemTxns = uhStruct.get(item);

            // Remaining items to combine with (items after current in order)
            List<Integer> remaining = freqItems.subList(i + 1, freqItems.size());

            // Start DFS with this item as prefix
            List<Integer> prefix = new ArrayList<>();
            prefix.add(item);

            mineProjected(prefix, itemTxns, remaining, n, allFrequent);
        }

        return allFrequent;
    }

    // ---------------------------------------------------------------
    // Core recursive DFS mining
    // ---------------------------------------------------------------

    /**
     * Recursively mine projected databases (DFS).
     *
     * @param prefix        Current itemset prefix being extended
     * @param prefixTxns    Projected DB for prefix: txnIndex -> joint prob so far
     * @param candidates    Remaining items to try extending with
     * @param n             Total transactions (for support normalization)
     * @param allFrequent   Result accumulator
     */
    private void mineProjected(List<Integer> prefix,
                                Map<Integer, Double> prefixTxns,
                                List<Integer> candidates,
                                int n,
                                List<Set<Integer>> allFrequent) {

        for (int i = 0; i < candidates.size(); i++) {
            int extItem = candidates.get(i);

            // --- Intersect prefix transactions with extItem transactions ---
            // For each transaction in BOTH:
            //   new joint prob = prefixTxns prob × p(extItem, t)
            Map<Integer, Double> newTxns = new LinkedHashMap<>();
            double expSupSum = 0.0;

            Map<Integer, Double> extItemTxns = getItemTxns(extItem);

            for (Map.Entry<Integer, Double> e : prefixTxns.entrySet()) {
                int    txnIdx  = e.getKey();
                double prefProb = e.getValue();

                Double extProb = extItemTxns.get(txnIdx);
                if (extProb != null) {
                    double jointProb = prefProb * extProb;
                    newTxns.put(txnIdx, jointProb);
                    expSupSum += jointProb;
                }
            }

            double expSup = expSupSum / n;

            if (expSup >= minSup) {
                // Build the new itemset = prefix + extItem
                List<Integer> newPrefix = new ArrayList<>(prefix);
                newPrefix.add(extItem);
                Set<Integer> itemset = new HashSet<>(newPrefix);
                allFrequent.add(itemset);

                System.out.println("  Frequent " + itemset.size()
                        + "-itemset: " + itemset
                        + "  support=" + String.format("%.4f", expSup));

                // DFS — recurse with remaining candidates after extItem
                List<Integer> remaining = candidates.subList(i + 1, candidates.size());
                if (!remaining.isEmpty() && !newTxns.isEmpty()) {
                    mineProjected(newPrefix, newTxns, remaining, n, allFrequent);
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // UH-Struct access
    // ---------------------------------------------------------------

    // Cache the UH-Struct so it is built once and reused across recursion
    private Map<Integer, Map<Integer, Double>> uhStructCache = null;

    private Map<Integer, Double> getItemTxns(int item) {
        if (uhStructCache == null) buildCache();
        return uhStructCache.getOrDefault(item, Collections.emptyMap());
    }

    private void buildCache() {
        uhStructCache = new LinkedHashMap<>();
        List<Transaction> transactions = db.getTransactions();
        for (int i = 0; i < transactions.size(); i++) {
            Transaction t = transactions.get(i);
            for (int item : t.getItems()) {
                uhStructCache
                    .computeIfAbsent(item, k -> new LinkedHashMap<>())
                    .put(i, t.getProbability(item));
            }
        }
    }
}