# ============================================================
#  Instalador de Apache Maven 3.9.9 para Windows
#  Ejecutar como Administrador en PowerShell
# ============================================================

$ErrorActionPreference = "Stop"

$MAVEN_VERSION  = "3.9.15"
$MAVEN_ZIP      = "apache-maven-$MAVEN_VERSION-bin.zip"
$DOWNLOAD_URL   = "https://downloads.apache.org/maven/maven-3/$MAVEN_VERSION/binaries/$MAVEN_ZIP"
$INSTALL_DIR    = "C:\maven"
$TEMP_ZIP       = "$env:TEMP\$MAVEN_ZIP"

Write-Host ""
Write-Host "=======================================" -ForegroundColor Cyan
Write-Host "  Instalando Apache Maven $MAVEN_VERSION" -ForegroundColor Cyan
Write-Host "=======================================" -ForegroundColor Cyan
Write-Host ""

# --- 1. Verificar que Java esta instalado ---
Write-Host "[1/5] Verificando Java..." -ForegroundColor Yellow
$javaCheck = cmd /c "java -version 2>&1"
if ($javaCheck -match "version") {
    Write-Host "      OK: $($javaCheck[0])" -ForegroundColor Green
} else {
    Write-Host "      ERROR: Java no encontrado. Instala el JDK antes de continuar." -ForegroundColor Red
    Write-Host "      Descarga: https://adoptium.net" -ForegroundColor Red
    exit 1
}

# --- 2. Descargar el ZIP ---
Write-Host "[2/5] Descargando Maven desde Apache..." -ForegroundColor Yellow
Write-Host "      URL: $DOWNLOAD_URL"
Invoke-WebRequest -Uri $DOWNLOAD_URL -OutFile $TEMP_ZIP -UseBasicParsing
Write-Host "      OK: Descarga completada -> $TEMP_ZIP" -ForegroundColor Green

# --- 3. Extraer en C:\maven ---
Write-Host "[3/5] Extrayendo en $INSTALL_DIR ..." -ForegroundColor Yellow
if (Test-Path $INSTALL_DIR) {
    Write-Host "      Directorio existente encontrado, eliminando..." -ForegroundColor DarkYellow
    Remove-Item -Recurse -Force $INSTALL_DIR
}
$TEMP_EXTRACT = "$env:TEMP\maven-extract"
if (Test-Path $TEMP_EXTRACT) { Remove-Item -Recurse -Force $TEMP_EXTRACT }
Expand-Archive -Path $TEMP_ZIP -DestinationPath $TEMP_EXTRACT
$innerFolder = Get-ChildItem $TEMP_EXTRACT | Select-Object -First 1
Move-Item $innerFolder.FullName $INSTALL_DIR
Remove-Item -Recurse -Force $TEMP_EXTRACT
Write-Host "      OK: Extraido en $INSTALL_DIR" -ForegroundColor Green

# --- 4. Configurar variables de entorno (sistema) ---
Write-Host "[4/5] Configurando variables de entorno..." -ForegroundColor Yellow

[System.Environment]::SetEnvironmentVariable("MAVEN_HOME", $INSTALL_DIR, "Machine")

$currentPath = [System.Environment]::GetEnvironmentVariable("Path", "Machine")
$mavenBin    = "$INSTALL_DIR\bin"

if ($currentPath -notlike "*$mavenBin*") {
    [System.Environment]::SetEnvironmentVariable("Path", "$currentPath;$mavenBin", "Machine")
    Write-Host "      OK: $mavenBin agregado al Path del sistema" -ForegroundColor Green
} else {
    Write-Host "      OK: Maven ya estaba en el Path, no se duplico" -ForegroundColor Green
}

$env:MAVEN_HOME = $INSTALL_DIR
$env:Path       = "$env:Path;$mavenBin"

# --- 5. Verificar instalacion ---
Write-Host "[5/5] Verificando instalacion..." -ForegroundColor Yellow
$mvnOut = cmd /c "`"$INSTALL_DIR\bin\mvn.cmd`" -version 2>&1"
Write-Host ""
Write-Host "=======================================" -ForegroundColor Cyan
Write-Host "  Instalacion exitosa!" -ForegroundColor Green
Write-Host "=======================================" -ForegroundColor Cyan
$mvnOut | ForEach-Object { Write-Host "  $_" }
Write-Host ""
Write-Host "  MAVEN_HOME = $INSTALL_DIR" -ForegroundColor Green
Write-Host ""
Write-Host "  NOTA: Abre una nueva terminal para usar 'mvn' globalmente." -ForegroundColor DarkYellow

# Limpiar ZIP temporal
Remove-Item -Force $TEMP_ZIP -ErrorAction SilentlyContinue