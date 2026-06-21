package algorithm;

import data.Database;

import java.util.*;

/**
 * Abstract base class for all frequent-itemset mining algorithms.
 *
 * Every algorithm works on an uncertain Database with a minimum
 * support threshold, and can produce either all frequent itemsets
 * or only the maximal ones.
 *
 * To add a new algorithm:
 *   1. Create a new class that extends Algorithm  (e.g. UFPGrowth.java)
 *   2. Implement mine()
 *   3. Register it in AlgorithmFactory
 *
 * The shared utility methods (filterMaximal, allSubsetsFrequent)
 * are available to every subclass for free.
 */
public abstract class Algorithm {

    protected final Database db;
    protected final double   minSup;

    public Algorithm(Database db, double minSup) {
        this.db     = db;
        this.minSup = minSup;
    }

    /**
     * Mine and return ALL frequent itemsets from the database.
     * Each subclass must implement its own mining strategy here.
     */
    public abstract List<Set<Integer>> mine();

    /**
     * Mine and return only MAXIMAL frequent itemsets.
     *
     * Default behaviour: run mine() then apply filterMaximal().
     * Subclasses may override this for a more efficient direct approach.
     */
    public List<Set<Integer>> mineMaximal() {
        return filterMaximal(mine());
    }

    /**
     * Apriori pruning check.
     *
     * A candidate k-itemset is valid only if every one of its
     * (k-1)-subsets appears in the previous frequent layer Lk_1.
     * If any subset is missing, the candidate cannot be frequent
     * (Apriori anti-monotone property) and can be safely skipped.
     */
    protected boolean allSubsetsFrequent(Set<Integer> candidate,
                                         List<Set<Integer>> Lk_1) {
        List<Integer> itemList = new ArrayList<>(candidate);
        for (int i = 0; i < itemList.size(); i++) {
            Set<Integer> subset = new HashSet<>(candidate);
            subset.remove(itemList.get(i));
            if (!Lk_1.contains(subset)) return false;
        }
        return true;
    }

    /**
     * Filter a list of frequent itemsets down to only the maximal ones.
     *
     * An itemset A is maximal if no other frequent itemset B exists
     * such that A ⊂ B (i.e. B is a proper superset of A).
     */
    protected List<Set<Integer>> filterMaximal(List<Set<Integer>> frequent) {
        List<Set<Integer>> maximal = new ArrayList<>();
        for (int i = 0; i < frequent.size(); i++) {
            Set<Integer> A = frequent.get(i);
            boolean isSubset = false;
            for (int j = 0; j < frequent.size(); j++) {
                if (i == j) continue;
                Set<Integer> B = frequent.get(j);
                if (B.size() > A.size() && B.containsAll(A)) {
                    isSubset = true;
                    break;
                }
            }
            if (!isSubset) maximal.add(A);
        }
        return maximal;
    }
}