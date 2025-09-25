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

            List<String> tokenList = tokenize(input);
            if (tokenList.isEmpty()) {
                continue;
            }
            String cmdName = tokenList.get(0);
            String[] tokens = tokenList.toArray(new String[0]);
            String[] buildInCommands = {"exit", "echo", "type", "pwd", "cd"};

            if (input.equals("exit 0")) {
                System.exit(0); // Exit with status code 0 as required
            }
            if (cmdName.equals("pwd")) {
                // Print absolute current working directory tracked by the shell
                System.out.println(currentDir.getCanonicalPath());
            }
            else if (cmdName.equals("cd")) {
                // cd handling (absolute and relative paths)
                if (tokens.length < 2) {
                    // No directory provided. Do nothing for now (later stages may use HOME).
                } else {
                    String target = tokens[1];
                    // Expand ~ to HOME for this stage
                    if (target.equals("~") || target.startsWith("~/")) {
                        String home = System.getenv("HOME");
                        if (home == null || home.isEmpty()) {
                            home = System.getProperty("user.home", "");
                        }
                        if (home != null && !home.isEmpty()) {
                            if (target.equals("~")) {
                                target = home;
                            } else {
                                target = home + target.substring(1); // replace leading ~ with HOME
                            }
                        }
                    }
                    File dest;
                    if (target.startsWith("/")) {
                        dest = new File(target);
                    } else {
                        // relative path against currentDir
                        dest = new File(currentDir, target);
                    }
                    File canonical;
                    try {
                        canonical = dest.getCanonicalFile();
                    } catch (IOException e) {
                        canonical = dest;
                    }
                    if (canonical.isDirectory()) {
                        currentDir = canonical;
                    } else {
                        System.out.println("cd: " + target + ": No such file or directory");
                    }
                }
            }
            else if (cmdName.equals("echo")) {
                // Join arguments with a single space (outside-quote spaces collapsed, quoted spaces preserved in tokens)
                if (tokens.length > 1) {
                    StringBuilder out = new StringBuilder();
                    for (int i = 1; i < tokens.length; i++) {
                        if (i > 1) out.append(' ');
                        out.append(tokens[i]);
                    }
                    System.out.println(out.toString());
                } else {
                    System.out.println();
                }
            }
            else if (cmdName.equals("type")) {
                if (tokens.length < 2) {
                    System.out.println("type: missing operand");
                    continue;
                }
                String name = tokens[1];
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
                    pb.directory(currentDir);
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

    // Tokenize an input line honoring single and double quotes and backslashes
    private static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        int n = input.length();
        for (int i = 0; i < n; i++) {
            char c = input.charAt(i);
            if (inSingle) {
                if (c == '\'') {
                    inSingle = false;
                } else {
                    current.append(c); // backslashes are literal in single quotes
                }
            } else if (inDouble) {
                if (c == '"') {
                    inDouble = false;
                } else if (c == '\\') {
                    if (i + 1 < n) {
                        char next = input.charAt(i + 1);
                        // In double quotes, backslash escapes " and \
                        if (next == '"' || next == '\\') {
                            current.append(next);
                            i++;
                        } else {
                            // Keep backslash literally for other chars
                            current.append('\\');
                        }
                    } else {
                        current.append('\\');
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (Character.isWhitespace(c)) {
                    if (current.length() > 0) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                    // collapse consecutive spaces
                } else if (c == '\'') {
                    inSingle = true;
                } else if (c == '"') {
                    inDouble = true;
                } else if (c == '\\') {
                    if (i + 1 < n) {
                        // Outside quotes, backslash escapes next char (including whitespace)
                        current.append(input.charAt(i + 1));
                        i++;
                    } else {
                        current.append('\\');
                    }
                } else {
                    current.append(c);
                }
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
