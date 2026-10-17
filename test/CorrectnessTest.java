import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Correctness tests of the egrep clone.
 *
 * 1. Hand-written cases (expected result known).
 * 2. Random regexes and random lines, compared with java.util.regex (used as a reference).
 * 3. Every line of every testbed, compared with java.util.regex.
 *
 * Usage: java -cp out CorrectnessTest [testbeds directory]
 */
public class CorrectnessTest {

    private static int failures = 0;

    public static void main(String[] args) throws IOException {
        String dir = args.length > 0 ? args[0] : "testbeds";
        handWrittenCases();
        randomCases(2000, 50, 42);
        testbedCases(dir);
        System.out.println(failures == 0 ? "\nALL TESTS PASSED" : "\n" + failures + " FAILURE(S)");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---- 1. Hand-written cases ----

    private static void handWrittenCases() {
        System.out.println("== Hand-written cases ==");
        Object[][] cases = {
                // regex, line, expected
                {"Sargon", "the king Sargon II", true},
                {"Sargon", "the king Sargo", false},
                {"S(a|g|r)*on", "Son of", true},
                {"S(a|g|r)*on", "in Sargon's palace", true},
                {"S(a|g|r)*on", "Sxon", false},
                {"ab|cd", "xxcdxx", true},
                {"ab|cd", "acbd", false},
                {"a.c", "xxabcx", true},
                {"a.c", "ac", false},
                {"a.c", "aéc", true},          // '.' matches a non-ASCII character
                {"(ab)*c", "ababx", false},
                {"(ab)*c", "c", true},
                {"a*", "xyz", true},           // matches the empty word, so every line
                {"a*", "", true},
                {"a", "", false},
                {"x.*y", "x then y", true},
                {"x.*y", "y then x", false},
                {"Babylon", "BABYLON", false}, // case sensitive, like egrep
        };
        for (Object[] c : cases) {
            String regex = (String) c[0], line = (String) c[1];
            boolean expected = (Boolean) c[2];
            boolean actual = new Matcher(regex).find(line);
            check(actual == expected, "find(\"" + regex + "\", \"" + line + "\") = " + actual + ", expected " + expected);
        }

        String[] invalid = {"", "(ab", "ab)", "*a", "a|", "|a", "()"};
        for (String regex : invalid) {
            boolean rejected;
            try {
                new Matcher(regex);
                rejected = false;
            } catch (IllegalArgumentException e) {
                rejected = true;
            }
            check(rejected, "invalid regex \"" + regex + "\" should be rejected");
        }
        System.out.println((cases.length + invalid.length) + " cases checked");
    }

    // ---- 2. Random regexes compared with java.util.regex ----

    private static void randomCases(int regexCount, int linesPerRegex, long seed) {
        System.out.println("\n== Random regexes vs java.util.regex ==");
        Random random = new Random(seed);
        int checked = 0;
        for (int r = 0; r < regexCount; r++) {
            String regex = randomRegex(random, 4);
            Matcher ours = new Matcher(regex);
            Pattern reference = toJavaPattern(regex);
            for (int l = 0; l < linesPerRegex; l++) {
                String line = randomLine(random, 12);
                boolean expected = reference.matcher(line).find();
                boolean actual = ours.find(line);
                checked++;
                check(actual == expected, "regex \"" + regex + "\" on \"" + line + "\": got " + actual + ", expected " + expected);
            }
        }
        System.out.println(regexCount + " random regexes, " + checked + " (regex, line) pairs checked");
    }

    private static String randomRegex(Random random, int depth) {
        if (depth == 0 || random.nextInt(10) < 3) {
            String leaves = "abc.";
            return String.valueOf(leaves.charAt(random.nextInt(leaves.length())));
        }
        switch (random.nextInt(3)) {
            case 0:  return randomRegex(random, depth - 1) + randomRegex(random, depth - 1);
            case 1:  return "(" + randomRegex(random, depth - 1) + "|" + randomRegex(random, depth - 1) + ")";
            default: return "(" + randomRegex(random, depth - 1) + ")*";
        }
    }

    private static String randomLine(Random random, int maxLength) {
        String letters = "abcd";
        StringBuilder sb = new StringBuilder();
        int length = random.nextInt(maxLength + 1);
        for (int i = 0; i < length; i++) sb.append(letters.charAt(random.nextInt(letters.length())));
        return sb.toString();
    }

    // ---- 3. Testbeds compared with java.util.regex ----

    static final String[] TESTBED_REGEXES = {
            "Sargon", "S(a|g|r)*on", "(B|b)abylon|Assyria", "th(e|is|at)", "h.s",
            "a(b|c|d)*e", "q.*z", "((a|e|i|o|u)(n|r|s|t))*ing", "Ch(a|e)p.*(I|V|X)",
    };

    private static void testbedCases(String dir) throws IOException {
        System.out.println("\n== Testbeds vs java.util.regex (every line) ==");
        java.io.File[] files = new java.io.File(dir).listFiles((d, name) -> name.endsWith(".txt"));
        if (files == null || files.length == 0) {
            System.out.println("No testbed found in " + dir + " (run scripts/download_testbeds.sh)");
            failures++;
            return;
        }
        java.util.Arrays.sort(files);
        System.out.printf("%-26s %-30s %10s %10s%n", "file", "regex", "matches", "mismatches");
        for (java.io.File file : files) {
            List<String> lines = readLines(file.getPath());
            for (String regex : TESTBED_REGEXES) {
                Matcher ours = new Matcher(regex);
                java.util.regex.Matcher reference = toJavaPattern(regex).matcher("");
                int matches = 0, mismatches = 0;
                for (String line : lines) {
                    boolean actual = ours.find(line);
                    if (actual) matches++;
                    if (actual != reference.reset(line).find()) mismatches++;
                }
                if (mismatches > 0) failures++;
                System.out.printf("%-26s %-30s %10d %10d%n", file.getName(), regex, matches, mismatches);
            }
        }
    }

    // ---- Helpers ----

    /** Our literals that are operators in Java regexes are escaped. DOTALL: '.' matches every character. */
    static Pattern toJavaPattern(String regex) {
        StringBuilder sb = new StringBuilder();
        for (char c : regex.toCharArray()) {
            if ("+?[]{}^$\\".indexOf(c) >= 0) sb.append('\\');
            sb.append(c);
        }
        return Pattern.compile(sb.toString(), Pattern.DOTALL);
    }

    static List<String> readLines(String path) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) lines.add(line);
        }
        return lines;
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            failures++;
            if (failures <= 20) System.out.println("  FAIL: " + message);
        }
    }
}
