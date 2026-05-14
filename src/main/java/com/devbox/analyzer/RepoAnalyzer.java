package com.devbox.analyzer;

import com.devbox.model.ProjectInfo;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class RepoAnalyzer {
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-6-20250514";

    private final String apiKey;
    private final String workDir;
    private final HttpClient httpClient;
    private final Gson gson;

    public RepoAnalyzer(String apiKey, String workDir) {
        this.apiKey = apiKey;
        this.workDir = workDir;
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    public ProjectInfo analyze(String repoUrl) throws Exception {
        String repoName = extractRepoName(repoUrl);
        Path repoPath = Paths.get(workDir, repoName);

        Files.createDirectories(Paths.get(workDir));

        if (!Files.exists(repoPath)) {
            cloneRepo(repoUrl, repoPath);
        }

        String readme = readFile(repoPath, "README.md");
        Map<String, String> deps = collectDependencyFiles(repoPath);
        Map<String, String> configs = collectConfigFiles(repoPath);

        String prompt = buildAnalysisPrompt(repoName, readme, deps, configs);
        String response = callAnthropic(prompt);

        return parseResponse(response);
    }

    private String extractRepoName(String url) {
        String name = url.replaceAll("/$", "");
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.replace(".git", "");
    }

    private void cloneRepo(String url, Path dest) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "git", "clone", "--depth", "1", url, dest.toString()
        );
        pb.inheritIO();
        Process p = pb.start();
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Git clone failed with exit code " + exitCode);
        }
    }

    private String readFile(Path repoPath, String filename) {
        Path file = repoPath.resolve(filename);
        if (Files.exists(file)) {
            try {
                String content = Files.readString(file);
                return content.length() > 8000 ? content.substring(0, 8000) : content;
            } catch (IOException ignored) {}
        }
        return "";
    }

    private Map<String, String> collectDependencyFiles(Path repoPath) {
        String[] patterns = {
                "package.json", "requirements.txt", "Pipfile", "pyproject.toml",
                "go.mod", "Cargo.toml", "Gemfile", "pom.xml", "build.gradle",
                "composer.json", "Dockerfile", "docker-compose.yml", "docker-compose.yaml",
                "Makefile", "CMakeLists.txt"
        };
        Map<String, String> found = new LinkedHashMap<>();
        for (String name : patterns) {
            Path path = repoPath.resolve(name);
            if (Files.exists(path)) {
                try {
                    String content = Files.readString(path);
                    found.put(name, content.length() > 4000 ? content.substring(0, 4000) : content);
                } catch (IOException ignored) {}
            }
        }
        return found;
    }

    private Map<String, String> collectConfigFiles(Path repoPath) {
        String[] patterns = {".env.example", ".env.template", ".env.sample", "config.example.yml"};
        Map<String, String> found = new LinkedHashMap<>();
        for (String name : patterns) {
            Path path = repoPath.resolve(name);
            if (Files.exists(path)) {
                try {
                    String content = Files.readString(path);
                    found.put(name, content.length() > 2000 ? content.substring(0, 2000) : content);
                } catch (IOException ignored) {}
            }
        }
        return found;
    }

    private String buildAnalysisPrompt(String name, String readme,
                                        Map<String, String> deps, Map<String, String> configs) {
        Yaml yaml = new Yaml();
        return String.format("""
                Analyze this GitHub repository and return a structured summary. Output ONLY valid YAML:

                repo: "%s"
                stack: <detected tech stack, e.g. nextjs, fastapi, go, rails, spring-boot>
                description: <one-line project description>
                runtime: <required runtime, e.g. node:20, python:3.11, go:1.22, java:17>
                dependencies: <list of key packages/frameworks>
                env_vars: <list of required env vars from config files>
                setup_commands: <list of shell commands to install deps>
                run_command: <single command to start the project>
                local_url: <local URL after start, e.g. http://localhost:8080>

                === README.md ===
                %s

                === Dependency Files ===
                %s

                === Config Files ===
                %s
                """,
                name,
                readme.length() > 6000 ? readme.substring(0, 6000) : readme,
                yaml.dump(deps.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().substring(0, Math.min(500, e.getValue().length()))))),
                yaml.dump(configs.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().substring(0, Math.min(500, e.getValue().length())))))
        );
    }

    private String callAnthropic(String prompt) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("max_tokens", 1024);
        body.add("messages", gson.toJsonTree(List.of(
                Map.of("role", "user", "content", prompt)
        )));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("API error: " + response.statusCode() + " " + response.body());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return json.getAsJsonArray("content")
                .get(0).getAsJsonObject()
                .get("text").getAsString();
    }

    @SuppressWarnings("unchecked")
    private ProjectInfo parseResponse(String text) {
        String block = text;
        if (block.contains("```")) {
            String[] parts = block.split("```");
            block = parts.length > 1 ? parts[1] : block;
            if (block.startsWith("yaml") || block.startsWith("yml")) {
                block = block.substring(block.indexOf('\n') + 1);
            }
        }

        Yaml yaml = new Yaml();
        Map<String, Object> data;
        try {
            data = yaml.load(block);
            if (data == null) data = Map.of();
        } catch (Exception e) {
            data = Map.of();
        }

        ProjectInfo info = new ProjectInfo();
        info.setName(str(data.getOrDefault("repo", "")));
        info.setStack(str(data.getOrDefault("stack", "unknown")));
        info.setDescription(str(data.getOrDefault("description", "")));
        info.setRuntime(str(data.getOrDefault("runtime", "")));
        info.setDependencies(list(data.get("dependencies")));
        info.setEnvVars(list(data.get("env_vars")));
        info.setLocalUrl(str(data.getOrDefault("local_url", "http://localhost:3000")));
        info.setSetupCommands(list(data.get("setup_commands")));
        info.setRunCommand(str(data.getOrDefault("run_command", "")));
        return info;
    }

    private String str(Object o) {
        return o == null ? "" : o.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> list(Object o) {
        if (o instanceof List) return (List<String>) o;
        return new ArrayList<>();
    }
}
