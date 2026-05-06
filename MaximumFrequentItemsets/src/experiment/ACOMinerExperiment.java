package experiment;

import algorithm.ACOMiner;
import algorithm.UApriori;
import data.Database;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Experiment class for ACO-Miner algorithm.
 *
 * Runs TWO experiments automatically:
 *   1. STATIC  — mine each dataset + coverage analysis (4 param configs)
 *   2. DYNAMIC — accumulated database (Time 1 -> 2 -> 3)
 *
 * Also computes U-Apriori exact answer for coverage/accuracy comparison.
 *
 * Output files written to output/ folder:
 *   aco_static_results.txt
 *   aco_static_parameters.txt
 *   aco_dynamic_results.txt
 *   aco_dynamic_parameters.txt
 *
 * Run: java experiment.ACOMinerExperiment
 */
public class ACOMinerExperiment {

    private static final String DATA_1000  = "../data/retail_uncertain_1000.txt";
    private static final String DATA_5000  = "../data/retail_uncertain_5000.txt";
    private static final String DATA_10000 = "../data/retail_uncertain_10000.txt";
    private static final String DATA_FULL  = "../data/retail_uncertain.txt";
    private static final String DYNAMIC_INITIAL = DATA_5000;
    private static final String DYNAMIC_BATCH_2 = DATA_1000;
    private static final String DYNAMIC_BATCH_3 = DATA_10000;
    private static final double MIN_SUP    = 0.05;
    private static final String OUT_DIR    = "../output";

    // Default ACO parameters
    private static final int    ANTS        = 20;
    private static final int    ITERATIONS  = 50;
    private static final double EVAPORATION = 0.3;
    private static final double ALPHA       = 1.0;
    private static final double BETA        = 2.0;

    // Coverage analysis configurations: {ants, iterations}
    private static final int[][] COVERAGE_CONFIGS = {
        {5, 10}, {20, 50}, {50, 100}, {100, 200}
    };

    public static void main(String[] args) throws Exception {
        new File(OUT_DIR).mkdirs();
        System.out.println("=== ACO-Miner Experiment ===");
        runStaticExperiment();
        runDynamicExperiment();
        System.out.println("\nDone! Output written to: " + OUT_DIR);
    }

    // ── STATIC ────────────────────────────────────────────────────

