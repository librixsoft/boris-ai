Paso 1: Ministral-3-14B-Reasoning-2512-Q4_K_M.gguf (Planner) recibe el requerimiento, realiza el análisis lógico y desglosa el trabajo en una lista estructurada de tareas orientada a objetivos, definiendo los requisitos técnicos y los archivos a modificar.

Paso 2: Ministral-3-14B-Reasoning-2512-Q4_K_M.gguf (Coder) genera el código completo para cada tarea definida anteriormente, asegurando la sintaxis correcta y empaquetando todo el resultado en un JSON estricto.

Formato JSON que elaborará Ministral (Coder):

JSON
{
  "tasks": [
    {
      "id": 1,
      "file": "${workspace/}ruta/archivo.ext",
      "description": "Detalle técnico",
      "code": "código_completo_y_funcional"
    }
  ]
}

NOTA IMPORTANTE: el nodo  "archivo" arriba de la clase java especifica la ruta principal
del workspace define al inicio de la clase el workspace  y tomala para incluirla
al inicilo de la ruta del archivo ejemplo ${workspace/} ${workspace/}ruta/archivo.ext

Paso 3: Gemma-4-E4b-it.Q4_K_M.gguf (Designer) analiza el JSON generado, verifica la coherencia global del sistema, valida dependencias entre archivos y realiza las correcciones de arquitectura o mejoras de estilo necesarias antes de la escritura.

Paso 4: Gemma-4-E4b-it.Q4_K_M.gguf (Writer) procesa el archivo JSON final y ejecuta la escritura física de los archivos en el disco duro, asegurando la estructura de directorios y la integridad del código resultante.

Notas: No agregues o quita throws exeptions del codigo para
no romper el flujo en su lugar solo cambialo por un logger