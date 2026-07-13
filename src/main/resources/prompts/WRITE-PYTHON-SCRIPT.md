---
name: createPythonScript
command: |
  case "$SKILL_path" in
    *.py) ;;
    *) SKILL_path="${SKILL_path}.py" ;;
  esac
  mkdir -p "$(dirname "$SKILL_path")"
  printf '%s' "$SKILL_content" > "$SKILL_path"
  echo "ok: script python creado en $(pwd)/$SKILL_path"
commandWindows: |
  $path = $env:SKILL_path
  if (-not $path.ToLower().EndsWith('.py')) { $path = "$path.py" }
  $dir = Split-Path -Parent $path
  if ($dir -and -not (Test-Path -LiteralPath $dir)) {
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
  }
  Set-Content -LiteralPath $path -Value $env:SKILL_content -NoNewline -Encoding utf8
  $full = Join-Path (Get-Location) $path
  Write-Output "ok: script python creado en $full"
workingDirectory: "."
description: Escribe un script .py en el workspace (~/.boris/workspace) para resolver tareas complejas (PDF, scrapear web, APIs, conversión de datos, etc.).
---

# createPythonScript

Usa esta skill cuando el usuario pida una **instrucción compleja** que no se resuelve con un solo comando simple: generar PDF/Excel, scrapear o visitar una web, llamar APIs, transformar datos, automatizar flujos, etc.

## Flujo obligatorio
1. Diseña un script Python autocontenido que cumpla la petición.
2. Créalo en el workspace con `createPythonScript` (cwd = `~/.boris/workspace`).
3. Si el script necesita librerías (`requests`, `reportlab`, `beautifulsoup4`, `pandas`, …), instálalas con `installPythonDeps`.
4. Ejecútalo con `runCommand` (`python script.py` / `python3 script.py`).
5. Lee la salida y responde al usuario con el resultado (o la ruta del artefacto generado).

## Parámetros
- `path` (String): Ruta relativa al workspace (ej. `crear_pdf.py`, `tasks/scrape_web.py`). Si no termina en `.py`, se añade.
- `content` (String): Código Python completo del script.

## Reglas del script
- Preferir stdlib; si hace falta un paquete de terceros, declararlo e instalarlo antes de correr.
- Imprimir progreso y resultado claro por stdout (rutas de archivos creados, resumen del scrape, errores útiles).
- Guardar artefactos (PDF, CSV, HTML, …) también dentro del workspace.
- No pedir confirmación interactiva.

## Ejemplo — "crea un PDF con el título Hola"

```json
{
  "actions": [
    {
      "tool": "createPythonScript",
      "args": {
        "path": "crear_pdf.py",
        "content": "from reportlab.pdfgen import canvas\np = canvas.Canvas('hola.pdf')\np.drawString(100, 750, 'Hola')\np.save()\nprint('ok: hola.pdf')"
      }
    },
    {
      "tool": "installPythonDeps",
      "args": {
        "packages": "reportlab"
      }
    },
    {
      "tool": "runCommand",
      "args": {
        "command": "python crear_pdf.py",
        "workingDirectory": "."
      }
    }
  ]
}
```

## Ejemplo — "entra a www.ejemplo.com y dime de qué trata"

```json
{
  "actions": [
    {
      "tool": "createPythonScript",
      "args": {
        "path": "resumen_web.py",
        "content": "import urllib.request\nfrom html.parser import HTMLParser\n\nclass T(HTMLParser):\n    def __init__(self):\n        super().__init__(); self.title=''; self._in=False\n    def handle_starttag(self, tag, attrs):\n        self._in = tag=='title'\n    def handle_endtag(self, tag):\n        if tag=='title': self._in=False\n    def handle_data(self, data):\n        if self._in: self.title += data\n\nurl='https://www.ejemplo.com'\nhtml=urllib.request.urlopen(url, timeout=30).read().decode('utf-8','replace')\np=T(); p.feed(html)\nprint('URL:', url)\nprint('Title:', p.title.strip() or '(sin title)')\nprint('Chars:', len(html))\nprint('Preview:', ' '.join(html.split())[:500])"
      }
    },
    {
      "tool": "runCommand",
      "args": {
        "command": "python resumen_web.py",
        "workingDirectory": "."
      }
    }
  ]
}
```
