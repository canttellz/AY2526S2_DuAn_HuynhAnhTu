package output;

import java.io.*;
import java.util.*;

/**
 * Compares maximal frequent itemset results between two mining runs.
 *
 * Used in the accumulated dynamic workflow to show what changed
 * after a new batch of transactions was added to the database:
 *   - Gained: newly discovered maximal itemsets
 *   - Lost:   no longer maximal or frequent
 *   - Unchanged: stable across both runs
 *
 * Also handles saving and loading results across separate program runs.
 */
public class ResultComparator {

    /**
     * Compare previous and current maximal frequent itemsets
     * and print a summary of what changed.
     */
    public static void compare(List<Set<Integer>> previous,
                                List<Set<Integer>> current) {
        List<Set<Integer>> gained    = new ArrayList<>();
        List<Set<Integer>> lost      = new ArrayList<>();
        List<Set<Integer>> unchanged = new ArrayList<>();

        for (Set<Integer> s : current) {
            if (containsSet(previous, s)) unchanged.add(s);
            else gained.add(s);
        }
        for (Set<Integer> s : previous) {
            if (!containsSet(current, s)) lost.add(s);
        }

        System.out.println("\n========== Changes Since Last Run ==========");
        System.out.println("Unchanged : " + unchanged.size());
        for (Set<Integer> s : unchanged) System.out.println("    = " + s);

        System.out.println("Gained    : " + gained.size()
                + "  (new maximal itemsets after this batch)");
        for (Set<Integer> s : gained) System.out.println("    + " + s);

        System.out.println("Lost      : " + lost.size()
                + "  (no longer maximal or frequent)");
        for (Set<Integer> s : lost) System.out.println("    - " + s);
        System.out.println("============================================");
    }

    /**
     * Save maximal itemsets to a results file for future comparison.
     * Each line: space-separated item IDs, e.g. "40 49 42"
     */
    public static void saveResults(List<Set<Integer>> maximal,
                                   String filePath) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filePath))) {
            for (Set<Integer> set : maximal) {
                StringJoiner sj = new StringJoiner(" ");
                for (int item : set) sj.add(String.valueOf(item));
                bw.write(sj.toString());
                bw.newLine();
            }
        }
    }

    /**
     * Load previously saved maximal itemsets from a results file.
     * Returns an empty list if the file does not exist (first run).
     */
    public static List<Set<Integer>> loadPreviousResults(String filePath) {
        List<Set<Integer>> results = new ArrayList<>();
        File file = new File(filePath);
        if (!file.exists()) return results;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                Set<Integer> set = new HashSet<>();
                for (String token : line.split("\\s+")) {
                    set.add(Integer.parseInt(token));
                }
                results.add(set);
            }
        } catch (IOException e) {
            System.err.println("Warning: could not load previous results: " + e.getMessage());
        }
        return results;
    }

    private static boolean containsSet(List<Set<Integer>> list, Set<Integer> target) {
        for (Set<Integer> s : list) {
            if (s.equals(target)) return true;
        }
        return false;
    }
}