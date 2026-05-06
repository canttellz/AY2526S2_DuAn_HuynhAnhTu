@echo off
echo ============================================================
echo  FULL BENCHMARK RUN — ALL DATASETS
echo ============================================================

cd E:\MaximumFrequentItemsets\MaximumFrequentItemsets\src

REM ── Clean previous benchmark ────────────────────────────────
del benchmark.csv 2>nul
del db_5000.state 2>nul
del db_5000.state.results 2>nul
del db_6000.state 2>nul
del db_6000.state.results 2>nul

echo.
echo ============================================================
echo  SECTION 1: Static runs — each dataset individually
echo ============================================================

REM ── 1000 transactions ───────────────────────────────────────
echo [1/3] Running on 1000 transactions...
java main.MaximalFrequentItemsets uapriori ..\data\retail_uncertain_1000.txt  --minsup 0.05 --benchmark benchmark.csv
java main.MaximalFrequentItemsets uhmine   ..\data\retail_uncertain_1000.txt  --minsup 0.05 --benchmark benchmark.csv
java main.MaximalFrequentItemsets aco      ..\data\retail_uncertain_1000.txt  --minsup 0.05 --benchmark benchmark.csv

REM ── 5000 transactions ───────────────────────────────────────
echo [2/3] Running on 5000 transactions...
java main.MaximalFrequentItemsets uapriori ..\data\retail_uncertain_5000.txt  --minsup 0.05 --benchmark benchmark.csv
java main.MaximalFrequentItemsets uhmine   ..\data\retail_uncertain_5000.txt  --minsup 0.05 --benchmark benchmark.csv
java main.MaximalFrequentItemsets aco      ..\data\retail_uncertain_5000.txt  --minsup 0.05 --benchmark benchmark.csv

REM ── 10000 transactions ──────────────────────────────────────
echo [3/3] Running on 10000 transactions...
java main.MaximalFrequentItemsets uapriori ..\data\retail_uncertain_10000.txt --minsup 0.05 --benchmark benchmark.csv
java main.MaximalFrequentItemsets uhmine   ..\data\retail_uncertain_10000.txt --minsup 0.05 --benchmark benchmark.csv
java main.MaximalFrequentItemsets aco      ..\data\retail_uncertain_10000.txt --minsup 0.05 --benchmark benchmark.csv

REM ── 86000+ transactions (full dataset) ─────────────────────
echo [4/4] Running on full dataset (86000+ transactions)...
echo NOTE: U-Apriori may be slow — be patient
java main.MaximalFrequentItemsets uapriori ..\data\retail_uncertain.txt       --minsup 0.05 --benchmark benchmark.csv
java main.MaximalFrequentItemsets uhmine   ..\data\retail_uncertain.txt       --minsup 0.05 --benchmark benchmark.csv
java main.MaximalFrequentItemsets aco      ..\data\retail_uncertain.txt       --minsup 0.05 --benchmark benchmark.csv

echo.
echo ============================================================
echo  SECTION 2: Dynamic accumulation experiment
echo  Time 1 (5000) -> Time 2 (+1000=6000) -> Time 3 (+10000=16000)
echo ============================================================

REM ── Time 1 — initial 5000 ───────────────────────────────────
echo [Time 1] Mining initial 5000 transactions...
java main.MaximalFrequentItemsets uapriori ..\data\retail_uncertain_5000.txt --minsup 0.05 --save-state db_5000.state --benchmark benchmark.csv
java main.MaximalFrequentItemsets uhmine   ..\data\retail_uncertain_5000.txt --minsup 0.05 --save-state db_5000.state --benchmark benchmark.csv
java main.MaximalFrequentItemsets aco      ..\data\retail_uncertain_5000.txt --minsup 0.05 --save-state db_5000.state --benchmark benchmark.csv

