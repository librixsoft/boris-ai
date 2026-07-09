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
import java.util.List;

@Component
public class ReadFileTool {

    @Autowired
    private BorisProperties borisProperties;

    public String call(String absolutePath) {
        try {
            String fullPath = PathResolver.resolveWorkspacePath(absolutePath, borisProperties.getWorkspacePrefix());
            Path path = Paths.get(fullPath);
            List<String> lines = Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                sb.append(i + 1).append(": ").append(lines.get(i)).append("\n");
            }
            sb.append("\n[Total: ").append(lines.size()).append(" líneas]");
            return sb.toString();
        } catch (Exception e) {
            throw new ToolException("error: " + e.getMessage());
        }
    }
}