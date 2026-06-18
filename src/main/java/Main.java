import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String command = sc.nextLine();
            if(command.equals("exit")){
                break; // or system.exist(0)
            }

            if(command.startsWith("echo ")){
                System.out.println(command.substring(5));
                continue;
            }
            System.out.println(command + ": command not found");
        }
    }
}

