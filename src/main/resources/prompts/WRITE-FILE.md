---
name: createFile
command: |
  mkdir -p "$(dirname "$SKILL_path")"
  printf '%s' "$SKILL_content" > "$SKILL_path"
  echo "ok: archivo creado en $SKILL_path"
commandWindows: |
  $dir = Split-Path -Parent $env:SKILL_path
  if ($dir -and -not (Test-Path -LiteralPath $dir)) {
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
  }
  Set-Content -LiteralPath $env:SKILL_path -Value $env:SKILL_content -NoNewline -Encoding utf8
  Write-Output "ok: archivo creado en $($env:SKILL_path)"
workingDirectory: "."
description: Crea un archivo con el contenido indicado (vía SO).
---

# createFile

## Parámetros
- `path` (String): Ruta del archivo.
- `content` (String): Contenido completo.

## Ejemplo

```json
{
  "actions": [
    {
      "tool": "createFile",
      "args": {
        "path": "/tmp/hola.txt",
        "content": "hola mundo"
      }
    }
  ]
}
```
