/**
 * Knuth-Morris-Pratt: finds a fixed word in a text.
 *
 * Only used to compare its speed with the minimal DFA, on regexes that are just a word (no operator).
 *
 * Idea: when a mismatch happens after k matching characters, we already know those k characters.
 * So instead of starting over one position further, we look at the longest prefix of the word that is
 * also a suffix of what was matched, and continue from there. The text is never read backwards.
 *
 * Small improvement from Knuth: if the character we would fall back to is the same one that just
 * failed, it will fail again, so the table directly jumps one step further.
 *
 * Cost: O(m) to build the table, O(n) to search.
 */
public class KMP {

    private final String pattern;
    private final int[] next;

    public KMP(String pattern) {
        if (pattern.isEmpty()) throw new IllegalArgumentException("empty pattern");
        this.pattern = pattern;
        this.next = buildTable(pattern);
    }

    /**
     * next[k] tells where to continue in the word after a mismatch on pattern[k].
     * -1 means "nothing to keep": move on to the next character of the text.
     */
    static int[] buildTable(String p) {
        int m = p.length();
        int[] next = new int[m + 1];
        next[0] = -1;
        int k = 0; // length of the longest prefix of p that is also a suffix of p[0..i-1]
        for (int i = 1; i < m; i++, k++) {
            if (p.charAt(i) == p.charAt(k)) {
                next[i] = next[k]; // falling back to k would compare the same character again
            } else {
                next[i] = k;
                while (k >= 0 && p.charAt(i) != p.charAt(k)) k = next[k];
            }
        }
        next[m] = k;
        return next;
    }

    /** Position of the first occurrence of the word in text, or -1 if it is not there. */
    public int indexOf(String text) {
        int m = pattern.length(), n = text.length();
        int k = 0; // how many characters of the word are matched so far
        for (int j = 0; j < n; ) {
            if (pattern.charAt(k) == text.charAt(j)) {
                j++;
                k++;
                if (k == m) return j - m;
            } else {
                k = next[k];
                if (k < 0) {
                    j++;
                    k = 0;
                }
            }
        }
        return -1;
    }

    public boolean find(String text) {
        return indexOf(text) >= 0;
    }
}
