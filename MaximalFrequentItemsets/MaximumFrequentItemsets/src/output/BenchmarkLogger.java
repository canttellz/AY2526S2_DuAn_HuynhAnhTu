package output;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Logs benchmark results to two CSV files:
 *
 * 1. benchmark.csv — runtime summary (all runs)
 *    timestamp, runType, algorithm, baseFile, batchesAdded,
 *    transactionsBefore, transactionsAfter, batchFiles,
 *    minSup, maximalCount, runtimeMs
 *
 * 2. benchmark_sets.csv — itemset details (dynamic runs only)
 *    timestamp, algorithm, transactionsBefore, transactionsAfter,
 *    batchFiles, gained, lost, unchanged, currentSets
 *
 * Example benchmark_sets.csv row:
 *   2025-01-01, uapriori, 5000, 6000, batch.txt,
 *   "[39 40]|[33 40]", "[49 40 42]", "[49 39]|[33]", "[39 40]|[33 40]|..."
 *
 * Items within a set are space-separated: "39 40"
 * Sets within a column are pipe-separated: "[39 40]|[33 40]"
 * Empty columns contain "-"
 */
public class BenchmarkLogger {

    private static final String CSV_HEADER =
            "timestamp,runType,algorithm,baseFile,batchesAdded," +
            "transactionsBefore,transactionsAfter,batchFiles," +
            "minSup,maximalCount,runtimeMs";

    private static final String SETS_CSV_HEADER =
            "timestamp,algorithm,transactionsBefore,transactionsAfter," +
            "batchFiles,gained,lost,unchanged,currentSets";

    // ----------------------------------------------------------------
    // Log a STATIC run — no itemset detail file written
    // ----------------------------------------------------------------

    public static void logStatic(String csvPath,
                                 String algorithm,
                                 String dataFile,
                                 int    transactions,
                                 double minSup,
                                 int    maximalCount,
                                 long   runtimeMs) throws IOException {
        writeRow(csvPath, "static",
                new File(dataFile).getName(),
                algorithm, 0, 0, transactions, "-",
                minSup, maximalCount, runtimeMs);
    }

    // ----------------------------------------------------------------
    // Log a DYNAMIC run — writes both benchmark.csv + benchmark_sets.csv
    // ----------------------------------------------------------------

    /**
     * Log a dynamic run with full itemset change details.
     *
     * @param csvPath            Path to the main benchmark CSV
     * @param algorithm          Algorithm name
     * @param baseFile           State file or initial data file loaded
     * @param addedBatchFiles    Batch files appended this run
     * @param transactionsBefore Transaction count before appending
     * @param transactionsAfter  Transaction count after appending
     * @param minSup             Minimum support threshold
     * @param previousMaximal    Maximal itemsets from previous run
     *                           (null or empty = first run, no comparison)
     * @param currentMaximal     Maximal itemsets from this run
     * @param runtimeMs          Runtime in milliseconds
     */
    public static void logDynamic(String             csvPath,
                                  String             algorithm,
                                  String             baseFile,
                                  List<String>       addedBatchFiles,
                                  int                transactionsBefore,
                                  int                transactionsAfter,
                                  double             minSup,
                                  List<Set<Integer>> previousMaximal,
                                  List<Set<Integer>> currentMaximal,
                                  long               runtimeMs) throws IOException {

        // Build batch file summary
        StringJoiner batchNames = new StringJoiner("+");
        for (String f : addedBatchFiles) batchNames.add(new File(f).getName());
        String batchSummary = addedBatchFiles.isEmpty() ? "-" : batchNames.toString();

        // Write to main benchmark.csv
        writeRow(csvPath, "dynamic",
                new File(baseFile).getName(),
                algorithm,
                addedBatchFiles.size(),
                transactionsBefore,
                transactionsAfter,
                batchSummary,
                minSup,
                currentMaximal.size(),
                runtimeMs);

        // Write to benchmark_sets.csv (alongside benchmark.csv)
        String setsPath = setsFilePath(csvPath);
        writeSetsRow(setsPath, algorithm,
                transactionsBefore, transactionsAfter,
                batchSummary,
                previousMaximal, currentMaximal);
    }

    // ----------------------------------------------------------------
    // Print summary table for benchmark.csv
    // ----------------------------------------------------------------

