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
public class EditFileWithReplacementTool implements ToolInterface {

    @Autowired
    private BorisProperties borisProperties;

    @Override
    public String call(String absolutePath, String param2, String param3) {
        // oldContent (param2), newContent (param3)
        try {
            String fullPath = PathResolver.resolveWorkspacePath(absolutePath, borisProperties.getWorkspacePrefix());
            Path path = Paths.get(fullPath);
            String fileContent = Files.readString(path);

              if (!fileContent.contains(param2)) {
                  throw new ToolException("no se encontró el texto a reemplazar: " + param2);
              }

            String updated = fileContent.replace(param2, param3);
            Files.writeString(path, updated);
            System.out.println("✏️ \u001B[94m[File Edited (surgical)]\u001B[0m " + fullPath);
            return "ok: archivo editado quirúrgicamente en " + fullPath;
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }
}