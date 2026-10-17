/**
 * Searches a regex inside lines of text, with the semantics of egrep:
 * a line matches if at least one of its substrings is accepted by the minimal DFA.
 */
public class Matcher {

    private final DFA dfa;

    public Matcher(String regex) {
        RegExTree tree = new RegExParser(regex).parse();
        this.dfa = DFAMinimizer.minimize(new DFA(new NFA(tree)));
    }

    public DFA getDfa() {
        return dfa;
    }

    /**
     * True if some substring of line is matched.
     * The DFA is run from every starting position i; a run stops as soon as it reaches
     * an accepting state (match found) or DEAD (no match starting at i).
     */
    public boolean find(String line) {
        int start = dfa.getStart();
        if (dfa.isAccepting(start)) return true; // the regex matches the empty word
        int n = line.length();
        for (int i = 0; i < n; i++) {
            int state = start;
            for (int j = i; j < n; j++) {
                state = dfa.next(state, line.charAt(j));
                if (state == DFA.DEAD) break;
                if (dfa.isAccepting(state)) return true;
            }
        }
        return false;
    }
}
