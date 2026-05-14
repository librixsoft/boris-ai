package com.boris.librixsoft.server.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servicio que gestiona la comunicación en tiempo real del estado de la aplicación.
 * Utiliza Server-Sent Events (SSE) para avisar al frontend.
 */
@Slf4j
@Service
public class StatusEventService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private String currentStatus = "DOWN";

    @jakarta.annotation.PostConstruct
    public void init() {
        this.currentStatus = "UP";
        log.info("[🚀] App Status: UP");
    }

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        log.info("[🛑] App Status: DOWN");
        broadcast("DOWN");
    }

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        this.emitters.add(emitter);
        
        emitter.onCompletion(() -> this.emitters.remove(emitter));
        emitter.onTimeout(() -> this.emitters.remove(emitter));
        
        // Enviar estado actual inmediatamente al conectar
        try {
            emitter.send(this.currentStatus);
        } catch (IOException e) {
            this.emitters.remove(emitter);
        }
        
        return emitter;
    }

    public void broadcast(String status) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(status);
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}
