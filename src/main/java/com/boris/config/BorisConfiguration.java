package com.boris.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.boris.settings.Settings;
import com.boris.settings.SettingsManager;

@Configuration
@ComponentScan(basePackages = "com.boris")
public class BorisConfiguration {

    private static final String DEFAULT_SETTINGS_PATH = System.getProperty("user.home") + "/.boris/settings.json";

    @Bean
    public SettingsManager settingsManager() {
        return new SettingsManager();
    }

    @Bean
    public Settings settings(SettingsManager settingsManager) {
        String settingsPath = System.getProperty("boris.settings.path", DEFAULT_SETTINGS_PATH);
        try {
            settingsManager.ensureAgentsMd();
            settingsManager.ensureExists(settingsPath);
            Settings s = settingsManager.loadSettings(settingsPath);
            if (s == null) {
                throw new IllegalStateException("Failed to load settings from: " + settingsPath);
            }
            return s;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize settings: " + e.getMessage(), e);
        }
    }

    @Bean
    public Path skillsDirectory() {
        String home = System.getProperty("user.home");
        return Paths.get(home, ".boris", "skills");
    }

    @Bean
    public Path workspaceDirectory() {
        String home = System.getProperty("user.home");
        return Paths.get(home, ".boris", "workspace");
    }

    @Bean
    public String settingsPath() {
        return System.getProperty("boris.settings.path", DEFAULT_SETTINGS_PATH);
    }
}
