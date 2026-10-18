# TODO - Boris AI

## Mejoras de exploracion de archivos (acelerar busqueda de carpetas/archivos)

Todas van sobre el tool `list_files` existente (src/main/java/com/boris/tooling/tool/ListFilesTool.java), sin crear tools nuevos.

### [x] list_files recursivo por defecto
El modelo explora un nivel por turno (`/tui4j -> src -> main -> java -> com -> ...`), quemando ~10 turnos para ver el arbol entero.

- Default `recursive:true` con `max_depth` limitado (ej. 5); actualizar la descripcion del tool (lineas 21-41) para indicarlo.
- Reduce la fase de exploracion de ~10 turnos a 1 llamada.

### [x] Parametro de filtro por nombre (glob) en list_files
El modelo casi nunca necesita ver el arbol completo: necesita ubicar archivos por nombre. Un filtro resuelve en 1 llamada lo que hoy le cuesta 10 turnos de listado.

- Agregar parametro `filter` al schema de `list_files` (ej. `filter: "*Dashboard*"`, `filter: "pom.xml"`).
- Cuando viene `filter`, listar de forma recursiva solo los paths que matcheen (respetando .gitignore si aplica).
- Dejar el modo actual (sin filtro) intacto para exploracion general.

### [x] Resultado compacto en list_files
Un listado lineal de 15 directorios consume contexto y confunde a modelos chicos.

- Devolver el listado como arbol compacto en una linea por nivel, ej: `src/main/java/com/boris[tool,dashboard,llm]/...`.

La combinacion optima es recursivo por defecto + filtro glob + salida compacta: la exploracion tipica baja de ~15 turnos a ~3.