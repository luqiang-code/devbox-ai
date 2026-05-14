# DevBox AI

AI-powered environment setup & local deployment assistant. Paste a GitHub repo URL, and it automatically configures the dev environment and launches the project locally.

## How It Works

1. **Analyze** — Clones the repo (shallow), reads README + dependency files, sends them to AI to detect the tech stack
2. **Setup** — Checks available local tools (Docker/Node/Python/Java etc.), AI generates the optimal install plan, executes it
3. **Deploy** — AI infers the right start command and launches the project

## Quick Start

```bash
# Build
mvn clean package -DskipTests

# Configure API key (DeepSeek recommended, cheaper & GPT-compatible)
cp .env.example .env
# Edit .env with DEEPSEEK_API_KEY or ANTHROPIC_API_KEY

# Run with AI analysis
java -jar target/devbox-ai-0.1.0.jar --repo https://github.com/user/repo

# Or use local file-based detection (no API key needed)
java -jar target/devbox-ai-0.1.0.jar --repo https://github.com/user/repo --local

# Dry-run (analyze only)
java -jar target/devbox-ai-0.1.0.jar --repo https://github.com/user/repo --dry-run

# Explicit provider
java -jar target/devbox-ai-0.1.0.jar --repo https://github.com/user/repo --provider deepseek
java -jar target/devbox-ai-0.1.0.jar --repo https://github.com/user/repo --provider anthropic
```

## AI Providers

| Provider | API Key Env | Default Model | Notes |
|----------|-------------|---------------|-------|
| DeepSeek | `DEEPSEEK_API_KEY` | `deepseek-v4-pro` / `deepseek-v4-flash` | GPT-compatible, cheaper |
| Anthropic | `ANTHROPIC_API_KEY` | `claude-sonnet-4-6` | More accurate analysis |

Auto-detection: DeepSeek preferred if both keys are set. Falls back to local detection if no key available (reads dependency files directly).

## Requirements

- Java 17+
- Maven 3.8+
- Git
- DeepSeek or Anthropic API key (optional, `--local` works without)

## Supported Stacks

- Node.js / Next.js / Express
- Python / Flask / Django / FastAPI
- Go / Rust
- Ruby on Rails
- Java / Spring Boot / Maven / Gradle
- PHP / Laravel
- Docker Compose projects

## License

MIT
