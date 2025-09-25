[![progress-banner](https://backend.codecrafters.io/progress/shell/355d12fc-406f-4fac-ace6-71724fd1c17c)](https://app.codecrafters.io/users/codecrafters-bot?r=2qF)
# My solutions for the CodeCrafters Shell challenge (Java)

This repository contains my solutions for the CodeCrafters "shell" challenge implemented in Java.

Contents
- A single-file Java shell implementation in `src/main/java/Main.java` providing:
   - A REPL with history and basic line editing (arrow keys for navigation).
   - Support for shell builtins: `echo`, `pwd`, `cd`, `exit`, `type`, and `history`.
   - Command history persistence via `history -r/-w/-a` and `HISTFILE` integration.
   - Multi-stage pipelines including pipelines that mix builtins and external commands.
   - Redirections for stdout/stderr with overwrite and append forms.
   - Command autocompletion (TAB) and candidate listing (double-TAB).

How to build

Run Maven to compile and package the project:

```bash
mvn package
```

How to run

Use the provided wrapper script to start the interactive shell (the course runner uses `your_program.sh`):

```bash
./your_program.sh
```

Notes
- This is a compact, single-file implementation intended for the CodeCrafters challenge; it focuses on correctness for the test harness rather than production-grade robustness.
- For more details, see `src/main/java/Main.java`.
1. Commit your changes and run `git push origin master` to submit your solution
