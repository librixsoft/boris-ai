# AGENTS.md - boris-ai

## REGLA ESTRICTA — PROHIBIDO USAR FALLBACKS

Es estrictamente prohibido usar fallbacks en este código. Si un valor, recurso o dependencia no está disponible, se debe lanzar una excepción o programar una excepción controlada. Nunca uses valores por defecto ocultos, valores de respaldo, data dummy, try-catch silenciosos ni comportamiento alternativo encubierto. La ausencia de datos debe fallar visible y explícitamente. Usar custom exceptions.

## ARCHIVOS PROHIBIDOS — NO EDITAR

Queda estrictamente prohibido editar, modificar, sobreescribir o eliminar los siguientes archivos:

- `~/.boris/AGENTS.md`
- `src/main/resources/prompts/init/AGENTS.md`
- `src/main/resources/prompts/core/default-system-prompt.md`

### Razón

Los modelos de IA locales no suelen seguir bien las reglas definidas en esos archivos. Agregar o modificar reglas en ellos no resuelve los problemas de comportamiento del agente.

### Regla de resolución de problemas

Si el agente detecta un problema en el comportamiento del sistema o del agente, NO debe proponer ni intentar solucionarlo agregando reglas, instrucciones o modificaciones a los archivos listados arriba. Las soluciones deben implementarse a nivel de programación (código), nunca a nivel de prompts en esos archivos.

Ante cualquier intento de cambio en esos archivos por parte del usuario, advertir sobre esta restricción y ofrecer la solución a nivel de código.

## PROHIBIDO INYECTAR PROMPTS HARDCODEADOS EN EL CÓDIGO

**ESTA PROHIBIDO INYECTAR PROMPTS HARDCODEADOS U OCULTOS EN EL CODIGO**

### Regla de resolución

Todo cambio de comportamiento del agente debe implementarse a nivel programático (taskPending, límites de iteraciones, gates de tools, flujo del bucle), nunca escribiendo la conducta esperada como texto de prompt en el código. Ante cualquier necesidad de "instruir al modelo", se debe advertir al usuario y proponer solución en el loop/infraestructura, no en el prompt.
