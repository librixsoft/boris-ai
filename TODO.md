# TODO.md — Boris CLI

---

2026-08-15 

- [x] Historial de conversaciones con Spring AI ChatMemory
  - [x] Implementar MessageWindowChatMemory con ventana configurable (5-10 mensajes)
  - [ ] Agregar comando /clear para limpiar historial de conversación actual
  - [ ] Implementar compresión de contexto (sumarización) para conversaciones largas
  - [ ] Implementar compresión por cantidad de mensajes (threshold configurable)
  - [ ] Limitar maxTokens en opciones del modelo para reducir consumo de memoria
  - [ ] Agregar estadísticas de uso de tokens/memoria por sesión
- [ ] Tool PdfGenerationTool para crear PDFs desde HTML, Markdown y texto plano
