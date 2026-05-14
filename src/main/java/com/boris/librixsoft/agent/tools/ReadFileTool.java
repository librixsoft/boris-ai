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
import java.util.List;

@Component
public class ReadFileTool implements ToolInterface {

    @Autowired
    private BorisProperties borisProperties;

    @Override
    public String call(String absolutePath) {
        try {
            String fullPath = PathResolver.resolveWorkspacePath(absolutePath, borisProperties.getWorkspacePrefix());
            Path path = Paths.get(fullPath);
            List<String> lines = Files.readAllLines(path);
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