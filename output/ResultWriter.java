package output;

import java.io.*;
import java.util.*;

/**
 * Handles all output for mining results.
 *
 * Writes to stdout by default, or to a file if a path is provided.
 * Results are sorted by itemset size (largest first) for readability.
 *
 * Important: when writing to stdout, close() only flushes — it never
 * closes System.out, so subsequent output (e.g. ResultComparator) works.
 */
public class ResultWriter {

    private final PrintWriter writer;
    private final boolean     isStdout;  // track so we never close System.out

    public ResultWriter(String filePath) throws IOException {
        if (filePath != null) {
            writer   = new PrintWriter(new BufferedWriter(new FileWriter(filePath)));
            isStdout = false;
        } else {
            writer   = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(System.out)));
            isStdout = true;
        }
    }

    public void write(List<Set<Integer>> maximalSets, long runtimeMs) {
        writer.println("\n========== Maximal Frequent Itemsets ==========");

        if (maximalSets.isEmpty()) {
            writer.println("  (none found — try lowering --minsup)");
        } else {
            maximalSets.sort((a, b) -> Integer.compare(b.size(), a.size()));
            for (Set<Integer> set : maximalSets) {
                writer.println("  " + set);
            }
        }

        writer.println("================================================");
        writer.println("Total maximal itemsets : " + maximalSets.size());
        writer.println("Runtime                : " + runtimeMs + " ms");
        writer.flush();
    }

    /**
     * Close the writer. If writing to stdout, only flushes — does NOT
     * close System.out so subsequent println() calls still work.
     */
    public void close() {
        writer.flush();
        if (!isStdout) {
            writer.close();
        }
    }
}