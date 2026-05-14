package com.devbox;

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
import java.util.Properties;
import java.util.concurrent.Callable;

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

    @Option(names = {"--local"}, description = "Use local file-based detection (no AI, no API key needed)")
    private boolean localMode;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new App()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        String apiKey = loadApiKey();
        boolean useAI = !localMode && apiKey != null;

        if (!useAI && !localMode && apiKey == null) {
            System.out.println("💡 No ANTHROPIC_API_KEY found — using local detection mode.");
            System.out.println("   Set ANTHROPIC_API_KEY env var for AI-powered analysis.");
            System.out.println("   Or use --local to suppress this message.");
            System.out.println();
        }

        System.out.println("🔍 Analyzing: " + repo);
        System.out.println("   Mode: " + (useAI ? "AI-powered (Claude)" : "Local file-based detection"));
        System.out.println();

        ProjectInfo project;

        if (useAI) {
            RepoAnalyzer analyzer = new RepoAnalyzer(apiKey, workDir);
            project = analyzer.analyze(repo);
        } else {
            LocalRepoAnalyzer analyzer = new LocalRepoAnalyzer(workDir);
            project = analyzer.analyze(repo);
        }

        printAnalysis(project);

        if (dryRun) {
            System.out.println("✓ Dry run complete. No changes made.");
            return 0;
        }

        EnvSetup env = new EnvSetup(apiKey, workDir);
        env.provision(project);

        Deployer deployer = new Deployer(apiKey, workDir);
        deployer.deploy(project);

        System.out.println("✓ Done! Project should be running at: " + project.getLocalUrl());
        return 0;
    }

    private void printAnalysis(ProjectInfo project) {
        System.out.println("┌─────────────────────────────────────────────");
        System.out.println("│ Project:  " + project.getName());
        System.out.println("│ Stack:    " + project.getStack());
        System.out.println("│ Runtime:  " + project.getRuntime());
        System.out.println("│ URL:      " + project.getLocalUrl());
        if (!project.getDescription().isEmpty()) {
            System.out.println("│ About:    " + project.getDescription());
        }
        if (!project.getDependencies().isEmpty()) {
            System.out.println("│ Deps:     " + String.join(", ", project.getDependencies()));
        }
        if (!project.getEnvVars().isEmpty()) {
            System.out.println("│ Env vars: " + String.join(", ", project.getEnvVars()));
        }
        System.out.println("│ Setup:    " + String.join(" && ", project.getSetupCommands()));
        System.out.println("│ Run:      " + project.getRunCommand());
        System.out.println("└─────────────────────────────────────────────");
        System.out.println();
    }

    private String loadApiKey() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        if (key != null && !key.isEmpty()) return key;

        Path envFile = Path.of(".env");
        if (Files.exists(envFile)) {
            try {
                Properties props = new Properties();
                props.load(Files.newInputStream(envFile));
                return props.getProperty("ANTHROPIC_API_KEY");
            } catch (IOException ignored) {}
        }
        return null;
    }
}
