package com.boris.tooling.quality;

import org.springframework.ai.chat.client.ChatClient;

/**
 * Segunda iteración de calidad: revisa el código antes de escribirlo.
 */
public class QualityIterator {

    private static final String QUALITY_PROMPT = """
        Eres un revisor de código experto. Revisa el siguiente código y devuelve SOLO el código corregido.

        Verifica:
        1. Sintaxis correcta
        2. Lógica sin errores obvios
        3. Nombres descriptivos
        4. Sin código duplicado innecesario
        5. Sin vulnerabilidades de seguridad obvias
        6. Sigue convenciones del lenguaje

        Si el código está bien, devuélvelo sin cambios.
        Si encuentras mejoras, aplícalas y devuelve el código corregido.

        IMPORTANTE: Devuelve SOLO el código, sin explicaciones ni markdown.

        Código a revisar:
        %s
        """;

    private final ChatClient chatClient;
    private volatile boolean enabled = true;

    public QualityIterator(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Ejecuta la segunda iteración de calidad sobre el contenido.
     * @param content El código a revisar
     * @param filePath Ruta del archivo (para contexto)
     * @return El código revisado/mejorado
     */
    public String iterate(String content, String filePath) {
        if (!enabled || content == null || content.isBlank()) {
            return content;
        }

        // Solo revisar archivos de código
        if (!isCodeFile(filePath)) {
            return content;
        }

        try {
            String prompt = String.format(QUALITY_PROMPT, content);
            String reviewed = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (reviewed != null && !reviewed.isBlank()) {
                // Limpiar posibles backticks de markdown
                reviewed = cleanMarkdown(reviewed);
                return reviewed;
            }
        } catch (Exception e) {
            // Si falla la revisión, devolver el original
            System.err.println("[QualityIterator] Error en revisión, usando original: " + e.getMessage());
        }

        return content;
    }

    private boolean isCodeFile(String filePath) {
        if (filePath == null) return false;
        String lower = filePath.toLowerCase();
        return lower.endsWith(".java")
            || lower.endsWith(".kt")
            || lower.endsWith(".py")
            || lower.endsWith(".js")
            || lower.endsWith(".ts")
            || lower.endsWith(".go")
            || lower.endsWith(".rs")
            || lower.endsWith(".c")
            || lower.endsWith(".cpp")
            || lower.endsWith(".h")
            || lower.endsWith(".xml")
            || lower.endsWith(".json")
            || lower.endsWith(".yaml")
            || lower.endsWith(".yml");
    }

    private String cleanMarkdown(String content) {
        if (content == null) return content;
        String trimmed = content.trim();

        // Remover bloques de código markdown
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
            }
        }

        return trimmed;
    }
}
