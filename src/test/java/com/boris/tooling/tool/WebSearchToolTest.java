package com.boris.tooling.tool;

import com.boris.tooling.ToolDefinition;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebSearchToolTest {

    @Mock
    HttpClient mockHttpClient;

    @Mock
    HttpResponse<String> mockHttpResponse;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // -- ToolDefinition tests --

    @Test
    void web_search_definition_hasCorrectName() {
        var def = WebSearchTool.web_search();
        assertEquals("web_search", def.name());
    }

    @Test
    void web_search_definition_hasDescription() {
        var def = WebSearchTool.web_search();
        assertNotNull(def.description());
        assertTrue(def.description().toLowerCase().contains("duckduckgo") || def.description().toLowerCase().contains("search"));
    }

    @Test
    void web_search_definition_hasQueryParameter() {
        var def = WebSearchTool.web_search();
        Map<String, Object> props = (Map<String, Object>) def.parameters().get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("query"));
    }

    @Test
    void web_search_definition_hasCountParameter() {
        var def = WebSearchTool.web_search();
        Map<String, Object> props = (Map<String, Object>) def.parameters().get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("count"));
    }

    @Test
    void web_search_definition_hasRegionParameter() {
        var def = WebSearchTool.web_search();
        Map<String, Object> props = (Map<String, Object>) def.parameters().get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("region"));
    }

    @Test
    void web_search_definition_hasSafeSearchParameter() {
        var def = WebSearchTool.web_search();
        Map<String, Object> props = (Map<String, Object>) def.parameters().get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("safeSearch"));
    }

    // -- Input validation tests --

    @Test
    void execute_returnsError_whenQueryIsNull() {
        var args = new HashMap<String, Object>();
        args.put("query", (Object) null);
        String result = WebSearchTool.execute(args);
        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.toLowerCase().contains("query"));
    }

    @Test
    void execute_returnsError_whenQueryIsEmpty() {
        String result = WebSearchTool.execute(Map.ofEntries(Map.entry("query", "")));
        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.toLowerCase().contains("query"));
    }

    @Test
    void execute_returnsError_whenQueryIsBlank() {
        String result = WebSearchTool.execute(Map.ofEntries(Map.entry("query", "   ")));
        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.toLowerCase().contains("query"));
    }

    // -- Successful search with DuckDuckGo HTML response --

    @Test
    @SuppressWarnings("unchecked")
    void execute_returnsResults_whenDuckDuckGoReturnsHtml() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(buildMockDuckDuckGoHtml());
        when(mockHttpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);

        var tool = new WebSearchTool(mockHttpClient);
        String result = tool.search(Map.ofEntries(Map.entry("query", "test query")));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(result.contains("\"results\""));
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_parsesTitleFromHtml() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(buildMockDuckDuckGoHtml());
        when(mockHttpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);

        var tool = new WebSearchTool(mockHttpClient);
        String result = tool.search(Map.ofEntries(Map.entry("query", "test query")));

        assertTrue(result.contains("First Result"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_parsesCorrectUrlFromHtml() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(buildMockDuckDuckGoHtml());
        when(mockHttpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);

        var tool = new WebSearchTool(mockHttpClient);
        String result = tool.search(Map.ofEntries(Map.entry("query", "test query")));

        assertTrue(result.contains("https://example.com/result1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_parsesSnippetFromHtml() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(buildMockDuckDuckGoHtml());
        when(mockHttpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);

        var tool = new WebSearchTool(mockHttpClient);
        String result = tool.search(Map.ofEntries(Map.entry("query", "test query")));

        assertTrue(result.contains("snippet text"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_returnsJsonWithCorrectResultFields() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(buildMockDuckDuckGoHtml());
        when(mockHttpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);

        var tool = new WebSearchTool(mockHttpClient);
        String result = tool.search(Map.ofEntries(Map.entry("query", "test query")));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(result.contains("\"title\""));
        assertTrue(result.contains("\"url\""));
        assertTrue(result.contains("\"snippet\""));
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_resultsHaveCorrectNumberOfResults() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(buildMockDuckDuckGoHtml());
        when(mockHttpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);

        var tool = new WebSearchTool(mockHttpClient);
        String result = tool.search(Map.ofEntries(Map.entry("query", "test query")));

        assertTrue(result.contains("First Result"));
        assertTrue(result.contains("Second Result"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_respectsCountParameter() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(buildMockDuckDuckGoHtml());
        when(mockHttpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);

        var tool = new WebSearchTool(mockHttpClient);
        String result = tool.search(Map.ofEntries(
                Map.entry("query", "test query"),
                Map.entry("count", 1)
        ));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(result.contains("First Result"));
        assertFalse(result.contains("Second Result"));
    }

    // -- Error cases --

    @Test
    @SuppressWarnings("unchecked")
    void execute_returnsError_whenDuckDuckGoReturnsEmptyResults() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(buildEmptyDuckDuckGoHtml());
        when(mockHttpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);

        var tool = new WebSearchTool(mockHttpClient);
        String result = tool.search(Map.ofEntries(Map.entry("query", "no results")));

        assertTrue(result.contains("\"success\":false"));
    }

    @Test
    void execute_returnsError_whenHttpTimeout() throws Exception {
        when(mockHttpClient.send(any(), any())).thenThrow(new java.net.http.HttpTimeoutException("Timed out"));

        var tool = new WebSearchTool(mockHttpClient);
        String result = tool.search(Map.ofEntries(Map.entry("query", "timeout test")));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.toLowerCase().contains("timed out"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_returnsError_whenHttp500() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(500);
        when(mockHttpResponse.body()).thenReturn("<html>Server Error</html>");
        when(mockHttpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);

        var tool = new WebSearchTool(mockHttpClient);
        String result = tool.search(Map.ofEntries(Map.entry("query", "server error")));

        assertTrue(result.contains("\"success\":false"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_returnsError_whenHttp403() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(403);
        when(mockHttpResponse.body()).thenReturn("<html>Forbidden</html>");
        when(mockHttpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);

        var tool = new WebSearchTool(mockHttpClient);
        String result = tool.search(Map.ofEntries(Map.entry("query", "forbidden")));

        assertTrue(result.contains("\"success\":false"));
    }

    @Test
    void execute_returnsError_whenIOException() throws Exception {
        when(mockHttpClient.send(any(), any())).thenThrow(new IOException("Connection refused"));

        var tool = new WebSearchTool(mockHttpClient);
        String result = tool.search(Map.ofEntries(Map.entry("query", "connection error")));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.toLowerCase().contains("error"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_returnsError_whenBotChallenge() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(buildBotChallengeHtml());
        when(mockHttpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);

        var tool = new WebSearchTool(mockHttpClient);
        String result = tool.search(Map.ofEntries(Map.entry("query", "bot test")));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.toLowerCase().contains("bot"));
    }

    @Test
    void execute_returnsError_whenInterrupted() throws Exception {
        when(mockHttpClient.send(any(), any())).thenThrow(new InterruptedException("Interrupted"));

        var tool = new WebSearchTool(mockHttpClient);
        String result = tool.search(Map.ofEntries(Map.entry("query", "interrupted")));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.toLowerCase().contains("interrupted"));
    }

    // -- SafeSearch tests --

    @Test
    @SuppressWarnings("unchecked")
    void execute_usesModerateSafeSearchByDefault() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(buildMockDuckDuckGoHtml());
        when(mockHttpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);

        var tool = new WebSearchTool(mockHttpClient);
        tool.search(Map.ofEntries(Map.entry("query", "test query")));

        verify(mockHttpClient, atLeast(1)).send(argThat(request -> {
            String uri = request.uri().toString();
            return uri.contains("kp=-1");
        }), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_usesStrictSafeSearch() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(buildMockDuckDuckGoHtml());
        when(mockHttpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);

        var tool = new WebSearchTool(mockHttpClient);
        tool.search(Map.ofEntries(Map.entry("query", "test query"), Map.entry("safeSearch", "strict")));

        verify(mockHttpClient, atLeast(1)).send(argThat(request -> {
            String uri = request.uri().toString();
            return uri.contains("kp=1");
        }), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_usesOffSafeSearch() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(buildMockDuckDuckGoHtml());
        when(mockHttpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);

        var tool = new WebSearchTool(mockHttpClient);
        tool.search(Map.ofEntries(Map.entry("query", "test query"), Map.entry("safeSearch", "off")));

        verify(mockHttpClient, atLeast(1)).send(argThat(request -> {
            String uri = request.uri().toString();
            return uri.contains("kp=-2");
        }), any(HttpResponse.BodyHandler.class));
    }

    // -- Region tests --

    @Test
    @SuppressWarnings("unchecked")
    void execute_usesRegionWhenProvided() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(buildMockDuckDuckGoHtml());
        when(mockHttpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);

        var tool = new WebSearchTool(mockHttpClient);
        tool.search(Map.ofEntries(Map.entry("query", "test query"), Map.entry("region", "us-en")));

        verify(mockHttpClient, atLeast(1)).send(argThat(request -> {
            String uri = request.uri().toString();
            return uri.contains("kl=us-en");
        }), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_doesNotAddRegionWhenNull() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(buildMockDuckDuckGoHtml());
        when(mockHttpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(mockHttpResponse);

        var tool = new WebSearchTool(mockHttpClient);
        tool.search(Map.ofEntries(Map.entry("query", "test query")));

        verify(mockHttpClient, atLeast(1)).send(argThat(request -> {
            String uri = request.uri().toString();
            return !uri.contains("kl=");
        }), any(HttpResponse.BodyHandler.class));
    }

    // -- Helpers --

    private String buildMockDuckDuckGoHtml() {
        return """
            <!DOCTYPE html>
            <html class="no-js">
            <body>
                <a class="result__snippet">snippet text for first result</a>
                <a class="result__a" href="https://example.com/result1">First Result</a>
                <a class="result__snippet">snippet text for second result</a>
                <a class="result__a" href="https://example.com/result2">Second Result</a>
            </body>
            </html>
            """;
    }

    private String buildEmptyDuckDuckGoHtml() {
        return """
            <!DOCTYPE html>
            <html class="no-js">
            <body>
                <div>No results found</div>
            </body>
            </html>
            """;
    }

    private String buildBotChallengeHtml() {
        return """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="g-recaptcha" data-sitekey="fake"></div>
                <form id="challenge-form"></form>
            </body>
            </html>
            """;
    }
}
