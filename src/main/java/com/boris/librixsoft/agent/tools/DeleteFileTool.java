package com.boris.librixsoft.agent.tools;

import com.boris.librixsoft.agent.exception.ToolException;
import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.util.PathResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class DeleteFileTool implements ToolInterface {

    @Autowired
    private BorisProperties borisProperties;

    @Override
    public String call(String absolutePath) {
        try {
            String fullPath = PathResolver.resolveWorkspacePath(absolutePath, borisProperties.getWorkspacePrefix());
            Path path = Paths.get(fullPath);
            Files.deleteIfExists(path);
            System.out.println("🗑️ \u001B[91m[File Deleted]\u001B[0m " + fullPath);
            return "ok: archivo borrado en " + fullPath;
        } catch (Exception e) {
            throw new ToolException("error: " + e.getMessage());
        }
    }
}