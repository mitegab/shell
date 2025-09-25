import java.util.Scanner;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

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
            // Extract redirections and clean tokens (supports with/without space)
            Redir redir = new Redir();
            List<String> cleaned = new ArrayList<>();
            for (int i = 0; i < tokenList.size(); i++) {
                String t = tokenList.get(i);
                String next = (i + 1 < tokenList.size()) ? tokenList.get(i + 1) : null;
                if (t.equals(">") || t.equals("1>") || t.equals(">>") || t.equals("1>>")) {
                    if (next != null) {
                        redir.stdoutTarget = next;
                        redir.stdoutAppend = t.endsWith(">>");
                        i++;
                    }
                } else if (t.equals("2>") || t.equals("2>>")) {
                    if (next != null) {
                        redir.stderrTarget = next;
                        redir.stderrAppend = t.endsWith(">>");
                        i++;
                    }
                } else if (t.startsWith("1>>")) {
                    redir.stdoutTarget = t.substring(3).isEmpty() && next != null ? next : t.substring(3);
                    redir.stdoutAppend = true;
                    if (t.substring(3).isEmpty() && next != null) { i++; }
                } else if (t.startsWith(">>")) {
                    redir.stdoutTarget = t.substring(2).isEmpty() && next != null ? next : t.substring(2);
                    redir.stdoutAppend = true;
                    if (t.substring(2).isEmpty() && next != null) { i++; }
                } else if (t.startsWith("1>")) {
                    redir.stdoutTarget = t.substring(2).isEmpty() && next != null ? next : t.substring(2);
                    redir.stdoutAppend = false;
                    if (t.substring(2).isEmpty() && next != null) { i++; }
                } else if (t.startsWith(">")) {
                    redir.stdoutTarget = t.substring(1).isEmpty() && next != null ? next : t.substring(1);
                    redir.stdoutAppend = false;
                    if (t.substring(1).isEmpty() && next != null) { i++; }
                } else if (t.startsWith("2>>")) {
                    redir.stderrTarget = t.substring(3).isEmpty() && next != null ? next : t.substring(3);
                    redir.stderrAppend = true;
                    if (t.substring(3).isEmpty() && next != null) { i++; }
                } else if (t.startsWith("2>")) {
                    redir.stderrTarget = t.substring(2).isEmpty() && next != null ? next : t.substring(2);
                    redir.stderrAppend = false;
                    if (t.substring(2).isEmpty() && next != null) { i++; }
                } else {
                    cleaned.add(t);
                }
            }
            if (cleaned.isEmpty()) {
                continue;
            }
            String cmdName = cleaned.get(0);
            String[] tokens = cleaned.toArray(new String[0]);
            String[] buildInCommands = {"exit", "echo", "type", "pwd", "cd"};

            if (input.equals("exit 0")) {
                System.exit(0); // Exit with status code 0 as required
            }
            if (cmdName.equals("pwd")) {
                // Print absolute current working directory tracked by the shell
                String line = currentDir.getCanonicalPath() + System.lineSeparator();
                // Ensure stderr file exists if redirected (builtins don't naturally write stderr)
                if (redir.stderrTarget != null) writeToFile(currentDir, redir.stderrTarget, "", redir.stderrAppend);
                if (redir.stdoutTarget != null) {
                    writeToFile(currentDir, redir.stdoutTarget, line, redir.stdoutAppend);
                } else {
                    System.out.print(line);
                }
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
                if (redir.stderrTarget != null) writeToFile(currentDir, redir.stderrTarget, "", redir.stderrAppend);
                if (tokens.length > 1) {
                    StringBuilder out = new StringBuilder();
                    for (int i = 1; i < tokens.length; i++) {
                        if (i > 1) out.append(' ');
                        out.append(tokens[i]);
                    }
                    if (redir.stdoutTarget != null) {
                        writeToFile(currentDir, redir.stdoutTarget, out.toString() + System.lineSeparator(), redir.stdoutAppend);
                    } else {
                        System.out.println(out.toString());
                    }
                } else {
                    if (redir.stdoutTarget != null) {
                        writeToFile(currentDir, redir.stdoutTarget, System.lineSeparator(), redir.stdoutAppend);
                    } else {
                        System.out.println();
                    }
                }
            }
            else if (cmdName.equals("type")) {
                if (tokens.length < 2) {
                    String msg = "type: missing operand" + System.lineSeparator();
                    if (redir.stdoutTarget != null) writeToFile(currentDir, redir.stdoutTarget, msg, redir.stdoutAppend);
                    else System.out.print(msg);
                    continue;
                }
                if (redir.stderrTarget != null) writeToFile(currentDir, redir.stderrTarget, "", redir.stderrAppend);
                String name = tokens[1];
                if (Arrays.asList(buildInCommands).contains(name)){
                    String msg = name+" is a shell builtin" + System.lineSeparator();
                    if (redir.stdoutTarget != null) writeToFile(currentDir, redir.stdoutTarget, msg, redir.stdoutAppend);
                    else System.out.print(msg);
                }
                else{
                    String path = findInPath(name);
                    if (path != null) {
                        String msg = name + " is " + path + System.lineSeparator();
                        if (redir.stdoutTarget != null) writeToFile(currentDir, redir.stdoutTarget, msg, redir.stdoutAppend);
                        else System.out.print(msg);
                    } else {
                        String msg = name + ": not found" + System.lineSeparator();
                        if (redir.stdoutTarget != null) writeToFile(currentDir, redir.stdoutTarget, msg, redir.stdoutAppend);
                        else System.out.print(msg);
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
                    // Configure redirections
                    if (redir.stdoutTarget != null) {
                        File f = resolvePath(currentDir, redir.stdoutTarget);
                        pb.redirectOutput(redir.stdoutAppend ? ProcessBuilder.Redirect.appendTo(f)
                                                            : ProcessBuilder.Redirect.to(f));
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }
                    if (redir.stderrTarget != null) {
                        File f = resolvePath(currentDir, redir.stderrTarget);
                        pb.redirectError(redir.stderrAppend ? ProcessBuilder.Redirect.appendTo(f)
                                                           : ProcessBuilder.Redirect.to(f));
                    } else {
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }
                    try {
                        Process process = pb.start();
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

    // Redirection configuration holder
    private static class Redir {
        String stdoutTarget;
        boolean stdoutAppend;
        String stderrTarget;
        boolean stderrAppend;
    }

    private static File resolvePath(File currentDir, String target) {
        if (target == null) return null;
        if (target.startsWith("/")) return new File(target);
        return new File(currentDir, target);
    }

    private static void writeToFile(File currentDir, String target, String content, boolean append) {
        try {
            File f = resolvePath(currentDir, target);
            Path p = f.toPath();
            if (append) {
                Files.write(p, content.getBytes(StandardCharsets.UTF_8),
                           StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
            } else {
                Files.write(p, content.getBytes(StandardCharsets.UTF_8),
                           StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            }
        } catch (IOException e) {
            // If writing fails, print nothing (real shells may report errors to stderr)
        }
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
