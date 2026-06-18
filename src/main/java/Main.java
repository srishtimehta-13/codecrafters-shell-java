import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
public class Main {
    private static List<String> parseCommand(String command) {

        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaping = false;

        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);

            if(escaping){
                current.append(ch);
                escaping = false;
                continue;
            }


            if(ch == '\\' && !inSingleQuote && !inDoubleQuote){
                escaping = true;
                continue;
            }

            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            }
            else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            }
            else if (Character.isWhitespace(ch) && !inSingleQuote && !inDoubleQuote) {

                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }

            }
            else {
                current.append(ch);
            }

        }

        if (current.length() > 0)
            tokens.add(current.toString());

        return tokens;
    }
    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage
        Scanner sc = new Scanner(System.in);
        String currentDirectory = System.getProperty("user.dir");

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String command = sc.nextLine();
            List<String> parts = parseCommand(command);

            if(parts.get(0).equals("exit")){
                break; // or system.exist(0)
            }

            else if(parts.get(0).equals("echo")){
                for(int i = 1;i<parts.size();i++){
                    if(i>1){
                        System.out.print(" ");
                    }
                    System.out.print(parts.get(i));
                }
                    System.out.println();
            }
            else if(parts.get(0).equals("pwd")){
                System.out.println(currentDirectory);
            }

            else if(parts.get(0).equals("type")){
                if(parts.size() >1){
                    String cmd = parts.get(1);
                    if(cmd.equals("echo")||cmd.equals("exit") || cmd.equals("type") || cmd.equals("pwd") || cmd.equals("cd")){
                        System.out.println(cmd + " is a shell builtin");
                    }
                    else{
                        String path = System.getenv("PATH");
                        String [] directories = path.split(java.io.File.pathSeparator);

                        boolean  found = false;
                        for(String dir : directories){
                            File file = new File(dir,cmd);
                            if(file.exists() && file.canExecute()){
                                System.out.println(cmd + " is " + file.getAbsolutePath());
                                found = true;
                                break;
                            }
                        }
                        if(!found){
                            System.out.println(cmd + ": not found");
                        }
                    }
                }
            }
            else if (parts.get(0).equals("cd")) {

                if (parts.size() > 1) {
                    String destination;

                    if (parts.get(1).equals("~")) {
                        destination = System.getenv("HOME");
                    } else {
                        destination = parts.get(1);
                    }
                    File dir;

                    if (new File(destination).isAbsolute()) {
                        dir = new File(destination);              // Absolute path
                    } else {
                        dir = new File(currentDirectory, destination); // Relative path
                    }

                    if (dir.exists() && dir.isDirectory()) {
                        currentDirectory = dir.getCanonicalPath();
                    } else {
                        System.out.println("cd: " + parts.get(1) + ": No such file or directory");
                    }
                }
            }
            else{
                String path = System.getenv("PATH");
                String[] dirs = path.split(File.pathSeparator);

                boolean found = false;

                for (String dir : dirs) {

                    File file = new File(dir, parts.get(0));

                    if (file.exists() && file.canExecute()) {
                    found = true;
                    break;
                    }
                }

                if (found) {

                    ProcessBuilder pb = new ProcessBuilder(parts);
                    pb.directory(new File(currentDirectory));
                    pb.inheritIO();

                    Process process = pb.start();
                    process.waitFor();

                } else {

                    System.out.println(command + ": command not found");

                }
            }
        }
    }
}

