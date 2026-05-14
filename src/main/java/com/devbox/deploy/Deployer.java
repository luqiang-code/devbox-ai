package com.devbox.deploy;

import com.devbox.ai.AiProvider;
import com.devbox.model.ProjectInfo;

import java.nio.file.*;

public class Deployer {
    private final AiProvider ai;
    private final String workDir;

    public Deployer(AiProvider ai, String workDir) {
        this.ai = ai;
        this.workDir = workDir;
    }

    public void deploy(ProjectInfo project) throws Exception {
        Path repoPath = Paths.get(workDir, project.getName());

        System.out.println(" Deploying " + project.getName() + "...");

        String cmd;
        if (!project.getRunCommand().isEmpty()) {
            cmd = project.getRunCommand();
        } else {
            cmd = inferRunCommand(project);
        }

        System.out.println("  Run command: " + cmd);
        System.out.println("  Working dir: " + repoPath);

        ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd);
        pb.directory(repoPath.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        Thread.sleep(3000);

        if (!process.isAlive()) {
            String output = new String(process.getInputStream().readAllBytes());
            System.out.println(" Process exited immediately!");
            System.out.println("  Output: " + output.substring(0, Math.min(500, output.length())));
            return;
        }

        System.out.println(" Project started (PID: " + process.pid() + ")");
        System.out.println("  URL: " + project.getLocalUrl());
    }

    private String inferRunCommand(ProjectInfo project) throws Exception {
        String prompt = "Project stack: " + project.getStack() +
                "\nDependencies: " + project.getDependencies() +
                "\nGive ONLY the single shell command to start this project locally. No explanation. No backticks.";

        return ai.chat(null, prompt, 128).trim().replace("`", "");
    }
}
