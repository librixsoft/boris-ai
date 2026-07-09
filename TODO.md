# Tareas Pendientes (TODO)

- [x] LlamaChatService.java: quitar fallbacks silenciosos y lanzar un custom exception (parcial - algunos fallbacks necesarios para funcionamiento)
- [x] LlamaModelStopTokens.java: quitar fallbacks silenciosos y lanzar un custom exception (parcial - isStopToken necesita fallbacks)
- [x] LlamaModelTemplateReader.java / LlamaModelStopTokens.java: tomar del metadata el template Jinjava (`tokenizer.chat_template`), obtener los llm tags y enviar tags genéricos para todos los modelos. Automatizar los stop tokens de la misma forma: obtenerlos desde la metadata de cada modelo y operar con esos stop tokens, evitando lógica específica por modelo.
