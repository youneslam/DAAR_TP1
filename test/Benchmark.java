import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.IntSupplier;

/**
 * Performance tests. Writes CSV files into results/, drawn by scripts/plot_results.py.
 *
 * 1. construction.csv      : automaton sizes (NFA, DFA, minimal DFA) and build time per regex
 * 2. search_by_regex.csv   : search time on babylon.txt per regex, our DFA vs java.util.regex
 * 3. search_vs_size.csv    : search time against text size (prefixes of all testbeds put together,
 *                            lines shuffled so that every prefix has the same kind of content)
 * 4. time_distribution.csv : every measured time of many runs of one search (frequency diagram)
 *
 * Texts are loaded in memory first: only the search is timed, not the file reading.
 * Each measure is repeated after a JIT warm-up; we keep the mean and the standard deviation.
 * scripts/run_benchmarks.sh runs each experiment in its own JVM so they do not influence each other.
 *
 * Usage: java -cp out Benchmark [testbeds directory] [results directory] [experiment]
 *        experiment = construction | regex | size | distribution | all (default)
 */
public class Benchmark {

    static final String[] REGEXES = {
            "Sargon", "S(a|g|r)*on", "(B|b)abylon|Assyria", "th(e|is|at)", "h.s",
            "a(b|c|d)*e", "q.*z", "((a|e|i|o|u)(n|r|s|t))*ing", "Ch(a|e)p.*(I|V|X)",
    };
    static final String[] SIZE_REGEXES = {"Sargon", "S(a|g|r)*on", "h.s", "q.*z"};
    static final String DISTRIBUTION_REGEX = "S(a|g|r)*on";

    static final int WARMUP = 20;
    static final int BATCH = 100;

    private static long sink; // results are accumulated here so that the JIT cannot skip the work

    public static void main(String[] args) throws IOException {
        String testbeds = args.length > 0 ? args[0] : "testbeds";
        String results = args.length > 1 ? args[1] : "results";
        String experiment = args.length > 2 ? args[2] : "all";
        boolean all = experiment.equals("all");
        new File(results).mkdirs();

        List<String> babylon = CorrectnessTest.readLines(testbeds + "/babylon.txt");

        if (all || experiment.equals("construction")) construction(results + "/construction.csv", 50);
        if (all || experiment.equals("regex")) searchByRegex(results + "/search_by_regex.csv", babylon, 30);
        if (all || experiment.equals("size")) searchVsSize(results + "/search_vs_size.csv", loadCorpus(testbeds), 10, 10);
        if (all || experiment.equals("distribution")) timeDistribution(results + "/time_distribution.csv", babylon, 300);
        System.out.println("(sink " + sink + ")");
    }

    /** All testbeds put together, lines shuffled with a fixed seed (same order at every run). */
    static List<String> loadCorpus(String testbeds) throws IOException {
        List<String> corpus = new ArrayList<>();
        File[] files = new File(testbeds).listFiles((d, name) -> name.endsWith(".txt"));
        Arrays.sort(files);
        for (File f : files) corpus.addAll(CorrectnessTest.readLines(f.getPath()));
        Collections.shuffle(corpus, new Random(42));
        return corpus;
    }

    // ---- 1. Construction of the automata ----

    static void construction(String csv, int reps) throws IOException {
        System.out.println("== Construction (" + reps + " batches of " + BATCH + " builds per regex) ==");
        // Building takes only microseconds: warm the JIT up on every regex before measuring anything
        for (int i = 0; i < 2000; i++) {
            for (String regex : REGEXES) sink += new Matcher(regex).getDfa().size();
        }
        try (PrintWriter out = new PrintWriter(csv)) {
            out.println("regex,nfa_states,dfa_states,min_dfa_states,build_mean_us,build_std_us");
            for (String regex : REGEXES) {
                NFA nfa = new NFA(new RegExParser(regex).parse());
                DFA dfa = new DFA(nfa);
                DFA min = DFAMinimizer.minimize(dfa);
                // One build is too short to be timed alone: each measure is a batch of BATCH builds
                double[] ms = time(() -> {
                    int total = 0;
                    for (int i = 0; i < BATCH; i++) total += new Matcher(regex).getDfa().size();
                    return total;
                }, reps);
                double[] us = Arrays.stream(ms).map(t -> t * 1000 / BATCH).toArray();
                out.printf(Locale.ROOT, "\"%s\",%d,%d,%d,%.3f,%.3f%n",
                        regex, nfa.size(), dfa.size(), min.size(), mean(us), std(us));
                System.out.printf(Locale.ROOT, "%-30s NFA %3d  DFA %3d  min %3d   build %8.2f us +- %.2f%n",
                        regex, nfa.size(), dfa.size(), min.size(), mean(us), std(us));
            }
        }
    }

    // ---- 2. Search time per regex: our DFA vs java.util.regex ----

