package output;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Logs benchmark results (runtime, dataset info, algorithm) to a CSV file.
 *
 * Each run appends one row to the CSV so you can accumulate results
 * across multiple runs and compare algorithms side by side.
 *
 * CSV format:
 *   timestamp, algorithm, dataFile, transactions, minSup, maximalCount, runtimeMs
 *
 * Example output (benchmark.csv):
 *   2025-01-01 10:00:00, uapriori,  retail_5000.txt,  5000,  0.05, 4, 63
 *   2025-01-01 10:00:05, ufgrowth,  retail_5000.txt,  5000,  0.05, 4, 21
 *   2025-01-01 10:00:10, uapriori,  retail_10000.txt, 10000, 0.05, 5, 95
 *   2025-01-01 10:00:15, ufgrowth,  retail_10000.txt, 10000, 0.05, 5, 38
 *
 * Usage:
 *   BenchmarkLogger.log("benchmark.csv", "uapriori", "retail_5000.txt",
 *                        5000, 0.05, 4, 63);
 */
public class BenchmarkLogger {

    private static final String CSV_HEADER =
            "timestamp,algorithm,dataFile,transactions,minSup,maximalCount,runtimeMs";

    /**
     * Log one benchmark result to a CSV file.
     *
     * If the file does not exist, it is created with a header row.
     * If it already exists, the result is appended as a new row.
     *
     * @param csvPath       Path to the CSV benchmark file
     * @param algorithm     Algorithm name (e.g. "uapriori", "ufgrowth")
     * @param dataFile      Name/path of the data file used
     * @param transactions  Number of transactions in the database
     * @param minSup        Minimum support threshold used
     * @param maximalCount  Number of maximal frequent itemsets found
     * @param runtimeMs     Runtime in milliseconds
     */
    public static void log(String csvPath,
                           String algorithm,
                           String dataFile,
                           int    transactions,
                           double minSup,
                           int    maximalCount,
                           long   runtimeMs) throws IOException {

        File file = new File(csvPath);
        boolean isNew = !file.exists() || file.length() == 0;

        try (PrintWriter pw = new PrintWriter(
                new BufferedWriter(new FileWriter(file, true)))) {  // append=true

            // Write header if file is new
            if (isNew) {
                pw.println(CSV_HEADER);
            }

            // Extract just the filename from the full path
            String fileName = new File(dataFile).getName();

            // Timestamp
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new Date());

            // Write the benchmark row
            pw.printf("%s,%s,%s,%d,%.4f,%d,%d%n",
                    timestamp,
                    algorithm,
                    fileName,
                    transactions,
                    minSup,
                    maximalCount,
                    runtimeMs);
        }

        System.out.println("Benchmark logged to  : " + csvPath);
    }

    /**
     * Print a formatted summary table of all results in a CSV file.
     * Useful for quickly reviewing results in the terminal.
     *
     * @param csvPath  Path to the benchmark CSV file
     */
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
                if (first) { first = false; continue; } // skip header
                rows.add(line.split(","));
            }
        }

        System.out.println("\n============================================================");
        System.out.println(" BENCHMARK SUMMARY");
        System.out.println("============================================================");
        System.out.printf(" %-12s %-30s %8s %8s %8s%n",
                "Algorithm", "DataFile", "Trans", "Maximal", "Time(ms)");
        System.out.println("------------------------------------------------------------");

        for (String[] row : rows) {
            if (row.length < 7) continue;
            System.out.printf(" %-12s %-30s %8s %8s %8s%n",
                    row[1].trim(),   // algorithm
                    row[2].trim(),   // dataFile
                    row[3].trim(),   // transactions
                    row[5].trim(),   // maximalCount
                    row[6].trim());  // runtimeMs
        }
        System.out.println("============================================================");
    }
}