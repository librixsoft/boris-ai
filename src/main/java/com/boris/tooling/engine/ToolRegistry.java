package com.boris.tooling.engine;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.boris.tooling.annotation.Tool;
import com.boris.tooling.annotation.ToolParam;

/**
 * Registro y ejecutor nativo de herramientas basadas en anotaciones {@link Tool} y {@link ToolParam}.
 * Genera el JSON Schema para OpenAI / Ollama y despacha las invocaciones usando Jackson y Java Reflection.
 *
 * <p><b>Referencia histórica / Equivalencia:</b><br>
 * Reemplaza a las utilidades de reflection y callbacks de <b>Spring AI 1.0.0-M6</b>:
 * <ul>
 *   <li>{@code org.springframework.ai.tool.ToolCallbacks.from(Object...)}</li>
 *   <li>{@code org.springframework.ai.tool.ToolCallback}</li>
 *   <li>{@code org.springframework.ai.model.function.FunctionCallback}</li>
 * </ul>
 * <b>Comportamiento implementado:</b>
 * <ul>
 *   <li>Escaneo de métodos públicos con anotación {@code @Tool}.</li>
 *   <li>Generación de JSON Schema compatible con OpenAI tool specification (types: object, string, integer, boolean, array).</li>
 *   <li>Conversión dinámica de argumentos JSON a tipos Java (incluyendo primitivos, String, List, Map) e invocación segura del método.</li>
 * </ul>
 * </p>
 */
