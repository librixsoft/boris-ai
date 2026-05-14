@echo off
echo [*] Cleaning up ports 9999 and 1234...

for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":9999"') do (
    taskkill /PID %%a /F >nul 2>&1
)
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":1234"') do (
    taskkill /PID %%a /F >nul 2>&1
)

echo [*] Building Frontend...
call npm run build

echo [*] Cleaning and Compiling Backend...
call mvn clean compile

echo [*] Starting Boris server...
:: NOTA: La carga de librerias nativas se gestiona internamente en la App (BorisProperties/PathResolver)
:: El usuario final ejecutara el JAR directamente, por lo que no dependemos de este .bat para el PATH.
call mvn spring-boot:run