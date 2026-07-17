# Boris Interview Copilot — TODO (solo backend)

> Escucha el audio del entrevistador (salida del sistema), lo transcribe y genera respuestas sugeridas en el **chat existente**. **La UI no se modifica.**

---

## Objetivo

1. Capturar **solo audio del entrevistador** (loopback WASAPI en Windows — altavoces/auriculares).
2. Transcribir con **whisper.cpp** local.
3. Al detectar una pregunta, llamar al LLM usando el **system prompt existente** (`prompts/system_prompt.md`).
4. Publicar en el chat actual: pregunta transcrita + respuestas sugeridas (vía `ConversationHistoryService`).

---

## Flujo

```
[WASAPI loopback] → [Whisper STT] → pregunta texto
                                          ↓
                    [system_prompt.md — perfil y reglas de entrevista]
                                          ↓
                    [LlamaChatService / ModelService]
                                          ↓
              [Chat existente: mensaje entrevistador + sugerencias]
```

---

## Prompt — `src/main/resources/prompts/system_prompt.md`

El perfil del candidato y las reglas de respuesta se configuran **aquí**, no en `application.yml`.

Ejemplo de contenido a agregar/editar en ese archivo:

```markdown
Eres un Fullstack Engineer Senior con 8+ años de experiencia.
Stack: Java, Spring Boot, Vue, PostgreSQL, Docker.

Modo entrevista:
- Recibirás la pregunta del entrevistador transcrita en tiempo real.
- Responde en primera persona, claro y conciso, como en una entrevista real.
- Genera 2–3 respuestas posibles. No inventes experiencia fuera de tu perfil.
```

`LlamaChatService` ya carga este archivo al arrancar — reutilizar tal cual, sin prompt adicional ni properties de perfil.

---

## Config (`application.yml`) — solo lo técnico

```yaml
boris:
  interview:
    enabled: false
    whisper-path: "/.boris/vendor/whisper.cpp"
    whisper-model: "ggml-small.en.bin"
    vad-silence-ms: 800
    session-id: "interview-session"
```

---

## Tareas

### 1. Prompt
- [ ] Editar `prompts/system_prompt.md` — perfil del candidato + instrucciones modo entrevista (1ª persona, sugerencias concisas).

### 2. Config mínima
- [ ] `InterviewProperties.java` — solo: `enabled`, `whisperPath`, `whisperModel`, `vadSilenceMs`, `sessionId`.

### 3. Captura de audio (solo entrevistador, sin micrófono)
- [ ] `SystemAudioCaptureService` — WASAPI loopback en Windows (JNA o sidecar mínimo).
- [ ] PCM 16 kHz mono → buffer por chunks (~1 s).
- [ ] `VadService` — silencio ≥ `vad-silence-ms` = fin de frase.

### 4. Transcripción
- [ ] `WhisperTranscriptionService` — wrapper sobre `whisper-cli`.
- [ ] PCM → WAV temp → texto.
- [ ] `WhisperDownloadService` — descargar binario + modelo (como llama-server).

### 5. Agente de respuestas
- [ ] `InterviewAgentService` — al recibir texto final:
  - append `"[Entrevistador]: {texto}"` en sesión.
  - llamar `ModelService.streamFlow()` (usa `system_prompt.md` existente).
  - append respuesta sugerida en sesión.
- [ ] `QuestionDetector` — ignorar segmentos muy cortos.

### 6. API REST (sin tocar frontend)
- [ ] `POST /api/v1/interview/start` — inicia captura + pipeline.
- [ ] `POST /api/v1/interview/stop` — detiene y libera recursos.
- [ ] `GET /api/v1/interview/status` — idle / escuchando / transcribiendo.
- [ ] Mensajes en chat vía `session-id` + `ConversationHistoryService`.

### 7. Tests
- [ ] Unit: `VadService`, flujo pregunta → sugerencia.
- [ ] Integration: WAV sample → texto → respuesta LLM.

---

## Stack

| Componente | Librería |
|-----------|----------|
| Prompt | `prompts/system_prompt.md` (existente) |
| Audio loopback | WASAPI (Windows) via JNA |
| STT | whisper.cpp |
| LLM | `LlamaChatService` / `ModelService` (existente) |
| Chat | `ConversationHistoryService` (existente) |

---

## MVP — Definition of Done

- [ ] Perfil e instrucciones de entrevista definidos en `system_prompt.md`.
- [ ] `POST /interview/start` captura audio del sistema (entrevistador).
- [ ] Pregunta transcrita + sugerencias aparecen en el chat de la sesión configurada.
- [ ] `POST /interview/stop` detiene todo.
- [ ] Sin cambios en Vue / `app.html` / `App.vue`.

---

## Fuera de alcance

- Properties de perfil / DTOs de candidato / prompts extra.
- Cambios de UI.
- Captura de micrófono.
- TTS / diarización.
