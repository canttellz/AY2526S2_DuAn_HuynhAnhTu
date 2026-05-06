package algorithm;

import data.Database;

/**
 * Factory class for creating mining algorithm instances by name.
 *
 * To add a new algorithm:
 *   1. Create YourAlgorithm.java in this package (extends Algorithm)
 *   2. Add a new case below
 */
public class AlgorithmFactory {

    /**
     * Create algorithm with default parameters.
     * Used for uapriori and uhmine.
     */
    public static Algorithm create(String name, Database db, double minSup) {
        switch (name.toLowerCase()) {
            case "uapriori": return new UApriori(db, minSup);
            case "uhmine":   return new UHMine(db, minSup);
            case "aco":      return new ACOMiner(db, minSup);
            default:         return null;
        }
    }

    /**
     * Create ACO algorithm with custom parameters.
     *
     * @param db           Database to mine
     * @param minSup       Minimum support threshold
     * @param ants         Number of ants per iteration
     * @param iterations   Number of ACO iterations
     * @param evaporation  Pheromone evaporation rate (0 < e < 1)
     * @param alpha        Pheromone influence weight
     * @param beta         Heuristic (support) influence weight
     */
    public static Algorithm createACO(Database db, double minSup,
                                      int ants, int iterations,
                                      double evaporation,
                                      double alpha, double beta) {
        return new ACOMiner(db, minSup, ants, iterations,
                            evaporation, alpha, beta);
    }
}