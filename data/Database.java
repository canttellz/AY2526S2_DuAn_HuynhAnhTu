package data;

import java.io.*;
import java.util.*;

/**
 * Holds a collection of uncertain transactions.
 * Supports dynamic accumulation via file appending, stdin input,
 * and persistent state saving/loading between runs.
 *
 * Data file format (space/tab separated):
 *   item:probability  item:probability  ...
 *   e.g.   1:0.9  3:0.7  5:0.5
 */
public class Database {

    private final List<Transaction> transactions = new ArrayList<>();

    // ---------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------

    /** Load transactions from a file, replacing any existing data. */
    public void loadFromFile(String filePath) throws IOException {
        transactions.clear();
        appendFromFile(filePath);
    }

    /**
     * Append transactions from a batch file to the existing database.
     * This is the core of "accumulated dynamic" — old data is kept,
     * new batch is added on top.
     */
    public void appendFromFile(String filePath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    transactions.add(Transaction.parse(line));
                }
            }
        }
    }

    /**
     * Append transactions typed interactively from stdin.
     * Type one transaction per line; type "done" to finish.
     */
    public void appendFromStdin() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.equalsIgnoreCase("done")) break;
            if (!line.isEmpty()) {
                transactions.add(Transaction.parse(line));
            }
        }
    }

    // ---------------------------------------------------------------
    // State persistence — save/load accumulated database between runs
    // ---------------------------------------------------------------

    /**
     * Save the entire accumulated database to a state file.
     * Format is identical to the input data format — human-readable
     * and reloadable with loadState() or appendFromFile().
     */
    public void saveState(String filePath) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(filePath)))) {
            for (Transaction t : transactions) {
                pw.println(t.toStateString());
            }
        }
    }

    /**
     * Load a previously saved database state, replacing any existing data.
     * This lets the next run resume from where the last one left off
     * without re-specifying all original batch files.
     */
    public void loadState(String filePath) throws IOException {
        transactions.clear();
        appendFromFile(filePath); // state file has same format as data file
    }

    // ---------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------

    public List<Transaction> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    public int size() {
        return transactions.size();
    }
}