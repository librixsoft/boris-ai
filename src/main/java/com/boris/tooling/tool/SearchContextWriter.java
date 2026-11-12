package com.boris.tooling.tool;

import com.boris.context.ProjectContext;
import com.boris.context.SearchSessionContext;
import com.boris.context.SessionDirectoryResolver;
import com.boris.exceptions.BorisException;
import com.boris.exceptions.SearchContextWriterException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/**
 * Escribe un registro de búsqueda/fetch en {PROYECTO}/.boris/{SESSION_ID}/searchs.md.
 * El archivo es un índice breve para humanos; el agente no puede leerlo porque
 * .boris está bloqueado por guards de ProjectContext.
 */
public final class SearchContextWriter {

    private static final String FILE_HEADER = "# Registro de búsquedas — Sesión ";

    private SearchContextWriter() {
    }

    /**
     * Escribe una entrada de registro para una herramienta de búsqueda/fetch.
     *
     * @param toolName      nombre de la herramienta (web_search, web_fetch)
     * @param toolInput     entrada de la herramienta (query o URLs)
     * @param summaryMarkdown resumen de resultado (n resultados / chars extraídos)
     * @param detailMarkdown  bloque markdown del detalle (línea breve: conteo + resultado principal)
     * @throws SearchContextWriterException si no puede escribir el archivo (p. ej. sin project path)
     */
    public static void append(String toolName, String toolInput, String summaryMarkdown, String detailMarkdown) {
        String sessionId = SearchSessionContext.requireSessionId();

        String projectPath = ProjectContext.getProjectPath();
        if (projectPath == null || projectPath.isBlank()) {
            throw new SearchContextWriterException(
                "La sesión no tiene project path seteado. Declará la ruta del proyecto en el mensaje para que las búsquedas queden registradas en searchs.md.");
        }

        Path file = SessionDirectoryResolver.searchesFile();
        try {
            Files.createDirectories(file.getParent());
        } catch (Exception e) {
            throw new SearchContextWriterException("No se pudo crear el directorio de sesión", e);
        }

        boolean writeHeader = !Files.exists(file);
        if (!writeHeader) {
            try {
                writeHeader = Files.size(file) == 0;
            } catch (IOException e) {
                writeHeader = true;
            }
        }
        String sessionHeader = FILE_HEADER + sessionId + "\n\n";
        String entry = String.format(
            "## %s — %s — `%s`%n%s%n---%n",
            LocalDateTime.now().toString(),
            toolName,
            toolInput,
            detailMarkdown
        );

        String content = (writeHeader ? sessionHeader : "") + entry;

        try {
            Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            throw new SearchContextWriterException("No se pudo escribir el registro de búsqueda", e);
        }
    }
}
