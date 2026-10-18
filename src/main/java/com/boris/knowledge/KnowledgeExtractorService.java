package com.boris.knowledge;

import com.boris.context.CodeSymbol;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;

import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Extracts and formats concise markdown technical cards from Java source or indexed symbols.
 */
public class KnowledgeExtractorService {

    private static final Set<String> IGNORED_METHODS = Set.of(
        "equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait"
    );

    private static final int MAX_KEY_METHODS = 20;

    public static String extractFromJavaFile(Path file, String framework) {
        try {
            String source = java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
            return extractFromSourceCode(source, framework, file.getFileName().toString());
        } catch (Exception e) {
            return null;
        }
    }

    public static String extractFromSourceCode(String sourceCode, String framework, String fileName) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(sourceCode);
            String pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");

            Optional<TypeDeclaration<?>> primaryType = cu.getPrimaryType();
            if (primaryType.isEmpty() && !cu.getTypes().isEmpty()) {
                primaryType = Optional.of(cu.getTypes().get(0));
            }
            if (primaryType.isEmpty()) {
                return null;
            }

            TypeDeclaration<?> type = primaryType.get();
            String className = type.getNameAsString();
            String kind = type.isEnumDeclaration() ? "enum" : (type.isClassOrInterfaceDeclaration() && type.asClassOrInterfaceDeclaration().isInterface() ? "interface" : "class");

            List<String> constructors = new ArrayList<>();
            for (ConstructorDeclaration ctor : type.getConstructors()) {
                if (ctor.isPublic()) {
                    String params = ctor.getParameters().stream()
                        .map(p -> p.getTypeAsString() + " " + p.getNameAsString())
                        .collect(Collectors.joining(", "));
                    constructors.add("`new " + className + "(" + params + ")`");
                }
            }

            List<String> methods = new ArrayList<>();
            for (MethodDeclaration method : type.getMethods()) {
                if (method.isPublic() && !IGNORED_METHODS.contains(method.getNameAsString())) {
                    String params = method.getParameters().stream()
                        .map(p -> p.getTypeAsString() + " " + p.getNameAsString())
                        .collect(Collectors.joining(", "));
                    methods.add("`" + method.getTypeAsString() + " " + method.getNameAsString() + "(" + params + ")`");
                }
            }

            List<String> fields = new ArrayList<>();
            for (FieldDeclaration field : type.getFields()) {
                if (field.isPublic() && field.isStatic()) {
                    for (var v : field.getVariables()) {
                        fields.add("`" + field.getCommonType().asString() + " " + v.getNameAsString() + "`");
                    }
                }
            }

            String javadoc = type.getJavadocComment()
                .map(j -> j.parse().toText().trim())
                .filter(t -> !t.isBlank())
                .orElse(null);

            return buildMarkdown(
                framework,
                className,
                pkg,
                fileName != null ? fileName : className + ".java",
                kind,
                javadoc,
                constructors,
                methods,
                fields,
                null,
                null
            );

        } catch (Exception e) {
            return null;
        }
    }

    public static String extractFromSymbols(String framework, String className, List<CodeSymbol> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return null;
        }

        String pkg = "";
        String file = "";
        String kind = "class";
        List<String> constructors = new ArrayList<>();
        List<String> methods = new ArrayList<>();
        List<String> fields = new ArrayList<>();

        for (CodeSymbol s : symbols) {
            if (s.getType() == CodeSymbol.SymbolType.CLASS || s.getType() == CodeSymbol.SymbolType.INTERFACE || s.getType() == CodeSymbol.SymbolType.ENUM) {
                if (s.getName().equalsIgnoreCase(className)) {
                    pkg = s.getParent() != null ? s.getParent() : "";
                    file = s.getFile() != null ? s.getFile() : "";
                    kind = s.getType().name().toLowerCase();
                }
            } else if (s.getType() == CodeSymbol.SymbolType.CONSTRUCTOR) {
                constructors.add("`" + s.getSignature() + "`");
            } else if (s.getType() == CodeSymbol.SymbolType.METHOD) {
                if (!IGNORED_METHODS.contains(s.getName())) {
                    methods.add("`" + s.getSignature() + "`");
                }
            } else if (s.getType() == CodeSymbol.SymbolType.FIELD) {
                fields.add("`" + s.getSignature() + "`");
            }
        }

        return buildMarkdown(
            framework,
            className,
            pkg,
            file,
            kind,
            null,
            constructors,
            methods,
            fields,
            null,
            null
        );
    }

    public static String buildMarkdown(
            String framework,
            String className,
            String pkg,
            String sourceFile,
            String kind,
            String purpose,
            List<String> constructors,
            List<String> methods,
            List<String> fields,
            String usageExample,
            String gotchas) {

        StringBuilder sb = new StringBuilder();
        sb.append("# [").append(framework).append("] ").append(className).append("\n\n");
        sb.append("- **Paquete:** `").append(pkg != null && !pkg.isBlank() ? pkg : "default").append("`\n");
        if (sourceFile != null && !sourceFile.isBlank()) {
            sb.append("- **Origen:** `").append(sourceFile).append("`\n");
        }
        sb.append("- **Tipo:** `").append(kind != null ? kind : "class").append("`\n");
        sb.append("- **Registrado en:** ").append(DateTimeFormatter.ISO_INSTANT.format(Instant.now())).append("\n\n");

        sb.append("## Propósito\n");
        if (purpose != null && !purpose.isBlank()) {
            sb.append(purpose).append("\n\n");
        } else {
            sb.append("Componente principal de `").append(framework).append("` para operaciones de `").append(className).append("`.\n\n");
        }

        if (constructors != null && !constructors.isEmpty()) {
            sb.append("## Constructores\n");
            for (String ctor : constructors) {
                sb.append("- ").append(ctor).append("\n");
            }
            sb.append("\n");
        }

        if (methods != null && !methods.isEmpty()) {
            sb.append("## Métodos Clave\n");
            int count = Math.min(methods.size(), MAX_KEY_METHODS);
            for (int i = 0; i < count; i++) {
                sb.append("- ").append(methods.get(i)).append("\n");
            }
            if (methods.size() > MAX_KEY_METHODS) {
                sb.append("- *(+").append(methods.size() - MAX_KEY_METHODS).append(" métodos secundarios omitidos por concisión)*\n");
            }
            sb.append("\n");
        }

        if (fields != null && !fields.isEmpty()) {
            sb.append("## Constantes y Campos Públicos\n");
            for (String field : fields) {
                sb.append("- ").append(field).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## Ejemplo de Uso\n");
        if (usageExample != null && !usageExample.isBlank()) {
            sb.append("```java\n").append(usageExample.trim()).append("\n```\n\n");
        } else {
            sb.append("```java\n");
            if (pkg != null && !pkg.isBlank()) {
                sb.append("import ").append(pkg).append(".").append(className).append(";\n\n");
            }
            if (constructors != null && !constructors.isEmpty()) {
                sb.append("// Instanciación común:\n");
                sb.append("// ").append(className).append(" instance = ").append(constructors.get(0).replace("`", "")).append(";\n");
            } else {
                sb.append("// Uso de métodos o constantes de ").append(className).append("\n");
            }
            sb.append("```\n\n");
        }

        if (gotchas != null && !gotchas.isBlank()) {
            sb.append("## Notas y Restricciones\n");
            sb.append(gotchas.trim()).append("\n\n");
        }

        return sb.toString();
    }
}
