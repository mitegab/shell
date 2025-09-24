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


            if (input.equals("exit 0")) {
                break; // EOF - exit the REPL
            }
            System.out.println(input + ": command not found");
        }
    }
}
