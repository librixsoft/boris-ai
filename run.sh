#!/bin/bash
# Limpiar procesos antiguos para evitar conflictos de puertos
echo "[*] Cleaning up ports 9999 and 1234..."
lsof -ti:9999 | xargs kill -9 2>/dev/null
lsof -ti:1234 | xargs kill -9 2>/dev/null

# Construir Frontend
echo "[*] Building Frontend..."
npm run build

# Limpiar y Compilar Backend
echo "[*] Cleaning and Compiling Backend..."
mvn clean compile

# Arrancar Boris
echo "[*] Starting Boris server..."
mvn spring-boot:run
