@echo off
echo [Reload] Starting Hot Reload Process...

echo [*] Building Frontend...
call npm run build
if %ERRORLEVEL% neq 0 (
    echo [X] Frontend build failed
    exit /b 1
)
echo [OK] Frontend build successful

echo [*] Compiling Backend...
call mvn clean compile
if %ERRORLEVEL% neq 0 (
    echo [X] Backend compilation failed
    exit /b 1
)
echo [OK] Backend compilation successful
echo [OK] Hot reload completed - Ready to test changes