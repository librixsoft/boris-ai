package com.boris.knowledge;

import com.boris.context.CodeSymbol;
import com.boris.context.SymbolRepository;
import com.boris.tooling.ToolProgressNotifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Tool for querying, saving, and listing persistent local framework knowledge cards (.boris/<framework>/<CLASE>.md).
 */
public class FrameworkKnowledgeTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String queryKnowledge(String framework, String className, String projectPath) {
        if (framework == null || framework.isBlank()) {
            return error("framework es requerido");
        }
        if (className == null || className.isBlank()) {
            return error("className es requerido");
        }

        Path projectRoot = KnowledgeStorageService.resolveProjectRoot(projectPath);

        // 1. Revisar si ya existe en .boris/<framework>/<CLASE>.md
        if (KnowledgeStorageService.hasKnowledge(projectRoot, framework, className)) {
            ToolProgressNotifier.notify("framework_knowledge", "Cache hit: .boris/" + framework + "/" + className + ".md");
            String content = KnowledgeStorageService.readKnowledge(projectRoot, framework, className);
            return success("Conocimiento previo recuperado de .boris/" + framework + "/" + className + ".md:\n\n" + content);
        }

        ToolProgressNotifier.notify("framework_knowledge", "Cache miss: buscando origen para " + className + " en " + framework);

        // 2. Intentar generar automáticamente desde archivo fuente en .boris/repos/<framework> o proyecto local
        Path foundFile = findSourceFile(projectRoot, framework, className);
        if (foundFile != null) {
            String extracted = KnowledgeExtractorService.extractFromJavaFile(foundFile, framework);
            if (extracted != null && !extracted.isBlank()) {
                try {
                    KnowledgeStorageService.saveKnowledge(projectRoot, framework, className, extracted);
                    ToolProgressNotifier.notify("framework_knowledge", "Sintetizado y guardado en .boris/" + framework + "/" + className + ".md");
                    return success("Ficha generada y guardada en .boris/" + framework + "/" + className + ".md:\n\n" + extracted);
                } catch (IOException e) {
                    return success("Ficha extraída:\n\n" + extracted);
                }
            }
        }

        // 3. Intentar generar desde SymbolRepository (si está indexado)
        if (SymbolRepository.totalCount() > 0) {
            List<CodeSymbol> symbols = SymbolRepository.search(className, null, framework);
            if (!symbols.isEmpty()) {
                String extracted = KnowledgeExtractorService.extractFromSymbols(framework, className, symbols);
                if (extracted != null && !extracted.isBlank()) {
                    try {
                        KnowledgeStorageService.saveKnowledge(projectRoot, framework, className, extracted);
                        ToolProgressNotifier.notify("framework_knowledge", "Generado desde símbolos en .boris/" + framework + "/" + className + ".md");
                        return success("Ficha generada desde símbolos y guardada en .boris/" + framework + "/" + className + ".md:\n\n" + extracted);
                    } catch (IOException e) {
                        return success("Ficha extraída desde símbolos:\n\n" + extracted);
                    }
                }
            }
        }

        return error("No hay ficha previa ni archivo fuente local para '" + className + "' en '" + framework + "'. " +
            "Inspecciona el archivo puntual con read_file o ejemplos, y guarda la ficha con save_framework_knowledge.");
    }

    public static String saveKnowledge(String framework, String className, String content, String projectPath) {
        if (framework == null || framework.isBlank()) {
            return error("framework es requerido");
        }
        if (className == null || className.isBlank()) {
            return error("className es requerido");
        }
        if (content == null || content.isBlank()) {
            return error("content es requerido");
        }

        Path projectRoot = KnowledgeStorageService.resolveProjectRoot(projectPath);
        try {
            KnowledgeStorageService.saveKnowledge(projectRoot, framework, className, content);
            ToolProgressNotifier.notify("framework_knowledge", "Guardado: .boris/" + framework + "/" + className + ".md");
            return success("Ficha guardada exitosamente en .boris/" + framework + "/" + className + ".md");
        } catch (IOException e) {
            return error("Error guardando conocimiento: " + e.getMessage());
        }
    }

    public static String listKnowledge(String framework, String projectPath) {
        Path projectRoot = KnowledgeStorageService.resolveProjectRoot(projectPath);

        if (framework != null && !framework.isBlank()) {
            List<String> classes = KnowledgeStorageService.listClasses(projectRoot, framework);
            try {
                ObjectNode root = MAPPER.createObjectNode();
                root.put("framework", framework);
                root.put("cached_classes_count", classes.size());
                ArrayNode arr = root.putArray("classes");
                classes.forEach(arr::add);
                return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            } catch (Exception e) {
                return error("Error listando: " + e.getMessage());
            }
        }

        List<String> frameworks = KnowledgeStorageService.listFrameworks(projectRoot);
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("total_frameworks", frameworks.size());
            ArrayNode arr = root.putArray("frameworks");
            frameworks.forEach(arr::add);
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return error("Error listando: " + e.getMessage());
        }
    }

    private static Path findSourceFile(Path projectRoot, String framework, String className) {
        // 1. Buscar en .boris/repos/<framework>
        Path repoDir = projectRoot.resolve(".boris").resolve("repos").resolve(framework);
        if (Files.isDirectory(repoDir)) {
            Path file = searchFile(repoDir, className + ".java");
            if (file != null) return file;
        }

        // 2. Buscar en el directorio actual del proyecto
        return searchFile(projectRoot, className + ".java");
    }

    private static Path searchFile(Path startDir, String fileName) {
        try (Stream<Path> stream = Files.walk(startDir, 6)) {
            return stream.filter(p -> p.getFileName().toString().equalsIgnoreCase(fileName))
                         .filter(p -> !p.toString().contains("/test/"))
                         .findFirst()
                         .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String success(String message) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("success", true);
            root.put("message", message);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"success\":true,\"message\":\"" + message + "\"}";
        }
    }

    private static String error(String message) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("success", false);
            root.put("error", message);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"" + message + "\"}";
        }
    }
}
