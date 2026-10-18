package com.boris.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.boris.settings.Settings;
import com.boris.tooling.tool.DeleteTool;
import com.boris.tooling.tool.EditTool;
import com.boris.tooling.tool.GitTool;
import com.boris.tooling.tool.OfficeDocumentTool;
import com.boris.tooling.tool.PdfGenerationTool;
import com.boris.tooling.tool.SystemInfoTool;
import com.boris.tooling.tool.WebSearchTool;
import com.boris.tooling.tool.WriteTool;
import com.boris.tooling.tool.LibraryContextTool;
import com.boris.tooling.parallel.BatchReadFileTool;
import com.boris.tooling.parallel.BatchEditTool;
import com.boris.tooling.parallel.BatchWebFetchTool;

import com.boris.tooling.parallel.BatchListDirectoriesTool;
import com.boris.tooling.parallel.BatchGitHubSearchTool;
import com.boris.tooling.parallel.ParallelSearchTool;
import com.boris.task.decomposition.TaskPlannerTool;

@Configuration
public class ToolConfiguration {

    @Bean
    public WriteTool writeTool() {
        return new WriteTool();
    }

    @Bean
    public DeleteTool deleteTool() {
        return new DeleteTool();
    }

    @Bean
    public GitTool gitTool() {
        return new GitTool();
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

    @Bean
    public LibraryContextTool libraryContextTool() {
        return new LibraryContextTool();
    }

    // Parallel tools
    @Bean
    public BatchReadFileTool batchReadFileTool() {
        return new BatchReadFileTool();
    }

    @Bean
    public BatchEditTool batchEditTool() {
        return new BatchEditTool();
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
    public BatchGitHubSearchTool batchGitHubSearchTool() {
        return new BatchGitHubSearchTool();
    }

    @Bean
    public ParallelSearchTool parallelSearchTool() {
        return new ParallelSearchTool();
    }
}
