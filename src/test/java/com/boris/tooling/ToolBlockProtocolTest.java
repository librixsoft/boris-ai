package com.boris.tooling;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolBlockProtocolTest {

    @Test
    void parsesSingleBlockWithMultilineJson() {
        String content = "Voy a leer el archivo.\n```tool:batch_read_files\n"
                + "{\"paths\": [\"src/App.java\"]}\n```\n";

        List<ToolBlockProtocol.ToolBlock> blocks = ToolBlockProtocol.parse(content);

        assertEquals(1, blocks.size());
        assertEquals("batch_read_files", blocks.getFirst().toolName());
        assertEquals("{\"paths\": [\"src/App.java\"]}", blocks.getFirst().jsonArguments());
    }

    @Test
    void parsesMultipleBlocks() {
        String content = "```\n```tool:get_system_info\n{}\n```\n```tool:list_files\n"
                + "{\"path\": \".\"}\n```\n";

        List<ToolBlockProtocol.ToolBlock> blocks = ToolBlockProtocol.parse(content);

        assertEquals(2, blocks.size());
        assertEquals("get_system_info", blocks.get(0).toolName());
        assertEquals("list_files", blocks.get(1).toolName());
    }

    @Test
    void emptyContentReturnsNoBlocks() {
        assertTrue(ToolBlockProtocol.parse(null).isEmpty());
        assertTrue(ToolBlockProtocol.parse("").isEmpty());
        assertTrue(ToolBlockProtocol.parse("Texto sin herramientas ```java:src/App.java").isEmpty());
    }

    @Test
    void unclosedBlockIsExecutedAtEndOfTurn() {
        String content = "```tool:web_search\n{\"query\": \"spring boot\"}";

        List<ToolBlockProtocol.ToolBlock> blocks = ToolBlockProtocol.parse(content);

        assertEquals(1, blocks.size());
        assertEquals("web_search", blocks.getFirst().toolName());
        assertEquals("{\"query\": \"spring boot\"}", blocks.getFirst().jsonArguments());
    }

    @Test
    void doesNotConfuseOtherFencedBlocks() {
        String content = "```java:src/App.java\npublic class App {}\n```\n```edit:src/App.java\n<<<<<<< SEARCH\n```\n";

        assertTrue(ToolBlockProtocol.parse(content).isEmpty());
    }

    @Test
    void blankToolNameThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> ToolBlockProtocol.parse("```tool:\n{}\n```"));
    }
}
