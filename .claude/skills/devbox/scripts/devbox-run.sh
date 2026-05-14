#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/../../../.." && pwd)"
JAR="$PROJECT_DIR/target/devbox-ai-0.1.0.jar"

# Build if jar doesn't exist or source changed
if [ ! -f "$JAR" ] || [ "$(find "$PROJECT_DIR/src" -newer "$JAR" -name '*.java' 2>/dev/null)" ]; then
    echo "Building devbox..."
    cd "$PROJECT_DIR" && mvn clean package -DskipTests -q
    echo ""
fi

# Load .env if exists
if [ -f "$PROJECT_DIR/.env" ]; then
    export $(grep -v '^#' "$PROJECT_DIR/.env" | xargs)
fi

exec java -jar "$JAR" "$@"
