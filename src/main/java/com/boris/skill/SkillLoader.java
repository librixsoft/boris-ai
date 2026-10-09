package com.boris.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class SkillLoader {

    private static final Pattern NAME_PATTERN = Pattern.compile("name:\\s*(.+)");
    private static final Pattern DESC_PATTERN = Pattern.compile("description:\\s*(.+)");

    private final Path skillsDir;

    public SkillLoader(Path skillsDir) {
        this.skillsDir = skillsDir;
    }

    public List<Skill> loadAll() {
        List<Skill> skills = new ArrayList<>();
        if (!Files.isDirectory(skillsDir)) {
            return skills;
        }

        try (Stream<Path> files = Files.list(skillsDir)) {
            files.filter(p -> p.toString().endsWith(".md"))
                 .filter(p -> !p.getFileName().toString().equals("AGENT.md"))
                 .forEach(p -> {
                     Skill skill = load(p);
                     if (skill != null) {
                         skills.add(skill);
                     }
                 });
        } catch (IOException e) {
            // ignore
        }

        return skills;
    }

    public Skill load(Path path) {
        try {
            String content = Files.readString(path);
            return parse(content);
        } catch (IOException e) {
            return null;
        }
    }

    public Skill parse(String content) {
        String name = extractField(content, NAME_PATTERN);
        String description = extractField(content, DESC_PATTERN);
        String command = extractCodeBlock(content, "## Comando", "bash");
        String commandWindows = extractCodeBlock(content, "## Comando Windows", "powershell");
        Map<String, Skill.SkillParam> params = extractParams(content);

        if (name == null || name.isEmpty()) {
            return null;
        }

        return new Skill(name, description, command, commandWindows, params);
    }

    private String extractField(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String extractCodeBlock(String content, String section, String lang) {
        int sectionIdx = content.indexOf(section);
        if (sectionIdx == -1) {
            return null;
        }

        String afterSection = content.substring(sectionIdx);
        String startMarker = "```" + lang;
        int startIdx = afterSection.indexOf(startMarker);
        if (startIdx == -1) {
            startMarker = "```";
            startIdx = afterSection.indexOf(startMarker);
            if (startIdx == -1) {
                return null;
            }
        }

        int codeStart = afterSection.indexOf("\n", startIdx) + 1;
        int codeEnd = afterSection.indexOf("```", codeStart);
        if (codeEnd == -1) {
            return null;
        }

        return afterSection.substring(codeStart, codeEnd).trim();
    }

    private Map<String, Skill.SkillParam> extractParams(String content) {
        Map<String, Skill.SkillParam> params = new HashMap<>();

        int tableStart = content.indexOf("## Parametros");
        if (tableStart == -1) {
            return params;
        }

        String afterParams = content.substring(tableStart);
        String[] lines = afterParams.split("\n");

        for (String line : lines) {
            if (line.startsWith("|") && !line.contains("---") && !line.contains("Nombre")) {
                String[] cols = line.split("\\|");
                if (cols.length >= 5) {
                    String paramName = cols[1].trim();
                    String paramType = cols[2].trim();
                    boolean required = cols[3].trim().equalsIgnoreCase("si") ||
                                       cols[3].trim().equalsIgnoreCase("yes") ||
                                       cols[3].trim().equalsIgnoreCase("true");
                    String desc = cols[4].trim();

                    if (!paramName.isEmpty()) {
                        params.put(paramName, new Skill.SkillParam(paramType, required, desc));
                    }
                }
            }
            if (line.startsWith("## ") && !line.contains("Parametros")) {
                break;
            }
        }

        return params;
    }
}
