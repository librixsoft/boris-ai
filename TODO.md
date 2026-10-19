# TODO - Boris AI

[x] El agente tiene que escribir generar codigo en el chat en tiempo real y cuando terminar guardar el file con write_file en el disco.

  Implementacion:
  - El flujo write_file ya guarda con la tool existente (WriteTool -> StreamingFileWriter), el problema real era
    que executeChatWithTools usaba llamada sincronica (chat()): el codigo viajaba dentro de los arguments del
    tool call (invisible) y el preview se dumpeaba de golpe recien cuando la tool ejecutaba.
  - Nuevo com.boris.tooling.WriteArgsPreviewStreamer: parsea incrementalmente los deltas JSON de arguments de
    write_file durante el streaming SSE, un-escapa el campo content en tiempo real (\\n, \\t, \", \\uXXXX) y emite
    header @@ file (crear) @@ + las lineas al DiffLineNotifier conforme el modelo las va generando. Expone por
    ThreadLocal el PreviewInfo(path, lineas mostradas) para la ejecucion posterior.
  - BorisLlmClient.executeChatWithTools: cuando hay consumer de streaming, cada iteracion del loop pasa de
    chat() sincronico a chatStream() (SSE real): los deltas de texto llegan al chat en tiempo real; los deltas
    de tool_calls se acumulan por indice (id/name/arguments) y el preview de write_file se streamea durante la
    generacion; al terminar el stream ejecuta las tools como antes (gates de EditGateService intactos).
  - StreamingFileWriter.writeFileWithPreview(path, content, streamedLines): si el preview ya se streameo en vivo
    (mismo path resuelto) omite el dump y solo escribe + emite footer; el dump completo queda para el caso
    sincronico (sendMessage sin streaming) sin cambiar comportamiento previo.
  - ChatService: el historial de la conversacion en modo streaming se guarda limpio de tags de think.

[x] - Siempre que se le pide al agente crear un file va y analiza todos los repos o hace list_files, solo debe de ir a analizar el repo cuando se le pida que cree un archivo o libreria que desconoce como "creame una app java tui en lanterna" en caso contrario debe crear el file o editar sin ir a analizar repos o documentarse (a nivel programacion no a nivel prompt)

  Diagnostico (traza real en tui4j, tarea simple: "mvn compile y corrige errores"):
  El agente termino en loop de list_files raiz->src->raiz, git log repetido y web_search antes de tocar nada.
  Causas encontradas:
  1. Gate de edicion en ToolCallingConfig (write_file ~:225 y apply_edit ~:330): si hay errores de compilacion,
     BLOQUEA la edicion y ordena "usa query_framework_knowledge". Con la lib externa (tui4j) sin indexar ni
     en conocimiento, query_symbols y query_framework_knowledge volvieron vacios y el agente quedo sin salida
     que no sea explorar el disco (list_files) en cascada: pom -> codigo -> arbol -> git log -> re-listado -> web_search.
  2. Validacion de write_file "Validates classes against indexed symbols BEFORE writing": cuando el indice no
     tiene los simbolos de la lib externa, tambien bloquea y empuja a explorar.
  3. Hint inyectado en ChatService.java:525 que promociona list_files como default para cualquier tarea.

  Fix propuesto (a nivel programacion, no prompt):
  - Cambiar la condicion de los gates: solo exigir documentacion (query_framework_knowledge / analyze repo)
    si la clase o libreria involucrada NO esta ni indexada en query_symbols NI tiene knowledge card
    (KnowledgeStorageService.hasKnowledge). Si ya esta, dejar editar directo sin ordenar documentarse.
  - Si la clase/lib es externa y desconocida: disparar una sola vez github_repo (cache a
    .boris/repos) y redirigir a query_symbols/query_framework_knowledge; no dejar caer a list_files.
  - Quitar o rebajar el hint de ChatService:525 (list_files como default).

  Fix EJECUTADO:
  - Nuevo com.boris.tooling.EditGateService: extrae clases involucradas de imports/refs del contenido
    editado; si todas estan indexadas (SymbolRepository.findExact) o tienen knowledge card
    (KnowledgeStorageService.hasKnowledge) permite editar directo. Si hay desconocidas, auto-indexa
    una sola vez desde cache .boris/repos (CodeIndexerService) y si sigue desconocida emite UN redirect
    por ciclo de errores apuntando a github_repo(query_symbols/query_framework_knowledge),
    prohibiendo list_files; los intentos siguientes no re-bloquean (sin cascada de exploracion).
  - ToolCallingConfig: gates de write_file y apply_edit ahora usan EditGateService.checkEditAllowed.
  - SymbolRepository: nuevo metodo findExact(name).
  - LearningLimiter: flag hasIssuedUnknownLibraryRedirect (se limpia en clearCompileErrors/reset).
  - ChatService.buildPromptWithHistory: hint rebajado, ya no promociona list_files como default; ahora
    pide editar directo sin explorar.

[ ] - Bloqueo de acceso del agente a $PROYECTO/.boris ya implementado (ProjectContext.isInsideAgentWorkspace + guards en ListFilesTool y BatchReadFileTool; query_symbols ahora sugiere query_framework_knowledge en vez de exponer la ruta del .md). Pendiente opcional: extender guard a write/delete para que el agente tampoco escriba o borre dentro de .boris via tools.

[x] - Sistema debe detectar automáticamente la ruta del proyecto del prompt y crear el cache .boris/repos en esa ruta (no hardcodear ~/.boris/repos/)

  Diagnostico:
  El usuario pasó un prompt con ruta absoluta al final: "/Users/lastprophet/Documents/workspaces-ai/landings-8b/index.html"
  pero el sistema siempre usaba el cache global ~/.boris/repos/ sin detectar la ruta del proyecto.

  Fix EJECUTADO:
  - LibraryContextTool.java: modificado para usar projectPath/.boris/repos/ cuando se proporciona project_path,
    fallback a ~/.boris/repos/ global si no se proporciona.
  - ToolCallingConfig.java: inyección automática de project_path desde ProjectContext cuando el modelo no lo provee.
  - ChatService.java: nuevo patrón ABSOLUTE_PATH_PATTERN para detectar rutas absolutas (/Users/..., /home/...)
    en cualquier posición del prompt; extractProjectPath extrae el directorio padre cuando detecta un archivo.
  - EditGateService.java: busca repos en ambos lugares (cache del proyecto + cache global).
  - FrameworkKnowledgeTool.java: busca archivos fuente en ambos caches (proyecto + global).
  - Actualizadas descripciones de parámetros project_path en LibraryContextTool y ToolCallingConfig.
