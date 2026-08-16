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

- [x] @Tool PdfGenerationTool para crear PDFs desde HTML, Markdown y texto plano
- [ ] Sistema de skills compatible con skills estándar (Claude, OpenClaw) en workspace
  - [ ] Implementar estructura de directorios .boris/skills/
  - [ ] Crear formato SKILL.md compatible con Claude/OpenClaw
  - [ ] Agregar sistema de carga/descarga de skills
  - [ ] Implementar detección automática de skills en workspace
  - [ ] Crear comandos /skill list, /skill load, /skill unload

- [x] En la UI cuando se envie un mensaje y aparece el spinner abajo siempre debera mostrarse el input text del usuario (de momento como no habra un sistema de colas complejo) cuando el modelo o chat aun este procesando la info y aparezda el spinner el user tendra habgilitado el campo de input si envia un nuevo prompt se enviara al chat pero no se proesara hasta q termine de responder el chat, el sistema de colas de momento sera muy sencillo se ira guardando en un array list e ira saliendo en ese orden de momento las colas son muy sencillas posteriormente se robusteceran
