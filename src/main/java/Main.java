
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
            for (int i = 0; i < parts.size(); i++) {
                if (parts.get(i).equals(">") || parts.get(i).equals("1>")) {
                    redirectOutput = true;
                    outputFile = parts.get(i + 1);
                    parts = new ArrayList<>(parts.subList(0, i));
                    break;
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
                    java.nio.file.Files.write(
                            java.nio.file.Paths.get(outputFile),
                            (output + System.lineSeparator()).getBytes()
                    );
                } else {
                    System.out.println(output);
                }
            } else if (parts.get(0).equals("pwd")) {
                String output = currentDirectory;

                if (redirectOutput) {
                    Files.write(
                            Paths.get(outputFile),
                            (output + System.lineSeparator()).getBytes()
                    );
                } else {
                    System.out.println(output);
                }
            } else if (parts.get(0).equals("type")) {

                if (parts.size() > 1) {

                    String cmd = parts.get(1);
                    String output = "";

                    if (cmd.equals("echo") || cmd.equals("exit")
                            || cmd.equals("type") || cmd.equals("pwd")
                            || cmd.equals("cd")) {

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

                    if (redirectOutput) {

                        Files.write(
                                Paths.get(outputFile),
                                (output + System.lineSeparator()).getBytes()
                        );

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
                        System.out.println("cd: " + parts.get(1) + ": No such file or directory");
                    }
                }
            } else {
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
                        pb.redirectOutput(new File(outputFile));
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    } else {
                        pb.inheritIO();
                    }

                    Process process = pb.start();
                    process.waitFor();
                } else {

                    System.out.println(command + ": command not found");

                }
            }
        }
    }
}
