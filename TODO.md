# TODO.md — Boris CLI

## Pendiente: Streaming Estilo Aider

### Problema actual
El `StreamingFileWriter` fue deshabilitado temporalmente porque:
1. Espera líneas completas (busca `\n`) pero el streaming envía chunks parciales
2. El texto se "tragaba" porque quedaba en el buffer esperando un newline que nunca llegaba
3. Chunks como "Hey" (sin newline) nunca se procesaban

### Solución requerida
Modificar `StreamingFileWriter.processChunk()` para:
1. Enviar texto inmediatamente aunque no tenga newline (para texto normal)
2. Solo buffear cuando está DENTRO de un bloque de código (`inCodeBlock = true`)
3. Al detectar `\n` dentro de un bloque de código, escribir esa línea al archivo

### Código a modificar
`src/main/java/com/boris/edit/StreamingFileWriter.java`:
```java
public String processChunk(String chunk) {
    // Si NO estamos en bloque de código, enviar todo inmediatamente
    if (!inCodeBlock) {
        // Buscar inicio de bloque en el chunk
        // Si no hay, devolver todo el chunk
        // Si hay, procesar el inicio y buffear el resto
    }
    
    // Si estamos en bloque de código, buffear hasta encontrar newlines
    // Cada newline = una línea para escribir al archivo
}
```

### Estado actual
- ✅ `StreamingFileWriter.processChunk()` corregido - devuelve texto parcial inmediatamente fuera de bloques de código
- ✅ `ChatService.sendMessageStream()` reconectado con `StreamingFileWriter`
- ✅ El streaming real funciona con escritura de archivos estilo Aider

### Feature completado ✅
1. ~~Arreglar `StreamingFileWriter` según la solución descrita~~ ✅
2. ~~Volver a conectar en `ChatService.sendMessageStream()`~~ ✅
3. (Opcional) Agregar tests para chunks parciales

---

## Pendiente: Validación de CONTEXT.md

### Problema
`BulkEditTool` y `SearchReplaceEditTool` validan contra CONTEXT.md pero:
1. Si no existe CONTEXT.md, no valida nada (pasa todo)
2. No hay forma de forzar la validación

### Mejora sugerida
- Opción para requerir CONTEXT.md antes de escribir código de librerías externas
- Warning cuando se escribe código sin contexto

