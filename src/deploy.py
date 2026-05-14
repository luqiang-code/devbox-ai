"""Deploy the project locally and verify it's running."""

import os
import subprocess
import time
import signal

import anthropic
from rich.console import Console

from src.analyzer import ProjectInfo

console = Console()


class Deployer:
    def __init__(self, api_key: str, work_dir: str = ".devbox"):
        self.client = anthropic.Anthropic(api_key=api_key)
        self.work_dir = work_dir
        self.process = None

    def deploy(self, project: ProjectInfo) -> None:
        repo_path = os.path.join(self.work_dir, project.name)

        console.print(f"[bold] Deploying {project.name}...[/bold]")

        if project.run_command:
            cmd = project.run_command
        else:
            cmd = self._infer_run_command(project)

        console.print(f"  Run command: {cmd}")
        console.print(f"  Working dir: {repo_path}")

        # Start the process
        self.process = subprocess.Popen(
            cmd,
            shell=True,
            cwd=repo_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            preexec_fn=os.setsid,
        )

        # Wait a moment and check if it's alive
        time.sleep(3)
        if self.process.poll() is not None:
            stdout, stderr = self.process.communicate()
            console.print(f"[red] Process exited immediately![/red]")
            console.print(f"  stdout: {stdout.decode()[-500:]}")
            console.print(f"  stderr: {stderr.decode()[-500:]}")
            return

        console.print(f"[green] Project started (PID: {self.process.pid})[/green]")
        console.print(f"  URL: {project.local_url}")

    def _infer_run_command(self, project: ProjectInfo) -> str:
        response = self.client.messages.create(
            model="claude-sonnet-4-6",
            max_tokens=128,
            messages=[{
                "role": "user",
                "content": f"Project stack: {project.stack}\nDependencies: {project.dependencies}\nGive ONLY the single shell command to start this project locally. No explanation."
            }],
        )
        return response.content[0].text.strip().strip("`")

    def stop(self) -> None:
        if self.process and self.process.poll() is None:
            os.killpg(os.getpgid(self.process.pid), signal.SIGTERM)
            console.print(" Project stopped.")
