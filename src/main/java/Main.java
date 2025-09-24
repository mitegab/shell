import java.util.Scanner;

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
            System.out.println(input + ": command not found");
        }
    }
}
