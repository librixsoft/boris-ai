package com.boris.librixsoft.server.queue;

import com.boris.librixsoft.server.dto.ChatMessageRequest;
import com.boris.librixsoft.server.dto.ChatMessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sistema de colas para manejar mensajes de chat por sesión.
 * Garantiza que los mensajes de cada sesión se procesen en orden FIFO
 * y que no se mezclen entre sesiones.
 */
@Slf4j
@Component
public class ChatMessageQueue {

    // Cola de mensajes pendientes por sessionId
    private final Map<String, BlockingQueue<ChatMessageRequest>> sessionQueues = new ConcurrentHashMap<>();
    
    // Mapa para almacenar las respuestas futuras por messageId
    private final Map<String, CompletableFuture<ChatMessageResponse>> pendingResponses = new ConcurrentHashMap<>();
    
    // Executor general para manejar múltiples sesiones en paralelo
    private final ExecutorService sessionManagerExecutor = Executors.newCachedThreadPool();
    
    // Flag para controlar el procesamiento
    private final AtomicBoolean isRunning = new AtomicBoolean(true);

    /**
     * Envía un mensaje a la cola de su sesión y retorna un CompletableFuture con la respuesta.
     */
    public CompletableFuture<ChatMessageResponse> sendMessage(String instruction, String sessionId) {
        if (!isRunning.get()) {
            return CompletableFuture.completedFuture(
                new ChatMessageResponse(null, "error", "Queue system is not running", null)
            );
        }

        String effectiveSessionId = (sessionId == null || sessionId.isBlank()) 
            ? UUID.randomUUID().toString() 
            : sessionId;

        ChatMessageRequest request = new ChatMessageRequest(instruction, effectiveSessionId);
        CompletableFuture<ChatMessageResponse> responseFuture = new CompletableFuture<>();
        
        pendingResponses.put(request.getMessageId(), responseFuture);
        
        // Obtener o crear cola para la sesión
        BlockingQueue<ChatMessageRequest> queue = sessionQueues.computeIfAbsent(
            effectiveSessionId, 
            k -> new LinkedBlockingQueue<>()
        );
        
        queue.offer(request);
        
        log.debug("Message {} queued for session {}", request.getMessageId(), effectiveSessionId);
        
        return responseFuture;
    }


    /**
     * Obtiene el siguiente mensaje pendiente de una sesión (bloqueante).
     */
    public ChatMessageRequest getNextMessage(String sessionId, long timeout, TimeUnit unit) 
            throws InterruptedException {
        BlockingQueue<ChatMessageRequest> queue = sessionQueues.get(sessionId);
        if (queue == null) {
            return null;
        }
        return queue.poll(timeout, unit);
    }

    /**
     * Completa la respuesta para un mensaje específico.
     */
    public void completeResponse(String messageId, ChatMessageResponse response) {
        CompletableFuture<ChatMessageResponse> future = pendingResponses.remove(messageId);
        if (future != null) {
            future.complete(response);
            log.debug("Completed response for message {}", messageId);
        } else {
            log.warn("No pending future found for message {}", messageId);
        }
    }

    /**
     * Completa la respuesta con error para un mensaje específico.
     */
    public void completeError(String messageId, String errorMessage) {
        ChatMessageResponse response = new ChatMessageResponse(
            messageId, 
            "error", 
            errorMessage, 
            null
        );
        completeResponse(messageId, response);
    }

    /**
     * Obtiene el tamaño de la cola para una sesión.
     */
    public int getQueueSize(String sessionId) {
        BlockingQueue<ChatMessageRequest> queue = sessionQueues.get(sessionId);
        return queue != null ? queue.size() : 0;
    }

    /**
     * Limpia todos los mensajes de una sesión.
     */
    public void clearSession(String sessionId) {
        BlockingQueue<ChatMessageRequest> queue = sessionQueues.remove(sessionId);
        if (queue != null) {
            queue.clear();
        }
        
        // Cancelar todas las respuestas pendientes de la sesión
        pendingResponses.entrySet().removeIf(entry -> {
            // Solo remover si el messageId corresponde a esta sesión
            // Nota: necesitamos rastrear sessionId por messageId para esto
            return false;
        });
        
        log.info("Cleared queue for session {}", sessionId);
    }

    /**
     * Limpia todas las colas y detiene el procesamiento.
     */
    public void shutdown() {
        isRunning.set(false);
        
        // Clear all queues
        sessionQueues.clear();
        
        // Complete all pending futures with error
        pendingResponses.forEach((messageId, future) -> {
            future.complete(new ChatMessageResponse(
                messageId, 
                "error", 
                "Queue system shutdown", 
                null
            ));
        });
        pendingResponses.clear();
        
        sessionManagerExecutor.shutdown();
        
        log.info("Chat message queue shutdown complete");
    }

    /**
     * Obtiene estadísticas del sistema de colas.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("activeSessions", sessionQueues.size());
        stats.put("pendingResponses", pendingResponses.size());
        
        Map<String, Integer> queueSizes = new ConcurrentHashMap<>();
        sessionQueues.forEach((sessionId, queue) -> {
            queueSizes.put(sessionId, queue.size());
        });
        stats.put("queueSizes", queueSizes);
        
        return stats;
    }
}
