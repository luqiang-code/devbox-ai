package com.devbox.analyzer;

import com.devbox.model.ProjectInfo;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Local tech-stack detection without AI — reads dependency files directly.
 * Falls back gracefully when no API key is available.
 */
public class LocalRepoAnalyzer {

    private final String workDir;

    public LocalRepoAnalyzer(String workDir) {
        this.workDir = workDir;
    }

    public ProjectInfo analyze(String repoUrl) throws Exception {
        String repoName = extractRepoName(repoUrl);
        Path repoPath = Paths.get(workDir, repoName);

        Files.createDirectories(Paths.get(workDir));

        if (!Files.exists(repoPath)) {
            System.out.println("  Cloning " + repoUrl + " ...");
            ProcessBuilder pb = new ProcessBuilder(
                    "git", "clone", "--depth", "1", repoUrl, repoPath.toString()
            );
            pb.inheritIO();
            int exitCode = pb.start().waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Git clone failed with exit code " + exitCode);
            }
            System.out.println("  Cloned to " + repoPath);
        }

        ProjectInfo info = new ProjectInfo();
        info.setName(repoName);

        // Detect stack from files
        Map<String, Boolean> files = scanFiles(repoPath);
        detectStack(info, files);
        detectEnvVars(repoPath, info);

        // Read description from README
        String readme = readFile(repoPath, "README.md");
        info.setDescription(extractDescription(readme));

        System.out.println("  Detected files: " + files.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .toList());

