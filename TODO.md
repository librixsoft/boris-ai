# TODO.md — Boris CLI

---

## Sistema de Contexto Persistente (LibraryContextTool)

**Estado:** Esperando que se restablezca el rate limit de GitHub (~1 hora desde 2026-10-16 22:30)

### Validar cuando rate limit se restablezca:

- [ ] Ejecutar test: `mvn test -Dtest=LibraryContextToolTest`
- [ ] Verificar que extrae métodos de clases principales:
  - `MultiWindowTextGUI` debe tener: `addWindow`, `addWindowAndWait`, `getActiveWindow`
  - `BasicWindow` debe tener: `setComponent`, `setTitle`, `close`
  - `Panel` debe tener: `addComponent`, `setLayoutManager`
- [ ] Verificar que el CONTEXT.md generado tiene estructura: `### Clase` seguido de `- método()`
- [ ] Probar con Boris: pedir crear dashboard con Lanterna y verificar que NO alucina métodos

### Mejoras pendientes si falla:

- [ ] Agregar token de GitHub para evitar rate limit (configurar en settings.json)
- [ ] Mejorar `extractPublicMethods` para capturar más métodos
- [ ] Agregar más clases clave a `findMainSourceFiles`
- [ ] Extraer parámetros de métodos, no solo nombres

### Archivos modificados:
- `src/main/java/com/boris/tooling/tool/LibraryContextTool.java`
- `src/test/java/com/boris/tooling/tool/LibraryContextToolTest.java`
- `src/main/java/com/boris/tooling/tool/WriteTool.java` (bloqueo sin contexto)
- `src/main/java/com/boris/tooling/tool/ContextTracker.java` (tracking de lectura)

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
