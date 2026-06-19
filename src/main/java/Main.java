
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

    private static void runPipeline(List<String> left,
            List<String> right,
            String currentDirectory) throws Exception {

        ProcessBuilder pb1 = new ProcessBuilder(left);
        ProcessBuilder pb2 = new ProcessBuilder(right);

        pb1.directory(new File(currentDirectory));
        pb2.directory(new File(currentDirectory));

        pb1.environment().put("PATH", System.getenv("PATH"));
        pb2.environment().put("PATH", System.getenv("PATH"));

        pb1.redirectError(ProcessBuilder.Redirect.INHERIT);
        pb2.redirectError(ProcessBuilder.Redirect.INHERIT);

        Process p1 = pb1.start();
        Process p2 = pb2.start();

        Thread pipeThread = new Thread(() -> {
            try (
                    var in = p1.getInputStream(); var out = p2.getOutputStream()) {
                in.transferTo(out);
                out.flush();
            } catch (Exception ignored) {
                // head exited -> broken pipe is expected
            }
        });

        pipeThread.start();

        // Print output of second command
        Thread outputThread = new Thread(() -> {
            try (var in = p2.getInputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    System.out.write(buffer, 0, n);
                    System.out.flush();   // IMPORTANT
                }
            } catch (Exception ignored) {
            }
        });

        outputThread.start();

        // Wait only for second command
        p2.waitFor();
        outputThread.join();

        // Stop first command if it's still running (tail -f)
        if (p1.isAlive()) {
            p1.destroyForcibly();
            p1.waitFor();
        }

        pipeThread.join();
    }

    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage
        Scanner sc = new Scanner(System.in);
        String currentDirectory = System.getProperty("user.dir");

        while (true) {
            reapJobs(false);
            System.out.print("$ ");
            System.out.flush();

            String command = sc.nextLine();
            List<String> parts = parseCommand(command);
            int pipeIndex = parts.indexOf("|");

            if (pipeIndex != -1) {
                List<String> left = new ArrayList<>(parts.subList(0, pipeIndex));
                List<String> right = new ArrayList<>(parts.subList(pipeIndex + 1, parts.size()));

                runPipeline(left, right, currentDirectory);
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
                            || cmd.equals("cd") || cmd.equals("jobs")) {

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
