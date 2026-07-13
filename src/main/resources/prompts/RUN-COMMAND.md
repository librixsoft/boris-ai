---
name: runCommand
command: |
  eval "$SKILL_command"
commandWindows: |
  Invoke-Expression $env:SKILL_command
workingDirectory: "{{workingDirectory}}"
description: Ejecuta un comando arbitrario del SO.
---

# runCommand

## Parámetros
- `command` (String): Comando a ejecutar.
- `workingDirectory` (String): Directorio de trabajo.

## Ejemplo

```json
{
  "actions": [
    {
      "tool": "runCommand",
      "args": {
        "command": "ls -la",
        "workingDirectory": "."
      }
    }
  ]
}
```
