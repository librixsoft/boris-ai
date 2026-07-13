---
name: listDir
command: |
  ls -la "$SKILL_path"
commandWindows: |
  Get-ChildItem -LiteralPath $env:SKILL_path -Force
workingDirectory: "."
description: Lista un directorio (vía SO).
---

# listDir

## Parámetros
- `path` (String): Ruta a listar.

## Ejemplo

```json
{
  "actions": [
    {
      "tool": "listDir",
      "args": {
        "path": "."
      }
    }
  ]
}
```
