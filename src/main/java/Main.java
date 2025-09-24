import java.util.Scanner;
import java.util.Arrays;

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
                if (Arrays.asList(buildInCommands).contains(input.substring(5))){
                    System.out.println(input.substring(5)+" is a shell builtin");
                }
                else{
                    System.out.println(input.substring(5)+": not found");
                }
                    }

                else {
                    System.out.println(input + ": command not found");
                }
            }

        }
    }

