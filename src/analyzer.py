"""Analyze a GitHub repository to detect its tech stack and requirements."""

import os
import subprocess
from dataclasses import dataclass, field

import anthropic


@dataclass
class ProjectInfo:
    name: str = ""
    stack: str = "unknown"
    description: str = ""
    runtime: str = ""
    dependencies: list[str] = field(default_factory=list)
    env_vars: list[str] = field(default_factory=list)
    local_url: str = ""
    setup_commands: list[str] = field(default_factory=list)
    run_command: str = ""


class RepoAnalyzer:
    def __init__(self, api_key: str, work_dir: str = ".devbox"):
        self.client = anthropic.Anthropic(api_key=api_key)
        self.work_dir = work_dir

    def analyze(self, repo_url: str) -> ProjectInfo:
        repo_name = repo_url.rstrip("/").split("/")[-1].replace(".git", "")
        repo_path = os.path.join(self.work_dir, repo_name)

        os.makedirs(self.work_dir, exist_ok=True)

        if not os.path.exists(repo_path):
            subprocess.run(
                ["git", "clone", "--depth", "1", repo_url, repo_path],
                check=True,
                capture_output=True,
            )

        readme = self._read_file(repo_path, "README.md") or ""
        deps = self._collect_dependency_files(repo_path)
        configs = self._collect_config_files(repo_path)

        prompt = self._build_analysis_prompt(repo_name, readme, deps, configs)
        response = self.client.messages.create(
            model="claude-sonnet-4-6",
            max_tokens=1024,
            messages=[{"role": "user", "content": prompt}],
        )

        return self._parse_response(response.content[0].text, repo_path)

    def _read_file(self, repo_path: str, filename: str) -> str | None:
        filepath = os.path.join(repo_path, filename)
        if os.path.exists(filepath):
            with open(filepath) as f:
                return f.read()[:8000]
        return None

    def _collect_dependency_files(self, repo_path: str) -> dict[str, str]:
        patterns = [
            "package.json", "requirements.txt", "Pipfile", "pyproject.toml",
            "go.mod", "Cargo.toml", "Gemfile", "pom.xml", "build.gradle",
            "composer.json", "Dockerfile", "docker-compose.yml", "docker-compose.yaml",
            "Makefile", "CMakeLists.txt",
        ]
        found = {}
        for name in patterns:
            path = os.path.join(repo_path, name)
            if os.path.exists(path):
                with open(path) as f:
                    found[name] = f.read()[:4000]
        return found

    def _collect_config_files(self, repo_path: str) -> dict[str, str]:
        patterns = [".env.example", ".env.template", ".env.sample", "config.example.yml"]
        found = {}
        for name in patterns:
            path = os.path.join(repo_path, name)
            if os.path.exists(path):
                with open(path) as f:
                    found[name] = f.read()[:2000]
        return found

    def _build_analysis_prompt(self, name: str, readme: str, deps: dict, configs: dict) -> str:
        return f"""Analyze this GitHub repository and return a structured summary. Output ONLY valid YAML:

repo: "{name}"
stack: <detected tech stack, e.g. nextjs, fastapi, go, rails>
description: <one-line project description>
runtime: <required runtime, e.g. node:20, python:3.11, go:1.22>
dependencies: <list of key packages/frameworks>
env_vars: <list of required env vars from config files>
setup_commands: <list of shell commands to install deps>
run_command: <single command to start the project>

=== README.md ===
{readme[:6000]}

=== Dependency Files ===
{yaml.dump({k: v[:500] for k, v in deps.items()})}

=== Config Files ===
{yaml.dump({k: v[:500] for k, v in configs.items()})}
"""

    def _parse_response(self, text: str, repo_path: str) -> ProjectInfo:
        import yaml

        block = text
        if "```" in text:
            block = text.split("```")[1]
            if block.startswith("yaml"):
                block = block[4:]

        try:
            data = yaml.safe_load(block)
        except yaml.YAMLError:
            data = {}

        return ProjectInfo(
            name=data.get("repo", ""),
            stack=data.get("stack", "unknown"),
            description=data.get("description", ""),
            runtime=data.get("runtime", ""),
            dependencies=data.get("dependencies", []),
            env_vars=data.get("env_vars", []),
            local_url=data.get("local_url", "http://localhost:3000"),
            setup_commands=data.get("setup_commands", []),
            run_command=data.get("run_command", ""),
        )
