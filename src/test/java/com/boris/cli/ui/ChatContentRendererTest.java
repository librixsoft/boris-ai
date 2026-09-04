package com.boris.cli.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatContentRendererTest {

    @Test
    void testColumnToCharIndexSimpleAscii() {
        ChatContentRenderer renderer = new ChatContentRenderer();
        String line = "Hello World";
        assertEquals(0, renderer.columnToCharIndex(line, 0));
        assertEquals(5, renderer.columnToCharIndex(line, 5));
        assertEquals(11, renderer.columnToCharIndex(line, 11));
        assertEquals(11, renderer.columnToCharIndex(line, 20));
    }

    @Test
    void testColumnToCharIndexNullOrEmpty() {
        ChatContentRenderer renderer = new ChatContentRenderer();
        assertEquals(0, renderer.columnToCharIndex(null, 5));
        assertEquals(0, renderer.columnToCharIndex("", 5));
        assertEquals(0, renderer.columnToCharIndex("Hello", -1));
    }
}
