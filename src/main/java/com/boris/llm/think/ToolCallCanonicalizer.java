package com.boris.llm.think;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Normaliza los argumentos JSON de una tool call a una forma canónica:
 * claves ordenadas, sin espacios y con números de punto flotante integrales
 * representados como enteros. Permite detectar repeticiones semánticas de
 * tool calls aunque el modelo varíe el formato textual del JSON.
 *
 * <p>Contrato: argumentos nulos o vacíos canonicalizan a {@code "{}"};
 * texto que no es JSON válido canonicaliza a su versión recortada.</p>
 */
public final class ToolCallCanonicalizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EMPTY_ARGUMENTS = "{}";

    private ToolCallCanonicalizer() {
    }

    public static String canonicalize(String arguments) {
        if (arguments == null) {
            return EMPTY_ARGUMENTS;
        }
        String trimmed = arguments.trim();
        if (trimmed.isEmpty()) {
            return EMPTY_ARGUMENTS;
        }
        try {
            JsonNode root = MAPPER.readTree(trimmed);
            return MAPPER.writeValueAsString(normalize(root));
        } catch (Exception e) {
            return trimmed;
        }
    }

    private static JsonNode normalize(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            ObjectNode normalized = MAPPER.createObjectNode();
            List<String> fieldNames = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(fieldNames::add);
            Collections.sort(fieldNames);
            for (String fieldName : fieldNames) {
                normalized.set(fieldName, normalize(objectNode.get(fieldName)));
            }
            return normalized;
        }
        if (node instanceof ArrayNode arrayNode) {
            ArrayNode normalized = MAPPER.createArrayNode();
            for (JsonNode element : arrayNode) {
                normalized.add(normalize(element));
            }
            return normalized;
        }
        if (isIntegralFloatingPoint(node)) {
            return new LongNode((long) node.doubleValue());
        }
        return node;
    }

    private static boolean isIntegralFloatingPoint(JsonNode node) {
        return node.isFloatingPointNumber()
                && !node.isIntegralNumber()
                && node.doubleValue() == Math.rint(node.doubleValue());
    }
}
