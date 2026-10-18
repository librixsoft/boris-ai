# TODO - Boris AI

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
  - Si la clase/lib es externa y desconocida: disparar una sola vez create_library_context (cache a
    .boris/repos) y redirigir a query_symbols/query_framework_knowledge; no dejar caer a list_files.
  - Quitar o rebajar el hint de ChatService:525 (list_files como default).

  Fix EJECUTADO:
  - Nuevo com.boris.tooling.EditGateService: extrae clases involucradas de imports/refs del contenido
    editado; si todas estan indexadas (SymbolRepository.findExact) o tienen knowledge card
    (KnowledgeStorageService.hasKnowledge) permite editar directo. Si hay desconocidas, auto-indexa
    una sola vez desde cache .boris/repos (CodeIndexerService) y si sigue desconocida emite UN redirect
    por ciclo de errores apuntando a create_library_context(query_symbols/query_framework_knowledge),
    prohibiendo list_files; los intentos siguientes no re-bloquean (sin cascada de exploracion).
  - ToolCallingConfig: gates de write_file y apply_edit ahora usan EditGateService.checkEditAllowed.
  - SymbolRepository: nuevo metodo findExact(name).
  - LearningLimiter: flag hasIssuedUnknownLibraryRedirect (se limpia en clearCompileErrors/reset).
  - ChatService.buildPromptWithHistory: hint rebajado, ya no promociona list_files como default; ahora
    pide editar directo sin explorar.

[ ] - Bloqueo de acceso del agente a $PROYECTO/.boris ya implementado (ProjectContext.isInsideAgentWorkspace + guards en ListFilesTool y BatchReadFileTool; query_symbols ahora sugiere query_framework_knowledge en vez de exponer la ruta del .md). Pendiente opcional: extender guard a write/delete para que el agente tampoco escriba o borre dentro de .boris via tools.