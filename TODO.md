# TODO


- [x]  el boton "+" carga o trata de cargar otro modelo cuando solo deberia limpíar el chat y limpiar kv cache y todo ventana de contexto etc
- [x]  Simplificar UI: Se eliminó la pantalla de bienvenida y el botón "Welcome", dejando el botón "+" como método principal para iniciar chats limpios.
- [x]  Optimización KV Cache: Se implementó limpieza forzada en la reutilización de modelos base y se optimizó la velocidad de limpieza en el backend.
- [x] Mejorar warmup del CUDA graph: Implementar warmup más completo con múltiples forward passes de diferentes tamaños de batch y patrones para igualar la velocidad de LM Studio en el primer mensaje (actualmente solo hace un forward pass dummy con "hello").
- [x] Warmup completo: Agregar simulaciones con prefill pequeño (32 tokens), mediano (n_batch/2) y multi-turno (prefill + varios decodes) para calentar todos los escenarios antes del primer mensaje real.
- [x] KV Cache conversacional: El segundo mensaje en adelante sigue tardando igual que el primero. Investigar por qué no se reutiliza el KV cache entre mensajes de la misma conversación (LM Studio lo hace y solo procesa los tokens nuevos de cada turno).

- [x] Enviar un "hello world" al calentamiento de cuda literal enviar esa cadena en el warmpup

- [x] en app.html desde el panel models cuando el usuario selecciona y carga un nuevo modelo en el calentamiento necesitas checar /api/models/warmup-status y agregar un spinner hasta q el calentamiento termina (puesto q se van a cargar modelos pesados) el calentamiento debe comenzar desde cargo el modelo... ahi agregas el spinner una vez q termine el calentamiento ahora si reedirijes al user a la pantalla de chat (ya recibira el primer mensaje rapido xq ya ocurrio el calentamiento) y tmb los mensajes posteriores ya los recibira rapido xq ya ocurrio la carga,
necesitas obligar al sistema a hacer calentamiento antes de comenzar a chatear para q el usuario ya no vea delay entre mensajes

- [x] Unificar mensaje json de respuesta en controller, services, respuestas success y de error, regresar un json unificado crear dto de respuesta
Map<String, Object> cambiarlo por un dto de respuesta
aplicar para todos los sercicios de controler en adelante unicamente para este endpoint 

    /** Ejecuta una instrucción de agente usando un único modelo. */
    @PostMapping("/chat/completions")
    public Map<String, Object> executeAgentAction(@RequestBody Map<String, Object> payload) {
        String instruction = (String) payload.get("instruction");
        String sessionId = (String) payload.get("sessionId");

        System.out.println("🔍 [DEBUG] Received sessionId: " + sessionId + " (null? " + (sessionId == null) + ", blank? " + (sessionId != null && sessionId.isBlank()) + ")");

        if (instruction == null || instruction.isBlank()) {
            return Map.of("status", "error", "message", "Missing 'instruction' field");
        }

        return borisServerService.executeAgentAction(instruction, sessionId);
    }

- [x] Implementar Chat Streaming (SSE) en 3 capas:
    - [x] **Capa 1: Controller (`BorisServerController`)**: Refactorizar `executeAgentAction` para que retorne un `Flux<ServerSentEvent<ChatMessageResponse>>` y soporte el media type `text/event-stream`.
    - [x] **Capa 2: Service (`BorisServerService` / `ModelService` / `LlamaChatService`)**:
        - [x] Eliminar el sistema de colas (`ChatMessageQueue`) y los bloqueos `.get()` que impiden el flujo.
        - [x] Crear un método puente que conecte el `Flux` del motor directamente con la respuesta del controlador.
        - [x] Asegurar que el historial de sesión se guarde al completarse el stream.
    - [x] **Capa 3: Motor (`JnaLlamaChatModel`)**: Aprovechar el método `.stream()` ya existente que genera tokens mediante callbacks de JNA.
    - [x] **Frontend (Vue)**: Implementar consumo de stream usando `fetch` y `ReadableStream` para actualizar la UI conforme llegan los tokens.

- [ ] Re-implementar el sistema de agentes y colas de forma reactiva (posterior a la implementación del streaming).
