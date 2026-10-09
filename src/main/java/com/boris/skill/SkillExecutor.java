package com.boris.skill;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class SkillExecutor {

    private final Path workingDir;
    private final boolean isWindows;

    public SkillExecutor(Path workingDir) {
        this.workingDir = workingDir;
        this.isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    }

    public void execute(Skill skill, Map<String, String> args, Consumer<String> output) {
        String command = isWindows ? skill.getCommandWindows() : skill.getCommand();

        if (command == null || command.isEmpty()) {
            output.accept("[error] Skill sin comando para este SO");
            return;
        }

        try {
            ProcessBuilder pb;
            if (isWindows) {
                pb = new ProcessBuilder("powershell", "-Command", command);
            } else {
                pb = new ProcessBuilder("bash", "-c", command);
            }

            pb.directory(workingDir.toFile());
            pb.redirectErrorStream(true);

            Map<String, String> env = pb.environment();
            if (args != null) {
                for (Map.Entry<String, String> arg : args.entrySet()) {
                    String envName = "SKILL_" + arg.getKey().toUpperCase();
                    env.put(envName, arg.getValue());
                }
            }

            Process process = pb.start();

            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        output.accept(line);
                    }
                } catch (Exception e) {
                    output.accept("[error] " + e.getMessage());
                }
            });
            reader.start();

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                output.accept("[error] Timeout - skill abortada");
            }

            reader.join(1000);

        } catch (Exception e) {
            output.accept("[error] " + e.getMessage());
        }
    }
}
