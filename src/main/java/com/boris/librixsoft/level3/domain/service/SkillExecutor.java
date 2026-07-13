package com.boris.librixsoft.level3.domain.service;

import com.boris.librixsoft.exception.ToolException;
import com.boris.librixsoft.level2.application.agent.tools.ShellRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ejecuta skills .md contra el SO. No hay AgentTools por acción:
 * cada skill declara {@code command} y se corre vía {@link ShellRunner}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillExecutor {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z0-9_]+)\\}\\}");

    private final SkillService skillService;
    private final ShellRunner shellRunner;

    public boolean canExecute(String skillName) {
        return skillService.findSkill(skillName)
                .map(SkillDefinition::isExecutable)
                .orElse(false);
    }

    public String execute(String skillName, Map<String, String> args) {
        Map<String, String> safeArgs = args != null ? args : Map.of();
        SkillDefinition skill = skillService.findSkill(skillName)
                .orElseThrow(() -> new ToolException(
                        "Skill desconocida: '" + skillName
                                + "'. Disponibles: " + skillService.getSkills().keySet()));

        if (!skill.isExecutable()) {
            throw new ToolException(
                    "Skill '" + skillName + "' no declara `command`/`commandWindows` en el frontmatter.");
        }

        String commandTemplate = resolveCommand(skill);
        String command = interpolate(commandTemplate, safeArgs, true);
        String workingDir = resolveWorkingDirectory(skill, safeArgs);

        Map<String, String> env = new HashMap<>();
        for (Map.Entry<String, String> entry : safeArgs.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String value = entry.getValue() != null ? entry.getValue() : "";
            env.put("SKILL_" + entry.getKey(), value);
        }

        log.info("🖥️ [SkillExecutor] '{}' cwd={} cmd={}", skill.name(), workingDir, command);
        return shellRunner.run(command, workingDir, env);
    }

    /**
     * Legacy posicional ya no aplica a skills shell; se rechaza con mensaje claro.
     */
    public String executeLegacy(String skillName, java.util.List<String> ignored) {
        throw new ToolException(
                "Skill '" + skillName + "' usa JSON con args nombrados "
                        + "(formato legacy posicional no soportado).");
    }

    /**
     * En Windows prefiere {@code commandWindows}; si falta, cae a {@code command} (Unix).
     */
    static String resolveCommand(SkillDefinition skill) {
        if (isWindows() && skill.hasWindowsCommand()) {
            return skill.commandWindows();
        }
        if (skill.command() != null && !skill.command().isBlank()) {
            return skill.command();
        }
        return skill.commandWindows();
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    private String resolveWorkingDirectory(SkillDefinition skill, Map<String, String> args) {
        String template = skill.workingDirectory();
        if (template != null && !template.isBlank()) {
            return interpolate(template, args, false);
        }
        return args.getOrDefault("workingDirectory", ".");
    }

    static String interpolate(String template, Map<String, String> args, boolean shellEscape) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = args.getOrDefault(key, "");
            if (value == null) {
                value = "";
            }
            String replacement = shellEscape ? shellEscape(value) : value;
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    static String shellEscape(String value) {
        if (isWindows()) {
            return "'" + value.replace("'", "''") + "'";
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
