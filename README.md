# DAAR TP1 — Clone of `egrep`

Java clone of `egrep` supporting letters, `.`, concatenation, `|`, `*` and parentheses.

## Requirements

- Java 11 or later
- For the tests only: bash (Git Bash on Windows), `curl`, `grep`, Python 3 with `matplotlib`

## Compile

From the root of the project:

```bash
javac -encoding UTF-8 -d out src/*.java
```

## Run

```bash
java -cp out Egrep "<regex>" <file>
```

Put the regex between quotes. Example:

```bash
java -cp out Egrep "S(a|g|r)*on" testbeds/babylon.txt
```

Exit code: `0` if a line matched, `1` if none, `2` on error.

## Tests

Download the test files (books from Project Gutenberg) into `testbeds/`:

```bash
bash scripts/download_testbeds.sh
```

Correctness tests:

```bash
javac -encoding UTF-8 -d out src/*.java test/*.java
java -cp out CorrectnessTest
```

Comparison with the real `grep -E`:

```bash
bash scripts/compare_with_egrep.sh
```

Performance tests (charts and CSV files written into `results/`):

```bash
bash scripts/run_benchmarks.sh
```
