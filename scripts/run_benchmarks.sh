#!/usr/bin/env bash
# Runs every performance experiment in its own JVM, then draws the charts into results/.
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p out results
javac -encoding UTF-8 -d out src/*.java test/*.java

for experiment in construction regex size distribution; do
    java -Xmx2g -cp out Benchmark testbeds results "$experiment"
done | tee results/benchmark_output.txt

python scripts/plot_results.py results