    private static void runStaticExperiment() throws Exception {
        System.out.println("\n[Static] Running on all datasets...");
        String[] datasets = {DATA_1000, DATA_5000, DATA_10000, DATA_FULL};
        List<StaticResult> results = new ArrayList<>();

        for (String f : datasets) {
            // Get exact answer first
            Database db = new Database();
            db.loadFromFile(f);
            List<Set<Integer>> exact =
                    new UApriori(db, MIN_SUP).mineMaximal();

            // Run ACO with default params
            Runtime rt = Runtime.getRuntime();
            System.gc();
            long mb = rt.totalMemory() - rt.freeMemory();
            long t0 = System.currentTimeMillis();
            List<Set<Integer>> maximal = new ACOMiner(db, MIN_SUP,
                    ANTS, ITERATIONS, EVAPORATION, ALPHA, BETA)
                    .mineMaximal();
            long elapsed = System.currentTimeMillis() - t0;
            double mem = (rt.totalMemory() - rt.freeMemory() - mb) / 1048576.0;

            int found = countFound(maximal, exact);
            double coverage = exact.isEmpty() ? 0.0
                    : found * 100.0 / exact.size();

            results.add(new StaticResult(f, db.size(), maximal,
                    exact, elapsed, mem, coverage));
            System.out.printf("  %-40s -> %d/%d sets (%.0f%%), %d ms%n",
                    new File(f).getName(), found, exact.size(),
                    coverage, elapsed);
        }

        // Coverage analysis on 5000 dataset
        System.out.println("[Static] Coverage analysis on 5000 dataset...");
        Database db5k = new Database();
        db5k.loadFromFile(DATA_5000);
        List<Set<Integer>> exact5k =
                new UApriori(db5k, MIN_SUP).mineMaximal();
        List<CoverageResult> coverageResults = new ArrayList<>();

        for (int[] cfg : COVERAGE_CONFIGS) {
            long t0 = System.currentTimeMillis();
            List<Set<Integer>> maximal = new ACOMiner(db5k, MIN_SUP,
                    cfg[0], cfg[1], EVAPORATION, ALPHA, BETA)
                    .mineMaximal();
            long elapsed = System.currentTimeMillis() - t0;
            int found = countFound(maximal, exact5k);
            double cov = exact5k.isEmpty() ? 0.0
                    : found * 100.0 / exact5k.size();
            coverageResults.add(new CoverageResult(
                    cfg[0], cfg[1], found, cov, elapsed));
            System.out.printf("  Ants=%3d Iter=%3d -> %d/%d (%.0f%%), %d ms%n",
                    cfg[0], cfg[1], found, exact5k.size(), cov, elapsed);
        }

        // Write results
        try (PrintWriter pw = new PrintWriter(
                new FileWriter(OUT_DIR + "/aco_static_results.txt"))) {
            pw.println("ACO-Miner — Static Results | minSup=" + MIN_SUP
                    + " | ants=" + ANTS + " iter=" + ITERATIONS
                    + " | " + timestamp());
            for (StaticResult r : results) {
                pw.println("\n--- " + new File(r.dataFile).getName()
                        + " (" + r.transactions + " transactions) ---");
                pw.println("Runtime : " + r.runtimeMs + " ms");
                pw.printf("Memory  : %.2f MB%n", r.memMB);
                pw.printf("Coverage: %.1f%% (%d of %d exact)%n",
                        r.coverage, r.maximal.size(), r.exact.size());
                pw.println("ACO results:");
                r.maximal.sort((a,b)->b.size()-a.size());
                for (int i = 0; i < r.maximal.size(); i++) {
                    List<Integer> s = new ArrayList<>(r.maximal.get(i));
                    Collections.sort(s);
                    pw.println("  " + (i+1) + ". " + s);
                }
                pw.println("Exact results (U-Apriori ground truth):");
                r.exact.sort((a,b)->b.size()-a.size());
                for (int i = 0; i < r.exact.size(); i++) {
                    List<Integer> s = new ArrayList<>(r.exact.get(i));
                    Collections.sort(s);
                    boolean found = containsSet(r.maximal, r.exact.get(i));
                    pw.println("  " + (i+1) + ". " + s
                            + (found ? "  [found]" : "  [MISSED]"));
                }
            }
            pw.println("\n\n=== Coverage Analysis (5000 transactions) ===");
            pw.printf("Exact answer: %d maximal itemsets%n", exact5k.size());
            pw.printf("%n%-6s %-6s %-8s %-10s %-12s %-10s%n",
                    "Ants","Iter","A×I","Found","Coverage(%)","Runtime(ms)");
            pw.println("-".repeat(55));
            for (CoverageResult c : coverageResults)
                pw.printf("%-6d %-6d %-8d %-10d %-12.1f %-10d%n",
                        c.ants, c.iter, c.ants*c.iter,
                        c.found, c.coverage, c.runtimeMs);
        }

        try (PrintWriter pw = new PrintWriter(
                new FileWriter(OUT_DIR + "/aco_static_parameters.txt"))) {
            pw.println("ACO-Miner — Static Parameters | minSup=" + MIN_SUP
                    + " | " + timestamp());
            pw.println("Algorithm   : ACO-Miner (Approximate, Swarm)");
            pw.println("Default ACO parameters:");
            pw.println("  Ants (A)       : " + ANTS);
            pw.println("  Iterations (I) : " + ITERATIONS);
            pw.println("  A × I          : " + (ANTS * ITERATIONS));
            pw.println("  Evaporation (ρ): " + EVAPORATION);
            pw.println("  Alpha (α)      : " + ALPHA);
            pw.println("  Beta (β)       : " + BETA);
            pw.printf("%n%-35s %8s %12s %10s %8s %10s%n",
                    "Dataset","Txn","Runtime(ms)","Mem(MB)",
                    "MaxSets","Coverage");
            pw.println("-".repeat(86));
            for (StaticResult r : results)
                pw.printf("%-35s %8d %12d %10.2f %8d %9.1f%%%n",
                        new File(r.dataFile).getName(),
                        r.transactions, r.runtimeMs, r.memMB,
                        r.maximal.size(), r.coverage);
        }
    }

    // ── DYNAMIC ───────────────────────────────────────────────────

