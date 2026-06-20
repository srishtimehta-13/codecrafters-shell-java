
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

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

    static class Job {

        int jobNumber;
        Process process;
        String command;

        Job(int jobNumber, Process process, String command) {
            this.jobNumber = jobNumber;
            this.process = process;
            this.command = command;
        }
    }

    private static void reapJobs(boolean printRunningJobs) throws InterruptedException {

        List<Job> finishedJobs = new ArrayList<>();

        int last = jobs.size() - 1;
        int secondLast = jobs.size() - 2;

        for (int i = 0; i < jobs.size(); i++) {

            Job job = jobs.get(i);

            char marker = ' ';
            if (i == last) {
                marker = '+';
            } else if (i == secondLast) {
                marker = '-';
            }

            if (job.process.isAlive()) {

                if (printRunningJobs) {
                    System.out.printf(
                            "[%d]%c  %-24s%s &%n",
                            job.jobNumber,
                            marker,
                            "Running",
                            job.command
                    );
                }

            } else {

                job.process.waitFor();

                System.out.printf(
                        "[%d]%c  %-24s%s%n",
                        job.jobNumber,
                        marker,
                        "Done",
                        job.command
                );

                finishedJobs.add(job);
            }
        }

        jobs.removeAll(finishedJobs);
    }

    static List<Job> jobs = new ArrayList<>();
    static List<String> history = new ArrayList<>();

    private static int getNextJobNumber() {
        if (jobs.isEmpty()) {
            return 1;
        }

        int max = 0;
        for (Job job : jobs) {
            max = Math.max(max, job.jobNumber);
        }

        return max + 1;
    }

    private static void runPipeline(
            List<List<String>> commands,
            String cwd) throws Exception {

        List<ProcessBuilder> builders = new ArrayList<>();

        for (List<String> cmd : commands) {

            ProcessBuilder pb = new ProcessBuilder(cmd);

            pb.directory(new File(cwd));
            pb.environment().put("PATH", System.getenv("PATH"));
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);

            builders.add(pb);
        }

        // Last command prints to terminal
        builders.get(builders.size() - 1)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT);

        List<Process> processes = ProcessBuilder.startPipeline(builders);

        // Wait ONLY for the last process
        processes.get(processes.size() - 1).waitFor();

        // Kill anything still running (tail -f etc.)
        for (Process p : processes) {
            if (p.isAlive()) {
                p.destroyForcibly();
            }
        }

        // Reap all processes
        for (Process p : processes) {
            p.waitFor();
        }
    }

    private static boolean isBuiltin(String cmd) {
        return switch (cmd) {
            case "echo", "pwd", "type", "cd", "exit", "jobs", "history" ->
                true;
            default ->
                false;
        };
    }

    private static String runBuiltin(List<String> parts, String currentDirectory) {

        switch (parts.get(0)) {

            case "echo":
                return String.join(" ", parts.subList(1, parts.size())) + "\n";

            case "pwd":
                return currentDirectory + "\n";

            case "type": {
                String cmd = parts.get(1);

                if (isBuiltin(cmd)) {
                    return cmd + " is a shell builtin\n";
                }

                String path = System.getenv("PATH");
                String[] directories = path.split(File.pathSeparator);

                for (String dir : directories) {
                    File file = new File(dir, cmd);
                    if (file.exists() && file.canExecute()) {
                        return cmd + " is " + file.getAbsolutePath() + "\n";
                    }
                }

                return cmd + ": not found\n";
            }
            case "history": {
                if(parts.size()>= 3 && parts.get(1).equals("-r")){
                    return "";
                }
                StringBuilder sb = new StringBuilder();

                int start = 0;

                if (parts.size() > 1) {
                    int n = Integer.parseInt(parts.get(1));
                    start = Math.max(0, history.size() - n);
                }

                for (int i = start; i < history.size(); i++) {
                    sb.append(String.format("%5d  %s%n", i + 1, history.get(i)));
                }

                return sb.toString();
            }

            default:
                return "";
        }
    }

    private static void runBuiltinToExternal(
            List<String> left,
            List<String> right,
            String currentDirectory) throws Exception {

        String output = runBuiltin(left, currentDirectory);

        ProcessBuilder pb = new ProcessBuilder(right);
        pb.directory(new File(currentDirectory));
        pb.environment().put("PATH", System.getenv("PATH"));

        Process p = pb.start();

        p.getOutputStream().write(output.getBytes());
        p.getOutputStream().close();

        p.getInputStream().transferTo(System.out);

        p.waitFor();
    }

    private static void runExternalToBuiltin(
            List<String> left,
            List<String> right,
            String currentDirectory) throws Exception {

        ProcessBuilder pb = new ProcessBuilder(left);

        pb.directory(new File(currentDirectory));
        pb.environment().put("PATH", System.getenv("PATH"));

        Process p = pb.start();

        // Drain output so the external command doesn't block
        p.getInputStream().transferTo(OutputStream.nullOutputStream());

        p.waitFor();

        System.out.print(runBuiltin(right, currentDirectory));
    }

    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage
        DefaultParser parser = new DefaultParser();
        parser.setEscapeChars(new char[0]);
        
        Terminal terminal = TerminalBuilder.terminal();
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .parser(parser) // Inject the custom parser here
                .build();

        String currentDirectory = System.getProperty("user.dir");

        while (true) {
            reapJobs(false);

            String command;
            try {
                command = reader.readLine("$ ");
            } catch (org.jline.reader.UserInterruptException | org.jline.reader.EndOfFileException e) {
                break; // Handle Ctrl+C or Ctrl+D to gracefully exit
            }

            history.add(command); // Keep your manual history for your "history" builtin
            List<String> parts = parseCommand(command);
            List<List<String>> commands = new ArrayList<>();
            List<String> current = new ArrayList<>();

            for (String token : parts) {
                if (token.equals("|")) {
                    commands.add(current);
                    current = new ArrayList<>();
                } else {
                    current.add(token);
                }
            }
            commands.add(current);

            if (commands.size() > 1) {

                boolean hasBuiltin = false;

                for (List<String> cmd : commands) {
                    if (isBuiltin(cmd.get(0))) {
                        hasBuiltin = true;
                        break;
                    }
                }

                if (!hasBuiltin) {

                    // All commands are external
                    runPipeline(commands, currentDirectory);

                } else {

                    // Builtin pipeline (only two commands for this stage)
                    List<String> left = commands.get(0);
                    List<String> right = commands.get(1);

                    boolean leftBuiltin = isBuiltin(left.get(0));
                    boolean rightBuiltin = isBuiltin(right.get(0));

                    if (leftBuiltin && !rightBuiltin) {

                        runBuiltinToExternal(left, right, currentDirectory);

                    } else if (!leftBuiltin && rightBuiltin) {

                        runExternalToBuiltin(left, right, currentDirectory);

                    } else if (leftBuiltin && rightBuiltin) {

                        System.out.print(runBuiltin(right, currentDirectory));

                    } else {

                        // Should never reach here because hasBuiltin == true
                        runPipeline(commands, currentDirectory);
                    }
                }

                continue;
            }
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
            }
            if (!parts.isEmpty() && parts.get(parts.size() - 1).equals("&")) {
                background = true;
                parts.remove(parts.size() - 1);
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
                            || cmd.equals("cd") || cmd.equals("jobs") || cmd.equals("history")) {

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

            } else if (parts.get(0).equals("history")) {
                if (parts.size() >= 3 && parts.get(1).equals("-r")) {
                    String filePath = parts.get(2);
                    try {
                        List<String> lines = Files.readAllLines(Paths.get(filePath));
                        for (String fileLine : lines) {
                            if (!fileLine.trim().isEmpty()) {
                                history.add(fileLine);             // Add to your custom history list
                                reader.getHistory().add(fileLine); // Add to JLine's arrow-key buffer
                            }
                        }
                    } catch (IOException e) {
                        System.err.println("history: cannot read file: " + e.getMessage());
                    }
                    continue; // Skip the printing logic below and loop to next prompt
                }
                int start = 0;

                if (parts.size() > 1) {
                    int n = Integer.parseInt(parts.get(1));
                    start = Math.max(0, history.size() - n);
                }

                for (int i = start; i < history.size(); i++) {
                    System.out.printf("%5d  %s%n", i + 1, history.get(i));
                }

            } else if (parts.get(0).equals("jobs")) {

                reapJobs(true);
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
                    pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
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
                    if (background) {
                        int jobNumber = getNextJobNumber();

                        Job job = new Job(jobNumber, process, String.join(" ", parts));
                        jobs.add(job);

                        System.out.println("[" + jobNumber + "] " + process.pid());
                    } else {
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
