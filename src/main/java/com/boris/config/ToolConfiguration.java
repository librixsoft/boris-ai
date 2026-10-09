package com.boris.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.boris.settings.Settings;
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

@Configuration
public class ToolConfiguration {

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
}
