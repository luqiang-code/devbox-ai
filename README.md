# DevBox AI

AI-powered environment setup & local deployment assistant. Paste a GitHub repo URL, and it automatically configures the dev environment and launches the project locally — powered by Claude.

## How It Works

1. **Analyze** — Clones the repo (shallow), reads README + dependency files, sends them to Claude to detect the tech stack
2. **Setup** — Checks available local tools (Docker/Node/Python/Java etc.), uses AI to generate the optimal install plan, executes it
3. **Deploy** — AI infers the right start command and launches the project

## Quick Start

```bash
# Build
mvn clean package -DskipTests

# Configure API key
cp .env.example .env
# Edit .env with your Anthropic API key

# Run
java -jar target/devbox-ai-0.1.0.jar --repo https://github.com/user/repo

# Or dry-run (analyze only)
java -jar target/devbox-ai-0.1.0.jar --repo https://github.com/user/repo --dry-run
```

## Requirements

- Java 17+
- Maven 3.8+
- Git
- Anthropic API key

## Supported Stacks

- Node.js / Next.js / Express
- Python / Flask / Django / FastAPI
- Go / Rust
- Ruby on Rails
- Java / Spring Boot / Maven / Gradle
- Docker Compose projects

## License

MIT
