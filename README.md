# DevBox AI

AI-powered environment setup and local deployment assistant. Give it a GitHub repo URL, and it automatically configures the development environment and deploys the project locally.

## How It Works

1. **Analyze** — Reads the repo's README, dependency files, and config to understand the project stack
2. **Setup** — Installs required runtimes, packages, databases, and tools via Docker or native
3. **Deploy** — Configures and launches the project locally with one command

## Quick Start

```bash
# Install
pip install -r requirements.txt

# Configure your API key
cp .env.example .env
# Edit .env with your Anthropic API key

# Run
python src/main.py --repo https://github.com/user/repo
```

## Supported Stacks

- Node.js / Next.js / Express
- Python / Flask / Django / FastAPI
- Go
- Rust
- Ruby on Rails
- Java / Spring Boot
- Docker Compose projects

## Requirements

- Python 3.10+
- Docker (optional, for containerized setups)
- Anthropic API key

## License

MIT
