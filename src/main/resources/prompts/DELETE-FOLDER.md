---
name: deleteFolder
command: |
  if [ ! -d "$SKILL_path" ]; then
    echo "error: no es una carpeta o no existe: $SKILL_path" >&2
    exit 1
  fi
  rm -rf "$SKILL_path"
  echo "ok: carpeta borrada $SKILL_path"
commandWindows: |
  if (-not (Test-Path -LiteralPath $env:SKILL_path -PathType Container)) {
    Write-Error "error: no es una carpeta o no existe: $($env:SKILL_path)"
    exit 1
  }
  Remove-Item -LiteralPath $env:SKILL_path -Recurse -Force
  Write-Output "ok: carpeta borrada $($env:SKILL_path)"
workingDirectory: "."
description: Elimina una carpeta y su contenido (vía SO).
---

# deleteFolder

## Parámetros
- `path` (String): Carpeta a eliminar.

## Ejemplo

```json
{
  "actions": [
    {
      "tool": "deleteFolder",
      "args": {
        "path": "/tmp/carpeta"
      }
    }
  ]
}
```
