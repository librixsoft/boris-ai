# TODO - Boris AI

* FIX THIS ERROR LOG  LOOP

TEST SESSION
===========

boris listo. Escribe tu mensaje.
>> /Users/lastprophet/Documents/workspaces-ai/tui4j creame un dashboard
sencilo con tui4j java
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/tui4j
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/tui4j
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/tui4j
[tool][list_files] Listando: /Users/lastprophet/Documents/workspaces-ai
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/boris-tamboui
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/boris-tamboui
[tool][batch_read_files] Leyendo 1 archivo(s):
/Users/lastprophet/Documents/workspaces-ai/boris-tamboui/pom.xml
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/boris-tamboui/src
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/boris-tamboui/src
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/boris-tamboui/src/main
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/boris-tamboui/src/main
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/boris-tamboui/src/main/java
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/boris-tamboui/src/main/java/com
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/boris-tamboui/src/main/java/com/
boris
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/boris-tamboui/src/main/java/com/
boris/dashboard
:: 

⚠️ Tarea no completada: se alcanzo el maximo de 15 turnos de ejecucion sin
aplicar modificaciones en los archivos.
[stats][14:17:05] 2m26s · in: 273 tokens · out: 14591 tokens
>> con tinua
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/tui4j
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/tui4j
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/tui4j
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/tui4j/.boris
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/tui4j/.boris/repos
[tool][batch_read_files] Leyendo 1 archivo(s):
/Users/lastprophet/Documents/workspaces-ai/tui4j/.boris/repos/tui4j/README.
md
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/tui4j/.boris/repos/tui4j/src
[tool][web_search] Buscando: tui4j simple dashboard example java
[tool][web_search] No se encontraron resultados
[tool][web_search] Límite alcanzado (máx 1 llamada por consulta)
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/tui4j/.boris/repos/tui4j
[tool][batch_read_files] Leyendo 1 archivo(s):
/Users/lastprophet/Documents/workspaces-ai/tui4j/.boris/repos/tui4j/AGENTS.
md
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/tui4j
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/tui4j/.boris/repos/tui4j/src/mai
n
[tool][list_files] Listando:
/Users/lastprophet/Documents/workspaces-ai/tui4j/.boris/repos/tui4j/src/mai
n/resources/examples
:: 

⚠️ Tarea no completada: se alcanzo el maximo de 15 turnos de ejecucion sin
aplicar modificaciones en los archivos.
[stats][14:20:06] 2m23s · in: 255 

## Propuestas de correccion (diagnostico del loop)

### [ ] Fix anti-loop de tool calls repetidas
El agente hace loop re-invocando `list_files` con el mismo path cuando el resultado es un error (ej. "Directory not found") y agota los 15 turnos sin modificar archivos.

- Archivo: `src/main/java/com/boris/llm/client/BorisLlmClient.java` (bucle en `executeChatWithTools`, ~linea 449).
- Guard anti-loop: si el tool call (nombre + argumentos) es identico al inmediatamente anterior, no ejecutarlo; devolver al modelo un instructivo: "Ya invocaste esta herramienta con los mismos argumentos. Usa el resultado anterior, corrige el path, o procede con las herramientas de edicion."
- No debe consumir turno de exploracion repetida.

### [ ] Error accionable en ListFilesTool
Cuando el path no existe, el modelo solo recibe `{"success":false,"message":"Directory not found"}` y reintenta ciegamente. El path del prompt (`workspaces-ai/tui4j`) no existe en disco: el repo esta en `boris-tamboui`.

- Archivo: `src/main/java/com/boris/tooling/tool/ListFilesTool.java` (linea 57, caso "Directory not found").
- Al no existir el path, incluir en el resultado informacion para autocorregir: listado del directorio padre (o paths cercanos existentes), en vez de solo el mensaje de error.

### [ ] Nudge a listado recursivo (opcional)
El modelo explora un nivel a la vez (`src -> main -> java -> com -> boris ...`) quemando turnos (default `recursive:false`, max_depth 3).

- Archivo: `src/main/java/com/boris/tooling/tool/ListFilesTool.java` (descripcion del tool, linea 21-41).
- Ajustar la descripcion del tool para indicar preferir `recursive:true` al explorar un proyecto. Evaluar default recursivo con max_depth limitado.

### [ ] Persistir/continuar historial entre turnos del usuario
Al escribir "continua" arranca una ejecucion nueva sin el historial de la corrida anterior (`fullMessages` se arma solo con system + conversacion, BorisLlmClient.java:225-231), por eso el agente vuelve a explorar desde cero.

- Archivo: `src/main/java/com/boris/llm/client/BorisLlmClient.java` (lineas 225-231).
- O donde se maneja el historial de sesion: propagar el historial de tools/results de la corrida anterior al reintentar la tarea.