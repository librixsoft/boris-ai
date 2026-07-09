#!/bin/bash

# Script para Hot Reload - Build Frontend y Compile Backend

echo "🔄 Starting Hot Reload Process..."

# Construir Frontend
echo "[*] Building Frontend..."
npm run build

if [ $? -eq 0 ]; then
    echo "✅ Frontend build successful"
else
    echo "❌ Frontend build failed"
    exit 1
fi

# Compilar Backend
echo "[*] Compiling Backend..."
mvn clean compile

if [ $? -eq 0 ]; then
    echo "✅ Backend compilation successful"
    echo "🚀 Hot reload completed - Ready to test changes"
else
    echo "❌ Backend compilation failed"
    exit 1
fi
