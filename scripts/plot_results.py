"""Draws the charts of the performance tests from the CSV files written by Benchmark.java.

Usage: python scripts/plot_results.py [results directory]
"""
import csv
import statistics
import sys
from collections import defaultdict
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

RESULTS = Path(sys.argv[1] if len(sys.argv) > 1 else "results")
COLORS = {"DFA": "#2a6fdb", "java.util.regex": "#e07b39"}


def read(name):
    with open(RESULTS / name, newline="") as f:
        return list(csv.DictReader(f))


def error_bars(means, stds):
    """mean ± std, without letting the bar go below 0 (a time cannot be negative)."""
    return [[min(m, s) for m, s in zip(means, stds)], stds]


def construction():
    rows = read("construction.csv")
    regexes = [r["regex"] for r in rows]
    x = range(len(regexes))
    width = 0.27

    fig, (top, bottom) = plt.subplots(2, 1, figsize=(11, 8), sharex=True)
    for i, (column, label, color) in enumerate([
        ("nfa_states", "epsilon-NFA", "#9aa5b1"),
        ("dfa_states", "DFA", "#2a6fdb"),
        ("min_dfa_states", "minimal DFA", "#2e9e6b"),
    ]):
        top.bar([p + (i - 1) * width for p in x], [int(r[column]) for r in rows], width, label=label, color=color)
    top.set_ylabel("number of states")
    top.set_title("Size of the automata at each step")
    top.legend()
    top.grid(axis="y", alpha=0.3)

    means = [float(r["build_mean_us"]) for r in rows]
    stds = [float(r["build_std_us"]) for r in rows]
    bottom.bar(x, means, yerr=error_bars(means, stds), capsize=4, color="#2a6fdb")
    bottom.set_ylabel("build time (µs), mean ± std")
    bottom.set_title("Time to build the minimal DFA (parse + NFA + DFA + minimization)")
    bottom.set_xticks(list(x))
    bottom.set_xticklabels(regexes, rotation=30, ha="right")
    bottom.grid(axis="y", alpha=0.3)
    fig.tight_layout()
    fig.savefig(RESULTS / "construction.png", dpi=150)


def search_by_regex():
    rows = read("search_by_regex.csv")
    regexes = list(dict.fromkeys(r["regex"] for r in rows))
    x = range(len(regexes))
    width = 0.4

    fig, ax = plt.subplots(figsize=(11, 5.5))
    for i, engine in enumerate(COLORS):
        data = {r["regex"]: r for r in rows if r["engine"] == engine}
        means = [float(data[g]["mean_ms"]) for g in regexes]
        stds = [float(data[g]["std_ms"]) for g in regexes]
        ax.bar([p + (i - 0.5) * width for p in x], means, width, yerr=error_bars(means, stds),
               capsize=4, label=engine, color=COLORS[engine])
    ax.set_xticks(list(x))
    ax.set_xticklabels(regexes, rotation=30, ha="right")
    ax.set_ylabel("search time (ms), mean ± std")
    ax.set_title("Search in babylon.txt (13 302 lines): our minimal DFA vs java.util.regex")
    ax.legend()
    ax.grid(axis="y", alpha=0.3)
    fig.tight_layout()
    fig.savefig(RESULTS / "search_by_regex.png", dpi=150)


def search_vs_size():
    rows = read("search_vs_size.csv")
    series = defaultdict(list)
    for r in rows:
        series[(r["regex"], r["engine"])].append(
            (int(r["chars"]) / 1e6, float(r["mean_ms"]), float(r["std_ms"])))
    regexes = list(dict.fromkeys(r["regex"] for r in rows))

    fig, axes = plt.subplots(1, 2, figsize=(13, 5), sharey=True)
    for ax, engine in zip(axes, COLORS):
        for regex in regexes:
            points = sorted(series[(regex, engine)])
            ax.errorbar([p[0] for p in points], [p[1] for p in points], yerr=[p[2] for p in points],
                        marker="o", markersize=4, capsize=3, label=regex)
        ax.set_title(engine)
        ax.set_xlabel("text size (millions of characters)")
        ax.grid(alpha=0.3)
        ax.legend()
    axes[0].set_ylabel("search time (ms), mean ± std")
    fig.suptitle("Search time against text size (shuffled lines of the 7 testbeds)")
    fig.tight_layout()
    fig.savefig(RESULTS / "search_vs_size.png", dpi=150)


def time_distribution():
    rows = read("time_distribution.csv")
    fig, axes = plt.subplots(1, 2, figsize=(12, 4.5))
    for ax, engine in zip(axes, COLORS):
        times = sorted(float(r["ms"]) for r in rows if r["engine"] == engine)
        # Zoom on the bulk of the runs: a few rare outliers (GC pauses) would squash the histogram
        cut = times[int(len(times) * 0.99) - 1]
        shown = [t for t in times if t <= cut]
        ax.hist(shown, bins=30, color=COLORS[engine])
        mean = statistics.mean(times)
        ax.axvline(mean, color="black", linestyle="--", label=f"mean {mean:.2f} ms")
        ax.set_title(f"{engine}: {len(times)} runs, std {statistics.stdev(times):.2f} ms\n"
                     f"({len(times) - len(shown)} outliers above {cut:.2f} ms not shown, max {times[-1]:.2f} ms)",
                     fontsize=10)
        ax.set_xlabel("search time (ms)")
        ax.set_ylabel("number of runs")
        ax.legend()
        ax.grid(axis="y", alpha=0.3)
    fig.suptitle('Distribution of the search time of "S(a|g|r)*on" on babylon.txt')
    fig.tight_layout()
    fig.savefig(RESULTS / "time_distribution.png", dpi=150)


if __name__ == "__main__":
    construction()
    search_by_regex()
    search_vs_size()
    time_distribution()
    print("Charts written to", RESULTS)
