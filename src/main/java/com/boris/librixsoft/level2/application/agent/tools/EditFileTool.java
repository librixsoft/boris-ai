package com.boris.librixsoft.level2.application.agent.tools;

import com.boris.librixsoft.exception.ToolException;
import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.util.PathResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Component
public class EditFileTool {

    @Autowired
    private CodeFormatter codeFormatter;

    @Autowired
    private BorisProperties borisProperties;


    public String call(String absolutePath, String oldContent, String newContent) {
        try {
            String fullPath = PathResolver.resolveWorkspacePath(absolutePath, borisProperties.getWorkspacePrefix());
            Path path = Paths.get(fullPath);

            if (oldContent == null || oldContent.isEmpty()) {
                // Modo 1: Sobrescribir todo el archivo (con formateo)
                String formattedContent = codeFormatter.format(newContent, absolutePath);
                Files.writeString(path, formattedContent, java.nio.charset.StandardCharsets.UTF_8);
                System.out.println("✏️ \u001B[94m[File Edited (full)]\u001B[0m " + fullPath);
                return "ok: archivo editado por completo en " + fullPath;
            } else {
                // Modo 2: Edición quirúrgica (cascada de reemplazos robustos)
                String fileContent = Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
                
                String updatedContent = performRobustReplacement(fileContent, oldContent, newContent);
                if (updatedContent == null) {
                    throw new ToolException("No se pudo encontrar una coincidencia única para el texto a reemplazar (se intentaron comparaciones exactas, normalizadas y de bloque difuso).");
                }
                
                Files.writeString(path, updatedContent, java.nio.charset.StandardCharsets.UTF_8);
                return "ok: archivo editado quirúrgicamente de forma robusta en " + fullPath;
            }
        } catch (Exception e) {
            throw new ToolException("error: " + e.getMessage());
        }
    }

    private String performRobustReplacement(String fileContent, String oldContent, String newContent) {
        // Nivel 1: Intento de coincidencia exacta
        if (fileContent.contains(oldContent)) {
            System.out.println("✏️ \u001B[94m[File Edited (surgical - exact match)]\u001B[0m");
            return fileContent.replace(oldContent, newContent);
        }

        // Nivel 2: Intento con fines de línea normalizados (\r\n -> \n)
        String fileNorm = fileContent.replace("\r\n", "\n");
        String oldNorm = oldContent.replace("\r\n", "\n");
        String newNorm = newContent.replace("\r\n", "\n");
        if (fileNorm.contains(oldNorm)) {
            System.out.println("✏️ \u001B[94m[File Edited (surgical - normalized newlines)]\u001B[0m");
            return fileNorm.replace(oldNorm, newNorm);
        }

        // Nivel 3: Fuzzy match por bloque de líneas (tolerante a indentación y espacios)
        String fuzzyResult = performFuzzyBlockReplacement(fileContent, oldContent, newContent);
        if (fuzzyResult != null) {
            System.out.println("✏️ \u001B[94m[File Edited (surgical - fuzzy line matching)]\u001B[0m");
            return fuzzyResult;
        }

        return null;
    }

    private String performFuzzyBlockReplacement(String fileContent, String oldContent, String newContent) {
        String[] fileLines = fileContent.split("\\r?\\n", -1);
        String[] oldLines = oldContent.split("\\r?\\n", -1);

        // Limpiar líneas vacías iniciales o finales del bloque a buscar (evita falsos negativos del LLM)
        int oldStart = 0;
        while (oldStart < oldLines.length && oldLines[oldStart].trim().isEmpty()) {
            oldStart++;
        }
        int oldEnd = oldLines.length - 1;
        while (oldEnd >= oldStart && oldLines[oldEnd].trim().isEmpty()) {
            oldEnd--;
        }

        if (oldStart > oldEnd) {
            return null; // El bloque de búsqueda estaba vacío
        }

        int targetLen = oldEnd - oldStart + 1;
        String[] targetLines = new String[targetLen];
        System.arraycopy(oldLines, oldStart, targetLines, 0, targetLen);

        int matchIndex = -1;
        int matchCount = 0;

        // Deslizar ventana para buscar coincidencias
        for (int j = 0; j <= fileLines.length - targetLen; j++) {
            boolean matches = true;
            for (int i = 0; i < targetLen; i++) {
                String fLine = fileLines[j + i].trim();
                String tLine = targetLines[i].trim();
                if (!fLine.equals(tLine)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                matchIndex = j;
                matchCount++;
            }
        }

        // Si encontramos una coincidencia única, realizamos la sustitución
        if (matchCount == 1) {
            List<String> newLines = new ArrayList<>();
            for (int k = 0; k < matchIndex; k++) {
                newLines.add(fileLines[k]);
            }
            
            // Insertar el nuevo contenido (puede contener múltiples líneas)
            newLines.add(newContent);
            
            for (int k = matchIndex + targetLen; k < fileLines.length; k++) {
                newLines.add(fileLines[k]);
            }
            
            return String.join("\n", newLines);
        }

        return null;
    }
}