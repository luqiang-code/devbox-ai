"""DevBox AI — Automated environment setup and local deployment from a GitHub repo URL."""

import os
import sys
import click
from dotenv import load_dotenv

from src.analyzer import RepoAnalyzer
from src.env_setup import EnvSetup
from src.deploy import Deployer


@click.command()
@click.option("--repo", "-r", required=True, help="GitHub repository URL")
@click.option("--work-dir", "-w", default=".devbox", help="Working directory for cloned repos")
@click.option("--dry-run", is_flag=True, help="Analyze only, skip setup and deploy")
def main(repo: str, work_dir: str, dry_run: bool):
    """DevBox AI — Analyze a GitHub repo and auto-deploy it locally."""
    load_dotenv()

    api_key = os.getenv("ANTHROPIC_API_KEY")
    if not api_key:
        click.echo("Error: ANTHROPIC_API_KEY not set. Copy .env.example to .env and configure it.")
        sys.exit(1)

    click.echo(f" Analyzing: {repo}")

    analyzer = RepoAnalyzer(api_key, work_dir)
    project_info = analyzer.analyze(repo)

    click.echo(f" Detected: {project_info.stack} — {project_info.description}")

    if dry_run:
        click.echo(f" Dependencies: {', '.join(project_info.dependencies)}")
        click.echo(f" Runtime: {project_info.runtime}")
        click.echo(" Dry run complete. No changes made.")
        return

    env = EnvSetup(api_key, work_dir)
    env.provision(project_info)

    deployer = Deployer(api_key, work_dir)
    deployer.deploy(project_info)

    click.echo(f" Done! Project running at: {project_info.local_url}")


if __name__ == "__main__":
    main()
