import java.io.File;
import java.util.Scanner;
public class Main {
    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String command = sc.nextLine();
            String [] parts  = command.split(" ",2);

            if(parts[0].equals("exit")){
                break; // or system.exist(0)
            }

            else if(parts[0].equals("echo")){
                if(parts.length >1)
                    System.out.println(parts[1]);
                else
                    System.out.println();
            }

            else if(parts[0].equals("type")){
                if(parts.length >1){
                    String cmd = parts[1];
                    if(cmd.equals("echo")||cmd.equals("exit") || cmd.equals("type")){
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
            else{
                System.out.println(command + ": command not found");
            }
        }
    }
}

