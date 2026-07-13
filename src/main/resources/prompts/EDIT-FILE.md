---
name: editFile
command: |
  if [ ! -f "$SKILL_path" ]; then
    echo "error: no existe $SKILL_path" >&2
    exit 1
  fi
  if [ -z "${SKILL_oldContent:-}" ]; then
    printf '%s' "$SKILL_newContent" > "$SKILL_path"
    echo "ok: archivo reemplazado por completo en $SKILL_path"
  else
    python3 -c '
  import os, pathlib
  path = pathlib.Path(os.environ["SKILL_path"])
  old = os.environ.get("SKILL_oldContent", "")
  new = os.environ.get("SKILL_newContent", "")
  text = path.read_text(encoding="utf-8")
  if old not in text:
      raise SystemExit("error: oldContent no encontrado")
  path.write_text(text.replace(old, new, 1), encoding="utf-8")
  print("ok: archivo editado en", path)
  '
  fi
commandWindows: |
  if (-not (Test-Path -LiteralPath $env:SKILL_path -PathType Leaf)) {
    Write-Error "error: no existe $($env:SKILL_path)"
    exit 1
  }
  if ([string]::IsNullOrEmpty($env:SKILL_oldContent)) {
    Set-Content -LiteralPath $env:SKILL_path -Value $env:SKILL_newContent -NoNewline -Encoding utf8
    Write-Output "ok: archivo reemplazado por completo en $($env:SKILL_path)"
  } else {
    python -c "import os, pathlib, sys; p=pathlib.Path(os.environ['SKILL_path']); o=os.environ.get('SKILL_oldContent',''); n=os.environ.get('SKILL_newContent',''); t=p.read_text(encoding='utf-8'); sys.exit('error: oldContent no encontrado') if o not in t else None; p.write_text(t.replace(o,n,1), encoding='utf-8'); print('ok: archivo editado en', p)"
  }
workingDirectory: "."
description: Edita un archivo (reemplazo total o quirúrgico vía SO/python).
---

# editFile

## Parámetros
- `path` (String): Ruta del archivo.
- `newContent` (String): Contenido nuevo o bloque de reemplazo.
- `oldContent` (String, opcional): Si se omite, se sobrescribe todo el archivo.

## Ejemplo

```json
{
  "actions": [
    {
      "tool": "editFile",
      "args": {
        "path": "/tmp/hola.txt",
        "oldContent": "hola",
        "newContent": "adios"
      }
    }
  ]
}
```
