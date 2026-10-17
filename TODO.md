# TODO - Boris AI

## Refactorización de Tools

### Problema actual
Cuando `execute_command` está disponible, el modelo lo usa para todo en lugar de las tools específicas, causando conflictos y comportamiento impredecible.

### Solución: Tools por categoría

| Tool | Operaciones |
|------|-------------|
| `git_tool` | status, commit, push, pull, diff, log, branch |
| `build_tool` | mvn, gradle, npm (compile, test, build) |
| `list_files` | ya existe |
| `batch_read_files` | ya existe |
| `parallel_search` | ya existe |
| `delete_file` | mantener separada (operación destructiva) |

### Principios
- 5-10 tools bien definidas (no una por comando)
- Operaciones destructivas (delete) separadas para control explícito
- Sin `execute_command` genérico
- Cada tool valida sus parámetros internamente

### Tareas
- [ ] Crear `git_tool` con operaciones: status, commit, push, pull, diff, log, branch
- [ ] Crear `build_tool` con operaciones: mvn compile, mvn test, gradle build, npm install
- [x] Eliminar `execute_command` y `batch_execute_commands`
- [x] Mantener `delete_file` como tool separada
