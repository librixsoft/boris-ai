# TODO.md — Boris CLI

## Completado: Streaming Estilo Aider ✅

- ✅ `StreamingFileWriter.processChunk()` corregido
- ✅ `ChatService.sendMessageStream()` reconectado
- ✅ Streaming funciona con escritura de archivos estilo Aider

---

## Completado: Loop infinito de comandos ✅ (2026-10-17)

- ✅ `BatchExecuteCommandTool.java`: Corregido mensaje de error
- ✅ Circuit breaker para comandos redirigidos (máx 3 intentos)

---

## Completado: Archivos en directorio incorrecto ✅ (2026-10-17)

- ✅ `ProjectContext.java`: ThreadLocal para compartir ruta del proyecto
- ✅ Todos los tools de edición usan `resolveRelativePath()`

---

## Completado: Búsquedas excesivas antes de escribir ✅ (2026-10-17)

- ✅ Prompts actualizados: modelo escribe libremente sin verificar antes
- ✅ Iteración 1 = escritura libre

---

## Pendiente: Segunda Iteración - Validación y Corrección

**Plan detallado:** `plans/segunda-iteracion-validacion.md`

### Flujo actual (Iteración 1)
El modelo escribe código libremente → se guarda el archivo → FIN

### Flujo deseado (Iteración 1 + 2)
1. **Iteración 1**: Modelo escribe código libremente → se guarda
2. **Iteración 2**: Validar cada línea contra CONTEXT.md → mostrar errores → modelo corrige línea por línea

### Estado
- [x] Iteración 1: Escritura libre (completado)
- [ ] Iteración 2: Validación + corrección línea por línea

---

## Pendiente: Validación de CONTEXT.md

### Problema
Si no existe CONTEXT.md, no se valida nada.

### Mejora sugerida
- Warning cuando se escribe código de librería sin CONTEXT.md
