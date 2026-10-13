# TODO.md — Boris CLI

---

## Paralelismo de Herramientas

- [ ] write_file_parallel: Crear tool `batch_write_files` que permita crear múltiples archivos en paralelo con una sola llamada (similar a `batch_apply_edits`).  
  - Parámetros: `paths: List<String>` con los paths de los archivos, `contents: List<String>` con el contenido correspondiente a cada archivo.  
  - Implementación: usar `ForkJoinPool` o `CompletableFuture.allOf()` para escribir los archivos en paralelo.  
  - Integrar en `ToolCallingConfig.java` como nuevo método anotado con `@Tool`.

- [ ] execute_command_batch: Crear tool `batch_execute_commands` para ejecutar múltiples comandos en una sola llamada (similar a `batch_apply_edits`).  
  - Actualmente cada comando se ejecuta individualmente con `execute_command`.  
  - Mejora: permitir pasar una lista de comandos `[cmd1, cmd2, cmd3]` y ejecutarlos en una sola llamada.  
  - Soportar diferentes shells: bash/sh en Linux/macOS, cmd/powershell en Windows.  
  - Retornar resultados individuales de cada comando + exit code del último.  
  - Parámetros: `commands: List<String>` con los comandos a ejecutar, `workingDirectory: String` opcional.