    static void searchByRegex(String csv, List<String> lines, int reps) throws IOException {
        System.out.println("\n== Search on babylon.txt, " + lines.size() + " lines (" + reps + " runs) ==");
        // Warm both engines up on every regex first, so that the first regex measured is not penalized
        for (String regex : REGEXES) {
            Matcher ours = new Matcher(regex);
            java.util.regex.Matcher java = CorrectnessTest.toJavaPattern(regex).matcher("");
            for (int i = 0; i < WARMUP; i++) sink += countOurs(ours, lines) + countJava(java, lines);
        }
        try (PrintWriter out = new PrintWriter(csv)) {
            out.println("regex,engine,matching_lines,mean_ms,std_ms");
            for (String regex : REGEXES) {
                Matcher ours = new Matcher(regex);
                java.util.regex.Matcher java = CorrectnessTest.toJavaPattern(regex).matcher("");
                double[] a = time(() -> countOurs(ours, lines), reps);
                double[] b = time(() -> countJava(java, lines), reps);
                out.printf(Locale.ROOT, "\"%s\",DFA,%d,%.4f,%.4f%n", regex, countOurs(ours, lines), mean(a), std(a));
                out.printf(Locale.ROOT, "\"%s\",java.util.regex,%d,%.4f,%.4f%n", regex, countJava(java, lines), mean(b), std(b));
                System.out.printf(Locale.ROOT, "%-30s DFA %7.2f ms +- %5.2f   java.util.regex %7.2f ms +- %5.2f%n",
                        regex, mean(a), std(a), mean(b), std(b));
            }
        }
    }

    // ---- 3. Search time against text size ----

    static void searchVsSize(String csv, List<String> corpus, int steps, int reps) throws IOException {
        System.out.println("\n== Search time vs text size (" + steps + " sizes, " + reps + " runs) ==");
        try (PrintWriter out = new PrintWriter(csv)) {
            out.println("regex,engine,lines,chars,mean_ms,std_ms");
            for (int s = 1; s <= steps; s++) {
                List<String> prefix = corpus.subList(0, corpus.size() * s / steps);
                long chars = 0;
                for (String line : prefix) chars += line.length() + 1;
                for (String regex : SIZE_REGEXES) {
                    Matcher ours = new Matcher(regex);
                    java.util.regex.Matcher java = CorrectnessTest.toJavaPattern(regex).matcher("");
                    double[] a = time(() -> countOurs(ours, prefix), reps);
                    double[] b = time(() -> countJava(java, prefix), reps);
                    out.printf(Locale.ROOT, "\"%s\",DFA,%d,%d,%.4f,%.4f%n", regex, prefix.size(), chars, mean(a), std(a));
                    out.printf(Locale.ROOT, "\"%s\",java.util.regex,%d,%d,%.4f,%.4f%n", regex, prefix.size(), chars, mean(b), std(b));
                }
                System.out.printf(Locale.ROOT, "size %2d/%d: %8d lines, %9d chars%n", s, steps, prefix.size(), chars);
            }
        }
    }

    // ---- 4. Distribution of the measured times (frequency diagram) ----

    static void timeDistribution(String csv, List<String> lines, int reps) throws IOException {
        System.out.println("\n== Distribution of " + reps + " runs of \"" + DISTRIBUTION_REGEX + "\" on babylon.txt ==");
        Matcher ours = new Matcher(DISTRIBUTION_REGEX);
        java.util.regex.Matcher java = CorrectnessTest.toJavaPattern(DISTRIBUTION_REGEX).matcher("");
        double[] a = time(() -> countOurs(ours, lines), reps);
        double[] b = time(() -> countJava(java, lines), reps);
        try (PrintWriter out = new PrintWriter(csv)) {
            out.println("run,engine,ms");
            for (int r = 0; r < reps; r++) out.printf(Locale.ROOT, "%d,DFA,%.4f%n", r, a[r]);
            for (int r = 0; r < reps; r++) out.printf(Locale.ROOT, "%d,java.util.regex,%.4f%n", r, b[r]);
        }
        System.out.printf(Locale.ROOT, "DFA %.2f ms +- %.2f   java.util.regex %.2f ms +- %.2f%n", mean(a), std(a), mean(b), std(b));
    }

    // ---- Helpers ----

    static int countOurs(Matcher m, List<String> lines) {
        int count = 0;
        for (String line : lines) if (m.find(line)) count++;
        return count;
    }

    static int countJava(java.util.regex.Matcher m, List<String> lines) {
        int count = 0;
        for (String line : lines) if (m.reset(line).find()) count++;
        return count;
    }

    /** Runs task WARMUP times without measuring, then reps times; returns each duration in ms. */
    static double[] time(IntSupplier task, int reps) {
        for (int i = 0; i < WARMUP; i++) sink += task.getAsInt();
        System.gc(); // start the measured series with a clean heap
        double[] ms = new double[reps];
        for (int r = 0; r < reps; r++) {
            long t0 = System.nanoTime();
            sink += task.getAsInt();
            ms[r] = (System.nanoTime() - t0) / 1e6;
        }
        return ms;
    }

    static double mean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    /** Sample standard deviation. */
    static double std(double[] values) {
        double m = mean(values), sum = 0;
        for (double v : values) sum += (v - m) * (v - m);
        return Math.sqrt(sum / (values.length - 1));
    }
}
