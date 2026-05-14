package com.boris.librixsoft.agent.tools;

import com.boris.librixsoft.agent.exception.ToolException;
import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.util.PathResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class DeleteFolderTool implements ToolInterface {

    @Autowired
    private BorisProperties borisProperties;

    @Override
    public String call(String absolutePath) {
        try {
            String fullPath = PathResolver.resolveWorkspacePath(absolutePath, borisProperties.getWorkspacePrefix());
            Path path = Paths.get(fullPath);
            if (!Files.exists(path)) {
                throw new ToolException("la carpeta no existe: " + fullPath);
            }
            if (!Files.isDirectory(path)) {
                throw new ToolException("la ruta no es una carpeta: " + fullPath);
            }
            Files.walkFileTree(path, new java.nio.file.SimpleFileVisitor<Path>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
                @Override
                public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
            System.out.println("🗑️ \u001B[91m[Folder Deleted]\u001B[0m " + fullPath);
            return "ok: carpeta borrada junto con todo su contenido en " + fullPath;
        } catch (Exception e) {
            throw new ToolException("error: " + e.getMessage());
        }
    }
}