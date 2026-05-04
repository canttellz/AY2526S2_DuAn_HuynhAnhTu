package algorithm;

import data.Database;
import data.Transaction;

import java.util.*;

/**
 * U-Apriori: the Apriori algorithm adapted for uncertain databases.
 *
 * Core idea — Expected Support:
 *   ExpSup(X) = (1 / |D|) * SUM_{t in D} P(X ⊆ t)
 *
 * where P(X ⊆ t) is the joint probability that all items in X
 * exist in transaction t:
 *   P(X ⊆ t) = PRODUCT_{x in X} p(x, t)   if all x present in t
 *             = 0                            if any x is absent
 *
 * An itemset X is frequent if ExpSup(X) >= minSup.
 *
 * Improvements over the base version:
 *   - Apriori pruning: candidates whose subsets are not all frequent
 *     are skipped before support computation (anti-monotone property)
 *   - Maximal filtering: inherited from Algorithm.filterMaximal()
 */
public class UApriori extends Algorithm {

    public UApriori(Database db, double minSup) {
        super(db, minSup);
    }

    /**
     * Run U-Apriori and return all frequent itemsets.
     */
    @Override
    public List<Set<Integer>> mine() {
        List<Transaction> transactions = db.getTransactions();
        int n = transactions.size();

        List<Set<Integer>> allFrequent = new ArrayList<>();

        // --- Level 1: find all frequent single items ---
        List<Set<Integer>> Lk = findFrequent1(transactions, n);
        allFrequent.addAll(Lk);

        // --- Level k >= 2: iterate until no new frequent sets found ---
        int k = 2;
        while (!Lk.isEmpty()) {

            // Generate candidate k-itemsets from (k-1)-frequent itemsets
            List<Set<Integer>> Ck = generateCandidates(Lk, k);

            List<Set<Integer>> nextLk = new ArrayList<>();

            for (Set<Integer> candidate : Ck) {

                // Apriori pruning: skip if any (k-1)-subset is not frequent
                if (!allSubsetsFrequent(candidate, Lk)) continue;

                double support = computeExpectedSupport(transactions, candidate, n);

                if (support >= minSup) {
                    nextLk.add(candidate);
                    allFrequent.add(candidate);
                    System.out.println("  Frequent " + k + "-itemset: "
                            + candidate + "  support=" + String.format("%.4f", support));
                }
            }

            Lk = nextLk;
            k++;
        }

        return allFrequent;
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    /**
     * Find all frequent 1-itemsets.
     * ExpSup({x}) = SUM_{t in D} p(x, t) / |D|
     */
    private List<Set<Integer>> findFrequent1(List<Transaction> transactions, int n) {
        Map<Integer, Double> supportMap = new HashMap<>();

        for (Transaction t : transactions) {
            for (int item : t.getItems()) {
                supportMap.merge(item, t.getProbability(item), Double::sum);
            }
        }

        List<Set<Integer>> L1 = new ArrayList<>();
        for (Map.Entry<Integer, Double> entry : supportMap.entrySet()) {
            double sup = entry.getValue() / n;
            if (sup >= minSup) {
                Set<Integer> itemset = new HashSet<>();
                itemset.add(entry.getKey());
                L1.add(itemset);
                System.out.println("  Frequent 1-itemset: " + itemset
                        + "  support=" + String.format("%.4f", sup));
            }
        }
        return L1;
    }

    /**
     * Compute the expected support of an itemset over the database.
     * ExpSup(X) = SUM_{t in D} P(X ⊆ t) / |D|
     */
    private double computeExpectedSupport(List<Transaction> transactions,
                                          Set<Integer> itemset, int n) {
        double sum = 0.0;
        for (Transaction t : transactions) {
            sum += t.jointProbability(itemset);
        }
        return sum / n;
    }

    /**
     * Generate candidate k-itemsets by joining pairs of (k-1)-frequent itemsets.
     * Two itemsets are joined if their union has exactly k items.
     */
    private List<Set<Integer>> generateCandidates(List<Set<Integer>> Lk_1, int k) {
        List<Set<Integer>> candidates = new ArrayList<>();
        for (int i = 0; i < Lk_1.size(); i++) {
            for (int j = i + 1; j < Lk_1.size(); j++) {
                Set<Integer> union = new HashSet<>(Lk_1.get(i));
                union.addAll(Lk_1.get(j));
                if (union.size() == k && !candidates.contains(union)) {
                    candidates.add(union);
                }
            }
        }
        return candidates;
    }
}