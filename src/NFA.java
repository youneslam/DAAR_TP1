import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Non-deterministic finite automaton with epsilon-transitions,
 * built from a RegExTree by Thompson's construction.
 *
 * Every fragment built for a node has exactly one start state and one accept state.
 * Each state has either one symbol transition, or up to two epsilon-transitions, or nothing.
 */
public class NFA {

    /** Label of a transition that matches any character (the '.' of the regex). */
    public static final int ANY = -2;
    /** Marks a state that has no symbol transition. */
    public static final int NONE = -1;

    // State i has a symbol transition labelled symbol[i] (a char code, ANY or NONE) towards symbolTarget[i]
    private final List<Integer> symbol = new ArrayList<>();
    private final List<Integer> symbolTarget = new ArrayList<>();
    // State i has epsilon-transitions towards every state of epsilon[i]
    private final List<List<Integer>> epsilon = new ArrayList<>();

    private final int start;
    private final int accept;

    public NFA(RegExTree tree) {
        if (tree == null) throw new IllegalArgumentException("tree is null");
        Fragment f = build(tree);
        this.start = f.start;
        this.accept = f.accept;
    }

    // ---- Thompson's construction ----

    /** Sub-automaton with a single entry and a single exit. */
    private static class Fragment {
        final int start;
        final int accept;

        Fragment(int start, int accept) {
            this.start = start;
            this.accept = accept;
        }
    }

    private Fragment build(RegExTree t) {
        switch (t.type) {
            case CHAR: {
                // start --c--> accept
                int s = newState(), a = newState();
                addSymbol(s, t.symbol, a);
                return new Fragment(s, a);
            }
            case DOT: {
                // start --any--> accept
                int s = newState(), a = newState();
                addSymbol(s, ANY, a);
                return new Fragment(s, a);
            }
            case CONCAT: {
                // L.accept --eps--> R.start
                Fragment l = build(t.left);
                Fragment r = build(t.right);
                addEpsilon(l.accept, r.start);
                return new Fragment(l.start, r.accept);
            }
            case ALTERN: {
                // start --eps--> L.start, R.start ; L.accept, R.accept --eps--> accept
                Fragment l = build(t.left);
                Fragment r = build(t.right);
                int s = newState(), a = newState();
                addEpsilon(s, l.start);
                addEpsilon(s, r.start);
                addEpsilon(l.accept, a);
                addEpsilon(r.accept, a);
                return new Fragment(s, a);
            }
            case STAR: {
                // start --eps--> E.start, accept ; E.accept --eps--> E.start, accept
                Fragment e = build(t.left);
                int s = newState(), a = newState();
                addEpsilon(s, e.start);
                addEpsilon(s, a);
                addEpsilon(e.accept, e.start);
                addEpsilon(e.accept, a);
                return new Fragment(s, a);
            }
            default:
                throw new IllegalStateException("Unknown node type " + t.type);
        }
    }

    private int newState() {
        symbol.add(NONE);
        symbolTarget.add(NONE);
        epsilon.add(new ArrayList<>());
        return symbol.size() - 1;
    }

    private void addSymbol(int from, int label, int to) {
        symbol.set(from, label);
        symbolTarget.set(from, to);
    }

    private void addEpsilon(int from, int to) {
        epsilon.get(from).add(to);
    }

    // ---- Accessors used by the DFA construction ----

    public int getStart() {
        return start;
    }

    public int getAccept() {
        return accept;
    }

    public int size() {
        return symbol.size();
    }

    /** All states reachable from the given states using only epsilon-transitions (including themselves). */
    public Set<Integer> epsilonClosure(Set<Integer> states) {
        Set<Integer> closure = new TreeSet<>(states);
        Deque<Integer> stack = new ArrayDeque<>(states);
        while (!stack.isEmpty()) {
            int q = stack.pop();
            for (int next : epsilon.get(q)) {
                if (closure.add(next)) stack.push(next);
            }
        }
        return closure;
    }

    /** All states reachable from the given states by reading character c (no epsilon-closure). */
    public Set<Integer> move(Set<Integer> states, char c) {
        Set<Integer> result = new TreeSet<>();
        for (int q : states) {
            int label = symbol.get(q);
            if (label == c || label == ANY) result.add(symbolTarget.get(q));
        }
        return result;
    }

    /** Characters that appear explicitly on a transition (ANY is not included). */
    public Set<Character> alphabet() {
        Set<Character> chars = new TreeSet<>();
        for (int label : symbol) {
            if (label >= 0) chars.add((char) label);
        }
        return chars;
    }

    /** Simulates the NFA: true if the whole string s is matched. Used to test the construction. */
    public boolean accepts(String s) {
        Set<Integer> current = epsilonClosure(Set.of(start));
        for (int i = 0; i < s.length() && !current.isEmpty(); i++) {
            current = epsilonClosure(move(current, s.charAt(i)));
        }
        return current.contains(accept);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("NFA: ").append(size()).append(" states, start = ").append(start)
                .append(", accept = ").append(accept).append('\n');
        for (int q = 0; q < size(); q++) {
            int label = symbol.get(q);
            if (label != NONE) {
                String l = label == ANY ? "any" : String.valueOf((char) label);
                sb.append("  ").append(q).append(" --").append(l).append("--> ").append(symbolTarget.get(q)).append('\n');
            }
            for (int next : epsilon.get(q)) {
                sb.append("  ").append(q).append(" --eps--> ").append(next).append('\n');
            }
        }
        return sb.toString();
    }

    /** Quick manual check: builds the NFA of a few regexes and tests some words. */
    public static void main(String[] args) {
        NFA nfa = new NFA(new RegExParser("S(a|g|r)*on").parse());
        System.out.println(nfa);

        String[][] tests = {
                {"S(a|g|r)*on", "Son", "Sargon", "Saaron", "Sxon", "Sargo", "sargon"},
                {"ab|c", "ab", "c", "a", "abc"},
                {"(ab)*", "", "ab", "abab", "aba"},
                {"a.c", "abc", "a c", "ac", "abbc"},
                {"a*b*", "", "aaa", "aabb", "ba"},
        };
        for (String[] test : tests) {
            NFA n = new NFA(new RegExParser(test[0]).parse());
            System.out.print(test[0] + " (" + n.size() + " states):");
            for (int i = 1; i < test.length; i++) {
                System.out.print("  \"" + test[i] + "\"=" + n.accepts(test[i]));
            }
            System.out.println();
        }
    }
}
