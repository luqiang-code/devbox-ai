package com.devbox;

import com.devbox.ai.AiProvider;
import com.devbox.ai.AnthropicProvider;
import com.devbox.ai.DeepSeekProvider;
import com.devbox.analyzer.LocalRepoAnalyzer;
import com.devbox.analyzer.RepoAnalyzer;
import com.devbox.deploy.Deployer;
import com.devbox.env.EnvSetup;
import com.devbox.model.ProjectInfo;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@Command(
        name = "devbox",
        mixinStandardHelpOptions = true,
        version = "0.1.0",
        description = "AI-powered environment setup & deployment — give it a GitHub URL, it does the rest."
)
public class App implements Callable<Integer> {

    @Option(names = {"-r", "--repo"}, required = true, description = "GitHub repository URL")
    private String repo;

    @Option(names = {"-w", "--work-dir"}, defaultValue = ".devbox", description = "Working directory for cloned repos")
    private String workDir;

    @Option(names = {"--dry-run"}, description = "Analyze only, skip setup and deploy")
    private boolean dryRun;

    @Option(names = {"--local"}, description = "Use local file-based detection (no AI)")
    private boolean localMode;

    @Option(names = {"--provider"}, defaultValue = "auto", description = "AI provider: auto, deepseek, anthropic")
    private String providerChoice;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new App()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        Properties env = loadEnv();

        AiProvider ai = null;
        if (!localMode) {
            ai = resolveProvider(env);
        }

        boolean useAI = ai != null;

        if (!useAI) {
            if (!localMode) {
                System.out.println(" No API key found (set DEEPSEEK_API_KEY or ANTHROPIC_API_KEY).");
                System.out.println("   Using local file-based detection.");
                System.out.println();
            }
        }

        System.out.println(" Analyzing: " + repo);
        System.out.println("   Mode: " + (useAI ? "AI-powered (" + ai.providerName() + ")" : "Local file-based detection"));
        System.out.println();

        ProjectInfo project;

        if (useAI) {
            RepoAnalyzer analyzer = new RepoAnalyzer(ai, workDir);
            project = analyzer.analyze(repo);
        } else {
            LocalRepoAnalyzer analyzer = new LocalRepoAnalyzer(workDir);
            project = analyzer.analyze(repo);
        }

        printAnalysis(project);

        if (dryRun) {
            System.out.println(" Dry run complete. No changes made.");
            return 0;
        }

        if (useAI) {
            EnvSetup envSetup = new EnvSetup(ai, workDir);
            envSetup.provision(project);

            Deployer deployer = new Deployer(ai, workDir);
            deployer.deploy(project);
        } else {
            localSetupAndDeploy(project);
        }

        System.out.println(" Done! Project at: " + project.getLocalUrl());
        return 0;
    }

    private void localSetupAndDeploy(ProjectInfo project) throws Exception {
        Path repoPath = Path.of(workDir, project.getName());

        System.out.println(" Setting up " + project.getName() + " (local mode)");
        for (String cmd : project.getSetupCommands()) {
            System.out.println("  -> " + cmd);
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd);
            pb.directory(repoPath.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean ok = p.waitFor(300, TimeUnit.SECONDS);
            if (!ok) {
                System.out.println("  Timed out, continuing...");
            }
        }

        if (!project.getEnvVars().isEmpty()) {
            Path envPath = repoPath.resolve(".env");
            if (!Files.exists(envPath)) {
                StringBuilder sb = new StringBuilder();
                for (String v : project.getEnvVars()) {
                    sb.append(v).append("=changeme\n");
                }
                Files.writeString(envPath, sb.toString());
                System.out.println("  Created .env with " + project.getEnvVars().size() + " variables to configure");
            }
        }

        System.out.println(" Deploying " + project.getName() + "...");
        String runCmd = project.getRunCommand();
        System.out.println("  Run command: " + runCmd);

        ProcessBuilder pb = new ProcessBuilder("sh", "-c", runCmd);
        pb.directory(repoPath.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        Thread.sleep(3000);
        if (process.isAlive()) {
            System.out.println(" Project started (PID: " + process.pid() + ")");
            System.out.println("  URL: " + project.getLocalUrl());
        } else {
            String output = new String(process.getInputStream().readAllBytes());
            System.out.println(" Process exited immediately!");
            System.out.println("  Output: " + output.substring(0, Math.min(500, output.length())));
        }
    }

    private AiProvider resolveProvider(Properties env) {
        // Explicit choice
        if ("deepseek".equalsIgnoreCase(providerChoice)) {
            String key = env.getProperty("DEEPSEEK_API_KEY");
            if (key != null && !key.isEmpty()) {
                return new DeepSeekProvider(key);
            }
            System.err.println("Error: --provider deepseek but DEEPSEEK_API_KEY not set");
            return null;
        }
        if ("anthropic".equalsIgnoreCase(providerChoice)) {
            String key = env.getProperty("ANTHROPIC_API_KEY");
            if (key != null && !key.isEmpty()) {
                return new AnthropicProvider(key);
            }
            System.err.println("Error: --provider anthropic but ANTHROPIC_API_KEY not set");
            return null;
        }

        // Auto-detect: prefer DeepSeek
        String dsKey = env.getProperty("DEEPSEEK_API_KEY");
        if (dsKey != null && !dsKey.isEmpty()) {
            return new DeepSeekProvider(dsKey);
        }
        String antKey = env.getProperty("ANTHROPIC_API_KEY");
        if (antKey != null && !antKey.isEmpty()) {
            return new AnthropicProvider(antKey);
        }
        return null;
    }

    private void printAnalysis(ProjectInfo project) {
        System.out.println("+---------------------------------------------");
        System.out.println("| Project:  " + project.getName());
        System.out.println("| Stack:    " + project.getStack());
        System.out.println("| Runtime:  " + project.getRuntime());
        System.out.println("| URL:      " + project.getLocalUrl());
        if (!project.getDescription().isEmpty()) {
            System.out.println("| About:    " + project.getDescription());
        }
        if (!project.getDependencies().isEmpty()) {
            System.out.println("| Deps:     " + String.join(", ", project.getDependencies()));
        }
        if (!project.getEnvVars().isEmpty()) {
            System.out.println("| Env vars: " + String.join(", ", project.getEnvVars()));
        }
        System.out.println("| Setup:    " + String.join(" && ", project.getSetupCommands()));
        System.out.println("| Run:      " + project.getRunCommand());
        System.out.println("+---------------------------------------------");
        System.out.println();
    }

    private Properties loadEnv() {
        Properties props = new Properties();
        // Load from .env file first
        Path envFile = Path.of(".env");
        if (Files.exists(envFile)) {
            try {
                props.load(Files.newInputStream(envFile));
            } catch (IOException ignored) {}
        }
        // Env vars override .env
        for (String key : List.of("DEEPSEEK_API_KEY", "ANTHROPIC_API_KEY")) {
            String val = System.getenv(key);
            if (val != null && !val.isEmpty()) {
                props.setProperty(key, val);
            }
        }
        return props;
    }
}
