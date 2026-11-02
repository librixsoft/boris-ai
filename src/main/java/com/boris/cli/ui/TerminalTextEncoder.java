package com.boris.cli.ui;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public final class TerminalTextEncoder {

    private TerminalTextEncoder() {
    }

    public static List<byte[]> encode(String text, Charset charset) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        if (charset == null) {
            throw new IllegalArgumentException("charset cannot be null");
        }
        List<byte[]> chunks = new ArrayList<>(text.length());
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            chunks.add(new String(Character.toChars(codePoint)).getBytes(charset));
            index += Character.charCount(codePoint);
        }
        return chunks;
    }
}
