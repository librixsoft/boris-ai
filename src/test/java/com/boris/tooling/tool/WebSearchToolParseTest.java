package com.boris.tooling.tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import com.boris.exceptions.WebSearchBlockedException;

class WebSearchToolParseTest {

    private static Path FIXTURES = Path.of("src/test/resources");

    private String readFixture(String name) {
        try {
            return Files.readString(FIXTURES.resolve(name));
        } catch (Exception e) {
            throw new RuntimeException("Fixture not found: " + name, e);
        }
    }

    @Test
    void parseBingHtml_extractsResultsFromMainSerp() {
        String html = readFixture("bing-serp-main.html");

        List<Map<String, String>> results = WebSearchTool.parseBingHtml(html);

        assertFalse(results.isEmpty(), "Should extract at least one result");
        assertTrue(results.size() >= 5, "Should extract at least 5 results");

        for (Map<String, String> result : results) {
            assertFalse(result.get("title").isBlank(), "Title should not be blank");
            assertFalse(result.get("url").isBlank(), "URL should not be blank");
            assertTrue(result.get("url").startsWith("http"), "URL should be http/https");
            assertFalse(result.get("url").contains("bing.com/ck/a"), "URL should not contain bing redirect");
            assertNotNull(result.get("snippet"), "Snippet should be present");
        }

        Map<String, String> first = results.get(0);
        assertEquals("Java 21 - Oracle", first.get("title"));
        assertEquals("https://www.oracle.com/java/technologies/java21/", first.get("url"));
    }

    @Test
    void parseBingHtml_detectsBlockedPage() {
        String html = readFixture("bing-serp-blocked.html");

        assertThrows(WebSearchBlockedException.class, () -> {
            WebSearchTool.parseBingHtml(html);
        });
    }

    @Test
    void parseBingHtml_zeroResultsReturnsEmptyList() {
        String html = """
            <html><body><div id="b_results"></div></body></html>
            """;

        List<Map<String, String>> results = WebSearchTool.parseBingHtml(html);

        assertNotNull(results);
        assertTrue(results.isEmpty(), "Should return empty list for zero results");
    }

    @Test
    void parseBingHtml_nullHtmlReturnsEmptyList() {
        List<Map<String, String>> results = WebSearchTool.parseBingHtml(null);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
}
