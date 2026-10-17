package com.boris.context;

import com.boris.context.CodeSymbol.SymbolType;
import com.boris.tooling.ToolProgressNotifier;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class CodeIndexerService {

    private static final int MAX_FILES = 500;
    private static final int BATCH_SIZE = 100;

    public static int indexRepository(String repoName, Path repoDir) {
        SymbolRepository.deleteByRepo(repoName);

        List<CodeSymbol> batch = new ArrayList<>();
        int totalCount = 0;

        try (Stream<Path> files = Files.walk(repoDir)) {
            List<Path> javaFiles = files
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.toString().contains("/test/"))
                .filter(p -> !p.toString().contains("\\test\\"))
                .limit(MAX_FILES)
                .toList();

            ToolProgressNotifier.notify("index_code",
                "Indexando " + javaFiles.size() + " archivos Java...");

            for (Path file : javaFiles) {
                List<CodeSymbol> symbols = indexFile(repoName, file, repoDir);
                batch.addAll(symbols);

                if (batch.size() >= BATCH_SIZE) {
                    SymbolRepository.saveBatch(batch);
                    totalCount += batch.size();
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                SymbolRepository.saveBatch(batch);
                totalCount += batch.size();
            }

        } catch (IOException e) {
            throw new RuntimeException("Error indexando repo: " + e.getMessage(), e);
        }

        ToolProgressNotifier.notify("index_code",
            "Indexados " + totalCount + " símbolos de " + repoName);

        return totalCount;
    }

    public static int indexFile(String repoName, Path file) {
        SymbolRepository.deleteByFile(file.toString());
        List<CodeSymbol> symbols = indexFile(repoName, file, file.getParent());
        SymbolRepository.saveBatch(symbols);
        return symbols.size();
    }

    private static List<CodeSymbol> indexFile(String repoName, Path file, Path baseDir) {
        List<CodeSymbol> symbols = new ArrayList<>();

        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            String pkg = cu.getPackageDeclaration()
                .map(p -> p.getNameAsString())
                .orElse("");

            String relativePath = baseDir.relativize(file).toString();

            for (TypeDeclaration<?> type : cu.getTypes()) {
                symbols.addAll(indexType(repoName, pkg, relativePath, type));
            }
        } catch (Exception e) {
            // Archivo no parseable, skip silenciosamente
        }

        return symbols;
    }

    private static List<CodeSymbol> indexType(String repoName, String pkg, String file,
                                               TypeDeclaration<?> type) {
        List<CodeSymbol> symbols = new ArrayList<>();
        String className = type.getNameAsString();
        String fullName = pkg.isEmpty() ? className : pkg + "." + className;

        // Determinar tipo
        SymbolType symbolType = SymbolType.CLASS;
        String signature = className;

        if (type instanceof ClassOrInterfaceDeclaration cid) {
            if (cid.isInterface()) {
                symbolType = SymbolType.INTERFACE;
                signature = "interface " + className;
            } else {
                signature = "class " + className;
            }

            if (!cid.getExtendedTypes().isEmpty()) {
                signature += " extends " + cid.getExtendedTypes().get(0).getNameAsString();
            }
            if (!cid.getImplementedTypes().isEmpty()) {
                signature += " implements " + cid.getImplementedTypes().stream()
                    .map(t -> t.getNameAsString())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            }
        } else if (type instanceof EnumDeclaration) {
            symbolType = SymbolType.ENUM;
            signature = "enum " + className;
        }

        // Agregar clase/interface/enum
        symbols.add(new CodeSymbol(
            className, symbolType, pkg, signature, null, file,
            type.getBegin().map(p -> p.line).orElse(0), repoName
        ));

        // Indexar constructores públicos
        for (ConstructorDeclaration ctor : type.getConstructors()) {
            if (ctor.isPublic()) {
                String ctorSig = "new " + className + "(" + getParamTypes(ctor.getParameters()) + ")";
                symbols.add(new CodeSymbol(
                    className, SymbolType.CONSTRUCTOR, fullName, ctorSig, className, file,
                    ctor.getBegin().map(p -> p.line).orElse(0), repoName
                ));
            }
        }

        // Indexar métodos públicos
        for (MethodDeclaration method : type.getMethods()) {
            if (method.isPublic()) {
                String methodSig = method.getNameAsString() + "(" +
                    getParamTypes(method.getParameters()) + ") : " + method.getTypeAsString();

                symbols.add(new CodeSymbol(
                    method.getNameAsString(), SymbolType.METHOD, fullName, methodSig,
                    method.getTypeAsString(), file,
                    method.getBegin().map(p -> p.line).orElse(0), repoName
                ));
            }
        }

        // Indexar campos públicos (constantes, etc)
        for (FieldDeclaration field : type.getFields()) {
            if (field.isPublic()) {
                for (var variable : field.getVariables()) {
                    String fieldSig = field.getCommonType().asString() + " " + variable.getNameAsString();
                    symbols.add(new CodeSymbol(
                        variable.getNameAsString(), SymbolType.FIELD, fullName, fieldSig,
                        field.getCommonType().asString(), file,
                        field.getBegin().map(p -> p.line).orElse(0), repoName
                    ));
                }
            }
        }

        // Indexar clases internas públicas
        for (var member : type.getMembers()) {
            if (member instanceof TypeDeclaration<?> innerType) {
                if (innerType instanceof ClassOrInterfaceDeclaration cid && cid.isPublic()) {
                    symbols.addAll(indexType(repoName, fullName, file, innerType));
                }
            }
        }

        return symbols;
    }

    private static String getParamTypes(List<com.github.javaparser.ast.body.Parameter> params) {
        if (params.isEmpty()) return "";
        return params.stream()
            .map(p -> p.getTypeAsString())
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }
}
