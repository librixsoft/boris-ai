@echo off
setlocal enabledelayedexpansion

set "DIR=%~dp0"
set "JAR=%DIR%target\boris-cli-1.0.0.jar"

set "SKIP_TESTS=true"
set "ARGS="

:loop
if "%~1"=="" goto :end_loop
if "%~1"=="--run-tests" (
    set "SKIP_TESTS=false"
) else if "%~1"=="--with-test" (
    set "SKIP_TESTS=false"
) else (
    set "ARGS=%ARGS% %~1"
)
shift
goto :loop

:end_loop

if "%SKIP_TESTS%"=="true" (
    echo => Making clean and package (skipping tests)...
    mvn clean package -DskipTests -q
) else (
    echo => Making clean and package (with tests)...
    mvn clean package -q
)

echo >> Running Boris CLI...
java -jar "%JAR%" %ARGS%