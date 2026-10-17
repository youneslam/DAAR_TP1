/**
 * Recursive-descent parser: turns a regex string into a RegExTree.
 *
 * Grammar (one method per precedence level, lowest priority first):
 *   altern -> concat ( '|' concat )*
 *   concat -> star star*
 *   star   -> atom '*'*
 *   atom   -> letter | '.' | '(' altern ')'
 *
 * Precedence: '*' > concatenation > '|'. Concatenation and '|' are left-associative.
 */
public class RegExParser {

    private final String regex;
    private int pos;

    public RegExParser(String regex) {
        if (regex == null) throw new IllegalArgumentException("regex is null");
        this.regex = regex;
        this.pos = 0;
    }

    /** Parses the whole string and returns the root of the syntax tree. */
    public RegExTree parse() {
        if (regex.isEmpty()) throw error("empty regex");
        RegExTree tree = parseAltern();
        if (pos < regex.length()) {
            // Only a ')' can stop parseAltern before the end of the string
            throw error("unexpected ')'");
        }
        return tree;
    }

    // ---- One method per precedence level ----

    /** altern -> concat ( '|' concat )* */
    private RegExTree parseAltern() {
        RegExTree left = parseConcat();
        while (peek() == '|') {
            pos++; // consume '|'
            RegExTree right = parseConcat();
            left = RegExTree.altern(left, right);
        }
        return left;
    }

    /** concat -> star star* (concatenation has no symbol: atoms simply follow each other) */
    private RegExTree parseConcat() {
        if (!startsAtom(peek())) {
            throw error("expected a letter, '.' or '(' but found " + describe(peek()));
        }
        RegExTree left = parseStar();
        while (startsAtom(peek())) {
            RegExTree right = parseStar();
            left = RegExTree.concat(left, right);
        }
        return left;
    }

    /** star -> atom '*'* (a** is the same as a*, so repeated stars are collapsed) */
    private RegExTree parseStar() {
        RegExTree atom = parseAtom();
        if (peek() == '*') {
            while (peek() == '*') pos++;
            atom = RegExTree.star(atom);
        }
        return atom;
    }

    /** atom -> letter | '.' | '(' altern ')' */
    private RegExTree parseAtom() {
        char c = peek();
        if (c == '(') {
            pos++; // consume '('
            if (peek() == ')') throw error("empty parentheses");
            RegExTree inside = parseAltern();
            if (peek() != ')') throw error("missing ')'");
            pos++; // consume ')'
            return inside;
        }
        if (c == '.') {
            pos++;
            return RegExTree.dot();
        }
        if (isLetter(c)) {
            pos++;
            return RegExTree.letter(c);
        }
        throw error("expected a letter, '.' or '(' but found " + describe(c));
    }

    // ---- Helpers ----

    private static final char END = '\0';

    private char peek() {
        return pos < regex.length() ? regex.charAt(pos) : END;
    }

    private static boolean startsAtom(char c) {
        return c == '(' || c == '.' || isLetter(c);
    }

    /** Any printable ASCII character that is not an operator is a literal. */
    private static boolean isLetter(char c) {
        return c >= 32 && c <= 126 && "()|*.".indexOf(c) < 0;
    }

    private static String describe(char c) {
        return c == END ? "end of regex" : "'" + c + "'";
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("Syntax error at position " + pos + " in \"" + regex + "\": " + message);
    }

    /** Quick manual check: parses a few regexes and prints their trees. */
    public static void main(String[] args) {
        String[] tests = {
                "S(a|g|r)*on", "ab|c", "ab*", "(ab)*", "a|b|c", "a.b", "a**",
                "", "(ab", "ab)", "*a", "a|", "|a", "()"
        };
        for (String regex : tests) {
            try {
                System.out.println("\"" + regex + "\" -> " + new RegExParser(regex).parse());
            } catch (IllegalArgumentException e) {
                System.out.println("\"" + regex + "\" -> " + e.getMessage());
            }
        }
    }
}
