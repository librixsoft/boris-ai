#!/bin/bash

set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$DIR/target/boris-cli-1.0.0.jar"

# Clean and package
echo "=> Making clean and package..."
mvn clean compile package -q

if [ $? != 0 ]; then
    echo "Error: Maven build failed." >&2
    exit 1
fi

# Find the jar
JAR="$DIR/target/boris-cli-1.0.0.jar"

if [ ! -f "$JAR" ]; then
    echo "Error: JAR file not found at $JAR" >&2
    exit 1
fi

echo ">> Running Boris CLI..."
exec java -jar "$JAR" "$@"
