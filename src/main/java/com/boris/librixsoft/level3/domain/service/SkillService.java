package com.boris.librixsoft.level3.domain.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Copia skills iniciales a {@code user.home/.boris/skills} y compone el system prompt.
 * Toda acción la define un {@code .md} con {@code command}; Java solo orquesta y corre el SO.
 */
@Slf4j
@Service
public class SkillService {

    public static final String SYSTEM_PROMPT_FILE = "AGENT.md";
    /** Directorio relativo a {@code user.home}: {@code .boris/skills}. */
    public static final String SKILLS_DIR_RELATIVE = ".boris/skills";
    private static final String CLASSPATH_PROMPTS = "classpath*:prompts/*";
    private static final String LEGACY_SKILLS_DIR = ".boris.skills";

    private final AtomicReference<String> cachedSystemPrompt = new AtomicReference<>();
    private final AtomicReference<Map<String, SkillDefinition>> cachedSkills =
            new AtomicReference<>(Map.of());
    private final Path skillsDirectory = Path.of(System.getProperty("user.home"), ".boris", "skills");

    @PostConstruct
    public void init() {
        ensureSkillsDirectory();
        migrateLegacySkillsDir();
        seedBundledSkills();
        reload();
        log.info("📂 [SkillService] Skills dir: {} ({} skills)",
                skillsDirectory, cachedSkills.get().size());
    }

    public Path getSkillsDirectory() {
        return skillsDirectory;
    }

    public String getSystemPrompt() {
        reload();
        String prompt = cachedSystemPrompt.get();
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalStateException(
                    "No se pudo construir el system prompt desde " + skillsDirectory);
        }
        return prompt;
    }

    public Map<String, SkillDefinition> getSkills() {
        reload();
        return cachedSkills.get();
    }

    public Optional<SkillDefinition> findSkill(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(getSkills().get(name));
    }

    public synchronized void reload() {
        List<Path> skillFiles = listSkillFiles();
        Map<String, SkillDefinition> skills = new LinkedHashMap<>();
        String systemPromptBody = "";

        for (Path path : skillFiles) {
            String filename = path.getFileName().toString();
            String raw = readUtf8(path);
            if (SYSTEM_PROMPT_FILE.equalsIgnoreCase(filename)) {
                systemPromptBody = raw.trim();
                continue;
            }
            Optional<SkillDefinition> parsed = SkillDefinition.parse(path, raw);
            if (parsed.isEmpty()) {
                continue;
            }
            SkillDefinition skill = parsed.get();
            skills.put(skill.name(), skill);
            if (!skill.isExecutable()) {
                log.warn("⚠️ [SkillService] '{}' sin `command` (no ejecutable)", skill.name());
            } else {
                log.info("🖥️ [SkillService] '{}' listo", skill.name());
            }
        }

        cachedSkills.set(Map.copyOf(skills));

        StringBuilder composed = new StringBuilder();
        if (!systemPromptBody.isBlank()) {
            composed.append(systemPromptBody).append("\n\n");
        }

        if (!skills.isEmpty()) {
            composed.append("### Skills disponibles\n\n");
            composed.append("Skills en `").append(skillsDirectory)
                    .append("`. Cada una declara `command` (Unix/sh) y opcionalmente "
                            + "`commandWindows` (PowerShell). El runtime elige según el SO. "
                            + "Args del JSON llegan como `$SKILL_<nombre>` / `$env:SKILL_<nombre>` "
                            + "y como `{{nombre}}`. "
                            + "Para agregar capacidades: crea un nuevo `.md` (sin código Java).\n\n");

            int index = 1;
            for (SkillDefinition skill : skills.values().stream()
                    .sorted(Comparator.comparing(SkillDefinition::name, String.CASE_INSENSITIVE_ORDER))
                    .toList()) {
                composed.append("#### ").append(index++).append(". `")
                        .append(skill.name()).append("`");
                if (!skill.isExecutable()) {
                    composed.append(" ⚠️ (sin command)");
                }
                composed.append("\n\n");
                if (!skill.description().isBlank()) {
                    composed.append(skill.description()).append("\n\n");
                }
                if (skill.command() != null && !skill.command().isBlank()) {
                    composed.append("Unix:\n```\n").append(skill.command()).append("\n```\n\n");
                }
                if (skill.hasWindowsCommand()) {
                    composed.append("Windows:\n```\n").append(skill.commandWindows()).append("\n```\n\n");
                }
                composed.append(skill.body().trim()).append("\n\n");
            }
        }

        cachedSystemPrompt.set(composed.toString().trim());
    }

    public List<Path> listSkillFiles() {
        if (!Files.isDirectory(skillsDirectory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(skillsDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".md"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            log.warn("No se pudieron listar skills en {}: {}", skillsDirectory, e.getMessage());
            return List.of();
        }
    }

    private void ensureSkillsDirectory() {
        try {
            Files.createDirectories(skillsDirectory);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo crear el directorio de skills: " + skillsDirectory, e);
        }
    }

    /**
     * Si existía la ruta legacy {@code ~/.boris.skills}, mueve los .md a {@code ~/.boris/skills}.
     */
    private void migrateLegacySkillsDir() {
        Path legacy = Path.of(System.getProperty("user.home"), LEGACY_SKILLS_DIR);
        if (!Files.isDirectory(legacy)) {
            return;
        }
        try (Stream<Path> stream = Files.list(legacy)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".md"))
                    .forEach(src -> {
                        Path target = skillsDirectory.resolve(src.getFileName().toString());
                        try {
                            if (!Files.exists(target)) {
                                Files.move(src, target);
                                log.info("📦 [SkillService] Migrado {} → {}", src, target);
                            }
                        } catch (IOException e) {
                            log.warn("No se pudo migrar {}: {}", src, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("No se pudo leer carpeta legacy {}: {}", legacy, e.getMessage());
        }
    }

    private void seedBundledSkills() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(CLASSPATH_PROMPTS);
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || !filename.toLowerCase().endsWith(".md")) {
                    continue;
                }
                Path target = skillsDirectory.resolve(filename);
                if (Files.exists(target)) {
                    continue;
                }
                try (InputStream in = resource.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    log.info("📄 [SkillService] Skill inicial copiado: {}", target);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Error copiando skills iniciales hacia " + skillsDirectory, e);
        }
    }

    private static String readUtf8(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer skill: " + path, e);
        }
    }
}
