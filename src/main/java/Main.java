import java.util.Scanner;
import java.util.Arrays;
import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        // REPL: print prompt, read a line, respond, repeat until EOF
        while (true) {
            System.out.print("$ ");
            System.out.flush();

            if (!scanner.hasNextLine()) {
                break; // EOF - exit the REPL
            }


            String input = scanner.nextLine();
            String[] buildInCommands = {"exit", "echo", "type"};

            if (input.equals("exit 0")) {
                break; // EOF - exit the REPL
            }
            if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));}
            else if (input.startsWith("type ")) {
                String name = input.substring(5).trim();
                if (Arrays.asList(buildInCommands).contains(name)){
                    System.out.println(name+" is a shell builtin");
                }
                else{
                    String path = findInPath(name);
                    if (path != null) {
                        System.out.println(name + " is " + path);
                    } else {
                        System.out.println(name + ": not found");
                    }
                }
                    }

                else {
                    System.out.println(input + ": command not found");
                }
            }

        }

    // Find the first executable matching name in PATH and return its absolute path or null if not found.
    private static String findInPath(String name) {
        if (name == null || name.isEmpty()) return null;
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isEmpty()) return null;
        String[] dirs = pathEnv.split(":", -1); // keep empty entries as current dir
        for (String dir : dirs) {
            String base = dir.isEmpty() ? "." : dir;
            File candidate = new File(base, name);
            if (candidate.exists() && candidate.isFile() && candidate.canExecute()) {
                return candidate.getAbsolutePath();
            }
        }
        return null;
    }
}
