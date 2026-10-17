import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Clone of egrep restricted to parentheses, alternation, concatenation, star, dot and ASCII letters.
 *
 * Usage: java Egrep <regex> <file>
 * Prints every line of file containing a match. Exit code: 0 if a line matched, 1 if none, 2 on error.
 */
public class Egrep {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java Egrep <regex> <file>");
            System.exit(2);
        }

        Matcher matcher;
        try {
            matcher = new Matcher(args[0]);
        } catch (IllegalArgumentException e) {
            System.err.println("egrep: " + e.getMessage());
            System.exit(2);
            return;
        }

        PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 1 << 16),
                false, StandardCharsets.UTF_8);
        boolean found = false;
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(new FileInputStream(args[1]), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (matcher.find(line)) {
                    out.println(line);
                    found = true;
                }
            }
        } catch (IOException e) {
            out.flush();
            System.err.println("egrep: " + args[1] + ": " + e.getMessage());
            System.exit(2);
        }
        out.flush();
        System.exit(found ? 0 : 1);
    }
}
