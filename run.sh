#!/bin/bash

set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$DIR/target/boris-cli-1.0.0.jar"

# Skip tests flag
SKIP_TESTS=false
BORIS_ARGS=()

for arg in "$@"; do
  case "$arg" in
    --skip-tests|--no-test)
      SKIP_TESTS=true
      ;;
    *)
      BORIS_ARGS+=("$arg")
      ;;
  esac
done

# Clean and package
if [ "$SKIP_TESTS" = true ]; then
  echo "=> Making clean and package (skipping tests)..."
  mvn clean package -DskipTests -q
else
  echo "=> Making clean and package..."
  mvn clean package -q
fi

echo ">> Running Boris CLI..."
exec java -jar "$JAR" "${BORIS_ARGS[@]}"