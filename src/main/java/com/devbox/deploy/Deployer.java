package com.devbox.deploy;

import com.devbox.model.ProjectInfo;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.List;
import java.util.Map;

public class Deployer {
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-6-20250514";

    private final String apiKey;
    private final String workDir;
    private final HttpClient httpClient;

    public Deployer(String apiKey, String workDir) {
        this.apiKey = apiKey;
        this.workDir = workDir;
        this.httpClient = HttpClient.newHttpClient();
    }

    public void deploy(ProjectInfo project) throws Exception {
        Path repoPath = Paths.get(workDir, project.getName());

        System.out.println("🚀 Deploying " + project.getName() + "...");

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

        // Give it a moment to start
        Thread.sleep(3000);

        if (!process.isAlive()) {
            String output = new String(process.getInputStream().readAllBytes());
            System.out.println("✗ Process exited immediately!");
            System.out.println("  Output: " + output.substring(0, Math.min(500, output.length())));
            return;
        }

        System.out.println("✓ Project started (PID: " + process.pid() + ")");
        System.out.println("  URL: " + project.getLocalUrl());
    }

    private String inferRunCommand(ProjectInfo project) throws Exception {
        var gson = new com.google.gson.Gson();
        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("max_tokens", 128);
        body.add("messages", gson.toJsonTree(List.of(
                Map.of("role", "user", "content",
                        "Project stack: " + project.getStack() +
                        "\nDependencies: " + project.getDependencies() +
                        "\nGive ONLY the single shell command to start this project locally. No explanation. No backticks.")
        )));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return json.getAsJsonArray("content").get(0).getAsJsonObject()
                .get("text").getAsString().trim().replace("`", "");
    }
}