public class ToolRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    public ToolRegistry() {
    }

    public static ToolRegistry from(Object... targets) {
        ToolRegistry registry = new ToolRegistry();
        if (targets != null) {
            for (Object target : targets) {
                if (target != null) {
                    registry.register(target);
                }
            }
        }
        return registry;
    }

    public synchronized ToolRegistry register(Object target) {
        if (target == null) {
            return this;
        }

        Method[] methods = target.getClass().getMethods();
        for (Method method : methods) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation == null) {
                continue;
            }

            String name = toolAnnotation.name().isBlank() ? method.getName() : toolAnnotation.name().trim();
            String description = toolAnnotation.description().trim();

            List<ToolParameter> parameters = new ArrayList<>();
            Parameter[] methodParams = method.getParameters();

            for (Parameter p : methodParams) {
                ToolParam paramAnnotation = p.getAnnotation(ToolParam.class);
                String paramName = (paramAnnotation != null && !paramAnnotation.name().isBlank())
                        ? paramAnnotation.name().trim()
                        : p.getName();
                String paramDesc = paramAnnotation != null ? paramAnnotation.description() : "";
                boolean required = paramAnnotation == null || paramAnnotation.required();

                parameters.add(new ToolParameter(
                        paramName,
                        p.getType(),
                        p.getParameterizedType(),
                        paramDesc,
                        required
                ));
            }

            Map<String, Object> schema = buildFunctionSchema(name, description, parameters);
            ToolDefinition definition = new ToolDefinition(
                    name,
                    description,
                    target,
                    method,
                    parameters,
                    schema
            );

            tools.put(name, definition);
        }

        return this;
    }

    public ToolDefinition getTool(String name) {
        return tools.get(name);
    }

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    public Collection<ToolDefinition> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    /**
     * Devuelve la lista de especificaciones de tools en el formato estándar esperado por OpenAI/Ollama:
     * [ { "type": "function", "function": { "name": "...", "description": "...", "parameters": { ... } } } ]
     */
    public List<Map<String, Object>> getOpenAiToolsSchema() {
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (ToolDefinition def : tools.values()) {
            schemas.add(def.openAiToolSchema());
        }
        return schemas;
    }

    /**
     * Construye un resumen en texto de las herramientas registradas para enriquecer el system prompt.
     */
    public String buildToolsSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("HERRAMIENTAS DISPONIBLES (NATIVAS):\n");
        for (ToolDefinition def : tools.values()) {
            sb.append("- ").append(def.name()).append("(");
            List<String> paramDefs = new ArrayList<>();
            for (ToolParameter p : def.parameters()) {
                paramDefs.add(p.name() + (p.required() ? "" : " [opcional]"));
            }
            sb.append(String.join(", ", paramDefs)).append("): ");
            sb.append(def.description()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Ejecuta una herramienta por nombre con los argumentos proporcionados como JSON string.
     */
    public String execute(String toolName, String jsonArguments) throws Exception {
        ToolDefinition tool = tools.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Herramienta no encontrada en el registro: " + toolName);
        }

        JsonNode argsNode;
        if (jsonArguments == null || jsonArguments.isBlank()) {
            argsNode = MAPPER.createObjectNode();
        } else {
            try {
                argsNode = MAPPER.readTree(jsonArguments);
            } catch (Exception e) {
                throw new IllegalArgumentException("Error parseando argumentos JSON para tool " + toolName + ": " + e.getMessage(), e);
            }
        }

        List<ToolParameter> params = tool.parameters();
        Object[] invokeArgs = new Object[params.size()];

        for (int i = 0; i < params.size(); i++) {
            ToolParameter p = params.get(i);
            JsonNode valNode = argsNode.has(p.name()) ? argsNode.get(p.name()) : null;

            if (valNode == null || valNode.isNull()) {
                if (p.javaType().isPrimitive()) {
                    invokeArgs[i] = getDefaultPrimitiveValue(p.javaType());
                } else {
                    invokeArgs[i] = null;
                }
            } else {
                try {
                    invokeArgs[i] = MAPPER.readValue(
                            MAPPER.treeAsTokens(valNode),
                            MAPPER.constructType(p.genericType())
                    );
                } catch (Exception e) {
                    // Fallback para tipos simples
                    if (p.javaType() == String.class) {
                        invokeArgs[i] = valNode.isTextual() ? valNode.asText() : valNode.toString();
                    } else if (p.javaType() == Integer.class || p.javaType() == int.class) {
                        invokeArgs[i] = valNode.asInt();
                    } else if (p.javaType() == Boolean.class || p.javaType() == boolean.class) {
                        invokeArgs[i] = valNode.asBoolean();
                    } else {
                        throw new IllegalArgumentException(
                                "No se pudo convertir argumento '" + p.name() + "' a tipo " + p.javaType().getSimpleName() + ": " + e.getMessage(), e);
                    }
                }
            }
        }

        Method method = tool.method();
        method.setAccessible(true);
        try {
            Object result = method.invoke(tool.target(), invokeArgs);
            if (result == null) {
                return "";
            }
            if (result instanceof String s) {
                return s;
            }
            return MAPPER.writeValueAsString(result);
        } catch (InvocationTargetException ite) {
            Throwable targetException = ite.getTargetException();
            if (targetException instanceof Exception ex) {
                throw ex;
            }
            throw new RuntimeException(targetException);
        }
    }

    private static Map<String, Object> buildFunctionSchema(String name, String description, List<ToolParameter> parameters) {
        Map<String, Object> functionMap = new LinkedHashMap<>();
        functionMap.put("name", name);
        if (description != null && !description.isBlank()) {
            functionMap.put("description", description);
        }

        Map<String, Object> parametersMap = new LinkedHashMap<>();
        parametersMap.put("type", "object");

        Map<String, Object> propertiesMap = new LinkedHashMap<>();
        List<String> requiredList = new ArrayList<>();

        for (ToolParameter param : parameters) {
            Map<String, Object> propMap = new LinkedHashMap<>();
            propMap.put("type", toJsonSchemaType(param.javaType()));
            if (param.description() != null && !param.description().isBlank()) {
                propMap.put("description", param.description());
            }

            if (param.javaType() == List.class) {
                // Especificar items como object/any por defecto
                Map<String, Object> itemsMap = new LinkedHashMap<>();
                itemsMap.put("type", "string");
                propMap.put("items", itemsMap);
            }

            propertiesMap.put(param.name(), propMap);
            if (param.required()) {
                requiredList.add(param.name());
            }
        }

        parametersMap.put("properties", propertiesMap);
        if (!requiredList.isEmpty()) {
            parametersMap.put("required", requiredList);
        }

        functionMap.put("parameters", parametersMap);

        Map<String, Object> toolSchema = new LinkedHashMap<>();
        toolSchema.put("type", "function");
        toolSchema.put("function", functionMap);
        return toolSchema;
    }

    private static String toJsonSchemaType(Class<?> type) {
        if (type == String.class || type == char.class || type == Character.class) {
            return "string";
        }
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class
                || type == Short.class || type == short.class || type == Byte.class || type == byte.class) {
            return "integer";
        }
        if (type == Double.class || type == double.class || type == Float.class || type == float.class) {
            return "number";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "boolean";
        }
        if (type.isArray() || List.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) {
            return "array";
        }
        return "object";
    }

    private static Object getDefaultPrimitiveValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        return null;
    }
}
