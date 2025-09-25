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
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
 

public class Main {
    // In-memory command history (stores trimmed input lines in order)
    private static final List<String> HISTORY = new ArrayList<>();
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

            // Record every non-empty command line in history BEFORE executing it
            HISTORY.add(input);

            List<String> tokenList = tokenize(input);
            if (tokenList.isEmpty()) {
                continue;
            }
            
            // Check for pipelines. If multiple pipes, handle N-stage pipeline
            int pipeCount = 0;
            for (String t : tokenList) if ("|".equals(t)) pipeCount++;
            if (pipeCount >= 1) {
                // Split into stages
                List<List<String>> stages = new ArrayList<>();
                List<String> cur = new ArrayList<>();
                for (String t : tokenList) {
                    if ("|".equals(t)) {
                        stages.add(cur);
                        cur = new ArrayList<>();
                    } else {
                        cur.add(t);
                    }
                }
                stages.add(cur);
                executePipelineChain(stages, currentDir);
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
            String[] buildInCommands = {"exit", "echo", "type", "pwd", "cd", "history"};

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
            else if (cmdName.equals("history")) {
                if (redir.stderrTarget != null) writeToFile(currentDir, redir.stderrTarget, "", redir.stderrAppend);
                int limit = -1; // -1 means print all
                if (tokens.length >= 2) {
                    try {
                        limit = Integer.parseInt(tokens[1]);
                    } catch (NumberFormatException nfe) {
                        limit = -1; // ignore invalid argument for now
                    }
                }
                StringBuilder out = new StringBuilder();
                int total = HISTORY.size();
                int start = 1;
                if (limit >= 0) {
                    start = Math.max(1, total - limit + 1);
                }
                for (int i = start; i <= total; i++) {
                    String cmd = HISTORY.get(i - 1);
                    out.append(String.format("%5d  %s%n", i, cmd));
                }
                String msg = out.toString();
                if (redir.stdoutTarget != null) writeToFile(currentDir, redir.stdoutTarget, msg, redir.stdoutAppend);
                else System.out.print(msg);
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

    // Execute a pipeline between two commands (replaced by generic chain executor)

    // Execute N-stage pipeline with builtin/external mixing
    private static void executePipelineChain(List<List<String>> stages, File currentDir) {
        try {
            // Prepare per-stage cleaned tokens and redirections
            int n = stages.size();
            List<List<String>> cleanedStages = new ArrayList<>(n);
            List<Redir> redirs = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                Redir r = new Redir();
                List<String> cleaned = extractRedirectionsAndClean(stages.get(i), r);
                if (cleaned.isEmpty()) return; // nothing to run
                cleanedStages.add(cleaned);
                redirs.add(r);
            }

            // Only apply stdout/stderr redirection of the LAST stage to the final output.
            Redir lastRedir = redirs.get(n - 1);

            // If all stages are external commands, use native streaming pipeline
            boolean allExternal = true;
            for (List<String> ts : cleanedStages) {
                if (isPipelineBuiltin(ts.get(0))) { allExternal = false; break; }
            }
            if (allExternal) {
                List<ProcessBuilder> builders = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    List<String> tokens = cleanedStages.get(i);
                    String cmd = tokens.get(0);
                    // Build command via sh -c so argv[0] is the typed name
                    List<String> command = new ArrayList<>();
                    command.add("/bin/sh");
                    command.add("-c");
                    command.add("exec \"$0\" \"$@\"");
                    command.add(cmd);
                    for (int a = 1; a < tokens.size(); a++) command.add(tokens.get(a));
                    ProcessBuilder pb = new ProcessBuilder(command);
                    pb.directory(currentDir);
                    // Only apply last stage redirections
                    if (i == n - 1) {
                        if (lastRedir.stdoutTarget != null) {
                            File f = resolvePath(currentDir, lastRedir.stdoutTarget);
                            pb.redirectOutput(lastRedir.stdoutAppend ? ProcessBuilder.Redirect.appendTo(f)
                                                                     : ProcessBuilder.Redirect.to(f));
                        } else {
                            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                        }
                        if (lastRedir.stderrTarget != null) {
                            File f = resolvePath(currentDir, lastRedir.stderrTarget);
                            pb.redirectError(lastRedir.stderrAppend ? ProcessBuilder.Redirect.appendTo(f)
                                                                   : ProcessBuilder.Redirect.to(f));
                        } else {
                            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                        }
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.PIPE); // will be wired by startPipeline
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }
                    builders.add(pb);
                }
                List<Process> procs = ProcessBuilder.startPipeline(builders);
                for (Process p : procs) p.waitFor();
                return;
            }

            // Stream the pipeline stage-by-stage in memory when builtins are involved.
            // Strategy: for each stage i
            //  - If external: start process, feed previous output (if any), capture stdout
            //  - If builtin: run via execBuiltinForPipeline with proper input/output stream
            byte[] prevOutput = null; // null means no stdin provided
            for (int i = 0; i < n; i++) {
                List<String> tokens = cleanedStages.get(i);
                String cmd = tokens.get(0);
                boolean isBuiltin = isPipelineBuiltin(cmd);
                boolean isLast = (i == n - 1);

                // Decide output sink for this stage:
                OutputStream stageOut;
                if (isLast) {
                    // Honor last stage redirection
                    if (lastRedir.stdoutTarget != null) {
                        File f = resolvePath(currentDir, lastRedir.stdoutTarget);
                        stageOut = Files.newOutputStream(
                            f.toPath(),
                            lastRedir.stdoutAppend ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND}
                                                   : new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING});
                    } else {
                        stageOut = System.out;
                    }
                } else {
                    // Intermediate stage: capture to buffer
                    stageOut = new ByteArrayOutputStream();
                }

