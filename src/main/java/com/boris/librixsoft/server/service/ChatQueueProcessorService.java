package com.boris.librixsoft.server.service;

import com.boris.librixsoft.server.dto.ApiResponse;
import com.boris.librixsoft.server.dto.ChatMessageRequest;
import com.boris.librixsoft.server.dto.ChatMessageResponse;
import com.boris.librixsoft.server.queue.ChatMessageQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Servicio que procesa mensajes de la cola de chat.
 * Se encarga de tomar mensajes de la cola y procesarlos secuencialmente por sesión.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatQueueProcessorService {

    private final ChatMessageQueue chatMessageQueue;
    private final ModelService modelService;
    private final ChatSessionService chatSessionService;
    
    private final ExecutorService processorExecutor = Executors.newCachedThreadPool();
    private final AtomicBoolean isRunning = new AtomicBoolean(true);

    /**
     * Inicia el procesamiento de mensajes para una sesión específica.
     */
    public void startProcessingSession(String sessionId) {
        if (!isRunning.get()) {
            log.warn("Queue processor is not running");
            return;
        }

        processorExecutor.submit(() -> processSessionQueue(sessionId));
    }

    /**
     * Procesa la cola de mensajes de una sesión específica.
     */
    private void processSessionQueue(String sessionId) {
        log.info("Starting queue processor for session {}", sessionId);

        try {
            while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    ChatMessageRequest request = chatMessageQueue.getNextMessage(sessionId, 1, java.util.concurrent.TimeUnit.SECONDS);
                    
                    if (request == null) {
                        // Si no hay mensajes, verificar si debemos detener el procesador
                        if (chatMessageQueue.getQueueSize(sessionId) == 0) {
                            log.debug("No more messages in queue for session {}, stopping processor", sessionId);
                            break;
                        }
                        continue;
                    }

                    log.info("Processing message {} for session {}", request.getMessageId(), sessionId);
                    
                    // Procesar el mensaje
                    CompletableFuture.runAsync(() -> {
                        try {
                            processMessage(request);
                        } catch (Exception e) {
                            log.error("Error processing message {}", request.getMessageId(), e);
                            chatMessageQueue.completeError(request.getMessageId(), e.getMessage());
                        }
                    }, processorExecutor);

                } catch (InterruptedException e) {
                    log.warn("Queue processor interrupted for session {}", sessionId);
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            log.info("Queue processor stopped for session {}", sessionId);
        }
    }

    /**
     * Procesa un mensaje individual.
     */
    private void processMessage(ChatMessageRequest request) {
        String sessionId = request.getSessionId();
        String instruction = request.getInstruction();

        try {
            // Obtener o crear sesión
            String effectiveSessionId = chatSessionService.getOrCreateSession(sessionId);
            
            // Obtener historial de la sesión
            List<Message> history = chatSessionService.getSessionHistory(effectiveSessionId);
            List<Message> trimmedHistory = trimHistory(history, 6);
            
            // Crear flag de cancelación para este mensaje específico
            AtomicBoolean cancellationRequested = new AtomicBoolean(false);
            
            // Ejecutar el flujo del modelo
            ApiResponse<Map<String, Object>> apiResponse = modelService.executeFlow(
                instruction, 
                trimmedHistory, 
                cancellationRequested, 
                effectiveSessionId
            );
            
            Map<String, Object> result = apiResponse.getData();
            
            // Extraer la respuesta real del modelo
            Object modelResponse = null;
            if (result != null) {
                modelResponse = result.get("result");
                if (modelResponse == null) {
                    // Fallback a 'responses' si no hay 'result'
                    Map<String, Object> responses = (Map<String, Object>) result.get("responses");
                    if (responses != null) {
                        modelResponse = responses.get("model1");
                    }
                }
            }
            
            // Crear respuesta
            ChatMessageResponse response = new ChatMessageResponse(
                request.getMessageId(),
                apiResponse.getStatus(),
                apiResponse.getMessage(),
                modelResponse != null ? modelResponse : (result != null ? result.get("content") : null)
            );
            
            // Completar la respuesta en la cola
            chatMessageQueue.completeResponse(request.getMessageId(), response);
            
            log.info("Message {} processed successfully for session {}", request.getMessageId(), sessionId);
            
        } catch (Exception e) {
            log.error("Error processing message {} for session {}", request.getMessageId(), sessionId, e);
            chatMessageQueue.completeError(request.getMessageId(), e.getMessage());
        }
    }

    /**
     * Limita el historial a los últimos N intercambios.
     */
    private List<Message> trimHistory(List<Message> history, int maxMessages) {
        if (history == null || history.size() <= maxMessages) {
            return history;
        }
        // maxMessages debe ser par para conservar pares completos
        int even = maxMessages % 2 == 0 ? maxMessages : maxMessages - 1;
        return history.subList(history.size() - even, history.size());
    }

    /**
     * Detiene el procesador de colas.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down chat queue processor");
        isRunning.set(false);
        processorExecutor.shutdown();
        try {
            if (!processorExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                processorExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            processorExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
