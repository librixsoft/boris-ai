package com.boris.llm.think;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ThinkClientHttpInterceptor implements ClientHttpRequestInterceptor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        String thinkMode = ThinkContextHolder.getThinkMode();
        byte[] outgoing = body;
        if (thinkMode != null && !thinkMode.isBlank() && body != null && body.length > 0) {
            outgoing = injectThink(body, thinkMode);
        }
        ThinkContextHolder.clearLastThinking();
        ClientHttpResponse response = execution.execute(request, outgoing);
        byte[] responseBody = response.getBody().readAllBytes();
        if (responseBody != null && responseBody.length > 0) {
            try {
                JsonNode root = MAPPER.readTree(responseBody);
                String thinking = ThinkResponseParser.extractThinking(root);
                if (thinking != null && !thinking.isBlank()) {
                    ThinkContextHolder.setLastThinking(thinking);
                }
            } catch (Exception ignored) {
            }
        }
        return new BufferedClientHttpResponse(response, responseBody);
    }

    static byte[] injectThink(byte[] body, String thinkMode) {
        try {
            JsonNode root = MAPPER.readTree(body);
            if (!root.isObject()) {
                return body;
            }
            ObjectNode obj = (ObjectNode) root;
            obj.put("think", thinkMode);
            JsonNode options = obj.get("options");
            ObjectNode optionsObj;
            if (options != null && options.isObject()) {
                optionsObj = (ObjectNode) options;
            } else {
                optionsObj = MAPPER.createObjectNode();
                obj.set("options", optionsObj);
            }
            optionsObj.put("think", thinkMode);
            return MAPPER.writeValueAsBytes(obj);
        } catch (Exception e) {
            return body;
        }
    }

    private static final class BufferedClientHttpResponse implements ClientHttpResponse {

        private final HttpStatusCode statusCode;
        private final int rawStatusCode;
        private final String statusText;
        private final HttpHeaders headers;
        private final byte[] body;

        BufferedClientHttpResponse(ClientHttpResponse delegate, byte[] body) throws IOException {
            this.statusCode = delegate.getStatusCode();
            this.rawStatusCode = delegate.getRawStatusCode();
            this.statusText = delegate.getStatusText();
            this.headers = delegate.getHeaders();
            this.body = body != null ? body : new byte[0];
            delegate.close();
        }

        @Override
        public HttpStatusCode getStatusCode() {
            return this.statusCode;
        }

        @Override
        public int getRawStatusCode() {
            return this.rawStatusCode;
        }

        @Override
        public String getStatusText() {
            return this.statusText;
        }

        @Override
        public void close() {
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(this.body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return this.headers;
        }

        @Override
        public String toString() {
            return new String(this.body, StandardCharsets.UTF_8);
        }
    }
}
