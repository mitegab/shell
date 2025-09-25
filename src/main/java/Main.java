import java.util.Scanner;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
 

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        // Track current working directory inside the shell
        File currentDir = new File(System.getProperty("user.dir")).getCanonicalFile();

    // Simple line editor to support TAB completion for builtins
    InputStream in = System.in;
    StringBuilder lineBuffer = new StringBuilder();
    // No explicit tabstop/prompt width needed with newline redraw
    int ignoreSpaces = 0; // number of spaces to consume silently after a detected tab-expansion

    // Try to enable raw mode so we receive key presses like TAB. Ignore failures (e.g., non-tty).
    boolean rawEnabled = setRawMode(true);
    // Track double-TAB state for ambiguous completion lists
    String lastTabPrefix = null;
    int tabPressCount = 0;

        // REPL: print prompt, read chars, handle TAB/backspace/enter, repeat until EOF
        while (true) {
            System.out.print("$ ");
            System.out.flush();

            lineBuffer.setLength(0);

            int ch;
            readLoop:
            while ((ch = in.read()) != -1) {
                if (ch == '\r') {
                    continue;
                }
                if (ch == '\n') {
                    System.out.print("\n");
                    System.out.flush();
                    break readLoop; // process the current line
                }
                if (ignoreSpaces > 0 && ch == ' ') {
                    ignoreSpaces--;
                    continue;
                }
                if (ch == '\t') {
                    // Direct TAB received: complete first token to echo/exit if unambiguous
                    String current = lineBuffer.toString();
                    int firstSpace = current.indexOf(' ');
                    if (firstSpace == -1) {
                        String[] builtins = {"echo", "exit"};
                        LinkedHashSet<String> cands = new LinkedHashSet<>();
                        for (String b : builtins) {
                            if (b.startsWith(current)) cands.add(b);
                        }
                        // Include executables from PATH
                        List<String> execMatches = findExecutablesByPrefix(current);
                        cands.addAll(execMatches);
                        String match = null;
                        if (cands.size() == 1) {
                            match = cands.iterator().next();
                        }
                        if (match != null) {
                            String completed = match + " ";
                            lineBuffer.setLength(0);
                            lineBuffer.append(completed);
                            redrawLine(lineBuffer.toString());
                            ignoreSpaces = 0; // reset any pending space swallowing
                            // reset double-tab state
                            lastTabPrefix = null;
                            tabPressCount = 0;
                        } else {
                            // Ambiguous or no matches
                            if (!execMatches.isEmpty() && cands.size() > 1) {
                                // Try partial completion to the longest common prefix among executables
                                String lcp = longestCommonPrefix(execMatches);
                                if (lcp.length() > current.length()) {
                                    // If LCP uniquely identifies a single executable, add trailing space
                                    List<String> after = findExecutablesByPrefix(lcp);
                                    lineBuffer.setLength(0);
                                    if (after.size() == 1) {
                                        lineBuffer.append(lcp).append(' ');
                                    } else {
                                        lineBuffer.append(lcp);
                                    }
                                    redrawLine(lineBuffer.toString());
                                    lastTabPrefix = null;
                                    tabPressCount = 0;
                                } else {
                                if (current.equals(lastTabPrefix) && tabPressCount >= 1) {
                                    // Second TAB: print list of matches (executables only) sorted, two spaces separated
                                    List<String> sorted = new ArrayList<>(new LinkedHashSet<>(execMatches));
                                    Collections.sort(sorted);
                                    System.out.print("\n");
                                    for (int i = 0; i < sorted.size(); i++) {
                                        if (i > 0) System.out.print("  ");
                                        System.out.print(sorted.get(i));
                                    }
                                    System.out.print("\n");
                                    // Reprint prompt and current buffer (unchanged)
                                    redrawLine(lineBuffer.toString());
                                    // reset state after listing
                                    lastTabPrefix = null;
                                    tabPressCount = 0;
                                } else {
                                    System.out.print("\u0007");
                                    System.out.flush();
                                    lastTabPrefix = current;
                                    tabPressCount = 1;
                                }
                                }
                            } else {
                                // No matches: just bell and reset
                                System.out.print("\u0007");
                                System.out.flush();
                                lastTabPrefix = null;
                                tabPressCount = 0;
                            }
                        }
                    } else {
                        System.out.print("\u0007");
                        System.out.flush();
                    }
                    continue;
                }
                if (ch == ' ') {
                    // Detect terminals that expand TAB into spaces to next tab stop
                    if (!rawEnabled) {
                        String current = lineBuffer.toString();
                        int firstSpace = current.indexOf(' ');
                        if (firstSpace == -1) {
                            String[] builtins = {"echo", "exit"};
                            LinkedHashSet<String> cands = new LinkedHashSet<>();
                            for (String b : builtins) {
                                if (b.startsWith(current)) cands.add(b);
                            }
                            List<String> execMatches = findExecutablesByPrefix(current);
                            cands.addAll(execMatches);
                            if (cands.size() == 1) {
                                String completed = cands.iterator().next() + " ";
                                // Treat this as a TAB expansion: swallow remaining spaces and redraw
                                ignoreSpaces = 4; // small number just in case
                                lineBuffer.setLength(0);
                                lineBuffer.append(completed);
                                redrawLine(lineBuffer.toString());
                                // reset double-tab state
                                lastTabPrefix = null;
                                tabPressCount = 0;
                                continue;
                            } else if (!execMatches.isEmpty() && cands.size() > 1) {
                                // Ambiguous: try partial LCP completion first
                                ignoreSpaces = 4; // swallow expansion spaces
                                String lcp = longestCommonPrefix(execMatches);
                                if (lcp.length() > current.length()) {
                                    List<String> after = findExecutablesByPrefix(lcp);
                                    lineBuffer.setLength(0);
                                    if (after.size() == 1) {
                                        lineBuffer.append(lcp).append(' ');
                                    } else {
                                        lineBuffer.append(lcp);
                                    }
                                    redrawLine(lineBuffer.toString());
                                    lastTabPrefix = null;
                                    tabPressCount = 0;
                                    continue;
                                }
                                // If no progress from LCP, emulate double-TAB list behavior
                                if (current.equals(lastTabPrefix) && tabPressCount >= 1) {
                                    List<String> sorted = new ArrayList<>(new LinkedHashSet<>(execMatches));
                                    Collections.sort(sorted);
                                    System.out.print("\n");
                                    for (int i = 0; i < sorted.size(); i++) {
                                        if (i > 0) System.out.print("  ");
                                        System.out.print(sorted.get(i));
                                    }
                                    System.out.print("\n");
                                    redrawLine(lineBuffer.toString());
                                    lastTabPrefix = null;
                                    tabPressCount = 0;
                                } else {
                                    System.out.print("\u0007");
                                    System.out.flush();
                                    lastTabPrefix = current;
                                    tabPressCount = 1;
                                }
                                continue;
                            }
                        }
                    } else {
                        // In raw mode, never treat spaces as TAB
                        ignoreSpaces = 0;
                    }
                    // Regular space (not a completion): update buffer and redraw
                    lineBuffer.append(' ');
                    redrawLine(lineBuffer.toString());
                    // reset double-tab state when buffer changes
                    lastTabPrefix = null;
                    tabPressCount = 0;
                    continue;
                }
                if (ch == 127 || ch == '\b') { // handle backspace/delete
                    if (lineBuffer.length() > 0) {
                        lineBuffer.setLength(lineBuffer.length() - 1);
                        redrawLine(lineBuffer.toString());
                        // reset double-tab state when buffer changes
                        lastTabPrefix = null;
                        tabPressCount = 0;
                    } else {
                        System.out.print("\u0007");
                        System.out.flush();
                    }
                    continue;
                }
                // Regular printable character: update buffer and redraw
                lineBuffer.append((char) ch);
                redrawLine(lineBuffer.toString());
                // reset double-tab state when buffer changes
                lastTabPrefix = null;
                tabPressCount = 0;
            }

            if (ch == -1) {
                break; // EOF
            }

            String input = lineBuffer.toString();
            input = input.trim();
            if (input.isEmpty()) {
                continue; // empty line, show prompt again
            }

            List<String> tokenList = tokenize(input);
            if (tokenList.isEmpty()) {
                continue;
            }
            
            // Check for pipeline (|) operator
            int pipeIndex = -1;
            for (int i = 0; i < tokenList.size(); i++) {
                if (tokenList.get(i).equals("|")) {
                    pipeIndex = i;
                    break;
                }
            }
            
            if (pipeIndex > 0 && pipeIndex < tokenList.size() - 1) {
                // This is a pipeline: split into left and right commands
                List<String> leftTokens = tokenList.subList(0, pipeIndex);
                List<String> rightTokens = tokenList.subList(pipeIndex + 1, tokenList.size());
                executePipeline(leftTokens, rightTokens, currentDir);
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
            else if (cmdName.equals("exit")) {
                // Only exit when explicitly given 0 as per stage requirements
                if (tokens.length >= 2 && tokens[1].equals("0")) {
                    System.exit(0);
                } else {
                    // Not supported variants: treat as no-op for now
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

    // Restore terminal mode if we changed it
    if (rawEnabled) setRawMode(false);
    scanner.close();
    }

    private static boolean setRawMode(boolean enable) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("/bin/sh");
            cmd.add("-c");
            if (enable) {
                cmd.add("stty -echo -icanon min 1 time 0 2>/dev/null");
            } else {
                cmd.add("stty echo icanon 2>/dev/null");
            }
            Process p = new ProcessBuilder(cmd).inheritIO().start();
            int code = p.waitFor();
            // Return true only if stty succeeded (exit code 0)
            return code == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // Redraw the current input line with prompt
    private static void redrawLine(String buffer) {
        System.out.print('\r');
        System.out.print("$ ");
        System.out.print(buffer);
        // Clear to end of line (ANSI); harmless if not supported
        System.out.print("\u001B[K");
        System.out.flush();
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

    // Find all executable file names in PATH that start with the given prefix.
    private static List<String> findExecutablesByPrefix(String prefix) {
        List<String> results = new ArrayList<>();
        if (prefix == null || prefix.isEmpty()) return results;
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isEmpty()) return results;
        String[] dirs = pathEnv.split(":", -1);
        Set<String> seen = new LinkedHashSet<>();
        for (String dir : dirs) {
            String base = dir.isEmpty() ? "." : dir;
            File d = new File(base);
            if (!d.isDirectory()) continue;
            File[] files = d.listFiles();
            if (files == null) continue;
            for (File f : files) {
                String name = f.getName();
                if (!name.startsWith(prefix)) continue;
                if (f.isFile() && f.canExecute()) {
                    if (seen.add(name)) {
                        results.add(name);
                    }
                }
            }
        }
        return results;
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
                } else if (c == '|') {
                    // Pipe character: emit current token if any, then emit pipe as separate token
                    if (current.length() > 0) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                    tokens.add("|");
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

    // Execute a pipeline between two commands
    private static void executePipeline(List<String> leftTokens, List<String> rightTokens, File currentDir) {
        try {
            // Parse left command (remove redirections, get clean tokens)
            Redir leftRedir = new Redir();
            List<String> leftCleaned = extractRedirectionsAndClean(leftTokens, leftRedir);
            if (leftCleaned.isEmpty()) return;
            
            // Parse right command (remove redirections, get clean tokens) 
            Redir rightRedir = new Redir();
            List<String> rightCleaned = extractRedirectionsAndClean(rightTokens, rightRedir);
            if (rightCleaned.isEmpty()) return;
            
            String leftCmd = leftCleaned.get(0);
            String rightCmd = rightCleaned.get(0);
            
            // Find executables in PATH
            String leftPath = findInPath(leftCmd);
            String rightPath = findInPath(rightCmd);
            
            if (leftPath == null) {
                System.out.println(leftCmd + ": command not found");
                return;
            }
            if (rightPath == null) {
                System.out.println(rightCmd + ": command not found");
                return;
            }
            
            // Create left process command
            List<String> leftCommand = new ArrayList<>();
            leftCommand.add("/bin/sh");
            leftCommand.add("-c");
            leftCommand.add("exec \"$0\" \"$@\"");
            leftCommand.add(leftCmd);
            for (int i = 1; i < leftCleaned.size(); i++) {
                leftCommand.add(leftCleaned.get(i));
            }
            
            // Create right process command
            List<String> rightCommand = new ArrayList<>();
            rightCommand.add("/bin/sh");
            rightCommand.add("-c");
            rightCommand.add("exec \"$0\" \"$@\"");
            rightCommand.add(rightCmd);
            for (int i = 1; i < rightCleaned.size(); i++) {
                rightCommand.add(rightCleaned.get(i));
            }
            
            // Create ProcessBuilders
            ProcessBuilder leftPb = new ProcessBuilder(leftCommand);
            ProcessBuilder rightPb = new ProcessBuilder(rightCommand);
            
            leftPb.directory(currentDir);
            rightPb.directory(currentDir);
            
            // Handle redirections for left command
            if (leftRedir.stderrTarget != null) {
                File f = resolvePath(currentDir, leftRedir.stderrTarget);
                leftPb.redirectError(leftRedir.stderrAppend ? ProcessBuilder.Redirect.appendTo(f) 
                                                           : ProcessBuilder.Redirect.to(f));
            } else {
                leftPb.redirectError(ProcessBuilder.Redirect.INHERIT);
            }
            
            // Handle redirections for right command
            if (rightRedir.stdoutTarget != null) {
                File f = resolvePath(currentDir, rightRedir.stdoutTarget);
                rightPb.redirectOutput(rightRedir.stdoutAppend ? ProcessBuilder.Redirect.appendTo(f)
                                                              : ProcessBuilder.Redirect.to(f));
            } else {
                rightPb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }
            
            if (rightRedir.stderrTarget != null) {
                File f = resolvePath(currentDir, rightRedir.stderrTarget);
                rightPb.redirectError(rightRedir.stderrAppend ? ProcessBuilder.Redirect.appendTo(f)
                                                             : ProcessBuilder.Redirect.to(f));
            } else {
                rightPb.redirectError(ProcessBuilder.Redirect.INHERIT);
            }
            
            // Create the pipeline using ProcessBuilder.startPipeline (Java 9+)
            // If not available, we'll use a different approach
            try {
                List<ProcessBuilder> builders = Arrays.asList(leftPb, rightPb);
                List<Process> processes = ProcessBuilder.startPipeline(builders);
                
                // Wait for all processes to complete
                for (Process p : processes) {
                    p.waitFor();
                }
            } catch (NoSuchMethodError e) {
                // Fallback for older Java versions - use manual connection
                Process leftProcess = leftPb.start();
                rightPb.redirectInput(ProcessBuilder.Redirect.PIPE);
                Process rightProcess = rightPb.start();
                
                // Use a separate thread to copy data
                Thread copyThread = new Thread(() -> {
                    try (var leftOutput = leftProcess.getInputStream();
                         var rightInput = rightProcess.getOutputStream()) {
                        
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = leftOutput.read(buffer)) != -1) {
                            rightInput.write(buffer, 0, bytesRead);
                        }
                    } catch (IOException ex) {
                        // Ignore - likely process terminated
                    }
                });
                
                copyThread.start();
                leftProcess.waitFor();
                copyThread.join();
                rightProcess.waitFor();
            }
            
        } catch (IOException | InterruptedException e) {
            System.out.println("Pipeline execution failed: " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    // Helper method to extract redirections from tokens and return clean command tokens
    private static List<String> extractRedirectionsAndClean(List<String> tokenList, Redir redir) {
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
        return cleaned;
    }

    // Compute the longest common prefix among a list of strings
    private static String longestCommonPrefix(List<String> items) {
        if (items == null || items.isEmpty()) return "";
        String prefix = items.get(0);
        for (int i = 1; i < items.size(); i++) {
            String s = items.get(i);
            int j = 0;
            int max = Math.min(prefix.length(), s.length());
            while (j < max && prefix.charAt(j) == s.charAt(j)) {
                j++;
            }
            prefix = prefix.substring(0, j);
            if (prefix.isEmpty()) break;
        }
        return prefix;
    }
}
