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

/**
 * Interceptor del RestClient interno de Spring AI (OpenAiApi).
 *
 * Inyecta "think" y "options.think" al JSON que Spring AI ya genera (nivel
 * cuando el think esta activo, false cuando esta desactivado) y captura el
 * trace de razonamiento que Ollama devuelve fuera del content
 * (thinking/reasoning_content/...) en ThinkContextHolder. No es una llamada
 * directa: solo enriquece/observa el trafico del ChatModel de Spring AI. El
 * control efectivo del think lo hace "reasoning_effort" via OpenAiChatOptions.
 */
public class ThinkClientHttpInterceptor implements ClientHttpRequestInterceptor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        byte[] outgoing = body;
        if (body != null && body.length > 0) {
            String thinkMode = ThinkContextHolder.getThinkMode();
            Object thinkValue = (thinkMode != null && !thinkMode.isBlank()) ? thinkMode : Boolean.FALSE;
            outgoing = injectThink(body, thinkValue);
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

    static byte[] injectThink(byte[] body, Object thinkValue) {
        try {
            JsonNode root = MAPPER.readTree(body);
            if (!root.isObject()) {
                return body;
            }
            ObjectNode obj = (ObjectNode) root;
            if (thinkValue instanceof Boolean disabled && !disabled) {
                obj.put("think", false);
            } else {
                obj.put("think", String.valueOf(thinkValue));
            }
            JsonNode options = obj.get("options");
            ObjectNode optionsObj;
            if (options != null && options.isObject()) {
                optionsObj = (ObjectNode) options;
            } else {
                optionsObj = MAPPER.createObjectNode();
                obj.set("options", optionsObj);
            }
            if (thinkValue instanceof Boolean disabled && !disabled) {
                optionsObj.put("think", false);
            } else {
                optionsObj.put("think", String.valueOf(thinkValue));
            }
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
