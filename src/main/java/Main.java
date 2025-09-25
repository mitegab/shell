import java.util.Scanner;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws Exception {
    Scanner scanner = new Scanner(System.in);
    // Track current working directory inside the shell
    File currentDir = new File(System.getProperty("user.dir")).getCanonicalFile();

        // REPL: print prompt, read a line, respond, repeat until EOF
        while (true) {
            System.out.print("$ ");
            System.out.flush();

            if (!scanner.hasNextLine()) {
                break; // EOF - exit the REPL
            }


            String input = scanner.nextLine();
            if (input == null) {
                break;
            }
            input = input.trim();
            if (input.isEmpty()) {
                continue; // empty line, show prompt again
            }

            String[] tokens = input.split("\\s+");
            String cmdName = tokens[0];
            String[] buildInCommands = {"exit", "echo", "type", "pwd", "cd"};

            if (input.equals("exit 0")) {
                System.exit(0); // Exit with status code 0 as required
            }
            if (cmdName.equals("pwd")) {
                // Print absolute current working directory tracked by the shell
                System.out.println(currentDir.getCanonicalPath());
            }
            else if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
            }
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
                // Try to execute external command found in PATH
                String path = findInPath(cmdName);
                if (path != null) {
                    // Use sh -c with positional parameters so argv[0] equals the typed command name
                    List<String> command = new ArrayList<>();
                    command.add("/bin/sh");
                    command.add("-c");
                    command.add("exec \"$0\" \"$@\"");
                    command.add(cmdName); // this becomes $0 for the exec'ed program
                    for (int i = 1; i < tokens.length; i++) {
                        command.add(tokens[i]); // these become $1..$n
                    }
                    ProcessBuilder pb = new ProcessBuilder(command);
                    pb.redirectErrorStream(true);
                    try {
                        Process process = pb.start();
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                System.out.println(line);
                            }
                        }
                        process.waitFor();
                    } catch (IOException | InterruptedException e) {
                        // If execution fails, mimic not found message
                        System.out.println(cmdName + ": command not found");
                        if (e instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } else {
                    System.out.println(cmdName + ": command not found");
                }
            }
            }

    scanner.close();
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
