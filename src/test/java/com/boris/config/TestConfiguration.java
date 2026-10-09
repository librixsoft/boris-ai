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
import com.boris.tooling.tool.ExecuteCommandTool;
import com.boris.tooling.tool.ListFilesTool;
import com.boris.tooling.tool.OfficeDocumentTool;
import com.boris.tooling.tool.PdfGenerationTool;
import com.boris.tooling.tool.ReadFileTool;
import com.boris.tooling.tool.SystemInfoTool;
import com.boris.tooling.tool.WebSearchTool;
import com.boris.tooling.tool.WriteTool;
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
    public SkillExecutor skillExecutor(Path workspaceDirectory) {
        return new SkillExecutor(workspaceDirectory);
    }

    @Bean
    @Primary
    public SkillManager skillManager(SkillLoader skillLoader, SkillExecutor skillExecutor) {
        return new SkillManager(skillLoader, skillExecutor);
    }

    @Bean
    @Primary
    public TaskAborter taskAborter() {
        return new TaskAborter();
    }

    @Bean
    public ReadFileTool readFileTool() {
        return new ReadFileTool();
    }

    @Bean
    public WriteTool writeTool() {
        return new WriteTool();
    }

    @Bean
    public DeleteTool deleteTool() {
        return new DeleteTool();
    }

    @Bean
    public ListFilesTool listFilesTool() {
        return new ListFilesTool();
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
    public ExecuteCommandTool executeCommandTool() {
        return new ExecuteCommandTool();
    }

    @Bean
    public TaskPlannerTool taskPlannerTool() {
        return new TaskPlannerTool();
    }

    @Bean
    @Primary
    public ToolCallingConfig toolCallingConfig(ReadFileTool readFileTool,
                                               WriteTool writeTool,
                                               DeleteTool deleteTool,
                                               ListFilesTool listFilesTool,
                                               EditTool editTool,
                                               SystemInfoTool systemInfoTool,
                                               WebSearchTool webSearchTool,
                                               PdfGenerationTool pdfGenerationTool,
                                               OfficeDocumentTool officeDocumentTool,
                                               ExecuteCommandTool executeCommandTool,
                                               TaskPlannerTool taskPlannerTool,
                                               Settings settings) {
        return new ToolCallingConfig(
                readFileTool,
                writeTool,
                deleteTool,
                listFilesTool,
                editTool,
                systemInfoTool,
                webSearchTool,
                pdfGenerationTool,
                officeDocumentTool,
                executeCommandTool,
                taskPlannerTool,
                settings
        );
    }
}
