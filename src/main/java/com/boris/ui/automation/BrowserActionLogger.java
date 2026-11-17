package com.boris.ui.automation;

import com.boris.ui.automation.model.BoundingBox;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registra por acción de browser: URL antes/después, acción, argumentos normalizados,
 * elemento elegido, bbox, confianza, ruta del artefacto PNG y resultado (success/failure con causa).
 * Guarda artefactos PNG en ~/.boris/browser-artifacts/<session>/<timestamp>-<nn>-<action>.png.
 * Nunca lanza si el directorio de artefactos no existe: lo crea o salta silenciosamente.
 */
public class BrowserActionLogger {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final Path logFile;
    private final Path artifactsDir;
    private final String sessionId;
    private final AtomicInteger sequence = new AtomicInteger(0);

    public BrowserActionLogger(String sessionId) {
        this.sessionId = sessionId != null && !sessionId.isBlank() ? sessionId : "default";
        Path home = Paths.get(System.getProperty("user.home"));
        this.artifactsDir = home.resolve(".boris").resolve("browser-artifacts").resolve(this.sessionId);
        this.logFile = artifactsDir.resolve("actions.log");
        try {
            Files.createDirectories(artifactsDir);
        } catch (IOException ignored) {
            // Si no se puede crear el directorio, continuamos sin guardar artefactos.
        }
    }

    /**
     * Registra una acción de browser y devuelve la ruta del artefacto PNG guardado (o null si no se guardó).
     */
    public String logAction(
            String action,
            String targetDomain,
            String clickTargetText,
            String typeText,
            String pressKeyAfter,
            String scrollDirection,
            String urlBefore,
            String urlAfter,
            String elementLabel,
            BoundingBox boundingBox,
            double confidence,
            byte[] screenshotPng,
            String result,
            String cause) {

        int seq = sequence.incrementAndGet();
        String timestamp = ZonedDateTime.now().format(TIMESTAMP_FORMATTER);

        String artifactPath = null;
        if (screenshotPng != null && screenshotPng.length > 0) {
            artifactPath = saveArtifact(screenshotPng, action, seq);
        }

        ObjectNode entry = MAPPER.createObjectNode();
        entry.put("session", sessionId);
        entry.put("sequence", seq);
        entry.put("timestamp", timestamp);
        entry.put("action", action);
        entry.put("target_domain", targetDomain);
        entry.put("click_target_text", clickTargetText);
        entry.put("type_text", typeText);
        entry.put("press_key_after", pressKeyAfter);
        entry.put("scroll_direction", scrollDirection);
        entry.put("url_before", urlBefore);
        entry.put("url_after", urlAfter);
        entry.put("element_label", elementLabel);
        if (boundingBox != null) {
            ObjectNode bbox = MAPPER.createObjectNode();
            bbox.put("x", boundingBox.x());
            bbox.put("y", boundingBox.y());
            bbox.put("width", boundingBox.width());
            bbox.put("height", boundingBox.height());
            entry.set("bounding_box", bbox);
        }
        entry.put("confidence", confidence);
        entry.put("artifact_path", artifactPath);
        entry.put("result", result);
        entry.put("cause", cause);

        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, MAPPER.writeValueAsString(entry) + "\n",
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // No fallamos si no podemos escribir el log.
        }

        return artifactPath;
    }

    private String saveArtifact(byte[] pngBytes, String action, int sequence) {
        try {
            String safeAction = action.replaceAll("[^a-zA-Z0-9_-]", "_");
            String fileName = String.format("%s-%03d-%s.png", safeAction, sequence, action);
            Path artifactFile = artifactsDir.resolve(fileName);
            try (FileOutputStream fos = new FileOutputStream(artifactFile.toFile())) {
                fos.write(pngBytes);
            }
            return artifactFile.toString();
        } catch (IOException ignored) {
            return null;
        }
    }
}