    private static void runDynamicExperiment() throws Exception {
        System.out.println("\n[Dynamic] Accumulated database experiment...");
        List<DynamicResult> steps = new ArrayList<>();
        List<Set<Integer>> prev = null;
        Database db = new Database();

        db.loadFromFile(DYNAMIC_INITIAL);
        List<Set<Integer>> exact1 =
                new UApriori(db, MIN_SUP).mineMaximal();
        DynamicResult r1 = runStep(db, prev, exact1,
                "Time 1 (initial)", DYNAMIC_INITIAL, 0);
        steps.add(r1); prev = r1.maximal;

        int before2 = db.size();
        db.appendFromFile(DYNAMIC_BATCH_2);
        List<Set<Integer>> exact2 =
                new UApriori(db, MIN_SUP).mineMaximal();
        DynamicResult r2 = runStep(db, prev, exact2,
                "Time 2 (+" + (db.size()-before2) + " txn)",
                DYNAMIC_BATCH_2, before2);
        steps.add(r2); prev = r2.maximal;

        int before3 = db.size();
        db.appendFromFile(DYNAMIC_BATCH_3);
        List<Set<Integer>> exact3 =
                new UApriori(db, MIN_SUP).mineMaximal();
        DynamicResult r3 = runStep(db, prev, exact3,
                "Time 3 (+" + (db.size()-before3) + " txn)",
                DYNAMIC_BATCH_3, before3);
        steps.add(r3);

        for (DynamicResult r : steps)
            System.out.printf("  %-22s -> %d sets (%.0f%% coverage), "
                    + "+%d gained, -%d lost, %d ms%n",
                    r.label, r.maximal.size(), r.coverage,
                    r.gained.size(), r.lost.size(), r.runtimeMs);

        try (PrintWriter pw = new PrintWriter(
                new FileWriter(OUT_DIR + "/aco_dynamic_results.txt"))) {
            pw.println("ACO-Miner — Dynamic Results | minSup=" + MIN_SUP
                    + " | ants=" + ANTS + " iter=" + ITERATIONS
                    + " | " + timestamp());
            for (DynamicResult r : steps) {
                pw.println("\n=== " + r.label + " ===");
                pw.println("Transactions: " + r.txnBefore + " -> " + r.txnAfter);
                pw.println("Runtime     : " + r.runtimeMs + " ms");
                pw.printf ("Memory      : %.2f MB%n", r.memMB);
                pw.printf ("Coverage    : %.1f%% (%d of %d exact)%n",
                        r.coverage, r.maximal.size(), r.exact.size());
                pw.println("ACO results (" + r.maximal.size() + "):");
                r.maximal.sort((a,b)->b.size()-a.size());
                for (int i = 0; i < r.maximal.size(); i++) {
                    List<Integer> s = new ArrayList<>(r.maximal.get(i));
                    Collections.sort(s);
                    pw.println("  " + (i+1) + ". " + s);
                }
                pw.println("Exact results (" + r.exact.size() + "):");
                r.exact.sort((a,b)->b.size()-a.size());
                for (int i = 0; i < r.exact.size(); i++) {
                    List<Integer> s = new ArrayList<>(r.exact.get(i));
                    Collections.sort(s);
                    pw.println("  " + (i+1) + ". " + s
                            + (containsSet(r.maximal,r.exact.get(i))
                            ? "  [found]" : "  [MISSED]"));
                }
                if (!r.gained.isEmpty() || !r.lost.isEmpty()) {
                    pw.println("Changes from previous step:");
                    for (Set<Integer> s : r.gained) {
                        List<Integer> sl=new ArrayList<>(s);
                        Collections.sort(sl);
                        pw.println("  + " + sl);
                    }
                    for (Set<Integer> s : r.lost) {
                        List<Integer> sl=new ArrayList<>(s);
                        Collections.sort(sl);
                        pw.println("  - " + sl);
                    }
                    for (Set<Integer> s : r.unchanged) {
                        List<Integer> sl=new ArrayList<>(s);
                        Collections.sort(sl);
                        pw.println("  = " + sl);
                    }
                }
            }
        }

        try (PrintWriter pw = new PrintWriter(
                new FileWriter(OUT_DIR + "/aco_dynamic_parameters.txt"))) {
            pw.println("ACO-Miner — Dynamic Parameters | minSup=" + MIN_SUP
                    + " | " + timestamp());
            pw.println("Algorithm : ACO-Miner (Approximate, Swarm)");
            pw.println("Ants=" + ANTS + "  Iterations=" + ITERATIONS
                    + "  Evaporation=" + EVAPORATION
                    + "  Alpha=" + ALPHA + "  Beta=" + BETA);
            pw.printf("%n%-25s %6s %6s %12s %10s %8s %10s %6s %6s%n",
                    "Step","Before","After","Runtime(ms)","Mem(MB)",
                    "MaxSets","Coverage","Gained","Lost");
            pw.println("-".repeat(96));
            for (DynamicResult r : steps)
                pw.printf("%-25s %6d %6d %12d %10.2f %8d %9.1f%% %6d %6d%n",
                        r.label, r.txnBefore, r.txnAfter,
                        r.runtimeMs, r.memMB, r.maximal.size(),
                        r.coverage, r.gained.size(), r.lost.size());
        }
    }

