# Object-Oriented Solutions to Mining Maximal Frequent Itemsets from Accumulated Dynamic Uncertain Databases

**Ton Duc Thang University — Faculty of Information Technology — 2025**

---

## Quick Start (Reproduce Results)

```bash
# 1. Clone
git clone https://github.com/[YOUR_USERNAME]/[REPO_NAME].git
cd [REPO_NAME]

# 2. Compile
cd src
javac algorithm/*.java data/*.java output/*.java experiment/*.java main/*.java

# 3. Run experiments — results written to output/ automatically
java experiment.UAprioriExperiment
java experiment.UHMineExperiment
java experiment.ACOMinerExperiment
```

---

## Project Structure

```
project/
├── src/
│   ├── algorithm/              ← Algorithm classes (NO main — pure logic)
│   │   ├── Algorithm.java      ← Abstract base class
│   │   ├── UApriori.java       ← U-Apriori: exact, BFS
│   │   ├── UHMine.java         ← UH-Mine: exact, DFS
│   │   ├── ACOMiner.java       ← ACO-Miner: approximate, swarm
│   │   └── AlgorithmFactory.java
│   ├── experiment/             ← Experiment classes (each has main)
│   │   ├── UAprioriExperiment.java   ← static + dynamic, writes to files
│   │   ├── UHMineExperiment.java     ← static + dynamic, writes to files
│   │   └── ACOMinerExperiment.java   ← static + dynamic + coverage analysis
│   ├── data/
│   │   ├── Database.java
│   │   └── Transaction.java
│   ├── output/
│   │   ├── ResultWriter.java
│   │   ├── ResultComparator.java
│   │   └── BenchmarkLogger.java
│   └── main/
│       └── MaximalFrequentItemsets.java  ← full CLI entry point
├── data/
│   ├── retail_uncertain_1000.txt
│   ├── retail_uncertain_5000.txt
│   ├── retail_uncertain_10000.txt
│   └── retail_uncertain.txt        ← 88,162 transactions
├── run_all.bat                     ← full benchmark test (static + dynamic)
└── README.md
```

---

## Output Files (auto-created in output/)

| File | Contents |
|------|----------|
| `uapriori_static_results.txt`     | Maximal itemsets per dataset |
| `uapriori_static_parameters.txt`  | Runtime (ms), memory (MB) per dataset |
| `uapriori_dynamic_results.txt`    | Maximal itemsets + gained/lost per time step |
| `uapriori_dynamic_parameters.txt` | Runtime, memory per time step |
| `uhmine_static_results.txt`       | Same as above for UH-Mine |
| `uhmine_static_parameters.txt`    | |
| `uhmine_dynamic_results.txt`      | |
| `uhmine_dynamic_parameters.txt`   | |
| `aco_static_results.txt`          | ACO results + found/MISSED vs exact + coverage table |
| `aco_static_parameters.txt`       | Runtime, memory, coverage% per dataset |
| `aco_dynamic_results.txt`         | ACO results + exact comparison + gained/lost |
| `aco_dynamic_parameters.txt`      | Runtime, memory, coverage% per time step |

---

## Dataset

Derived from the UCI Online Retail dataset with simulated existential probabilities
(uniform distribution in (0.5, 1.0]).

**Original source:** https://archive.ics.uci.edu/dataset/352/online+retail

**Format** — `item:probability` space-separated, one transaction per line:
```
40:0.9 49:0.8 42:0.7
33:0.6 39:0.5 40:0.9
```

| File | Transactions |
|------|-------------|
| `retail_uncertain_1000.txt`  | 1,000  |
| `retail_uncertain_5000.txt`  | 5,000  |
| `retail_uncertain_10000.txt` | 10,000 |
| `retail_uncertain.txt`       | 88,162 |

---

## Algorithms

| Algorithm | Type | Strategy | Accuracy | Reference |
|-----------|------|----------|----------|-----------|
| U-Apriori | Exact | BFS level-wise candidate generation | 100% | Chui et al. (2007) |
| UH-Mine | Exact | DFS hyperlinked projected lists | 100% (= U-Apriori) | Leung et al. (2007) |
| ACO-Miner | Approximate | Ant colony pheromone-guided search | Configurable | Malipatil & Reddy (2023) |

---

## Way 1 — Experiment Classes (Recommended for Reproducing Results)

Each algorithm has its own experiment class with a `main` method.
It reads data automatically and writes all results to files.

```bash
cd src

# U-Apriori — static on all datasets + dynamic accumulation (Time 1->2->3)
java experiment.UAprioriExperiment

# UH-Mine — static on all datasets + dynamic accumulation
java experiment.UHMineExperiment

# ACO-Miner — static + dynamic + coverage analysis (4 parameter configs)
java experiment.ACOMinerExperiment
```

