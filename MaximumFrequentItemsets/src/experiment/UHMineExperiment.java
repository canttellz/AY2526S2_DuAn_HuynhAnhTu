package experiment;

import algorithm.UHMine;
import data.Database;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Experiment class for UH-Mine algorithm.
 *
 * Runs TWO experiments automatically:
 *   1. STATIC  — mine each dataset individually
 *   2. DYNAMIC — accumulated database (Time 1 -> 2 -> 3)
 *
 * Output files written to output/ folder:
 *   uhmine_static_results.txt
 *   uhmine_static_parameters.txt
 *   uhmine_dynamic_results.txt
 *   uhmine_dynamic_parameters.txt
 *
 * Run: java experiment.UHMineExperiment
 */
public class UHMineExperiment {

    private static final String DATA_1000  = "../data/retail_uncertain_1000.txt";
    private static final String DATA_5000  = "../data/retail_uncertain_5000.txt";
    private static final String DATA_10000 = "../data/retail_uncertain_10000.txt";
    private static final String DATA_FULL  = "../data/retail_uncertain.txt";
    private static final String DYNAMIC_INITIAL = DATA_5000;
    private static final String DYNAMIC_BATCH_2 = DATA_1000;
    private static final String DYNAMIC_BATCH_3 = DATA_10000;
    private static final double MIN_SUP    = 0.05;
    private static final String OUT_DIR    = "../output";

    public static void main(String[] args) throws Exception {
        new File(OUT_DIR).mkdirs();
        System.out.println("=== UH-Mine Experiment ===");
        runStaticExperiment();
        runDynamicExperiment();
        System.out.println("\nDone! Output written to: " + OUT_DIR);
    }

    private static void runStaticExperiment() throws Exception {
        System.out.println("\n[Static] Running on all datasets...");
        String[] datasets = {DATA_1000, DATA_5000, DATA_10000, DATA_FULL};
        List<StaticResult> results = new ArrayList<>();

        for (String f : datasets) {
            Database db = new Database();
            db.loadFromFile(f);
            Runtime rt = Runtime.getRuntime();
            System.gc();
            long mb = rt.totalMemory() - rt.freeMemory();
            long t0 = System.currentTimeMillis();
            List<Set<Integer>> maximal =
                    new UHMine(db, MIN_SUP).mineMaximal();
            long elapsed = System.currentTimeMillis() - t0;
            double mem = (rt.totalMemory() - rt.freeMemory() - mb) / 1048576.0;
            results.add(new StaticResult(f, db.size(), maximal, elapsed, mem));
            System.out.printf("  %-40s -> %d sets, %d ms%n",
                    new File(f).getName(), maximal.size(), elapsed);
        }

        try (PrintWriter pw = new PrintWriter(
                new FileWriter(OUT_DIR + "/uhmine_static_results.txt"))) {
            pw.println("UH-Mine — Static Results | minSup=" + MIN_SUP
                    + " | " + timestamp());
            for (StaticResult r : results) {
                pw.println("\n--- " + new File(r.dataFile).getName()
                        + " (" + r.transactions + " transactions) ---");
                pw.println("Runtime: " + r.runtimeMs + " ms");
                pw.printf("Memory : %.2f MB%n", r.memMB);
                pw.println("Maximal itemsets (" + r.maximal.size() + "):");
                r.maximal.sort((a, b) -> b.size() - a.size());
                for (int i = 0; i < r.maximal.size(); i++) {
                    List<Integer> s = new ArrayList<>(r.maximal.get(i));
                    Collections.sort(s);
                    pw.println("  " + (i+1) + ". " + s);
                }
            }
        }

        try (PrintWriter pw = new PrintWriter(
                new FileWriter(OUT_DIR + "/uhmine_static_parameters.txt"))) {
            pw.println("UH-Mine — Static Parameters | minSup=" + MIN_SUP
                    + " | " + timestamp());
            pw.println("Algorithm: UH-Mine (Exact, DFS)");
            pw.println("Accuracy : 100% (exact — identical to U-Apriori)");
            pw.printf("%n%-35s %8s %12s %10s %10s%n",
                    "Dataset","Txn","Runtime(ms)","Mem(MB)","MaxSets");
            pw.println("-".repeat(78));
            for (StaticResult r : results)
                pw.printf("%-35s %8d %12d %10.2f %10d%n",
                        new File(r.dataFile).getName(),
                        r.transactions, r.runtimeMs,
                        r.memMB, r.maximal.size());
        }
    }

