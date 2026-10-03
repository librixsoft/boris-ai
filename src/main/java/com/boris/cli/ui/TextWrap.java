package com.boris.cli.ui;

import java.util.ArrayList;
import java.util.List;

public final class TextWrap {

    private TextWrap() {
    }

    public static String wrap(String text, int width) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (String rawLine : text.split("\n", -1)) {
            if (rawLine.isEmpty()) {
                out.append('\n');
                continue;
            }
            List<String> pieces = wrapLine(rawLine, width);
            for (String piece : pieces) {
                out.append(piece).append('\n');
            }
        }
        if (out.length() > 0) {
            out.setLength(out.length() - 1);
        }
        return out.toString();
    }

    public static List<String> wrapLine(String line, int width) {
        List<String> result = new ArrayList<>();
        if (line.length() <= width) {
            result.add(line);
            return result;
        }
        String[] words = line.split(" ", -1);
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            while (word.length() > width) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                result.add(word.substring(0, width));
                word = word.substring(width);
            }
            int extra = current.length() == 0 ? 0 : 1;
            if (current.length() + extra + word.length() > width) {
                result.add(current.toString());
                current.setLength(0);
                current.append(word);
            } else {
                if (current.length() > 0) current.append(' ');
                current.append(word);
            }
        }
        result.add(current.toString());
        return result;
    }
}
