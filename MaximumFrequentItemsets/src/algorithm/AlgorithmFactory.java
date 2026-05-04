package algorithm;

import data.Database;

/**
 * Factory class for creating mining algorithm instances by name.
 *
 * This is the ONLY place that needs to change when a new algorithm
 * is added to the project. All other classes remain untouched.
 *
 * To add a new algorithm:
 *   1. Create YourAlgorithm.java in this package (extends Algorithm)
 *   2. Add a new case below
 */
public class AlgorithmFactory {

    /**
     * Create and return an Algorithm instance by name.
     *
     * @param name    Algorithm name (case-insensitive)
     * @param db      The database to mine
     * @param minSup  Minimum support threshold
     * @return        A ready-to-use Algorithm, or null if unknown
     */
    public static Algorithm create(String name, Database db, double minSup) {
        switch (name.toLowerCase()) {
            case "uapriori":  return new UApriori(db, minSup);
            case "uhmine":   return new UHMine(db, minSup);
            // case "aco":    return new ACOMiner(db, minSup);
            default:          return null;
        }
    }
}