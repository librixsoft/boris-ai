package com.boris.context;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

import com.boris.exceptions.SessionLogException;
import com.boris.tooling.ToolProgressNotifier;
import com.boris.tooling.ToolResultNotifier;

/**
 * Escribe eventos de sesión en un archivo de log persistente.
 * El writer se inicializa con una ruta de archivo y un flag de habilitación.
 * Todas las escrituras son sincronizadas para soportar hilos múltiples.
 * Se registra como listener en ToolResultNotifier y ToolProgressNotifier
 * para capturar llamadas y resultados de tools automaticamente.
 */
public class SessionLogWriter {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final int MAX_RESULT_LENGTH = 1000;

    private final Path logFile;
    private final boolean enabled;
    private BufferedWriter writer;

    /**
     * Crea el writer de log. Si enabled es true, crea el directorio y el archivo,
     * y se registra como listener en los notifiers de tools.
     * Si no se puede crear el directorio ni el archivo, lanza SessionLogException.
     *
     * @param logFile ruta del archivo de log
     * @param enabled si el logging está habilitado
     */
    public SessionLogWriter(Path logFile, boolean enabled) {
        this.logFile = logFile;
        this.enabled = enabled;
        if (enabled) {
            try {
                Files.createDirectories(logFile.getParent());
                this.writer = Files.newBufferedWriter(logFile,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new SessionLogException(
                        "No se pudo crear el archivo de log: " + logFile, e);
            }
        }

        registrarListeners();
    }

    private void registrarListeners() {
        ToolResultNotifier.addListener(result -> {
            String content = result.toolName() + ": ";
            if (result.diff() != null && !result.diff().isEmpty()) {
                content += truncar(result.diff());
            } else {
                content += (result.success() ? "OK" : "FAIL");
            }
            logEvent("TOOL_RESULT", content);
        });

        ToolResultNotifier.addQueryListener(queryResult -> {
            logEvent("TOOL_RESULT",
                    queryResult.toolName() + ": " + truncar(queryResult.result()));
        });

        ToolProgressNotifier.addListener((toolName, message) -> {
            logEvent("TOOL_CALL", toolName + ": " + truncar(message));
        });
    }

    /**
     * Escribe un evento de log con timestamp ISO-8601.
     *
     * @param eventType tipo de evento (SESSION_START, USER, BOT, TOOL_CALL, TOOL_RESULT, ERROR, SESSION_END)
     * @param content contenido del evento
     */
    public synchronized void logEvent(String eventType, String content) {
        if (!enabled || writer == null) {
            return;
        }
        try {
            String timestamp = ZonedDateTime.now().format(TIMESTAMP_FORMATTER);
            writer.write("[" + timestamp + "] [" + eventType + "] " + content);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            throw new SessionLogException(
                    "Error escribiendo en el log: " + logFile, e);
        }
    }

    /**
     * Escribe el evento de inicio de sesión.
     */
    public synchronized void logSessionStart() {
        logEvent("SESSION_START", "Session started");
    }

    /**
     * Escribe el evento de fin de sesión y cierra el writer.
     */
    public synchronized void logSessionEnd() {
        logEvent("SESSION_END", "Session ended");
        close();
    }

    public void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                throw new SessionLogException("Error cerrando el writer de log", e);
            }
        }
    }

    private String truncar(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_RESULT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_RESULT_LENGTH)
                + "\n... [truncated, " + text.length() + " chars total]";
    }
}
