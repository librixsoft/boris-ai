package com.boris.tooling.tool;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import com.boris.context.ProjectContext;
import com.boris.context.SearchSessionContext;
import com.boris.context.SessionDirectoryResolver;
import com.boris.exceptions.BorisException;
import com.boris.exceptions.SearchContextWriterException;

import static org.junit.jupiter.api.Assertions.*;

class SearchContextWriterTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void limpiar() {
        ProjectContext.clear();
        SearchSessionContext.clear();
    }

    @Test
    void appendCreaArchivoDeSesionConEncabezadoYEntrada() throws Exception {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.setSessionId("sesion-test");

        SearchContextWriter.append("web_search", "java 21 programming",
                "5 resultados", "5 resultados · Principal: \"Título\" (https://example.com)");

        Path file = SessionDirectoryResolver.searchesFile();
        assertTrue(Files.exists(file), "El archivo searchs.md debe existir");
        String content = Files.readString(file);
        assertTrue(content.contains("# Registro de búsquedas — Sesión sesion-test"), "Debe tener encabezado de sesión");
        assertTrue(content.contains("web_search — `java 21 programming`"), "Debe tener encabezado de entrada");
        assertTrue(content.contains("5 resultados · Principal: \"Título\" (https://example.com)"), "Debe tener detalle");
        assertTrue(content.contains("---"), "Debe tener separador");
    }

    @Test
    void dosAppendAcumulanEntradasConSeparador() throws Exception {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.setSessionId("sesion-test");

        SearchContextWriter.append("web_search", "query 1", "1 resultado", "1 resultado · Principal: \"A\" (https://a.com)");
        SearchContextWriter.append("web_fetch", "https://a.com", "1 URL", "Contenido extraído (100 caracteres)");

        String content = Files.readString(SessionDirectoryResolver.searchesFile());
        assertTrue(content.contains("query 1"), "Debe contener la primera query");
        assertTrue(content.contains("https://a.com"), "Debe contener la segunda URL");
        // Dos separadores: uno al final de cada entrada
        assertEquals(2, countOccurrences(content, "---"), "Debe haber dos separadores");
    }

    @Test
    void appendSinSessionIdLanzaBorisException() {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.clear();
        assertThrows(BorisException.class,
                () -> SearchContextWriter.append("web_search", "q", "s", "d"));
    }

    @Test
    void appendSinProjectPathLanzaSearchContextWriterException() {
        SearchSessionContext.setSessionId("sesion");
        ProjectContext.clear();
        assertThrows(SearchContextWriterException.class,
                () -> SearchContextWriter.append("web_search", "q", "s", "d"));
    }

    @Test
    void detalleNoContieneSnippetsNiListasLargas() throws Exception {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.setSessionId("sesion-test");

        SearchContextWriter.append("web_search", "java 21 programming",
                "5 resultados", "5 resultados · Principal: \"Título\" (https://example.com)");

        String content = Files.readString(SessionDirectoryResolver.searchesFile());
        // El detalle es una línea breve, sin snippets largos
        assertFalse(content.contains("snippet"), "No debe contener snippets");
        assertTrue(content.contains("Principal: \"Título\""), "Debe tener el resultado principal");
    }

    private static int countOccurrences(String text, String substring) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substring, idx)) >= 0) {
            count++;
            idx += substring.length();
        }
        return count;
    }
}