    private static DynamicResult runStep(Database db,
            List<Set<Integer>> prev, List<Set<Integer>> exact,
            String label, String batchFile, int txnBefore) {
        Runtime rt = Runtime.getRuntime();
        System.gc();
        long mb = rt.totalMemory() - rt.freeMemory();
        long t0 = System.currentTimeMillis();
        List<Set<Integer>> maximal = new ACOMiner(db, MIN_SUP,
                ANTS, ITERATIONS, EVAPORATION, ALPHA, BETA)
                .mineMaximal();
        long elapsed = System.currentTimeMillis() - t0;
        double mem = (rt.totalMemory()-rt.freeMemory()-mb)/1048576.0;
        int found = countFound(maximal, exact);
        double cov = exact.isEmpty() ? 0.0 : found*100.0/exact.size();
        List<Set<Integer>> gained=new ArrayList<>(),
                lost=new ArrayList<>(), unch=new ArrayList<>();
        if (prev != null) {
            for (Set<Integer> s : maximal)
                (containsSet(prev,s) ? unch : gained).add(s);
            for (Set<Integer> s : prev)
                if (!containsSet(maximal,s)) lost.add(s);
        }
        return new DynamicResult(label, batchFile, txnBefore,
                db.size(), maximal, exact, gained, lost, unch,
                elapsed, mem, cov);
    }

    // ── Inner classes ─────────────────────────────────────────────

    private static class StaticResult {
        String dataFile; int transactions;
        List<Set<Integer>> maximal, exact;
        long runtimeMs; double memMB, coverage;
        StaticResult(String f,int t,List<Set<Integer>> m,
                List<Set<Integer>> e,long r,double mem,double cov){
            dataFile=f;transactions=t;maximal=m;exact=e;
            runtimeMs=r;memMB=mem;coverage=cov;}
    }
    private static class DynamicResult {
        String label,batchFile; int txnBefore,txnAfter;
        List<Set<Integer>> maximal,exact,gained,lost,unchanged;
        long runtimeMs; double memMB,coverage;
        DynamicResult(String lb,String bf,int tb,int ta,
                List<Set<Integer>> m,List<Set<Integer>> ex,
                List<Set<Integer>> g,List<Set<Integer>> l,
                List<Set<Integer>> u,long r,double mem,double cov){
            label=lb;batchFile=bf;txnBefore=tb;txnAfter=ta;
            maximal=m;exact=ex;gained=g;lost=l;unchanged=u;
            runtimeMs=r;memMB=mem;coverage=cov;}
    }
    private static class CoverageResult {
        int ants,iter,found; double coverage; long runtimeMs;
        CoverageResult(int a,int i,int f,double c,long r){
            ants=a;iter=i;found=f;coverage=c;runtimeMs=r;}
    }
    private static int countFound(List<Set<Integer>> aco,
                                  List<Set<Integer>> exact){
        int c=0;
        for(Set<Integer> e:exact) if(containsSet(aco,e)) c++;
        return c;
    }
    private static boolean containsSet(List<Set<Integer>> list,
                                       Set<Integer> t){
        for(Set<Integer> s:list) if(s.equals(t)) return true;
        return false;
    }
    private static String timestamp(){
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());}
}