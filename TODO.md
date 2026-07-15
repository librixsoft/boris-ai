# Tareas Pendientes (TODO)

- [x] LlamaChatService.java: quitar fallbacks silenciosos y lanzar un custom exception (parcial - algunos fallbacks necesarios para funcionamiento)
- [x] LlamaModelStopTokens.java: quitar fallbacks silenciosos y lanzar un custom exception (parcial - isStopToken necesita fallbacks)
- [x] LlamaModelTemplateReader.java / LlamaModelStopTokens.java: tomar del metadata el template Jinjava (`tokenizer.chat_template`), obtener los llm tags y enviar tags genéricos para todos los modelos. Automatizar los stop tokens de la misma forma: obtenerlos desde la metadata de cada modelo y operar con esos stop tokens, evitando lógica específica por modelo.
- [x] Crear una skill para cuando el user pida instrucciones complejas crear un script python en el workspace ejemoplo "crea un pdf", "entra a la web www.x y dime qde que trata", etc (+ installPythonDeps)

- [ ] problema actual 


 Problema actual los workers repiten la tarea en evz de dividirla

  Pero el problema conceptual también existe: el POC actual solo manda la misma
  tarea a los cuatro con enfoques distintos. Eso produce cuatro
  interpretaciones, no cuatro subtareas ejecutables. Y no creó hola.html porque
  el POC deshabilita herramientas intencionalmente.

  Para que funcione de verdad debe ser así:

  Tarea: crear hola.html
       ↓
  Descomposición en acciones atómicas
       ↓
  1. Definir contenido HTML
  2. Crear el archivo (único dueño de hola.html)
  3. Verificar ruta, nombre y estructura
  4. Validar que el archivo existe y contiene “Hola mundo”
       ↓
  Resultados compartidos

  La regla clave es: cada archivo o recurso tiene un único dueño para
  escribirlo. Los otros workers no intentan crear lo mismo; hacen partes
  distintas y útiles.

  También hay un límite lógico: “crear un archivo pequeño” es casi una sola
  acción. No se puede dividir en cuatro escrituras paralelas sin duplicar
  trabajo o generar conflicto. En esos casos, un integrante escribe, mientras
  los demás especifican, revisan y validan. Para tareas grandes sí se divide por
  módulos, archivos o pruebas.

  El siguiente cambio correcto sería reemplazar el POC de “cuatro opiniones” por
  una cola real de subtareas con propiedad, ejecución de tools y validación.

  Objetivo: repartir una tarea en los N cantidad de workers