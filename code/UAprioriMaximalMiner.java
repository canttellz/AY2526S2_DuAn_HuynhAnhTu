import java.io.*;
import java.util.*;

public class UAprioriMaximalMiner {

    static double minSup = 0.05;

    public static void main(String[] args) throws Exception {

        long start = System.currentTimeMillis();

        String file = "E:\\Project\\output\\retail_uncertain_5000.txt";

        List<Map<Integer, Double>> database = loadDatabase(file);

        int transactionCount = database.size();

        List<Set<Integer>> allFrequent = new ArrayList<>();

        List<Set<Integer>> Lk = findFrequent1(database, transactionCount);

        allFrequent.addAll(Lk);

        int k = 2;

        while (!Lk.isEmpty()) {

            List<Set<Integer>> Ck = generateCandidates(Lk, k);

            Map<Set<Integer>, Double> supportMap =
                    computeSupport(database, Ck, transactionCount);

            Lk = new ArrayList<>();

            for (Set<Integer> candidate : supportMap.keySet()) {

                double support = supportMap.get(candidate);

                if (support >= minSup) {

                    Lk.add(candidate);

                    allFrequent.add(candidate);

                    System.out.println(candidate + " -> " + support);
                }
            }

            k++;
        }

        List<Set<Integer>> maximal = findMaximal(allFrequent);

        System.out.println("\nMaximal Frequent Itemsets:");

        for (Set<Integer> set : maximal) {

            System.out.println(set);
        }

        long end = System.currentTimeMillis();

        System.out.println("\nRuntime: " + (end - start) + " ms");
    }

    static List<Map<Integer, Double>> loadDatabase(String file) throws Exception {

        List<Map<Integer, Double>> database = new ArrayList<>();

        BufferedReader br = new BufferedReader(new FileReader(file));

        String line;

        while ((line = br.readLine()) != null) {

            Map<Integer, Double> transaction = new HashMap<>();

            String[] tokens = line.trim().split(" ");

            for (String token : tokens) {

                String[] pair = token.split(":");

                int item = Integer.parseInt(pair[0]);

                double prob = Double.parseDouble(pair[1]);

                transaction.put(item, prob);
            }

            database.add(transaction);
        }

        br.close();

        return database;
    }

    static List<Set<Integer>> findFrequent1(List<Map<Integer, Double>> db, int n) {

        Map<Integer, Double> support = new HashMap<>();

        for (Map<Integer, Double> t : db) {

            for (int item : t.keySet()) {

                support.put(item,
                        support.getOrDefault(item, 0.0) + t.get(item));
            }
        }

        List<Set<Integer>> L1 = new ArrayList<>();

        for (int item : support.keySet()) {

            double sup = support.get(item) / n;

            if (sup >= minSup) {

                Set<Integer> set = new HashSet<>();

                set.add(item);

                L1.add(set);

                System.out.println(set + " -> " + sup);
            }
        }

        return L1;
    }

    static List<Set<Integer>> generateCandidates(List<Set<Integer>> Lk, int k) {

        List<Set<Integer>> candidates = new ArrayList<>();

        for (int i = 0; i < Lk.size(); i++) {

            for (int j = i + 1; j < Lk.size(); j++) {

                Set<Integer> a = new HashSet<>(Lk.get(i));

                Set<Integer> b = new HashSet<>(Lk.get(j));

                Set<Integer> union = new HashSet<>(a);

                union.addAll(b);

                if (union.size() == k && !candidates.contains(union)) {

                    candidates.add(union);
                }
            }
        }

        return candidates;
    }

    static Map<Set<Integer>, Double> computeSupport(
            List<Map<Integer, Double>> db,
            List<Set<Integer>> candidates,
            int n) {

        Map<Set<Integer>, Double> supportMap = new HashMap<>();

        for (Set<Integer> candidate : candidates) {

            double sum = 0;

            for (Map<Integer, Double> t : db) {

                boolean contains = true;

                double prob = 1.0;

                for (int item : candidate) {

                    if (!t.containsKey(item)) {

                        contains = false;

                        break;
                    }

                    prob *= t.get(item);
                }

                if (contains) {

                    sum += prob;
                }
            }

            double support = sum / n;

            supportMap.put(candidate, support);
        }

        return supportMap;
    }

    static List<Set<Integer>> findMaximal(List<Set<Integer>> frequentSets) {

        List<Set<Integer>> maximal = new ArrayList<>();

        for (int i = 0; i < frequentSets.size(); i++) {

            Set<Integer> A = frequentSets.get(i);

            boolean isSubset = false;

            for (int j = 0; j < frequentSets.size(); j++) {

                if (i == j) continue;

                Set<Integer> B = frequentSets.get(j);

                if (B.size() > A.size() && B.containsAll(A)) {

                    isSubset = true;

                    break;
                }
            }

            if (!isSubset) {

                maximal.add(A);
            }
        }

        return maximal;
    }
}