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

### Unica excepcion

Solo que el usuario te lo pida si puedes modificar esos archivos de otro modo no debes hacerlo.

## REGLA DRY — DON'T REPEAT YOURSELF

No repitas código. Si una lógica se necesita en más de un lugar, extraela a una función, método, clase o utilidad compartida. Cada pieza de lógica debe tener una única fuente de verdad.

## REGLA TDD — TEST DRIVEN DEVELOPMENT

Escribe las pruebas antes que el código de implementación. El ciclo es: prueba fallando (red), escribir código mínimo para pasar (green), refactorizar manteniendo las pruebas verdes. Nunca escribas código de producción sin una prueba que lo respalde.

## REGLA DE EXCEPCIONES — PROHIBIDO FALLBACKS Y CATCHS VACÍOS

- Está prohibido usar fallbacks o valores por defecto ocultos. Si algo falla, lanza una excepción o programa una excepción controlada.
- Está prohibido dejar bloques `catch` vacíos. Todo `catch` debe manejar la excepción de forma explícita.
- Usa custom exceptions específicas en lugar de excepciones genéricas (ej. `Exception`, `RuntimeException`, `Throwable`). Cada error debe tener su propia clase de excepción descriptiva.

## REGLAS COMPLEMENTARIAS

- **Tipado estricto**: usa tipos estrictos en todo el código. Prohibido `any`, `object`, `var` sin tipo inferido claro, o casts innecesarios.
- **Nombres explícitos**: variables, funciones y clases deben nombrarse por su intención, no por su implementación. Nada de `data`, `temp`, `foo`, `bar`.
- **Funciones pequeñas**: cada función debe hacer una sola cosa. Máximo 4 parámetros; si necesita más, agrupa en un objeto/DTO.
- **No magic numbers**: literales numéricos y strings de negocio deben extraerse a constantes con nombre descriptivo.
- **Inmutabilidad preferida**: prefiere `val`/`const` sobre `var`/`let` mutable salvo que sea estrictamente necesario.
- **Composición sobre herencia**: prefiere componer comportamientos mediante interfaces o delegación en lugar de jerarquías de herencia profundas.
- **Documentación de funciones públicas**: toda función/clase pública debe tener un comentario breve que explique qué hace, qué recibe y qué devuelve.
- **Validación en entradas**: valida los argumentos de entrada en funciones públicas y lanza custom exceptions si son inválidos.