- [ ] Delegar una tarea entre los 4 modelos usando el concepto de lógica en cadena

  Reutilizar la misma lógica de interacción en cadena ya implementada en el
  punto anterior (donde cada modelo recibe la respuesta del anterior como
  input y responde secuencialmente), pero en vez de que los 4 modelos
  hagan la misma operación (+1), cada uno ejecuta un paso distinto de una
  tarea compleja dividida en sub-tareas.

  Es decir: se mantiene el mismo mecanismo de paso de mensajes
  (output del modelo N = input del modelo N+1), solo cambia QUÉ hace
  cada modelo con ese input.

  Ejemplo de funcionamiento actual (POC suma incremental, ya implementado):
  User envía: 5
  Modelo 1 recibe: 5 → responde: 5 + 1 = 6
  Modelo 2 recibe: 5 + 1 = 6 → responde: 6 + 1 = 7
  Modelo 3 recibe: 6 + 1 = 7 → responde: 7 + 1 = 8
  Modelo 4 recibe: 7 + 1 = 8 → responde: 8 + 1 = 9

  Ejemplo de funcionamiento propuesto (misma lógica, aplicada a delegación real):
  User envía: "Analiza las ventas del Q2 y dame un resumen ejecutivo"
  Modelo 1 recibe: la tarea completa → responde: datos extraídos/estructurados
  Modelo 2 recibe: datos estructurados del Modelo 1 → responde: hallazgos clave del análisis
  Modelo 3 recibe: hallazgos del Modelo 2 → responde: borrador del resumen en prosa
  Modelo 4 recibe: borrador del Modelo 3 → responde: versión final pulida

  Cambios necesarios sobre la implementación actual:
  1. El pipeline de paso de mensajes (Modelo N → Modelo N+1) se reutiliza tal cual
  2. Reemplazar el prompt fijo "+1" por un prompt/rol distinto por posición
     en la cadena (extracción, análisis, redacción, revisión)
  3. Definir cómo se le indica a cada modelo cuál es SU rol dentro de la
     tarea general (hardcodeado por ahora, dinámico como mejora futura)
  4. Mantener el mismo manejo de errores/timeouts entre pasos que ya
     existe en el POC de suma