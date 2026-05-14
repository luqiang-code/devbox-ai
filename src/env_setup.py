"""Provision the runtime environment for a project."""

import os
import subprocess
import shutil

import anthropic
from rich.console import Console
from rich.progress import Progress

from src.analyzer import ProjectInfo

console = Console()


class EnvSetup:
    def __init__(self, api_key: str, work_dir: str = ".devbox"):
        self.client = anthropic.Anthropic(api_key=api_key)
        self.work_dir = work_dir

    def provision(self, project: ProjectInfo) -> None:
        repo_path = os.path.join(self.work_dir, project.name)

        console.print(f"[bold] Setting up environment for {project.name}[/bold]")
        console.print(f"  Stack: {project.stack}")
        console.print(f"  Runtime: {project.runtime}")

        # Check what's available locally
        available = self._check_available_tools()

        # Use AI to generate the best setup plan given local tools
        setup_plan = self._generate_setup_plan(project, available)

        with Progress() as progress:
            task = progress.add_task("[cyan]Provisioning...", total=len(setup_plan))

            for step in setup_plan:
                console.print(f"  -> {step['description']}")
                try:
                    subprocess.run(
                        step["command"],
                        shell=True,
                        check=True,
                        cwd=repo_path,
                        capture_output=True,
                    )
                except subprocess.CalledProcessError as e:
                    console.print(f"[red] Failed: {e.stderr.decode()}[/red]")
                    # Ask AI how to recover
                    fix = self._suggest_fix(project, step, e)
                    console.print(f"[yellow]  Suggestion: {fix}[/yellow]")

                progress.update(task, advance=1)

        # Write .env if needed
        if project.env_vars:
            self._write_env_template(repo_path, project.env_vars)

        console.print("[green] Environment ready.[/green]")

    def _check_available_tools(self) -> dict[str, bool]:
        tools = {
            "docker": shutil.which("docker"),
            "node": shutil.which("node"),
            "npm": shutil.which("npm"),
            "python3": shutil.which("python3"),
            "pip": shutil.which("pip"),
            "go": shutil.which("go"),
            "rust": shutil.which("rustc"),
            "ruby": shutil.which("ruby"),
            "java": shutil.which("java"),
            "maven": shutil.which("mvn"),
            "gradle": shutil.which("gradle"),
        }
        return {k: bool(v) for k, v in tools.items()}

    def _generate_setup_plan(self, project: ProjectInfo, available: dict) -> list[dict]:
        prompt = f"""Generate a setup plan for this project. Available tools: {available}.
Project stack: {project.stack}
Runtime: {project.runtime}
Dependencies: {project.dependencies}

Return a JSON list of steps, each with "description" and "command" keys.
Use Docker if native tools are missing. Prefer native tools when available.
Only output the JSON array, nothing else."""

        response = self.client.messages.create(
            model="claude-sonnet-4-6",
            max_tokens=1024,
            messages=[{"role": "user", "content": prompt}],
        )

        import json
        text = response.content[0].text
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            return [{"description": f"Install dependencies", "command": "echo 'No steps generated'"}]

    def _suggest_fix(self, project: ProjectInfo, step: dict, error: Exception) -> str:
        response = self.client.messages.create(
            model="claude-sonnet-4-6",
            max_tokens=256,
            messages=[{
                "role": "user",
                "content": f"Setup step failed: {step}\nError: {error}\nProject: {project.stack}\nSuggest a fix in one sentence."
            }],
        )
        return response.content[0].text

    def _write_env_template(self, repo_path: str, env_vars: list[str]) -> None:
        env_path = os.path.join(repo_path, ".env")
        if os.path.exists(env_path):
            return
        with open(env_path, "w") as f:
            for var in env_vars:
                f.write(f"{var}=changeme\n")
        console.print(f"  Created .env with {len(env_vars)} variables to configure")
