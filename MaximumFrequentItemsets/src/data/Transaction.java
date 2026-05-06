package data;

import java.util.*;

/**
 * Represents a single uncertain transaction.
 *
 * Each item in the transaction has an existential probability (0 < p <= 1)
 * representing how likely the item truly exists in this transaction.
 *
 * Data file format (one line):
 *   item:probability  item:probability  ...
 *   e.g.   1:0.9  3:0.7  5:0.5
 */
public class Transaction {

    private final Map<Integer, Double> items; // item ID -> existential probability

    public Transaction(Map<Integer, Double> items) {
        this.items = Collections.unmodifiableMap(new HashMap<>(items));
    }

    // ----------------------------------------------------------------
    // Parsing
    // ----------------------------------------------------------------

    /**
     * Parse a raw text line into a Transaction.
     * Example input: "1:0.9 3:0.7 5:0.5"
     */
    public static Transaction parse(String line) {
        Map<Integer, Double> map = new HashMap<>();
        for (String token : line.trim().split("\\s+")) {
            String[] pair = token.split(":");
            int    item = Integer.parseInt(pair[0].trim());
            double prob = Double.parseDouble(pair[1].trim());
            map.put(item, prob);
        }
        return new Transaction(map);
    }

    // ----------------------------------------------------------------
    // Accessors
    // ----------------------------------------------------------------

    /** Returns true if this transaction contains the given item. */
    public boolean containsItem(int item) {
        return items.containsKey(item);
    }

    /** Returns the existential probability of an item (0.0 if absent). */
    public double getProbability(int item) {
        return items.getOrDefault(item, 0.0);
    }

    /** Returns all item IDs in this transaction. */
    public Set<Integer> getItems() {
        return items.keySet();
    }

    // ----------------------------------------------------------------
    // Probability computation
    // ----------------------------------------------------------------

    /**
     * Computes the joint probability that an entire itemset exists
     * in this transaction:
     *   P(X ⊆ t) = PRODUCT of p(item) for each item in X
     *
     * Returns 0.0 immediately if any item in the itemset is absent.
     */
    public double jointProbability(Set<Integer> itemset) {
        double prob = 1.0;
        for (int item : itemset) {
            if (!items.containsKey(item)) return 0.0;
            prob *= items.get(item);
        }
        return prob;
    }

    // ----------------------------------------------------------------
    // State persistence
    // ----------------------------------------------------------------

    /**
     * Serialize this transaction back to the data file format.
     * Used by Database.saveState() to persist the full database.
     * Example output: "1:0.9 3:0.7 5:0.5"
     */
    public String toStateString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Double> entry : items.entrySet()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(entry.getKey()).append(":").append(entry.getValue());
        }
        return sb.toString();
    }
}