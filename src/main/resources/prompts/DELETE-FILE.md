---
name: deleteFile
command: |
  rm -f "$SKILL_path"
  echo "ok: archivo borrado $SKILL_path"
commandWindows: |
  Remove-Item -LiteralPath $env:SKILL_path -Force -ErrorAction SilentlyContinue
  Write-Output "ok: archivo borrado $($env:SKILL_path)"
workingDirectory: "."
description: Elimina un archivo (vía SO).
---

# deleteFile

## Parámetros
- `path` (String): Archivo a eliminar.

## Ejemplo

```json
{
  "actions": [
    {
      "tool": "deleteFile",
      "args": {
        "path": "/tmp/hola.txt"
      }
    }
  ]
}
```
