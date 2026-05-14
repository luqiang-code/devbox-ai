package com.devbox.env;

import com.devbox.model.ProjectInfo;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class EnvSetup {
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-6-20250514";

    private final String apiKey;
    private final String workDir;
    private final HttpClient httpClient;
    private final Gson gson;

    public EnvSetup(String apiKey, String workDir) {
        this.apiKey = apiKey;
        this.workDir = workDir;
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    public void provision(ProjectInfo project) throws Exception {
        Path repoPath = Paths.get(workDir, project.getName());

        System.out.println("⚙ Setting up environment for " + project.getName());
        System.out.println("  Stack: " + project.getStack());
        System.out.println("  Runtime: " + project.getRuntime());

        Map<String, Boolean> available = checkAvailableTools();
        List<Step> plan = generateSetupPlan(project, available);

        for (Step step : plan) {
            System.out.println("  -> " + step.description);
            try {
                ProcessBuilder pb = new ProcessBuilder("sh", "-c", step.command);
                pb.directory(repoPath.toFile());
                pb.redirectErrorStream(true);
                Process p = pb.start();
                boolean ok = p.waitFor(120, TimeUnit.SECONDS);
                if (!ok || p.exitValue() != 0) {
                    String output = new String(p.getInputStream().readAllBytes());
                    System.out.println("  ⚠ Command may have issues: " + output.substring(0, Math.min(200, output.length())));
                }
            } catch (Exception e) {
                String suggestion = suggestFix(project, step, e.getMessage());
                System.out.println("  💡 Suggestion: " + suggestion);
            }
        }

        if (!project.getEnvVars().isEmpty()) {
            writeEnvTemplate(repoPath, project.getEnvVars());
        }

        System.out.println("✓ Environment ready.");
    }

    private Map<String, Boolean> checkAvailableTools() {
        String[] tools = {"docker", "node", "npm", "python3", "pip", "pip3",
                "go", "rustc", "ruby", "java", "mvn", "gradle"};
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String tool : tools) {
            try {
                Process p = new ProcessBuilder("which", tool).start();
                result.put(tool, p.waitFor() == 0);
            } catch (Exception e) {
                result.put(tool, false);
            }
        }
        return result;
    }

    private List<Step> generateSetupPlan(ProjectInfo project, Map<String, Boolean> available) throws Exception {
        String prompt = String.format("""
                Generate a setup plan for this project. Available tools on this machine: %s.
                Project stack: %s
                Runtime: %s
                Dependencies: %s

                Return ONLY a JSON array. Each element has "description" and "command" keys.
                Use Docker if native tools are missing. Prefer native tools when available.
                Use pip3 instead of pip if needed. Use npm/npx for Node projects.
                """,
                available, project.getStack(), project.getRuntime(), project.getDependencies()
        );

        String response = callAnthropic(prompt);
        // Parse JSON array
        String json = response.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("```json?|```", "").trim();
        }

        try {
            Type listType = new TypeToken<List<Step>>(){}.getType();
            return gson.fromJson(json, listType);
        } catch (Exception e) {
            return List.of(new Step("Install dependencies", "echo 'No setup steps generated'"));
        }
    }

    private String suggestFix(ProjectInfo project, Step step, String error) throws Exception {
        String prompt = String.format(
                "Setup step failed: %s\nError: %s\nProject: %s\nSuggest a fix in one sentence.",
                step.description, error, project.getStack()
        );
        return callAnthropicShort(prompt);
    }

    private void writeEnvTemplate(Path repoPath, List<String> envVars) throws IOException {
        Path envPath = repoPath.resolve(".env");
        if (Files.exists(envPath)) return;
        StringBuilder sb = new StringBuilder();
        for (String v : envVars) {
            sb.append(v).append("=changeme\n");
        }
        Files.writeString(envPath, sb.toString());
        System.out.println("  Created .env with " + envVars.size() + " variables to configure");
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
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return json.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
    }

    private String callAnthropicShort(String prompt) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("max_tokens", 256);
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
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return json.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
    }

    static class Step {
        String description;
        String command;

        Step(String description, String command) {
            this.description = description;
            this.command = command;
        }
    }
}
