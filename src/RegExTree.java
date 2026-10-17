/**
 * Syntax tree of a regular expression restricted to:
 * parentheses, alternation (|), concatenation, star (*), dot (.) and ASCII letters.
 */
public class RegExTree {

    public enum Type { CHAR, DOT, CONCAT, ALTERN, STAR }

    public final Type type;
    public final char symbol;        // only used when type == CHAR
    public final RegExTree left;     // operand for STAR, left operand for CONCAT/ALTERN
    public final RegExTree right;    // right operand for CONCAT/ALTERN, null otherwise

    private RegExTree(Type type, char symbol, RegExTree left, RegExTree right) {
        this.type = type;
        this.symbol = symbol;
        this.left = left;
        this.right = right;
    }

    // ---- Factory methods ----

    public static RegExTree letter(char c) {
        return new RegExTree(Type.CHAR, c, null, null);
    }

    public static RegExTree dot() {
        return new RegExTree(Type.DOT, '.', null, null);
    }

    public static RegExTree concat(RegExTree l, RegExTree r) {
        if (l == null || r == null) throw new IllegalArgumentException("concat needs two operands");
        return new RegExTree(Type.CONCAT, '\0', l, r);
    }

    public static RegExTree altern(RegExTree l, RegExTree r) {
        if (l == null || r == null) throw new IllegalArgumentException("altern needs two operands");
        return new RegExTree(Type.ALTERN, '|', l, r);
    }

    public static RegExTree star(RegExTree sub) {
        if (sub == null) throw new IllegalArgumentException("star needs one operand");
        return new RegExTree(Type.STAR, '*', sub, null);
    }

    // ---- Helpers ----

    public boolean isLeaf() {
        return type == Type.CHAR || type == Type.DOT;
    }

    /** Prefix notation, e.g. a(b|c)* -> concat(a,star(altern(b,c))) */
    @Override
    public String toString() {
        switch (type) {
            case CHAR:   return String.valueOf(symbol);
            case DOT:    return ".";
            case STAR:   return "star(" + left + ")";
            case CONCAT: return "concat(" + left + "," + right + ")";
            case ALTERN: return "altern(" + left + "," + right + ")";
            default:     throw new IllegalStateException("Unknown type " + type);
        }
    }

    /** Quick manual check: builds S(a|g|r)*on by hand and prints it. */
    public static void main(String[] args) {
        RegExTree t = concat(
                concat(
                        concat(letter('S'), star(altern(altern(letter('a'), letter('g')), letter('r')))),
                        letter('o')),
                letter('n'));
        System.out.println(t);
    }
}
