/**
 * Boyer-Moore: finds a fixed word in a text.
 *
 * Only used to compare its speed with the minimal DFA and KMP, on regexes that are just a word.
 *
 * Idea: the word is compared with the text from right to left. When a mismatch happens, we can often
 * shift the word by several positions at once, using two tables computed in advance:
 *   - bad character: the text character that did not match is lined up with its last occurrence
 *     in the word. If it does not appear in the word at all, the whole word jumps past it.
 *   - good suffix: the end of the word that did match is lined up with another place where the
 *     same characters appear in the word.
 * We take the bigger of the two shifts.
 *
 * On ordinary text it skips most characters, so it gets faster as the word gets longer.
 * Worst case O(n * m), but that needs very repetitive texts.
 */
public class BoyerMoore {

    /** The word only contains printable ASCII (see RegExParser), so any other character is not in it. */
    private static final int ALPHABET = 128;

    private final String pattern;
    private final int[] badChar;
    private final int[] goodSuffix;

    public BoyerMoore(String pattern) {
        if (pattern.isEmpty()) throw new IllegalArgumentException("empty pattern");
        this.pattern = pattern;
        this.badChar = buildBadChar(pattern);
        this.goodSuffix = buildGoodSuffix(pattern);
    }

    /** badChar[c] = distance between the last occurrence of c in the word (last letter left out) and the end. */
    static int[] buildBadChar(String p) {
        int m = p.length();
        int[] table = new int[ALPHABET];
        java.util.Arrays.fill(table, m);
        for (int i = 0; i < m - 1; i++) {
            char c = p.charAt(i);
            if (c < ALPHABET) table[c] = m - 1 - i;
        }
        return table;
    }

    /** suff[i] = length of the longest piece ending at position i that is also an ending of the whole word. */
    static int[] suffixes(String p) {
        int m = p.length();
        int[] suff = new int[m];
        suff[m - 1] = m;
        int g = m - 1, f = 0;
        for (int i = m - 2; i >= 0; i--) {
            if (i > g && suff[i + m - 1 - f] < i - g) {
                suff[i] = suff[i + m - 1 - f]; // already known from a previous comparison
            } else {
                if (i < g) g = i;
                f = i;
                while (g >= 0 && p.charAt(g) == p.charAt(g + m - 1 - f)) g--;
                suff[i] = f - g;
            }
        }
        return suff;
    }

    /** goodSuffix[i] = shift to use when the mismatch is at position i (so p[i+1..m-1] did match). */
    static int[] buildGoodSuffix(String p) {
        int m = p.length();
        int[] suff = suffixes(p);
        int[] table = new int[m];
        java.util.Arrays.fill(table, m);
        // The matched ending does not appear again, but a beginning of the word is also its ending
        int j = 0;
        for (int i = m - 1; i >= 0; i--) {
            if (suff[i] == i + 1) {
                for (; j < m - 1 - i; j++) {
                    if (table[j] == m) table[j] = m - 1 - i;
                }
            }
        }
        // The matched ending appears again inside the word: use the rightmost one (written last)
        for (int i = 0; i <= m - 2; i++) table[m - 1 - suff[i]] = m - 1 - i;
        return table;
    }

    /** Position of the first occurrence of the word in text, or -1 if it is not there. */
    public int indexOf(String text) {
        int m = pattern.length(), n = text.length();
        int j = 0; // where the word currently starts in the text
        while (j <= n - m) {
            int i = m - 1;
            while (i >= 0 && pattern.charAt(i) == text.charAt(i + j)) i--;
            if (i < 0) return j;
            char c = text.charAt(i + j);
            int bad = (c < ALPHABET ? badChar[c] : m) - m + 1 + i;
            j += Math.max(goodSuffix[i], bad);
        }
        return -1;
    }

    public boolean find(String text) {
        return indexOf(text) >= 0;
    }
}
