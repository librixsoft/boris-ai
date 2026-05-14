package com.boris.librixsoft.agent.tools;

public interface ToolInterface {

    default String call(String absolutePath, String content) {
        return "error: este tool no soporta call(absolutePath, content)";
    }

    default String call(String absolutePath) {
        return "error: este tool no soporta call(absolutePath)";
    }

    default String call(String absolutePath, String oldContent, String newContent) {
        return "error: este tool no soporta call(absolutePath, oldContent, newContent)";
    }
}