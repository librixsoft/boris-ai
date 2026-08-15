package com.boris.tooling.tool;

import com.boris.tooling.ToolDefinition;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebSearchToolTest {

    @Mock
    HttpFetcher mockFetcher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void web_search_definition_hasCorrectName() {
        var def = WebSearchTool.web_search();
        assertEquals("web_search", def.name());
    }

    @Test
    void web_search_definition_hasDescription() {
        var def = WebSearchTool.web_search();
        assertNotNull(def.description());
        assertTrue(def.description().contains("DuckDuckGo") || def.description().contains("search"));
    }

    @Test
    void web_search_definition_hasQueryParameter() {
        var def = WebSearchTool.web_search();
        Map<String, Object> props = (Map<String, Object>) def.parameters().get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("query"));
    }

    @Test
    void execute_returnsError_whenQueryIsNull() {
        var args = new HashMap<String, Object>();
        args.put("query", (Object) null);
        String result = WebSearchTool.execute(args);
        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("query"));
    }

    @Test
    void execute_returnsError_whenQueryIsEmpty() {
        String result = WebSearchTool.execute(Map.ofEntries(Map.entry("query", "")));
        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("query"));
    }

    @Test
    void execute_returnsError_whenQueryIsBlank() {
        String result = WebSearchTool.execute(Map.ofEntries(Map.entry("query", "   ")));
        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("query"));
    }

    @Test
    void execute_returnsResults_whenDuckDuckGoReturnsHtml() throws Exception {
        FakeHttpResponse mockResponse = new FakeHttpResponse(200, buildMockDuckDuckGoHtml());
        FakeHttpResponse mockContent = new FakeHttpResponse(200, "<html><body><p>Test content for first result</p></body></html>");
        when(mockFetcher.send(any(HttpRequest.class), any(Duration.class)))
                .thenReturn(mockResponse)
                .thenReturn(mockContent);

        WebSearchTool tool = new WebSearchTool(mockFetcher);
        String result = tool.search(Map.ofEntries(Map.entry("query", "test query")));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(result.contains("\"results\""));
        verify(mockFetcher, times(2)).send(any(HttpRequest.class), any(Duration.class));
    }

    @Test
    void execute_parsesTitleFromHtml() throws Exception {
        FakeHttpResponse mockResponse = new FakeHttpResponse(200, buildMockDuckDuckGoHtml());
        FakeHttpResponse mockContent = new FakeHttpResponse(200, "<html><body><p>Test content for first result</p></body></html>");
        when(mockFetcher.send(any(HttpRequest.class), any(Duration.class)))
                .thenReturn(mockResponse)
                .thenReturn(mockContent);

        WebSearchTool tool = new WebSearchTool(mockFetcher);
        String result = tool.search(Map.ofEntries(Map.entry("query", "test query")));

        assertTrue(result.contains("First Result"));
    }

    @Test
    void execute_parsesAndDecodesRedirectUrlFromHtml() throws Exception {
        FakeHttpResponse mockResponse = new FakeHttpResponse(200, buildMockDuckDuckGoHtml());
        FakeHttpResponse mockContent = new FakeHttpResponse(200, "<html><body><p>Test content for first result</p></body></html>");
        when(mockFetcher.send(any(HttpRequest.class), any(Duration.class)))
                .thenReturn(mockResponse)
                .thenReturn(mockContent);

        WebSearchTool tool = new WebSearchTool(mockFetcher);
        String result = tool.search(Map.ofEntries(Map.entry("query", "test query")));

        // La URL real debe salir desenvuelta del redirect uddg=..., no la URL de duckduckgo.com/l/?...
        assertTrue(result.contains("https://example.com/result1"));
        assertFalse(result.contains("\"url\":\"//duckduckgo.com"));
    }

    @Test
    void execute_parsesSnippetFromHtml() throws Exception {
        FakeHttpResponse mockResponse = new FakeHttpResponse(200, buildMockDuckDuckGoHtml());
        FakeHttpResponse mockContent = new FakeHttpResponse(200, "<html><body><p>This is a longer snippet text for the first result here and some more content to make it valid</p></body></html>");
        when(mockFetcher.send(any(HttpRequest.class), any(Duration.class)))
                .thenReturn(mockResponse)
                .thenReturn(mockContent);

        WebSearchTool tool = new WebSearchTool(mockFetcher);
        String result = tool.search(Map.ofEntries(Map.entry("query", "test query")));

        assertTrue(result.contains("snippet text"));
    }

    @Test
    void execute_returnsEmptyResults_whenHtmlHasNoResults() throws Exception {
        FakeHttpResponse mockResponse = new FakeHttpResponse(200, "<html><body><div id=\"no_results\">No results</div></body></html>");
        when(mockFetcher.send(any(HttpRequest.class), any(Duration.class))).thenReturn(mockResponse);

        WebSearchTool tool = new WebSearchTool(mockFetcher);
        String result = tool.search(Map.ofEntries(Map.entry("query", "no results")));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(result.contains("\"results\":[]"));
    }

    @Test
    void execute_returnsError_whenHttpTimeout() throws Exception {
        when(mockFetcher.send(any(HttpRequest.class), any(Duration.class)))
                .thenThrow(new java.net.http.HttpTimeoutException("Timed out"));

        WebSearchTool tool = new WebSearchTool(mockFetcher);
        String result = tool.search(Map.ofEntries(Map.entry("query", "timeout test")));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("timed out"));
    }

    @Test
    void execute_returnsError_whenHttp500() throws Exception {
        FakeHttpResponse mockResponse = new FakeHttpResponse(500, "<html><body>Server Error</body></html>");
        when(mockFetcher.send(any(HttpRequest.class), any(Duration.class))).thenReturn(mockResponse);

        WebSearchTool tool = new WebSearchTool(mockFetcher);
        String result = tool.search(Map.ofEntries(Map.entry("query", "server error")));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("500"));
    }

    @Test
    void execute_returnsError_whenHttp403() throws Exception {
        FakeHttpResponse mockResponse = new FakeHttpResponse(403, "<html><body>Forbidden</body></html>");
        when(mockFetcher.send(any(HttpRequest.class), any(Duration.class))).thenReturn(mockResponse);

        WebSearchTool tool = new WebSearchTool(mockFetcher);
        String result = tool.search(Map.ofEntries(Map.entry("query", "forbidden")));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("403"));
    }

    @Test
    void execute_returnsError_whenIOException() throws Exception {
        when(mockFetcher.send(any(HttpRequest.class), any(Duration.class)))
                .thenThrow(new IOException("Connection refused"));

        WebSearchTool tool = new WebSearchTool(mockFetcher);
        String result = tool.search(Map.ofEntries(Map.entry("query", "connection error")));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("error"));
    }

    @Test
    void execute_buildsCorrectDuckDuckGoUrl() throws Exception {
        FakeHttpResponse mockResponse = new FakeHttpResponse(200, buildMockDuckDuckGoHtml());
        when(mockFetcher.send(any(HttpRequest.class), any(Duration.class))).thenReturn(mockResponse);

        WebSearchTool tool = new WebSearchTool(mockFetcher);
        tool.search(Map.ofEntries(Map.entry("query", "mi consulta")));

        verify(mockFetcher).send(argThat(request -> {
            String uri = request.uri().toString();
            return uri.contains("html.duckduckgo.com/html")
                    && uri.contains("q=mi+consulta")
                    && uri.contains("kl=mx-es");
        }), any(Duration.class));
    }

    @Test
    void execute_returnsError_whenInterrupted() throws Exception {
        when(mockFetcher.send(any(HttpRequest.class), any(Duration.class)))
                .thenThrow(new InterruptedException("Interrupted"));

        WebSearchTool tool = new WebSearchTool(mockFetcher);
        String result = tool.search(Map.ofEntries(Map.entry("query", "interrupted")));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("interrupted"));
    }

    @Test
    void execute_returnsJsonWithContentField() throws Exception {
        FakeHttpResponse mockResponse = new FakeHttpResponse(200, buildMockDuckDuckGoHtml());
        FakeHttpResponse mockContent = new FakeHttpResponse(200, "<html><body><p>Test content for first result</p></body></html>");
        when(mockFetcher.send(any(HttpRequest.class), any(Duration.class)))
                .thenReturn(mockResponse)
                .thenReturn(mockContent);

        WebSearchTool tool = new WebSearchTool(mockFetcher);
        String result = tool.search(Map.ofEntries(Map.entry("query", "test query")));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(result.contains("\"title\""));
        assertTrue(result.contains("\"url\""));
        assertTrue(result.contains("\"content\""));
    }

    @Test
    void execute_resultsHaveCorrectNumberOfResults() throws Exception {
        FakeHttpResponse mockResponse = new FakeHttpResponse(200, buildMockDuckDuckGoHtml());
        FakeHttpResponse mockContent = new FakeHttpResponse(200, "<html><body><p>Some content from the first result page for testing purposes</p></body></html>");
        when(mockFetcher.send(any(HttpRequest.class), any(Duration.class)))
                .thenReturn(mockResponse)
                .thenReturn(mockContent);

        WebSearchTool tool = new WebSearchTool(mockFetcher);
        String result = tool.search(Map.ofEntries(Map.entry("query", "test query")));

        assertTrue(result.contains("First Result"));
        assertTrue(result.contains("Second Result"));
    }

    private String buildMockDuckDuckGoHtml() {
        return """
            <html>
            <body>
            <div class="result results_links results_links_deep web-result">
                <div class="result__body">
                    <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fresult1&rut=abc">First Result</a>
                    <a class="result__snippet">This is a longer snippet text for the first result here</a>
                    <div class="result__snippet">This is a longer snippet text for the first result here</div>
                </div>
            </div>
            <div class="result results_links results_links_deep web-result">
                <div class="result__body">
                    <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fresult2&rut=def">Second Result</a>
                    <div class="result__snippet">This is a longer snippet text for the second result here</div>
                </div>
            </div>
            </body>
            </html>
            """;
    }

    private static class FakeHttpResponse implements HttpResponse<String> {
        private final int statusCode;
        private final String body;

        FakeHttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override public int statusCode() { return statusCode; }
        @Override public String body() { return body; }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (n, v) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return null; }
        @Override public HttpRequest request() { return null; }
        @Override public HttpClient.Version version() { return null; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
    }
}