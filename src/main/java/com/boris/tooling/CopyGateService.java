package com.boris.tooling;

import java.util.Locale;
import java.util.Set;

public final class CopyGateService {

    public record CopyDecision(boolean allowed, String reason, String learningHint) {}

    public record BlockedFile(String path, String reason, String learningHint) {}

    public static final String CODE_HINT =
        "Leé este contenido, identificá el patrón (estructura, nombres, flujo) y generá tu propia "
        + "implementación para este proyecto con las convenciones existentes.";

    private static final Set<String> ALLOWED_COPY_EXTENSIONS = Set.of(
        "png", "jpg", "jpeg", "gif", "svg", "ico", "webp",
        "woff", "woff2", "ttf", "eot", "otf",
        "mp4", "webm", "mp3", "wav",
        "json", "xml", "yml", "yaml", "csv", "tsv",
        "md", "txt", "rst", "adoc"
    );

    private CopyGateService() {
    }

    public static CopyDecision decide(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath es requerido para decidir copia");
        }
        String ext = extensionOf(relativePath);
        if (ALLOWED_COPY_EXTENSIONS.contains(ext)) {
            return new CopyDecision(true, null, null);
        }
        return new CopyDecision(false, blockedReason(ext), CODE_HINT);
    }

    private static String blockedReason(String ext) {
        if (ext.isBlank()) {
            return "Copia bloqueada: el archivo no tiene extensión y no está en la lista de assets permitidos."
                + " El contenido se entrega para lectura y análisis; generá código propio.";
        }
        return "Copia bloqueada: los archivos '." + ext + "' son de código o dato no permitido para copia"
            + " directa de un repo externo. Usá el contenido leído para aprender el patrón y generá tu"
            + " propia implementación adaptada a este proyecto.";
    }

    private static String extensionOf(String path) {
        String name = path;
        int slash = name.lastIndexOf('/');
        if (slash >= 0 && slash < name.length() - 1) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
