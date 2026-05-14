package com.devbox.model;

import java.util.ArrayList;
import java.util.List;

public class ProjectInfo {
    private String name = "";
    private String stack = "unknown";
    private String description = "";
    private String runtime = "";
    private List<String> dependencies = new ArrayList<>();
    private List<String> envVars = new ArrayList<>();
    private String localUrl = "http://localhost:3000";
    private List<String> setupCommands = new ArrayList<>();
    private String runCommand = "";

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStack() { return stack; }
    public void setStack(String stack) { this.stack = stack; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRuntime() { return runtime; }
    public void setRuntime(String runtime) { this.runtime = runtime; }

    public List<String> getDependencies() { return dependencies; }
    public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }

    public List<String> getEnvVars() { return envVars; }
    public void setEnvVars(List<String> envVars) { this.envVars = envVars; }

    public String getLocalUrl() { return localUrl; }
    public void setLocalUrl(String localUrl) { this.localUrl = localUrl; }

    public List<String> getSetupCommands() { return setupCommands; }
    public void setSetupCommands(List<String> setupCommands) { this.setupCommands = setupCommands; }

    public String getRunCommand() { return runCommand; }
    public void setRunCommand(String runCommand) { this.runCommand = runCommand; }

    @Override
    public String toString() {
        return "ProjectInfo{" +
                "name='" + name + '\'' +
                ", stack='" + stack + '\'' +
                ", description='" + description + '\'' +
                ", runtime='" + runtime + '\'' +
                ", dependencies=" + dependencies +
                ", envVars=" + envVars +
                ", localUrl='" + localUrl + '\'' +
                ", runCommand='" + runCommand + '\'' +
                '}';
    }
}
