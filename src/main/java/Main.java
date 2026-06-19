
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
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

            if (escaping) {
                current.append(ch);
                escaping = false;
                continue;
            }

            if (ch == '\\') {
                if (!inSingleQuote && !inDoubleQuote) {
                    escaping = true;
                    continue;
                }

                if (inDoubleQuote) {
                    if (i + 1 < command.length()) {
                        char next = command.charAt(i + 1);
                        if (next == '"' || next == '\\') {
                            current.append(next);
                            i++;
                        } else {
                            current.append('\\');
                        }
                        continue;
                    }
                    current.append('\\');
                    continue;
                }

            }

            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (Character.isWhitespace(ch) && !inSingleQuote && !inDoubleQuote) {

                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }

            } else {
                current.append(ch);
            }

        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

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
            String outputFile = null;
            boolean redirectOutput = false;
            String errorFile = null;
            boolean redirectError = false;
            boolean appendOutput = false;
            boolean appendError = false;
            boolean background = false;

            for (int i = 0; i < parts.size(); i++) {
                if (parts.get(i).equals(">") || parts.get(i).equals("1>")) {
                    redirectOutput = true;
                    outputFile = parts.get(i + 1);
                    parts = new ArrayList<>(parts.subList(0, i));
                    break;
                }
                if (parts.get(i).equals(">>") || parts.get(i).equals("1>>")) {
                    redirectOutput = true;
                    appendOutput = true;
                    outputFile = parts.get(i + 1);
                    parts = new ArrayList<>(parts.subList(0, i));
                    break;
                }
                if (parts.get(i).equals("2>")) {
                    redirectError = true;
                    appendError = false;
                    errorFile = parts.get(i + 1);
                    parts = new ArrayList<>(parts.subList(0, i));
                    break;
                }
                if (parts.get(i).equals("2>>")) {
                    redirectError = true;
                    appendError = true;
                    errorFile = parts.get(i + 1);
                    parts = new ArrayList<>(parts.subList(0, i));
                    break;
                }

                if(!parts.isEmpty()&& parts.get(parts.size() - 1).equals("&")){
                    background = true;
                    parts.remove(parts.size() -1);
                }
            }

            if (parts.get(0).equals("exit")) {
                break; // or system.exist(0)
            } else if (parts.get(0).equals("echo")) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < parts.size(); i++) {
                    if (i > 1) {
                        sb.append(" ");
                    }
                    sb.append(parts.get(i));
                }
                String output = sb.toString();
                if (redirectOutput) {
                    if (appendOutput) {
                        Files.write(
                                Paths.get(outputFile),
                                (output + System.lineSeparator()).getBytes(),
                                java.nio.file.StandardOpenOption.CREATE,
                                java.nio.file.StandardOpenOption.APPEND
                        );
                    } else {
                        Files.write(
                                Paths.get(outputFile),
                                (output + System.lineSeparator()).getBytes()
                        );
                    }
                } else {
                    System.out.println(output);
                }
                if (redirectError) {
                    Files.write(Paths.get(errorFile), new byte[0]);
                }
            } else if (parts.get(0).equals("pwd")) {
                String output = currentDirectory;

                if (redirectOutput) {
                    if (appendOutput) {
                        Files.write(
                                Paths.get(outputFile),
                                (output + System.lineSeparator()).getBytes(),
                                java.nio.file.StandardOpenOption.CREATE,
                                java.nio.file.StandardOpenOption.APPEND
                        );
                    } else {
                        Files.write(
                                Paths.get(outputFile),
                                (output + System.lineSeparator()).getBytes()
                        );
                    }
                } else {
                    System.out.println(output);
                }
                if (redirectError) {
                    Files.write(Paths.get(errorFile), new byte[0]);
                }
            } else if (parts.get(0).equals("type")) {

                if (parts.size() > 1) {

                    String cmd = parts.get(1);
                    String output = "";

                    if (cmd.equals("echo") || cmd.equals("exit")
                            || cmd.equals("type") || cmd.equals("pwd")
                            || cmd.equals("cd")|| cmd.equals("jobs")) {

                        output = cmd + " is a shell builtin";

                    } else {

                        String path = System.getenv("PATH");
                        String[] directories = path.split(File.pathSeparator);

                        File executable = null;

                        for (String dir : directories) {

                            File file = new File(dir, cmd);

                            if (file.exists() && file.canExecute()) {
                                executable = file;
                                break;
                            }
                        }

                        if (executable != null) {
                            output = cmd + " is " + executable.getAbsolutePath();
                        } else {
                            output = cmd + ": not found";
                        }
                    }
                    if (redirectError) {
                        Files.write(Paths.get(errorFile), new byte[0]);
                    }

                    if (redirectOutput) {
                        if (appendOutput) {
                            Files.write(
                                    Paths.get(outputFile),
                                    (output + System.lineSeparator()).getBytes(),
                                    java.nio.file.StandardOpenOption.CREATE,
                                    java.nio.file.StandardOpenOption.APPEND
                            );
                        } else {
                            Files.write(
                                    Paths.get(outputFile),
                                    (output + System.lineSeparator()).getBytes()
                            );
                        }
                    } else {
                        System.out.println(output);
                    }
                }

            } else if (parts.get(0).equals("cd")) {

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
                        String error = "cd: " + parts.get(1) + ": No such file or directory";

                        if (redirectError) {
                            if (appendError) {
                                Files.write(
                                        Paths.get(errorFile),
                                        (error + System.lineSeparator()).getBytes(),
                                        java.nio.file.StandardOpenOption.CREATE,
                                        java.nio.file.StandardOpenOption.APPEND
                                );
                            } else {
                                Files.write(
                                        Paths.get(errorFile),
                                        (error + System.lineSeparator()).getBytes()
                                );
                            }
                        } else {
                            System.out.println(error);
                        }
                    }
                }
            } 
            else if(parts.get(0).equals("jobs")){

            }
            else {
                String path = System.getenv("PATH");
                String[] dirs = path.split(File.pathSeparator);

                File executable = null;

                for (String dir : dirs) {

                    File file = new File(dir, parts.get(0));

                    if (file.exists() && file.canExecute()) {
                        executable = file;
                        break;
                    }
                }

                if (executable != null) {

                    ProcessBuilder pb = new ProcessBuilder(parts);
                    pb.directory(new File(currentDirectory));

                    // Preserve the PATH so ProcessBuilder can find the executable
                    pb.environment().put("PATH", System.getenv("PATH"));

                    if (redirectOutput) {
                        if (appendOutput) {
                            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(outputFile)));
                        } else {
                            pb.redirectOutput(new File(outputFile));
                        }
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }
                    if (redirectError) {
                        if (appendError) {
                            pb.redirectError(ProcessBuilder.Redirect.appendTo(new File(errorFile)));
                        } else {
                            pb.redirectError(new File(errorFile));
                        }
                    } else {
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }

                    Process process = pb.start();
                    if(background){
                        System.out.println("[1] " + process.pid());
                    }else{
                        process.waitFor();
                    }

                    
                } else {
                    String error = command + ": command not found";

                    if (redirectError) {
                        if (appendError) {
                            Files.write(
                                    Paths.get(errorFile),
                                    (error + System.lineSeparator()).getBytes(),
                                    java.nio.file.StandardOpenOption.CREATE,
                                    java.nio.file.StandardOpenOption.APPEND
                            );
                        } else {
                            Files.write(
                                    Paths.get(errorFile),
                                    (error + System.lineSeparator()).getBytes()
                            );
                        }
                    } else {
                        System.out.println(error);
                    }

                }
            }
        }
    }
}