REM ── Time 2 — append 1000 (total 6000) ───────────────────────
echo [Time 2] Appending 1000 transactions (total: 6000)...
java main.MaximalFrequentItemsets uapriori --load-state db_5000.state --add ..\data\retail_uncertain_1000.txt  --minsup 0.05 --save-state db_6000.state --benchmark benchmark.csv
java main.MaximalFrequentItemsets uhmine   --load-state db_5000.state --add ..\data\retail_uncertain_1000.txt  --minsup 0.05 --save-state db_6000.state --benchmark benchmark.csv
java main.MaximalFrequentItemsets aco      --load-state db_5000.state --add ..\data\retail_uncertain_1000.txt  --minsup 0.05 --save-state db_6000.state --benchmark benchmark.csv

REM ── Time 3 — append 10000 (total 16000) ─────────────────────
echo [Time 3] Appending 10000 transactions (total: 16000)...
java main.MaximalFrequentItemsets uapriori --load-state db_6000.state --add ..\data\retail_uncertain_10000.txt --minsup 0.05 --save-state db_16000.state --benchmark benchmark.csv
java main.MaximalFrequentItemsets uhmine   --load-state db_6000.state --add ..\data\retail_uncertain_10000.txt --minsup 0.05 --save-state db_16000.state --benchmark benchmark.csv
java main.MaximalFrequentItemsets aco      --load-state db_6000.state --add ..\data\retail_uncertain_10000.txt --minsup 0.05 --save-state db_16000.state --benchmark benchmark.csv

echo.
echo ============================================================
echo  SECTION 3: ACO coverage analysis
echo  (varies ants and iterations on 5000 dataset)
echo ============================================================

java main.MaximalFrequentItemsets aco ..\data\retail_uncertain_5000.txt --minsup 0.05 --ants 5   --iterations 10  --benchmark benchmark.csv
java main.MaximalFrequentItemsets aco ..\data\retail_uncertain_5000.txt --minsup 0.05 --ants 20  --iterations 50  --benchmark benchmark.csv
java main.MaximalFrequentItemsets aco ..\data\retail_uncertain_5000.txt --minsup 0.05 --ants 50  --iterations 100 --benchmark benchmark.csv
java main.MaximalFrequentItemsets aco ..\data\retail_uncertain_5000.txt --minsup 0.05 --ants 100 --iterations 200 --benchmark benchmark.csv

echo.
echo ============================================================
echo  SECTION 4: Different minSup values on full dataset
echo  (shows how threshold affects number of maximal itemsets)
echo ============================================================


java main.MaximalFrequentItemsets uapriori ..\data\retail_uncertain.txt --minsup 0.01 --benchmark benchmark.csv
java main.MaximalFrequentItemsets uapriori ..\data\retail_uncertain.txt --minsup 0.05 --benchmark benchmark.csv
java main.MaximalFrequentItemsets uapriori ..\data\retail_uncertain.txt --minsup 0.10 --benchmark benchmark.csv
java main.MaximalFrequentItemsets uapriori ..\data\retail_uncertain.txt --minsup 0.20 --benchmark benchmark.csv

java main.MaximalFrequentItemsets uhmine   ..\data\retail_uncertain.txt --minsup 0.01 --benchmark benchmark.csv
java main.MaximalFrequentItemsets uhmine   ..\data\retail_uncertain.txt --minsup 0.05 --benchmark benchmark.csv
java main.MaximalFrequentItemsets uhmine   ..\data\retail_uncertain.txt --minsup 0.10 --benchmark benchmark.csv
java main.MaximalFrequentItemsets uhmine   ..\data\retail_uncertain.txt --minsup 0.20 --benchmark benchmark.csv

java main.MaximalFrequentItemsets aco      ..\data\retail_uncertain.txt --minsup 0.01 --benchmark benchmark.csv
java main.MaximalFrequentItemsets aco      ..\data\retail_uncertain.txt --minsup 0.05 --benchmark benchmark.csv
java main.MaximalFrequentItemsets aco      ..\data\retail_uncertain.txt --minsup 0.10 --benchmark benchmark.csv
java main.MaximalFrequentItemsets aco      ..\data\retail_uncertain.txt --minsup 0.20 --benchmaretail_rk benchm

echo.
echo ============================================================
echo  ALL RUNS COMPLETE — printing benchmark summary
echo ============================================================
java main.MaximalFrequentItemsets --benchmark-summary benchmark.csv

echo.
echo Done! Results saved to benchmark.csv
pause
