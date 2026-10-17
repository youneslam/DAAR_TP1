import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic finite automaton built from an epsilon-NFA by the subset construction.
 *
 * Each DFA state corresponds to a set of NFA states. Characters are grouped into classes:
 * one class per character appearing in the regex, plus one class OTHER for every
 * character that does not appear in it (those can only be read by a '.').
 */
public class DFA {

    /** Target of a missing transition: the automaton blocks (dead state). */
    public static final int DEAD = -1;

    private final char[] alphabet;      // alphabet[k] = character of class k (class alphabet.length = OTHER)
    private final int[] classOf;        // classOf[c] = class of ASCII character c
    private final int[][] transitions;  // transitions[state][class] = next state or DEAD
    private final boolean[] accepting;
    private final List<Set<Integer>> subsets; // NFA states behind each DFA state (for display)
    private final int start = 0;

    public DFA(NFA nfa) {
        if (nfa == null) throw new IllegalArgumentException("nfa is null");

        // ---- Character classes ----
        Set<Character> chars = nfa.alphabet();
        alphabet = new char[chars.size()];
        int k = 0;
        for (char c : chars) alphabet[k++] = c;
        int other = alphabet.length;
        classOf = new int[128];
        Arrays.fill(classOf, other);
        for (k = 0; k < alphabet.length; k++) {
            if (alphabet[k] < 128) classOf[alphabet[k]] = k;
        }

        // ---- Subset construction ----
        Map<Set<Integer>, Integer> ids = new HashMap<>();
        List<Set<Integer>> sets = new ArrayList<>();
        List<int[]> rows = new ArrayList<>();
        Deque<Integer> todo = new ArrayDeque<>();

        Set<Integer> initial = nfa.epsilonClosure(Set.of(nfa.getStart()));
        ids.put(initial, 0);
        sets.add(initial);
        todo.add(0);

        while (!todo.isEmpty()) {
            int d = todo.poll(); // FIFO + increasing ids: rows are filled in order, rows.get(d) is this row
            Set<Integer> current = sets.get(d);
            int[] row = new int[other + 1];

            for (k = 0; k <= other; k++) {
                Set<Integer> moved = k < other ? nfa.move(current, alphabet[k]) : nfa.moveOther(current);
                if (moved.isEmpty()) {
                    row[k] = DEAD;
                    continue;
                }
                Set<Integer> target = nfa.epsilonClosure(moved);
                Integer id = ids.get(target);
                if (id == null) {
                    id = sets.size();
                    ids.put(target, id);
                    sets.add(target);
                    todo.add(id);
                }
                row[k] = id;
            }
            rows.add(row);
        }

        transitions = rows.toArray(new int[0][]);
        accepting = new boolean[sets.size()];
        for (int d = 0; d < sets.size(); d++) {
            accepting[d] = sets.get(d).contains(nfa.getAccept());
        }
        subsets = sets;
    }

    // ---- Accessors used by the minimization and the matcher ----

    public int getStart() {
        return start;
    }

    public int size() {
        return transitions.length;
    }

    public boolean isAccepting(int state) {
        return accepting[state];
    }

    /** Number of character classes (characters of the regex + OTHER). */
    public int classCount() {
        return alphabet.length + 1;
    }

    public int classOf(char c) {
        return c < 128 ? classOf[c] : alphabet.length;
    }

    /** Next state when reading c from state, or DEAD. */
    public int next(int state, char c) {
        return transitions[state][classOf(c)];
    }

    /** Next state when reading a character of class k from state, or DEAD. */
    public int nextByClass(int state, int k) {
        return transitions[state][k];
    }

    /** Runs the DFA: true if the whole string s is matched. Used to test the construction. */
    public boolean accepts(String s) {
        int state = start;
        for (int i = 0; i < s.length(); i++) {
            state = next(state, s.charAt(i));
            if (state == DEAD) return false;
        }
        return accepting[state];
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DFA: ").append(size()).append(" states, start = ").append(start).append('\n');
        sb.append(String.format("%7s", "state"));
        for (char c : alphabet) sb.append(String.format("%4s", c));
        sb.append(String.format("%7s", "other")).append("   NFA states\n");
        for (int d = 0; d < size(); d++) {
            sb.append(String.format("%6d%s", d, accepting[d] ? "*" : " "));
            for (int k = 0; k < classCount(); k++) {
                String target = transitions[d][k] == DEAD ? "-" : String.valueOf(transitions[d][k]);
                sb.append(String.format(k < alphabet.length ? "%4s" : "%7s", target));
            }
            sb.append("   ").append(subsets.get(d)).append('\n');
        }
        sb.append("(* = accepting, - = dead)\n");
        return sb.toString();
    }

    /** Quick manual check: prints a DFA, then compares DFA and NFA on every word up to length 6. */
    public static void main(String[] args) {
        System.out.println(new DFA(new NFA(new RegExParser("S(a|g|r)*on").parse())));

        String[] regexes = {"S(a|g|r)*on", "ab|c", "(ab)*", "a.c", "a*b*", "(a|b)*abb", ".*a.*", "((a|b)c)*|x"};
        char[] letters = {'a', 'b', 'c', 'x'};
        for (String regex : regexes) {
            NFA nfa = new NFA(new RegExParser(regex).parse());
            DFA dfa = new DFA(nfa);
            int checked = 0, errors = 0;
            List<String> words = new ArrayList<>(List.of(""));
            for (int len = 0; len <= 6; len++) {
                List<String> longer = new ArrayList<>();
                for (String w : words) {
                    checked++;
                    if (nfa.accepts(w) != dfa.accepts(w)) {
                        errors++;
                        System.out.println("  MISMATCH on \"" + w + "\"");
                    }
                    for (char c : letters) longer.add(w + c);
                }
                words = longer;
            }
            System.out.printf("%-14s NFA %2d states -> DFA %2d states, %d words checked, %d mismatches%n",
                    regex, nfa.size(), dfa.size(), checked, errors);
        }
    }
}
