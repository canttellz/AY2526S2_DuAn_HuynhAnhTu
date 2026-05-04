package main;

import algorithm.Algorithm;
import algorithm.AlgorithmFactory;
import data.Database;
import output.BenchmarkLogger;
import output.ResultComparator;
import output.ResultWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Entry point for the Maximal Frequent Itemsets miner.
 *
 * Usage:
 *   java main.MaximalFrequentItemsets <algorithm> <datafile> [options]
 *   java main.MaximalFrequentItemsets <algorithm> --load-state <file> [options]
 *
 * Options:
 *   --minsup <value>        Minimum support threshold (default: 0.05)
 *   --add <file>            Append a batch file (repeatable)
 *   --append                Interactively append transactions from stdin
 *   --save-state <file>     Save accumulated database state after mining
 *   --load-state <file>     Load a previously saved database state
 *   --output <file>         Write results to a file instead of stdout
 *   --benchmark <file>      Append runtime result to a CSV benchmark file
 *   --benchmark-summary     Print a summary table of the benchmark CSV
 *
 * Accumulated Dynamic workflow:
 *
 *   # Time 1 — mine initial batch, save state, log benchmark
 *   java main.MaximalFrequentItemsets uapriori ..\data\batch1.txt --minsup 0.05 --save-state db.state --benchmark benchmark.csv
 *
 *   # Time 2 — load saved state, append new batch, re-mine
 *   java main.MaximalFrequentItemsets uapriori --load-state db.state --add ..\data\batch2.txt --minsup 0.05 --save-state db.state --benchmark benchmark.csv
 *
 *   # Compare algorithms on same data
 *   java main.MaximalFrequentItemsets uapriori  ..\data\batch1.txt --minsup 0.05 --benchmark benchmark.csv
 *   java main.MaximalFrequentItemsets ufgrowth  ..\data\batch1.txt --minsup 0.05 --benchmark benchmark.csv
 *
 *   # Print benchmark summary table
 *   java main.MaximalFrequentItemsets --benchmark-summary benchmark.csv
 */
public class MaximalFrequentItemsets {

    public static void main(String[] args) throws Exception {

        // --- Special mode: just print benchmark summary ---
        if (args.length >= 2 && args[0].equals("--benchmark-summary")) {
            BenchmarkLogger.printSummary(args[1]);
            return;
        }

        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }

        String algorithmName = args[0].toLowerCase();

        // --- Default values ---
        double       minSup        = 0.05;
        String       dataFile      = null;
        String       loadState     = null;
        List<String> addFiles      = new ArrayList<>();
        boolean      appendMode    = false;
        String       saveState     = null;
        String       outputFile    = null;
        String       benchmarkFile = null;

        // Second arg: either a data file or --load-state <file>
        int startIndex;
        if (args[1].equals("--load-state")) {
            if (args.length < 3) { printUsage(); System.exit(1); }
            loadState  = args[2];
            startIndex = 3;
        } else {
            dataFile   = args[1];
            startIndex = 2;
        }

        // Parse remaining flags
        for (int i = startIndex; i < args.length; i++) {
            switch (args[i]) {
                case "--minsup":
                    minSup = Double.parseDouble(args[++i]);
                    break;
                case "--add":
                    addFiles.add(args[++i]);
                    break;
                case "--append":
                    appendMode = true;
                    break;
                case "--save-state":
                    saveState = args[++i];
                    break;
                case "--load-state":
                    loadState = args[++i];
                    break;
                case "--output":
                    outputFile = args[++i];
                    break;
                case "--benchmark":
                    benchmarkFile = args[++i];
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        try {
            Database db = new Database();

            // --- Step 1: Load initial data ---
            if (loadState != null) {
                db.loadState(loadState);
                System.out.println("Loaded saved state  : " + db.size()
                        + " transactions from " + loadState);
            } else {
                db.loadFromFile(dataFile);
                System.out.println("Loaded              : " + db.size()
                        + " transactions from " + dataFile);
            }

            // --- Step 2: Append new batch files (accumulated dynamic) ---
            for (String addFile : addFiles) {
                int before = db.size();
                db.appendFromFile(addFile);
                System.out.println("Appended batch      : +" + (db.size() - before)
                        + " from " + addFile + "  [total: " + db.size() + "]");
            }

            // --- Step 3: Append from stdin ---
            if (appendMode) {
                System.out.println("Enter transactions (format: item:prob item:prob ...).");
                System.out.println("Type 'done' to finish:");
                int before = db.size();
                db.appendFromStdin();
                System.out.println("Appended from stdin : +" + (db.size() - before)
                        + "  [total: " + db.size() + "]");
            }

            // --- Step 4: Load previous results BEFORE overwriting anything ---
            List<Set<Integer>> previousMaximal = null;
            if (loadState != null) {
                previousMaximal = ResultComparator.loadPreviousResults(loadState + ".results");
                if (!previousMaximal.isEmpty()) {
                    System.out.println("Loaded " + previousMaximal.size()
                            + " previous maximal itemsets for comparison.");
                }
            }

            // --- Step 5: Save accumulated database state for next run ---
            if (saveState != null) {
                db.saveState(saveState);
                System.out.println("State saved to      : " + saveState);
            }

            // --- Step 6: Select and run algorithm ---
            Algorithm algorithm = AlgorithmFactory.create(algorithmName, db, minSup);
            if (algorithm == null) {
                System.err.println("Unknown algorithm: " + algorithmName);
                System.err.println("Available: uapriori, ufgrowth, aco");
                System.exit(1);
            }

            long start   = System.currentTimeMillis();
            List<Set<Integer>> maximal = algorithm.mineMaximal();
            long elapsed = System.currentTimeMillis() - start;

            // --- Step 7: Write results ---
            ResultWriter writer = new ResultWriter(outputFile);
            writer.write(maximal, elapsed);
            writer.close();

            // --- Step 8: Show what changed since last run ---
            if (previousMaximal != null && !previousMaximal.isEmpty()) {
                ResultComparator.compare(previousMaximal, maximal);
            }

            // --- Step 9: Save current results for future comparison ---
            if (saveState != null) {
                ResultComparator.saveResults(maximal, saveState + ".results");
            }

            // --- Step 10: Log benchmark result ---
            if (benchmarkFile != null) {
                String loggedFile = (dataFile != null) ? dataFile
                        : (loadState + (addFiles.isEmpty() ? "" : "+" + addFiles.size() + "batches"));
                BenchmarkLogger.log(benchmarkFile, algorithmName, loggedFile,
                        db.size(), minSup, maximal.size(), elapsed);
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java main.MaximalFrequentItemsets <algorithm> <datafile|--load-state file> [options]");
        System.out.println("  --minsup <value>        Minimum support (default: 0.05)");
        System.out.println("  --add <file>            Append a batch file (repeatable)");
        System.out.println("  --append                Append transactions from stdin");
        System.out.println("  --save-state <file>     Save database state for next run");
        System.out.println("  --load-state <file>     Load previously saved database state");
        System.out.println("  --output <file>         Write results to file");
        System.out.println("  --benchmark <file>      Log runtime to CSV benchmark file");
        System.out.println("  --benchmark-summary     Print summary table from benchmark CSV");
        System.out.println("Algorithms: uapriori, ufgrowth, aco");
        System.out.println("\nBenchmark summary: java main.MaximalFrequentItemsets --benchmark-summary benchmark.csv");
    }
}