package com.boris.librixsoft.level3.domain.service;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Skill cargado desde {@code ~/.boris/skills/*.md}.
 * Toda skill ejecutable es shell: el SO corre {@code command} (Unix) o
 * {@code commandWindows} (PowerShell en Windows).
 *
 * <pre>
 * ---
 * name: createFile
 * command: |
 *   mkdir -p "$(dirname "$SKILL_path")"
 *   printf '%s' "$SKILL_content" > "$SKILL_path"
 * commandWindows: |
 *   $dir = Split-Path -Parent $env:SKILL_path
 *   if ($dir) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
 *   Set-Content -LiteralPath $env:SKILL_path -Value $env:SKILL_content -NoNewline -Encoding utf8
 * workingDirectory: "."
 * description: Crea un archivo
 * ---
 * </pre>
 *
 * Args del JSON llegan como env {@code $SKILL_<arg>} (Unix) /
 * {@code $env:SKILL_<arg>} (Windows) y también como placeholders {@code {{arg}}}.
 */
public record SkillDefinition(
        String name,
        String command,
        String commandWindows,
        String workingDirectory,
        String description,
        String body,
        Path source
) {
    public boolean isExecutable() {
        return (command != null && !command.isBlank())
                || (commandWindows != null && !commandWindows.isBlank());
    }

    public boolean hasWindowsCommand() {
        return commandWindows != null && !commandWindows.isBlank();
    }

    public static Optional<SkillDefinition> parse(Path source, String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String content = raw.trim();
        Map<String, String> frontmatter = Map.of();
        String body = content;

        if (content.startsWith("---")) {
            int end = content.indexOf("---", 3);
            if (end > 0) {
                frontmatter = parseSimpleFrontmatter(content.substring(3, end).trim());
                body = content.substring(end + 3).trim();
            }
        }

        String name = frontmatter.getOrDefault("name", "").trim();
        if (name.isBlank()) {
            String file = source.getFileName().toString();
            name = file.toLowerCase(Locale.ROOT).endsWith(".md")
                    ? file.substring(0, file.length() - 3)
                    : file;
        }

        return Optional.of(new SkillDefinition(
                name,
                frontmatter.getOrDefault("command", "").trim(),
                frontmatter.getOrDefault("commandWindows", "").trim(),
                frontmatter.getOrDefault("workingDirectory", "").trim(),
                frontmatter.getOrDefault("description", "").trim(),
                body,
                source
        ));
    }

    static Map<String, String> parseSimpleFrontmatter(String yaml) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        String[] lines = yaml.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();

            if ("|".equals(value) || ">".equals(value)) {
                StringBuilder block = new StringBuilder();
                i++;
                while (i < lines.length) {
                    String blockLine = lines[i];
                    if (!blockLine.isEmpty()
                            && !Character.isWhitespace(blockLine.charAt(0))
                            && blockLine.contains(":")) {
                        i--;
                        break;
                    }
                    if (!blockLine.isEmpty()
                            && !Character.isWhitespace(blockLine.charAt(0))
                            && !blockLine.startsWith("#")) {
                        i--;
                        break;
                    }
                    if (block.length() > 0) {
                        block.append('\n');
                    }
                    block.append(stripBlockIndent(blockLine));
                    i++;
                }
                value = block.toString().trim();
            } else if ((value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2)
                    || (value.startsWith("'") && value.endsWith("'") && value.length() >= 2)) {
                value = value.substring(1, value.length() - 1);
            }
            map.put(key, value);
        }
        return map;
    }

    private static String stripBlockIndent(String line) {
        if (line.startsWith("    ")) {
            return line.substring(4);
        }
        if (line.startsWith("  ")) {
            return line.substring(2);
        }
        if (line.startsWith("\t")) {
            return line.substring(1);
        }
        return line;
    }
}
