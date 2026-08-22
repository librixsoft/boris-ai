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
  - [ ] Sistema de gestión de contexto con H2 en memoria y ventana deslizante por tokens (max 16k)
  - [ ] Contar tokens del historial enviado al modelo (no solo respuestas)
  - [ ] Contar tokens reales consumidos por el servidor Llama (no aproximación por chunks)

- [x] @Tool PdfGenerationTool para crear PDFs desde HTML, Markdown y texto plano
- [ ] Sistema de skills compatible con skills estándar (Claude, OpenClaw) en workspace
  - [ ] Implementar estructura de directorios .boris/skills/
  - [ ] Crear formato SKILL.md compatible con Claude/OpenClaw
  - [ ] Agregar sistema de carga/descarga de skills
  - [ ] Implementar detección automática de skills en workspace
  - [ ] Crear comandos /skill list, /skill load, /skill unload

- [x] En la UI cuando se envie un mensaje y aparece el spinner abajo siempre debera mostrarse el input text del usuario (de momento como no habra un sistema de colas complejo) cuando el modelo o chat aun este procesando la info y aparezda el spinner el user tendra habgilitado el campo de input si envia un nuevo prompt se enviara al chat pero no se proesara hasta q termine de responder el chat, el sistema de colas de momento sera muy sencillo se ira guardando en un array list e ira saliendo en ese orden de momento las colas son muy sencillas posteriormente se robusteceran

- [ ] Al pedirle al agente "busca info sobre esta web y generame un odf con esta info" ha creado un pdf con css, el agente no sabe interpretar o hace falta decirle como usar la tool o hacer strip solo de contenido

- [x] En el chat cuando se recibe una respuesta del modelo con formato json o markdown se repite muchas veces la respuesta en el chat, se imprimie multiples veces

- [ ] Implementar un settings doctor (settings.json) por si se rompe el file de config se restaure

- [ ] Aplicar plan core  diffs: /Users/lastprophet/Documents/workspaces/boris-ai/plans/plan-so-tool-calling-refined.md

- [x] /Users/lastprophet/.boris/settings.json  "enableHistory": false,  el sistema de history de spring ai debera funcionar ahora con memory ram g2 bd xq se tiene un context window demasiado limitado xq es ia local este context se vas reiniciando al llegar al limite para eso debera estar la capa de persistenmcia de memory ram bd recordando todo a pesar de q se reiniicie el context windows de spring debes habilitarlo pero no inyectar todo el historial , todo el historiasl completo debera ir en la memory ram bd h2 y haer la busqueda en la bd cuando se ocupe algo o se encuentre e inyectarlo en el context windows de spering ai

- [ ] Sistema CodeArtifact: swap H2 para código (context window limpio)
  - Entidad JPA CodeArtifact: id, sessionId, type(CLASS|METHOD|FILE), name, filePath, content, tokens, language, createdAt
  - Auto-detección en MemoryService.saveAssistantMessage(): parsea respuesta, extrae bloques ```java ...```, crea CodeArtifact por clase/método
  - Repository CodeArtifactRepository: findBySessionIdAndNameContaining, findBySessionIdAndType
  - MemoryService.buildContextPrompt():
    - searchRelevantCodeArtifacts(query): busca por nombre de clase/método en CodeArtifact
    - Inyecta solo metadata: `[CODEARTIFACT] CLASS MiClase (src/main/java/.../MiClase.java, 2.3k tokens, java)`
    - NO inyecta contenido completo
  - Tools nuevas:
    - readCodeArtifact(name): devuelve contenido completo de la clase/método
    - listCodeArtifacts(sessionId): lista todos los artifacts de la sesión
  - Swap real: context window = referencias (~100 tokens c/u); código pesado (2k-10k tokens) vive en H2 RAM disk
  - Limpieza: TTL opcional o max artifacts por sesión

- [ ] Memoria por historial al llegar al limite se guardaa en bd y en seguiente iteracion hace busqueda e inyecta en el prompt (probar feature)