**To change dataset or settings**, edit the constants at the top of each file:
```java
private static final String DATA_5000 = "../data/retail_uncertain_5000.txt";
private static final double MIN_SUP   = 0.05;

// ACO-Miner only:
private static final int    ANTS        = 20;
private static final int    ITERATIONS  = 50;
private static final double EVAPORATION = 0.3;
private static final double ALPHA       = 1.0;
private static final double BETA        = 2.0;
```

---

## Way 2 — Full CLI via main (Flexible Use)

The main entry point supports all options for flexible, interactive use.

### Basic static run
```bash
java main.MaximalFrequentItemsets <algorithm> <datafile> [options]

# Examples:
java main.MaximalFrequentItemsets uapriori ../data/retail_uncertain_5000.txt --minsup 0.05
java main.MaximalFrequentItemsets uhmine   ../data/retail_uncertain_5000.txt --minsup 0.05
java main.MaximalFrequentItemsets aco      ../data/retail_uncertain_5000.txt --minsup 0.05
```

### Dynamic accumulated database
```bash
# Time 1 — mine initial batch, save state
java main.MaximalFrequentItemsets uapriori ../data/retail_uncertain_5000.txt \
    --minsup 0.05 --save-state db.state

# Time 2 — load state, append new batch, re-mine
java main.MaximalFrequentItemsets uapriori --load-state db.state \
    --add ../data/retail_uncertain_1000.txt \
    --minsup 0.05 --save-state db.state

# Time 3 — append another batch
java main.MaximalFrequentItemsets uapriori --load-state db.state \
    --add ../data/retail_uncertain_10000.txt \
    --minsup 0.05 --save-state db.state
```

### Benchmark logging
```bash
# Log runtime to CSV
java main.MaximalFrequentItemsets uapriori ../data/retail_uncertain_5000.txt \
    --minsup 0.05 --benchmark benchmark.csv

# Print benchmark summary table
java main.MaximalFrequentItemsets --benchmark-summary benchmark.csv
```

### All CLI options
```
General:
  --minsup <value>        Minimum support threshold (default: 0.05)
  --add <file>            Append a batch file (repeatable)
  --append                Append transactions interactively from stdin
  --save-state <file>     Save accumulated database state for next run
  --load-state <file>     Load previously saved database state
  --output <file>         Write results to file instead of console
  --benchmark <file>      Log runtime to CSV benchmark file
  --benchmark-summary     Print summary table from benchmark CSV

ACO only:
  --ants <n>              Number of ants per iteration (default: 20)
  --iterations <n>        Number of ACO iterations    (default: 50)
  --evaporation <v>       Pheromone evaporation rate  (default: 0.3)
  --alpha <v>             Pheromone influence weight  (default: 1.0)
  --beta <v>              Support heuristic weight    (default: 2.0)
```

---

## Way 3 — Full Benchmark Test via run_all.bat (Windows)

For a complete automated test covering static runs on all datasets,
dynamic accumulation (Time 1 → 2 → 3), ACO coverage analysis,
and minSup sensitivity analysis — run the provided batch file.

```bat
cd src
run_all.bat
```

Or to save all output to a file:
```bat
cd src
.\run_all.bat *>> full_output.txt
```

The batch file runs all algorithms on all datasets in sequence and
prints a full benchmark summary at the end. Results are also saved
to `benchmark.csv` which can be opened in Excel.

**What run_all.bat covers:**
| Section | What it runs |
|---------|-------------|
| Section 1 | All 3 algorithms × all 4 datasets (static) |
| Section 2 | Dynamic accumulation: Time 1 (5k) → Time 2 (+1k) → Time 3 (+10k) |
| Section 3 | ACO coverage: 4 parameter configs on 5k dataset |
| Section 4 | All 3 algorithms × 4 minSup values on full 88k dataset |

---

## Requirements

- Java SE 17 or later
- No external libraries required

---

## References

1. Chui, C.-K., Kao, B., & Hung, E. (2007). Mining frequent itemsets from uncertain data. *PAKDD*, 47–58.
2. Leung, C. K. S., Carmichael, C. L., & Hao, B. (2007). Efficient mining of frequent patterns from uncertain data. *ICDMW*, 489–494.
3. Malipatil, S., & Hanumantha Reddy, T. (2023). Discovery of interesting frequent item sets in an uncertain database using ant colony optimization. *International Journal of Computers and Applications*, 45(11), 673–679.
4. UCI Machine Learning Repository. Online Retail Dataset. https://archive.ics.uci.edu/dataset/352/online+retail