                try (OutputStream os = (stageOut == System.out ? null : stageOut)) {
                    OutputStream actualOut = (os == null ? System.out : os);
                    if (isBuiltin) {
                        execBuiltinForPipeline(tokens, currentDir,
                            prevOutput == null ? null : new ByteArrayInputStream(prevOutput),
                            actualOut);
                    } else {
                        // Build external command
                        List<String> command = new ArrayList<>();
                        command.add("/bin/sh");
                        command.add("-c");
                        command.add("exec \"$0\" \"$@\"");
                        command.add(cmd);
                        for (int a = 1; a < tokens.size(); a++) command.add(tokens.get(a));

                        ProcessBuilder pb = new ProcessBuilder(command);
                        pb.directory(currentDir);

                        // stderr handling: for intermediate stages, inherit; for last stage, honor lastRedir
                        if (isLast && lastRedir.stderrTarget != null) {
                            File f = resolvePath(currentDir, lastRedir.stderrTarget);
                            pb.redirectError(lastRedir.stderrAppend ? ProcessBuilder.Redirect.appendTo(f)
                                                                    : ProcessBuilder.Redirect.to(f));
                        } else {
                            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                        }

                        if (prevOutput == null) {
                            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                        } else {
                            pb.redirectInput(ProcessBuilder.Redirect.PIPE);
                        }

                        if (isLast && lastRedir.stdoutTarget != null) {
                            // When last stage has redirection, let ProcessBuilder write directly to file
                            File f = resolvePath(currentDir, lastRedir.stdoutTarget);
                            pb.redirectOutput(lastRedir.stdoutAppend ? ProcessBuilder.Redirect.appendTo(f)
                                                                     : ProcessBuilder.Redirect.to(f));
                            Process p = pb.start();
                            if (prevOutput != null) {
                                try (OutputStream pin = p.getOutputStream()) {
                                    pin.write(prevOutput);
                                }
                            }
                            p.waitFor();
                            // nothing to capture further
                        } else {
                            pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
                            Process p = pb.start();
                            if (prevOutput != null) {
                                try (OutputStream pin = p.getOutputStream()) {
                                    pin.write(prevOutput);
                                }
                            }
                            // Read process stdout and forward to stageOut (System.out or buffer)
                            try (InputStream procOut = p.getInputStream()) {
                                byte[] buf = new byte[8192];
                                int r;
                                while ((r = procOut.read(buf)) != -1) {
                                    actualOut.write(buf, 0, r);
                                }
                            }
                            actualOut.flush();
                            p.waitFor();
                        }
                    }

                    // Prepare prevOutput for next stage if needed
                    if (!isLast) {
                        if (actualOut instanceof ByteArrayOutputStream) {
                            prevOutput = ((ByteArrayOutputStream) actualOut).toByteArray();
                        } else if (stageOut instanceof ByteArrayOutputStream) {
                            prevOutput = ((ByteArrayOutputStream) stageOut).toByteArray();
                        } else {
                            // If output went to System.out unexpectedly, no data for next stage
                            prevOutput = null;
                        }
                    }
                }
            }
            
        } catch (IOException | InterruptedException e) {
            System.out.println("Pipeline execution failed: " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static boolean isPipelineBuiltin(String cmd) {
        return "echo".equals(cmd) || "type".equals(cmd) || "pwd".equals(cmd) || "history".equals(cmd);
    }

    // Minimal builtin executor for pipeline contexts. Writes to provided OutputStream only (stdout).
    private static void execBuiltinForPipeline(List<String> tokens, File currentDir, InputStream in, OutputStream out) {
        String cmd = tokens.get(0);
        try {
            if ("echo".equals(cmd)) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < tokens.size(); i++) {
                    if (i > 1) sb.append(' ');
                    sb.append(tokens.get(i));
                }
                sb.append(System.lineSeparator());
                out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                out.flush();
            } else if ("pwd".equals(cmd)) {
                String line;
                try {
                    line = currentDir.getCanonicalPath() + System.lineSeparator();
                } catch (IOException e) {
                    line = currentDir.getPath() + System.lineSeparator();
                }
                out.write(line.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } else if ("type".equals(cmd)) {
                String[] buildInCommands = {"exit", "echo", "type", "pwd", "cd", "history"};
                String msg;
                if (tokens.size() < 2) {
                    msg = "type: missing operand" + System.lineSeparator();
                } else {
                    String name = tokens.get(1);
                    if (Arrays.asList(buildInCommands).contains(name)) {
                        msg = name + " is a shell builtin" + System.lineSeparator();
                    } else {
                        String path = findInPath(name);
                        if (path != null) msg = name + " is " + path + System.lineSeparator();
                        else msg = name + ": not found" + System.lineSeparator();
                    }
                }
                out.write(msg.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } else if ("history".equals(cmd)) {
                int limit = -1;
                if (tokens.size() >= 2) {
                    try {
                        limit = Integer.parseInt(tokens.get(1));
                    } catch (NumberFormatException nfe) {
                        limit = -1;
                    }
                }
                int total = HISTORY.size();
                int start = 1;
                if (limit >= 0) {
                    start = Math.max(1, total - limit + 1);
                }
                for (int i = start; i <= total; i++) {
                    String line = String.format("%5d  %s%n", i, HISTORY.get(i - 1));
                    out.write(line.getBytes(StandardCharsets.UTF_8));
                }
                out.flush();
            }
        } catch (IOException ioe) {
            // swallow for pipeline context
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
