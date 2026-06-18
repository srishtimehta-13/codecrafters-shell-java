import java.io.File;
import java.util.Scanner;
public class Main {
    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage
        Scanner sc = new Scanner(System.in);
        String currentDirectory = System.getProperty("user.dir");

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String command = sc.nextLine();
            String [] parts  = command.trim().split("\\s+");

            if(parts[0].equals("exit")){
                break; // or system.exist(0)
            }

            else if(parts[0].equals("echo")){
                for(int i = 1;i<parts.length;i++){
                    if(i>1){
                        System.out.print(" ");
                    }
                    System.out.print(parts[i]);
                }
                    System.out.println();
            }
            else if(parts[0].equals("pwd")){
                System.out.println(System.getProperty(currentDirectory));
            }

            else if(parts[0].equals("type")){
                if(parts.length >1){
                    String cmd = parts[1];
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
            else if (parts[0].equals("cd")){
                if(parts.length > 1){
                    File dir = new File(parts[1]);
                    if(dir.exists() && dir.isDirectory()){
                        currentDirectory = dir.getAbsolutePath();
                    }
                    else{
                        System.out.println("cd: "+ parts[1]+ ": No such file or directort");
                    }
                }
            }
            else{
                String path = System.getenv("PATH");
                String[] dirs = path.split(File.pathSeparator);

                boolean found = false;

                for (String dir : dirs) {

                    File file = new File(dir, parts[0]);

                    if (file.exists() && file.canExecute()) {
                    found = true;
                    break;
                    }
                }

                if (found) {

                    ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
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

