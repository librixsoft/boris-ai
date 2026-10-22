package com.boris.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.boris.settings.Settings;
import com.boris.settings.SettingsManager;
import com.boris.skill.SkillExecutor;
import com.boris.skill.SkillLoader;
import com.boris.skill.SkillManager;
import com.boris.task.TaskAborter;
import com.boris.tooling.tool.DeleteTool;
import com.boris.tooling.tool.EditTool;
import com.boris.tooling.tool.OfficeDocumentTool;
import com.boris.tooling.tool.PdfGenerationTool;
import com.boris.tooling.tool.SystemInfoTool;
import com.boris.tooling.tool.WebSearchTool;
import com.boris.tooling.parallel.BatchReadFileTool;
import com.boris.tooling.parallel.BatchWebFetchTool;

import com.boris.tooling.parallel.BatchListDirectoriesTool;
import com.boris.tooling.parallel.ParallelSearchTool;
import com.boris.task.decomposition.TaskPlannerTool;
import com.boris.tooling.integration.ToolCallingConfig;

@Configuration
public class TestConfiguration {

    @Bean
    @Primary
    public SettingsManager settingsManager() {
        return new SettingsManager();
    }

    @Bean
    @Primary
    public Settings testSettings() {
        return new Settings();
    }

    @Bean
    @Primary
    public Path skillsDirectory() {
        return Paths.get(System.getProperty("java.io.tmpdir"), "boris-test", "skills");
    }

    @Bean
    @Primary
    public Path workspaceDirectory() {
        return Paths.get(System.getProperty("java.io.tmpdir"), "boris-test", "workspace");
    }

    @Bean
    @Primary
    public SkillLoader skillLoader(Path skillsDirectory) {
        return new SkillLoader(skillsDirectory);
    }

    @Bean
    @Primary
    public SkillExecutor skillExecutor(Path workspaceDirectory, TaskAborter taskAborter) {
        return new SkillExecutor(workspaceDirectory, taskAborter);
    }

    @Bean
    @Primary
    public SkillManager skillManager(SkillLoader skillLoader, SkillExecutor skillExecutor, TaskAborter taskAborter) {
        return new SkillManager(skillLoader, skillExecutor, taskAborter);
    }

    @Bean
    @Primary
    public TaskAborter taskAborter() {
        return new TaskAborter();
    }

    @Bean
    public DeleteTool deleteTool() {
        return new DeleteTool();
    }

    @Bean
    public EditTool editTool() {
        return new EditTool();
    }

    @Bean
    public SystemInfoTool systemInfoTool() {
        return new SystemInfoTool();
    }

    @Bean
    public WebSearchTool webSearchTool() {
        return new WebSearchTool();
    }

    @Bean
    public PdfGenerationTool pdfGenerationTool() {
        return new PdfGenerationTool();
    }

    @Bean
    public OfficeDocumentTool officeDocumentTool() {
        return new OfficeDocumentTool();
    }

    @Bean
    public TaskPlannerTool taskPlannerTool() {
        return new TaskPlannerTool();
    }

    // Parallel tools
    @Bean
    public BatchReadFileTool batchReadFileTool() {
        return new BatchReadFileTool();
    }

    @Bean
    public BatchWebFetchTool batchWebFetchTool() {
        return new BatchWebFetchTool();
    }

    @Bean
    public BatchListDirectoriesTool batchListDirectoriesTool() {
        return new BatchListDirectoriesTool();
    }

    @Bean
    public ParallelSearchTool parallelSearchTool() {
        return new ParallelSearchTool();
    }

    @Bean
    @Primary
    public ToolCallingConfig toolCallingConfig(
                                               DeleteTool deleteTool,
                                               EditTool editTool,
                                               SystemInfoTool systemInfoTool,
                                               PdfGenerationTool pdfGenerationTool,
                                               OfficeDocumentTool officeDocumentTool,
                                               TaskPlannerTool taskPlannerTool,
                                               Settings settings) {
        return new ToolCallingConfig(
                deleteTool,
                editTool,
                systemInfoTool,
                pdfGenerationTool,
                officeDocumentTool,
                taskPlannerTool,
                settings
        );
    }
}
