package com.boris.skill;

import java.util.Map;

public class Skill {

    private final String name;
    private final String description;
    private final String command;
    private final String commandWindows;
    private final Map<String, SkillParam> params;

    public Skill(String name, String description, String command, String commandWindows, Map<String, SkillParam> params) {
        this.name = name;
        this.description = description;
        this.command = command;
        this.commandWindows = commandWindows;
        this.params = params;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCommand() {
        return command;
    }

    public String getCommandWindows() {
        return commandWindows;
    }

    public Map<String, SkillParam> getParams() {
        return params;
    }

    public static class SkillParam {
        private final String type;
        private final boolean required;
        private final String description;

        public SkillParam(String type, boolean required, String description) {
            this.type = type;
            this.required = required;
            this.description = description;
        }

        public String getType() {
            return type;
        }

        public boolean isRequired() {
            return required;
        }

        public String getDescription() {
            return description;
        }
    }
}
