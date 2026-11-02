package com.boris.cli.ui;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TerminalTextEncoderTest {

    @Test
    void encodeEmojiSchemaWritesCompleteUtf8Sequence() {
        List<byte[]> chunks = TerminalTextEncoder.encode("📂 batch_read_files", StandardCharsets.UTF_8);

        assertEquals(18, chunks.size());
        byte[] expected = {(byte) 0xF0, (byte) 0x9F, (byte) 0x93, (byte) 0x82};
        assertArrayEquals(expected, chunks.get(0));
    }

    @Test
    void encodeAsciiTextProducesOneBytePerChar() {
        List<byte[]> chunks = TerminalTextEncoder.encode("abc", StandardCharsets.UTF_8);

        assertEquals(3, chunks.size());
        assertArrayEquals(new byte[]{0x61}, chunks.get(0));
        assertArrayEquals(new byte[]{0x62}, chunks.get(1));
        assertArrayEquals(new byte[]{0x63}, chunks.get(2));
    }

    @Test
    void encodeBoxDrawingCharProducesThreeUtf8Bytes() {
        List<byte[]> chunks = TerminalTextEncoder.encode("│", StandardCharsets.UTF_8);

        assertEquals(1, chunks.size());
        byte[] expected = {(byte) 0xE2, (byte) 0x94, (byte) 0x82};
        assertArrayEquals(expected, chunks.get(0));
    }

    @Test
    void encodeVariationSelectorCharKeepsAsSeparateChunk() {
        List<byte[]> chunks = TerminalTextEncoder.encode("✏️", StandardCharsets.UTF_8);

        assertEquals(2, chunks.size());
        byte[] pencil = {(byte) 0xE2, (byte) 0x9C, (byte) 0x8F};
        byte[] selector = {(byte) 0xEF, (byte) 0xB8, (byte) 0x8F};
        assertArrayEquals(pencil, chunks.get(0));
        assertArrayEquals(selector, chunks.get(1));
    }

    @Test
    void encodeThrowsOnCharsetNotProvided() {
        assertThrows(IllegalArgumentException.class, () -> TerminalTextEncoder.encode("text", null));
        assertThrows(IllegalArgumentException.class, () -> TerminalTextEncoder.encode(null, StandardCharsets.UTF_8));
    }
}
