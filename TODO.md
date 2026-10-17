# TODO.md — Boris CLI

## Completado: Streaming Estilo Aider ✅

- ✅ `StreamingFileWriter.processChunk()` corregido
- ✅ `ChatService.sendMessageStream()` reconectado
- ✅ Streaming funciona con escritura de archivos estilo Aider

---

## Completado: Loop infinito de comandos ✅ (2026-10-17)

### Problema
El modelo entraba en loop infinito reintentando `ls`, `find`, `cat` porque el mensaje de error era incorrecto ("Redirigiendo a GitHub API" en vez de "USA list_files").

### Solución aplicada
- ✅ `BatchExecuteCommandTool.java`: Corregido mensaje de error para usar `blockedReason` real
- ✅ Agregado circuit breaker para comandos redirigidos (máx 3 intentos)
- ✅ Mensajes claros: "REDIRIGIR (intento 1/3): USA list_files en lugar de ls"

---

## Completado: Archivos en directorio incorrecto ✅ (2026-10-17)

### Problema
Al escribir `/ruta/proyecto comando`, el modelo creaba archivos en el directorio de boris-ai en vez del proyecto especificado.

### Solución aplicada
- ✅ `ProjectContext.java` (nuevo): ThreadLocal para compartir ruta del proyecto
- ✅ `ChatService.java`: Extrae ruta del mensaje y setea ProjectContext
- ✅ `StreamingFileWriter.java`: Usa `resolveRelativePath()` para rutas Aider
- ✅ `BulkEditTool.java`: Usa `resolveRelativePath()` + quitó prints
- ✅ `SearchReplaceEditTool.java`: Usa `resolveRelativePath()`
- ✅ `WriteTool.java`: Usa `resolveRelativePath()`
- ✅ `EditTool.java`: Usa `resolveRelativePath()`

---

## Completado: Búsquedas excesivas antes de escribir ✅ (2026-10-17)

### Problema
El modelo hacía decenas de `search_context` para "verificar" cada clase/método ANTES de escribir código, causando lentitud extrema.

### Solución aplicada
- ✅ `default-system-prompt.md`: Cambió de "verificar antes" a "validación automática post-escritura"
- ✅ `AGENTS.md`: Mismo cambio - escribir directo, validar después
- ✅ Nuevo flujo: escribir → validación automática → corregir errores

---

## Pendiente: Autocorrección de Alucinaciones

**Plan detallado:** `plans/autocorreccion-alucinaciones.md`

### Problema
El modelo local alucina código (APIs incorrectas). Se valida DESPUÉS de generar pero no hay autocorrección.

### Solución propuesta
Autocorrección determinística SIN llamar al LLM cuando hay 1 sugerencia clara:
- `client.fetch()` → `client.send()` (autocorregir automáticamente)
- Múltiples opciones → micro-prompt de ~20 tokens

### Estado
- [ ] Fase 1: Autocorrección determinística (LineValidator + BulkEditTool)
- [ ] Fase 2: Micro-prompts para casos ambiguos

---

## Pendiente: Validación de CONTEXT.md

### Problema
`BulkEditTool` y `SearchReplaceEditTool` validan contra CONTEXT.md pero:
1. Si no existe CONTEXT.md, no valida nada (pasa todo)
2. No hay forma de forzar la validación

### Mejora sugerida
- Opción para requerir CONTEXT.md antes de escribir código de librerías externas
- Warning cuando se escribe código sin contexto
