---
name: installPythonDeps
command: |
  if [ -z "${SKILL_packages:-}" ]; then
    echo "error: packages vacío" >&2
    exit 1
  fi
  if command -v python3 >/dev/null 2>&1; then
    PY=python3
  elif command -v python >/dev/null 2>&1; then
    PY=python
  else
    echo "error: python/python3 no encontrado en PATH" >&2
    exit 1
  fi
  # shellcheck disable=SC2086
  $PY -m pip install --disable-pip-version-check $SKILL_packages
  echo "ok: dependencias instaladas: $SKILL_packages"
commandWindows: |
  if ([string]::IsNullOrWhiteSpace($env:SKILL_packages)) {
    Write-Error "error: packages vacío"
    exit 1
  }
  $py = $null
  foreach ($c in @('python', 'python3', 'py')) {
    if (Get-Command $c -ErrorAction SilentlyContinue) { $py = $c; break }
  }
  if (-not $py) {
    Write-Error "error: python/python3/py no encontrado en PATH"
    exit 1
  }
  $pkgs = $env:SKILL_packages -split '\s+' | Where-Object { $_ }
  & $py -m pip install --disable-pip-version-check @pkgs
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
  Write-Output "ok: dependencias instaladas: $($env:SKILL_packages)"
workingDirectory: "."
description: Instala paquetes Python con pip en el entorno actual (reportlab, requests, beautifulsoup4, pandas, etc.). Usar antes de ejecutar scripts que importen librerías de terceros.
---

# installPythonDeps

Instala dependencias Python vía `python -m pip install` antes de correr un script del workspace que las necesite.

## Cuándo usarla
- Tras `createPythonScript`, si el script importa paquetes que no son de la stdlib.
- Cuando el usuario pida explícitamente instalar librerías Python.
- Si `runCommand` falla con `ModuleNotFoundError` / `No module named …`.

## Parámetros
- `packages` (String): Uno o más paquetes separados por espacio (nombre PyPI). Ejemplos: `reportlab`, `requests beautifulsoup4`, `pandas openpyxl`.

## Reglas
- Usar nombres de PyPI (`beautifulsoup4`, no `bs4` como paquete a instalar).
- Solo paquetes necesarios para la tarea actual.
- Preferir `python -m pip` / `python3 -m pip` (no asumir que `pip` está en PATH).

## Ejemplo

```json
{
  "actions": [
    {
      "tool": "installPythonDeps",
      "args": {
        "packages": "requests beautifulsoup4 reportlab"
      }
    }
  ]
}
```
