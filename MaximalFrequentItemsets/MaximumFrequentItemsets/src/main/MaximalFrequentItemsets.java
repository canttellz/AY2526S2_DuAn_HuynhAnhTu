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
 * Algorithms:
 *   uapriori   Exact  — BFS candidate generation (U-Apriori)
 *   uhmine     Exact  — DFS hyperlinked structure (UH-Mine)
 *   aco        Approx — Ant Colony Optimization (ACO-Miner)
 *
 * General options:
 *   --minsup <value>        Minimum support threshold (default: 0.05)
 *   --add <file>            Append a batch file (repeatable)
 *   --append                Interactively append transactions from stdin
 *   --save-state <file>     Save accumulated database state after mining
 *   --load-state <file>     Load a previously saved database state
 *   --output <file>         Write results to a file instead of stdout
 *   --benchmark <file>      Append runtime result to a CSV benchmark file
 *   --benchmark-summary     Print a summary table of the benchmark CSV
 *
 * ACO-specific options (only used when algorithm = aco):
 *   --ants <n>              Number of ants per iteration (default: 20)
 *   --iterations <n>        Number of ACO iterations    (default: 50)
 *   --evaporation <v>       Pheromone evaporation rate  (default: 0.3)
 *   --alpha <v>             Pheromone influence weight  (default: 1.0)
 *   --beta <v>              Support heuristic weight    (default: 2.0)
 *
 * Examples:
 *   java main.MaximalFrequentItemsets uapriori data.txt --minsup 0.05
 *   java main.MaximalFrequentItemsets uhmine   data.txt --minsup 0.05
 *   java main.MaximalFrequentItemsets aco      data.txt --minsup 0.05
 *   java main.MaximalFrequentItemsets aco      data.txt --minsup 0.05 --ants 50 --iterations 100
 *
 *   # Accumulated dynamic workflow:
 *   java main.MaximalFrequentItemsets uapriori data.txt --minsup 0.05 --save-state db.state --benchmark bench.csv
 *   java main.MaximalFrequentItemsets uapriori --load-state db.state --add batch2.txt --minsup 0.05 --save-state db.state --benchmark bench.csv
 *
 *   # Benchmark summary:
 *   java main.MaximalFrequentItemsets --benchmark-summary bench.csv
 */
public class MaximalFrequentItemsets {

    public static void main(String[] args) throws Exception {

        // --- Special mode: print benchmark summary ---
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

        // ACO-specific parameters
        int    acoAnts        = 20;
        int    acoIterations  = 50;
        double acoEvaporation = 0.3;
        double acoAlpha       = 1.0;
        double acoBeta        = 2.0;

        // Second arg: data file or --load-state
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
                case "--minsup":      minSup        = Double.parseDouble(args[++i]); break;
                case "--add":         addFiles.add(args[++i]);                        break;
                case "--append":      appendMode    = true;                           break;
                case "--save-state":  saveState     = args[++i];                     break;
                case "--load-state":  loadState     = args[++i];                     break;
                case "--output":      outputFile    = args[++i];                     break;
                case "--benchmark":   benchmarkFile = args[++i];                     break;
                case "--ants":        acoAnts       = Integer.parseInt(args[++i]);   break;
                case "--iterations":  acoIterations = Integer.parseInt(args[++i]);   break;
                case "--evaporation": acoEvaporation= Double.parseDouble(args[++i]); break;
                case "--alpha":       acoAlpha      = Double.parseDouble(args[++i]); break;
                case "--beta":        acoBeta       = Double.parseDouble(args[++i]); break;
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

            // --- Step 2: Append batch files (accumulated dynamic) ---
            int transactionsBefore = db.size();
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

            // --- Step 4: Load previous results BEFORE overwriting state ---
            List<Set<Integer>> previousMaximal = null;
            if (loadState != null) {
                previousMaximal = ResultComparator.loadPreviousResults(loadState + ".results");
                if (!previousMaximal.isEmpty()) {
                    System.out.println("Loaded " + previousMaximal.size()
                            + " previous maximal itemsets for comparison.");
                }
            }

            // --- Step 5: Save accumulated state ---
            if (saveState != null) {
                db.saveState(saveState);
                System.out.println("State saved to      : " + saveState);
            }

            // --- Step 6: Select and run algorithm ---
            Algorithm algorithm;
            if (algorithmName.equals("aco")) {
                algorithm = AlgorithmFactory.createACO(db, minSup,
                        acoAnts, acoIterations, acoEvaporation, acoAlpha, acoBeta);
                System.out.println("ACO params          : ants=" + acoAnts
                        + "  iterations=" + acoIterations
                        + "  evaporation=" + acoEvaporation
                        + "  alpha=" + acoAlpha
                        + "  beta=" + acoBeta);
            } else {
                algorithm = AlgorithmFactory.create(algorithmName, db, minSup);
            }

            if (algorithm == null) {
                System.err.println("Unknown algorithm: " + algorithmName);
                System.err.println("Available: uapriori, uhmine, aco");
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

            // --- Step 10: Log benchmark ---
            if (benchmarkFile != null) {
                if (loadState != null || !addFiles.isEmpty()) {
                    String baseFile = (loadState != null) ? loadState : dataFile;
                    // Pass empty list if no previous results (first dynamic run)
                    List<Set<Integer>> prevForLog = (previousMaximal != null)
                            ? previousMaximal : new ArrayList<>();
                    BenchmarkLogger.logDynamic(benchmarkFile, algorithmName, baseFile,
                            addFiles, transactionsBefore, db.size(),
                            minSup, prevForLog, maximal, elapsed);
                } else {
                    BenchmarkLogger.logStatic(benchmarkFile, algorithmName, dataFile,
                            db.size(), minSup, maximal.size(), elapsed);
                }
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java main.MaximalFrequentItemsets <algorithm> <datafile|--load-state file> [options]");
        System.out.println("Algorithms : uapriori (exact), uhmine (exact), aco (approximate)");
        System.out.println("General options:");
        System.out.println("  --minsup <value>        Minimum support (default: 0.05)");
        System.out.println("  --add <file>            Append a batch file (repeatable)");
        System.out.println("  --append                Append transactions from stdin");
        System.out.println("  --save-state <file>     Save database state for next run");
        System.out.println("  --load-state <file>     Load previously saved database state");
        System.out.println("  --output <file>         Write results to file");
        System.out.println("  --benchmark <file>      Log runtime to CSV benchmark file");
        System.out.println("  --benchmark-summary     Print summary table from benchmark CSV");
        System.out.println("ACO options (only used when algorithm = aco):");
        System.out.println("  --ants <n>              Number of ants per iteration (default: 20)");
        System.out.println("  --iterations <n>        Number of ACO iterations    (default: 50)");
        System.out.println("  --evaporation <v>       Pheromone evaporation rate  (default: 0.3)");
        System.out.println("  --alpha <v>             Pheromone influence weight  (default: 1.0)");
        System.out.println("  --beta <v>              Support heuristic weight    (default: 2.0)");
        System.out.println("\nBenchmark summary: java main.MaximalFrequentItemsets --benchmark-summary bench.csv");
    }
}