    public static void printSummary(String csvPath) throws IOException {
        File file = new File(csvPath);
        if (!file.exists()) {
            System.out.println("No benchmark file found at: " + csvPath);
            return;
        }

        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; }
                String[] cols = line.split(",", -1);
                if (cols.length >= 11) rows.add(cols);
            }
        }

        System.out.println("\n======================================================================================");
        System.out.println(" BENCHMARK SUMMARY");
        System.out.println("======================================================================================");
        System.out.printf(" %-8s %-10s %-25s %7s %7s %7s %5s %8s%n",
                "Type", "Algorithm", "BaseFile",
                "Before", "After", "Batches", "Max", "Time(ms)");
        System.out.println("--------------------------------------------------------------------------------------");
        for (String[] r : rows) {
            System.out.printf(" %-8s %-10s %-25s %7s %7s %7s %5s %8s%n",
                    r[1].trim(), r[2].trim(), r[3].trim(),
                    r[5].trim(), r[6].trim(), r[4].trim(),
                    r[9].trim(), r[10].trim());
        }
        System.out.println("======================================================================================");

        // Print sets detail if file exists
        String setsPath = setsFilePath(csvPath);
        if (new File(setsPath).exists()) {
            printSetsSummary(setsPath);
        }
    }

    // ----------------------------------------------------------------
    // Print summary table for benchmark_sets.csv
    // ----------------------------------------------------------------

    public static void printSetsSummary(String setsPath) throws IOException {
        File file = new File(setsPath);
        if (!file.exists()) {
            System.out.println("No sets file found at: " + setsPath);
            return;
        }

        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; }
                // Split carefully — sets may contain spaces inside quotes
                rows.add(line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1));
            }
        }

        System.out.println("\n======================================================================================");
        System.out.println(" ITEMSET CHANGE DETAILS (dynamic runs)");
        System.out.println("======================================================================================");
        for (String[] r : rows) {
            if (r.length < 9) continue;
            System.out.println();
            System.out.printf("  Algorithm  : %s%n", r[1].trim());
            System.out.printf("  Batch      : %s%n", r[4].trim());
            System.out.printf("  Transactions: %s -> %s%n",
                    r[2].trim(), r[3].trim());
            System.out.printf("  Gained     : %s%n",
                    r[5].trim().replace("|", "  "));
            System.out.printf("  Lost       : %s%n",
                    r[6].trim().replace("|", "  "));
            System.out.printf("  Unchanged  : %s%n",
                    r[7].trim().replace("|", "  "));
            System.out.printf("  Current    : %s%n",
                    r[8].trim().replace("|", "  "));
            System.out.println("  " + "-".repeat(78));
        }
        System.out.println("======================================================================================");
        System.out.println("  Full itemset details saved to: " + setsPath);
        System.out.println("======================================================================================");
    }

    // ----------------------------------------------------------------
    // Internal helpers
    // ----------------------------------------------------------------

    /** Derive the sets CSV path from the main CSV path. */
    private static String setsFilePath(String csvPath) {
        int dot = csvPath.lastIndexOf('.');
        if (dot > 0) {
            return csvPath.substring(0, dot) + "_sets" + csvPath.substring(dot);
        }
        return csvPath + "_sets.csv";
    }

    /** Serialize a list of itemsets to pipe-separated bracket notation. */
    private static String serializeSets(List<Set<Integer>> sets) {
        if (sets == null || sets.isEmpty()) return "-";
        StringJoiner sj = new StringJoiner("|");
        for (Set<Integer> s : sets) {
            // Sort items for consistent output
            List<Integer> sorted = new ArrayList<>(s);
            Collections.sort(sorted);
            StringJoiner items = new StringJoiner(" ");
            for (int item : sorted) items.add(String.valueOf(item));
            sj.add("[" + items + "]");
        }
        return sj.toString();
    }

    /** Write one row to benchmark_sets.csv. */
    private static void writeSetsRow(String             setsPath,
                                     String             algorithm,
                                     int                txnBefore,
                                     int                txnAfter,
                                     String             batchFiles,
                                     List<Set<Integer>> previous,
                                     List<Set<Integer>> current) throws IOException {

        // Compute gained, lost, unchanged
        List<Set<Integer>> gained    = new ArrayList<>();
        List<Set<Integer>> lost      = new ArrayList<>();
        List<Set<Integer>> unchanged = new ArrayList<>();

        if (previous != null && !previous.isEmpty()) {
            for (Set<Integer> s : current) {
                if (containsSet(previous, s)) unchanged.add(s);
                else gained.add(s);
            }
            for (Set<Integer> s : previous) {
                if (!containsSet(current, s)) lost.add(s);
            }
        }

        File file = new File(setsPath);
        boolean isNew = !file.exists() || file.length() == 0;

        try (PrintWriter pw = new PrintWriter(
                new BufferedWriter(new FileWriter(file, true)))) {
            if (isNew) pw.println(SETS_CSV_HEADER);

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new Date());

            // Wrap set columns in quotes to protect pipe separators and spaces
            pw.printf("%s,%s,%d,%d,%s,\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    timestamp,
                    algorithm,
                    txnBefore,
                    txnAfter,
                    batchFiles,
                    serializeSets(gained),
                    serializeSets(lost),
                    serializeSets(unchanged),
                    serializeSets(current));
        }

        System.out.println("Itemset details saved: " + setsPath);
    }

    /** Write one row to main benchmark.csv. */
    private static void writeRow(String csvPath,
                                 String runType,
                                 String baseFile,
                                 String algorithm,
                                 int    batchesAdded,
                                 int    transactionsBefore,
                                 int    transactionsAfter,
                                 String batchFiles,
                                 double minSup,
                                 int    maximalCount,
                                 long   runtimeMs) throws IOException {

        File file = new File(csvPath);
        boolean isNew = !file.exists() || file.length() == 0;

        try (PrintWriter pw = new PrintWriter(
                new BufferedWriter(new FileWriter(file, true)))) {
            if (isNew) pw.println(CSV_HEADER);

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new Date());

            pw.printf("%s,%s,%s,%s,%d,%d,%d,%s,%.4f,%d,%d%n",
                    timestamp, runType, algorithm, baseFile,
                    batchesAdded, transactionsBefore, transactionsAfter,
                    batchFiles, minSup, maximalCount, runtimeMs);
        }

        System.out.println("Benchmark logged to  : " + csvPath);
    }

    private static boolean containsSet(List<Set<Integer>> list,
                                       Set<Integer> target) {
        for (Set<Integer> s : list) {
            if (s.equals(target)) return true;
        }
        return false;
    }
}