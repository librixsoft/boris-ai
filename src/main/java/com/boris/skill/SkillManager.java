package com.boris.skill;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SkillManager {

    private final SkillLoader loader;
    private final SkillExecutor executor;
    private final Map<String, Skill> skills;

    public SkillManager(SkillLoader loader, SkillExecutor executor) {
        this.loader = loader;
        this.executor = executor;
        this.skills = new HashMap<>();
        reload();
    }

    public void reload() {
        skills.clear();
        List<Skill> loaded = loader.loadAll();
        for (Skill skill : loaded) {
            skills.put(skill.getName().toLowerCase(), skill);
        }
    }

    public List<Skill> getSkills() {
        return List.copyOf(skills.values());
    }

    public Skill getSkill(String name) {
        return skills.get(name.toLowerCase());
    }

    public boolean hasSkill(String name) {
        return skills.containsKey(name.toLowerCase());
    }

    public void execute(String skillName, Map<String, String> args, Consumer<String> output) {
        Skill skill = getSkill(skillName);
        if (skill == null) {
            output.accept("[error] Skill no encontrada: " + skillName);
            return;
        }
        executor.execute(skill, args, output);
    }

    public void execute(String skillName, Consumer<String> output) {
        execute(skillName, null, output);
    }

    public static Path getDefaultSkillsDir() {
        String home = System.getProperty("user.home");
        return Paths.get(home, ".boris", "skills");
    }

    public static Path getDefaultWorkspaceDir() {
        String home = System.getProperty("user.home");
        return Paths.get(home, ".boris", "workspace");
    }
}
