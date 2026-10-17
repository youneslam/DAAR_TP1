import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * DFA minimization by Moore's algorithm (successive refinement of partitions).
 *
 * 1. Start with two groups: accepting states and non-accepting states.
 * 2. Two states of a same group stay together only if, for every character class,
 *    their transitions lead into the same group (DEAD counts as its own group).
 * 3. Repeat until the number of groups no longer changes. Each group becomes one state.
 *
 * The DFA from the subset construction has no unreachable state and no state equivalent
 * to DEAD (every Thompson state can reach the accept state), so the result is minimal.
 */
public class DFAMinimizer {

    private DFAMinimizer() {
    }

    public static DFA minimize(DFA dfa) {
        int n = dfa.size();
        int classes = dfa.classCount();

        // ---- Initial partition: accepting / non-accepting ----
        int[] group = new int[n];
        for (int s = 0; s < n; s++) group[s] = dfa.isAccepting(s) ? 1 : 0;
        int groupCount = countDistinct(group);

        // ---- Refinement ----
        while (true) {
            // Signature of a state: its current group + the group reached by each character class
            Map<List<Integer>, Integer> ids = new HashMap<>();
            int[] refined = new int[n];
            for (int s = 0; s < n; s++) {
                List<Integer> signature = new ArrayList<>(classes + 1);
                signature.add(group[s]);
                for (int k = 0; k < classes; k++) {
                    int t = dfa.nextByClass(s, k);
                    signature.add(t == DFA.DEAD ? DFA.DEAD : group[t]);
                }
                // States are scanned in order, so the start state 0 always gets group 0
                Integer id = ids.get(signature);
                if (id == null) {
                    id = ids.size();
                    ids.put(signature, id);
                }
                refined[s] = id;
            }
            group = refined;
            // A refinement only splits groups: same count means nothing changed
            if (ids.size() == groupCount) break;
            groupCount = ids.size();
        }

        // ---- Build the minimal DFA: one state per group ----
        int[][] transitions = new int[groupCount][classes];
        boolean[] accepting = new boolean[groupCount];
        List<Set<Integer>> merged = new ArrayList<>();
        for (int g = 0; g < groupCount; g++) merged.add(new TreeSet<>());

        for (int s = 0; s < n; s++) {
            int g = group[s];
            merged.get(g).add(s);
            accepting[g] = dfa.isAccepting(s);
            for (int k = 0; k < classes; k++) {
                int t = dfa.nextByClass(s, k);
                transitions[g][k] = t == DFA.DEAD ? DFA.DEAD : group[t];
            }
        }
        return new DFA(dfa, transitions, accepting, merged);
    }

    private static int countDistinct(int[] values) {
        Set<Integer> distinct = new TreeSet<>();
        for (int v : values) distinct.add(v);
        return distinct.size();
    }

    /** Quick manual check: prints a minimal DFA, then compares DFA and minimal DFA on every word up to length 6. */
    public static void main(String[] args) {
        DFA dfa = new DFA(new NFA(new RegExParser("S(a|g|r)*on").parse()));
        System.out.println("Before minimization:\n" + dfa);
        System.out.println("After minimization:\n" + minimize(dfa));

        String[] regexes = {"S(a|g|r)*on", "ab|c", "(ab)*", "a.c", "a*b*", "(a|b)*abb", ".*a.*", "((a|b)c)*|x", "(a|b)*(a|b)*"};
        char[] letters = {'a', 'b', 'c', 'x'};
        for (String regex : regexes) {
            NFA nfa = new NFA(new RegExParser(regex).parse());
            DFA d = new DFA(nfa);
            DFA min = minimize(d);
            int checked = 0, errors = 0;
            List<String> words = new ArrayList<>(List.of(""));
            for (int len = 0; len <= 6; len++) {
                List<String> longer = new ArrayList<>();
                for (String w : words) {
                    checked++;
                    if (d.accepts(w) != min.accepts(w)) {
                        errors++;
                        System.out.println("  MISMATCH on \"" + w + "\"");
                    }
                    for (char c : letters) longer.add(w + c);
                }
                words = longer;
            }
            System.out.printf("%-14s NFA %2d -> DFA %2d -> minimal DFA %2d states, %d words checked, %d mismatches%n",
                    regex, nfa.size(), d.size(), min.size(), checked, errors);
        }
    }
}
