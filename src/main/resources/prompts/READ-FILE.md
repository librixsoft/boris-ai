---
name: readFile
command: |
  if [ ! -f "$SKILL_path" ]; then
    echo "error: no existe $SKILL_path" >&2
    exit 1
  fi
  nl -ba "$SKILL_path"
  echo ""
  echo "[Total: $(wc -l < "$SKILL_path" | tr -d ' ') líneas]"
commandWindows: |
  if (-not (Test-Path -LiteralPath $env:SKILL_path -PathType Leaf)) {
    Write-Error "error: no existe $($env:SKILL_path)"
    exit 1
  }
  $i = 1
  Get-Content -LiteralPath $env:SKILL_path | ForEach-Object {
    "{0,6}`t{1}" -f $i, $_
    $i++
  }
  $lines = @(Get-Content -LiteralPath $env:SKILL_path).Count
  Write-Output ""
  Write-Output "[Total: $lines líneas]"
workingDirectory: "."
description: Lee un archivo y muestra contenido numerado (vía SO).
---

# readFile

## Parámetros
- `path` (String): Ruta del archivo a leer.

## Ejemplo

```json
{
  "actions": [
    {
      "tool": "readFile",
      "args": {
        "path": "/tmp/hola.txt"
      }
    }
  ]
}
```