    private static void runDynamicExperiment() throws Exception {
        System.out.println("\n[Dynamic] Accumulated database experiment...");
        List<DynamicResult> steps = new ArrayList<>();
        List<Set<Integer>> prev = null;
        Database db = new Database();

        db.loadFromFile(DYNAMIC_INITIAL);
        DynamicResult r1 = runStep(db, prev, "Time 1 (initial)",
                DYNAMIC_INITIAL, 0);
        steps.add(r1); prev = r1.maximal;

        int before2 = db.size();
        db.appendFromFile(DYNAMIC_BATCH_2);
        DynamicResult r2 = runStep(db, prev,
                "Time 2 (+" + (db.size()-before2) + " txn)",
                DYNAMIC_BATCH_2, before2);
        steps.add(r2); prev = r2.maximal;

        int before3 = db.size();
        db.appendFromFile(DYNAMIC_BATCH_3);
        DynamicResult r3 = runStep(db, prev,
                "Time 3 (+" + (db.size()-before3) + " txn)",
                DYNAMIC_BATCH_3, before3);
        steps.add(r3);

        for (DynamicResult r : steps)
            System.out.printf("  %-22s -> %d sets, %d ms, "
                    + "+%d gained, -%d lost%n",
                    r.label, r.maximal.size(), r.runtimeMs,
                    r.gained.size(), r.lost.size());

        try (PrintWriter pw = new PrintWriter(
                new FileWriter(OUT_DIR + "/uhmine_dynamic_results.txt"))) {
            pw.println("UH-Mine — Dynamic Results | minSup=" + MIN_SUP
                    + " | " + timestamp());
            for (DynamicResult r : steps) {
                pw.println("\n=== " + r.label + " ===");
                pw.println("Transactions: " + r.txnBefore + " -> " + r.txnAfter);
                pw.println("Runtime     : " + r.runtimeMs + " ms");
                pw.printf ("Memory      : %.2f MB%n", r.memMB);
                pw.println("Maximal itemsets (" + r.maximal.size() + "):");
                r.maximal.sort((a, b) -> b.size() - a.size());
                for (int i = 0; i < r.maximal.size(); i++) {
                    List<Integer> s = new ArrayList<>(r.maximal.get(i));
                    Collections.sort(s);
                    pw.println("  " + (i+1) + ". " + s);
                }
                if (!r.gained.isEmpty() || !r.lost.isEmpty()) {
                    pw.println("Gained (" + r.gained.size() + "):");
                    for (Set<Integer> s : r.gained) {
                        List<Integer> sl = new ArrayList<>(s);
                        Collections.sort(sl);
                        pw.println("  + " + sl);
                    }
                    pw.println("Lost (" + r.lost.size() + "):");
                    for (Set<Integer> s : r.lost) {
                        List<Integer> sl = new ArrayList<>(s);
                        Collections.sort(sl);
                        pw.println("  - " + sl);
                    }
                    pw.println("Unchanged (" + r.unchanged.size() + "):");
                    for (Set<Integer> s : r.unchanged) {
                        List<Integer> sl = new ArrayList<>(s);
                        Collections.sort(sl);
                        pw.println("  = " + sl);
                    }
                }
            }
        }

        try (PrintWriter pw = new PrintWriter(
                new FileWriter(OUT_DIR + "/uhmine_dynamic_parameters.txt"))) {
            pw.println("UH-Mine — Dynamic Parameters | minSup=" + MIN_SUP
                    + " | " + timestamp());
            pw.println("Algorithm: UH-Mine (Exact, DFS)");
            pw.println("Accuracy : 100% (exact — identical to U-Apriori)");
            pw.printf("%n%-25s %6s %6s %12s %10s %8s %6s %6s%n",
                    "Step","Before","After","Runtime(ms)","Mem(MB)",
                    "MaxSets","Gained","Lost");
            pw.println("-".repeat(82));
            for (DynamicResult r : steps)
                pw.printf("%-25s %6d %6d %12d %10.2f %8d %6d %6d%n",
                        r.label,r.txnBefore,r.txnAfter,
                        r.runtimeMs,r.memMB,r.maximal.size(),
                        r.gained.size(),r.lost.size());
        }
    }

    private static DynamicResult runStep(Database db,
            List<Set<Integer>> prev, String label,
            String batchFile, int txnBefore) {
        Runtime rt = Runtime.getRuntime();
        System.gc();
        long mb = rt.totalMemory() - rt.freeMemory();
        long t0 = System.currentTimeMillis();
        List<Set<Integer>> maximal =
                new UHMine(db, MIN_SUP).mineMaximal();
        long elapsed = System.currentTimeMillis() - t0;
        double mem = (rt.totalMemory() - rt.freeMemory() - mb) / 1048576.0;
        List<Set<Integer>> gained=new ArrayList<>(),
                lost=new ArrayList<>(), unch=new ArrayList<>();
        if (prev != null) {
            for (Set<Integer> s : maximal)
                (containsSet(prev,s) ? unch : gained).add(s);
            for (Set<Integer> s : prev)
                if (!containsSet(maximal,s)) lost.add(s);
        }
        return new DynamicResult(label,batchFile,txnBefore,
                db.size(),maximal,gained,lost,unch,elapsed,mem);
    }

    private static class StaticResult {
        String dataFile; int transactions;
        List<Set<Integer>> maximal; long runtimeMs; double memMB;
        StaticResult(String f,int t,List<Set<Integer>> m,long r,double mem){
            dataFile=f;transactions=t;maximal=m;runtimeMs=r;memMB=mem;}
    }
    private static class DynamicResult {
        String label,batchFile; int txnBefore,txnAfter;
        List<Set<Integer>> maximal,gained,lost,unchanged;
        long runtimeMs; double memMB;
        DynamicResult(String lb,String bf,int tb,int ta,
                List<Set<Integer>> m,List<Set<Integer>> g,
                List<Set<Integer>> l,List<Set<Integer>> u,
                long r,double mem){
            label=lb;batchFile=bf;txnBefore=tb;txnAfter=ta;
            maximal=m;gained=g;lost=l;unchanged=u;runtimeMs=r;memMB=mem;}
    }
    private static boolean containsSet(List<Set<Integer>> list,Set<Integer> t){
        for(Set<Integer> s:list) if(s.equals(t)) return true; return false;}
    private static String timestamp(){
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());}
}