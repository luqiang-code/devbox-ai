---
name: devbox
description: AI-powered dev environment setup. Clone a GitHub repo, detect tech stack, install dependencies, and launch locally. Use when user provides a GitHub URL to set up and run, or mentions "setup project", "deploy locally", "启动项目", "搭建环境".
---

# DevBox — One-shot dev environment setup

## Quick start

```bash
bash .claude/skills/devbox/scripts/devbox-run.sh --repo https://github.com/user/repo
```

## Workflow

When user gives you a GitHub repo URL:

1. **Collect missing info** — if no API key configured, ask whether to use `--local` (file-based detection, no AI) or to set `DEEPSEEK_API_KEY` / `ANTHROPIC_API_KEY`
2. **Run devbox** — execute the script with the repo URL, piping output back to user
3. **Report results** — summarize the detected stack, dependencies, and how to access the running project

## Key flags

| Flag | Use case |
|------|----------|
| `--repo <url>` | GitHub repo URL (required) |
| `--local` | File-based detection, no AI key needed |
| `--dry-run` | Analyze only, don't install or launch |
| `--provider deepseek\|anthropic` | Force a specific AI provider |
| `--work-dir <path>` | Custom clone directory (default: `.devbox`) |

## Provider selection

- Both keys set → DeepSeek preferred (cheaper)
- One key set → auto uses it
- No keys → falls back to `--local`

## Requirements

- Java 17+, Maven 3.8+, Git
- `DEEPSEEK_API_KEY` or `ANTHROPIC_API_KEY` in `.env` (optional for `--local`)