        return info;
    }

    private String extractRepoName(String url) {
        String name = url.replaceAll("/$", "");
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.replace(".git", "");
    }

    private Map<String, Boolean> scanFiles(Path repoPath) {
        String[] patterns = {
                "package.json", "requirements.txt", "Pipfile", "pyproject.toml",
                "go.mod", "Cargo.toml", "Gemfile", "pom.xml", "build.gradle",
                "composer.json", "Dockerfile", "docker-compose.yml", "docker-compose.yaml",
                "Makefile", "CMakeLists.txt"
        };
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String name : patterns) {
            result.put(name, Files.exists(repoPath.resolve(name)));
        }
        return result;
    }

    private void detectStack(ProjectInfo info, Map<String, Boolean> files) {
        boolean hasDockerCompose = files.getOrDefault("docker-compose.yml", false)
                || files.getOrDefault("docker-compose.yaml", false);

        if (files.getOrDefault("pom.xml", false) || files.getOrDefault("build.gradle", false)) {
            info.setStack("java");
            info.setRuntime("java:17");
            info.setSetupCommands(List.of(
                    files.get("pom.xml") ? "mvn clean install -DskipTests" : "gradle build -x test"
            ));
            info.setRunCommand(files.get("pom.xml") ? "mvn spring-boot:run" : "gradle bootRun");
            info.setLocalUrl("http://localhost:8080");
        } else if (files.getOrDefault("package.json", false)) {
            info.setStack("nodejs");
            info.setRuntime("node:20");
            info.setSetupCommands(List.of("npm install"));
            // Read package.json for start script
            String pkgJson = readFile(Paths.get(workDir, info.getName()), "package.json");
            info.setRunCommand(inferNodeRunCommand(pkgJson));
            info.setLocalUrl("http://localhost:3000");
        } else if (files.getOrDefault("requirements.txt", false)
                || files.getOrDefault("pyproject.toml", false)
                || files.getOrDefault("Pipfile", false)) {
            info.setStack("python");
            info.setRuntime("python:3.11");
            if (files.getOrDefault("Pipfile", false)) {
                info.setSetupCommands(List.of("pipenv install"));
                info.setRunCommand("pipenv run python main.py");
            } else {
                info.setSetupCommands(List.of("pip3 install -r requirements.txt"));
                info.setRunCommand("python3 main.py");
            }
            info.setLocalUrl("http://localhost:8000");
        } else if (files.getOrDefault("go.mod", false)) {
            info.setStack("go");
            info.setRuntime("go:1.22");
            info.setSetupCommands(List.of("go mod download"));
            info.setRunCommand("go run .");
            info.setLocalUrl("http://localhost:8080");
        } else if (files.getOrDefault("Cargo.toml", false)) {
            info.setStack("rust");
            info.setRuntime("rust:latest");
            info.setSetupCommands(List.of("cargo build --release"));
            info.setRunCommand("cargo run");
            info.setLocalUrl("http://localhost:8080");
        } else if (files.getOrDefault("Gemfile", false)) {
            info.setStack("ruby");
            info.setRuntime("ruby:3.3");
            info.setSetupCommands(List.of("bundle install"));
            info.setRunCommand("bundle exec rails server");
            info.setLocalUrl("http://localhost:3000");
        } else if (files.getOrDefault("composer.json", false)) {
            info.setStack("php");
            info.setRuntime("php:8.3");
            info.setSetupCommands(List.of("composer install"));
            info.setRunCommand("php artisan serve");
            info.setLocalUrl("http://localhost:8000");
        } else if (hasDockerCompose) {
            info.setStack("docker-compose");
            info.setRuntime("docker");
            info.setSetupCommands(List.of("docker compose build"));
            info.setRunCommand("docker compose up");
            info.setLocalUrl("http://localhost:8080");
        } else {
            info.setStack("unknown");
            info.setRuntime("unknown");
            info.setSetupCommands(List.of("echo 'Could not detect stack — check README'"));
            info.setRunCommand("echo 'No run command inferred'");
            info.setLocalUrl("unknown");
        }

        // Extract actual dependencies from files
        info.setDependencies(extractDependencies(Paths.get(workDir, info.getName()), info.getStack()));
    }

    private String inferNodeRunCommand(String packageJson) {
        if (packageJson == null || packageJson.isEmpty()) return "npm start";

        // Try to extract scripts.start
        int scriptsIdx = packageJson.indexOf("\"scripts\"");
        if (scriptsIdx > 0) {
            int startIdx = packageJson.indexOf("\"start\"", scriptsIdx);
            if (startIdx > 0) {
                int colonIdx = packageJson.indexOf(':', startIdx);
                int valStart = packageJson.indexOf('"', colonIdx + 1);
                int valEnd = packageJson.indexOf('"', valStart + 1);
                if (valStart > 0 && valEnd > 0) {
                    String startScript = packageJson.substring(valStart + 1, valEnd);
                    if (!startScript.isEmpty()) {
                        return "npm run start";
                    }
                }
            }
            // Check for "dev" script
            int devIdx = packageJson.indexOf("\"dev\"", scriptsIdx);
            if (devIdx > 0) {
                return "npm run dev";
            }
        }
        return "npm start";
    }

    private List<String> extractDependencies(Path repoPath, String stack) {
        List<String> deps = new ArrayList<>();
        try {
            if (stack.equals("nodejs")) {
                Path pkg = repoPath.resolve("package.json");
                if (Files.exists(pkg)) {
                    String content = Files.readString(pkg);
                    // Extract key deps from dependencies
                    String depSection = content.substring(content.indexOf("\"dependencies\""));
                    depSection = depSection.substring(0, Math.min(2000, depSection.length()));
                    // Quick extraction of dep names
                    String[] lines = depSection.split("\n");
                    int count = 0;
                    for (String line : lines) {
                        if (line.contains("\":")) {
                            String name = line.trim().replaceAll("\"", "").split(":")[0].trim();
                            if (!name.isEmpty() && !name.equals("dependencies") && !name.equals("}")) {
                                deps.add(name);
                                count++;
                                if (count >= 10) break;
                            }
                        }
                    }
                }
            } else if (stack.equals("python")) {
                Path req = repoPath.resolve("requirements.txt");
                if (Files.exists(req)) {
                    List<String> lines = Files.readAllLines(req);
                    for (String line : lines) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            deps.add(line.split("[>=<]")[0].trim());
                            if (deps.size() >= 10) break;
                        }
                    }
                }
            }
        } catch (IOException ignored) {}
        return deps;
    }

    private void detectEnvVars(Path repoPath, ProjectInfo info) {
        List<String> envVars = new ArrayList<>();
        String[] envFiles = {".env.example", ".env.template", ".env.sample"};
        for (String name : envFiles) {
            Path envFile = repoPath.resolve(name);
            if (Files.exists(envFile)) {
                try {
                    List<String> lines = Files.readAllLines(envFile);
                    for (String line : lines) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#") && line.contains("=")) {
                            String varName = line.split("=")[0].trim();
                            envVars.add(varName);
                        }
                    }
                    if (!envVars.isEmpty()) break;
                } catch (IOException ignored) {}
            }
        }
        info.setEnvVars(envVars);
    }

    private String extractDescription(String readme) {
        if (readme == null || readme.isEmpty()) return "";
        // Get first meaningful line after the title
        String[] lines = readme.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) continue;
            if (trimmed.length() > 10 && trimmed.length() < 200) {
                return trimmed;
            }
        }
        return "";
    }

    private String readFile(Path repoPath, String filename) {
        Path file = repoPath.resolve(filename);
        if (Files.exists(file)) {
            try {
                return Files.readString(file);
            } catch (IOException ignored) {}
        }
        return "";
    }
}
