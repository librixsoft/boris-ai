package com.boris.cli.ui;

import com.googlecode.lanterna.TextColor;
import java.util.*;

public final class SyntaxHighlighter {

    public record SyntaxToken(String text, TextColor color) {}

    private static final Set<String> KEYWORDS = Set.of(
        "abstract", "assert", "async", "await", "break", "case", "catch", "class", "const",
        "continue", "debugger", "default", "def", "delete", "do", "else", "enum", "export",
        "extends", "final", "finally", "fn", "for", "from", "function", "goto", "if", "implements",
        "import", "in", "instanceof", "interface", "let", "match", "mut", "native", "new",
        "package", "pass", "private", "protected", "pub", "public", "record", "return", "sealed",
        "static", "strictfp", "struct", "super", "switch", "synchronized", "this", "throw",
        "throws", "transient", "try", "typeof", "use", "var", "void", "volatile", "while",
        "with", "yield"
    );

    private static final Set<String> TYPES = Set.of(
        "boolean", "byte", "char", "double", "float", "int", "long", "short", "void",
        "Boolean", "Byte", "Character", "Double", "Float", "Integer", "Long", "Short", "String",
        "Object", "List", "Map", "Set", "Optional", "CompletableFuture", "Promise",
        "any", "number", "string", "unknown", "never", "Array", "Record", "i32", "i64", "u32", "u64", "f32", "f64"
    );

    private static final Set<String> LITERALS = Set.of(
        "true", "false", "null", "undefined", "nil", "None", "NaN"
    );

    private SyntaxHighlighter() {}

    public static List<SyntaxToken> tokenizeLine(String line) {
        List<SyntaxToken> tokens = new ArrayList<>();
        if (line == null || line.isEmpty()) {
            return tokens;
        }

        int len = line.length();
        int i = 0;

        while (i < len) {
            char c = line.charAt(i);

            // 1. Whitespace
            if (Character.isWhitespace(c)) {
                int start = i;
                while (i < len && Character.isWhitespace(line.charAt(i))) {
                    i++;
                }
                tokens.add(new SyntaxToken(line.substring(start, i), UiTheme.SYNTAX_TEXT));
                continue;
            }

            // 2. Line comments (// or #)
            if ((c == '/' && i + 1 < len && line.charAt(i + 1) == '/') || (c == '#' && (i == 0 || Character.isWhitespace(line.charAt(i - 1))))) {
                tokens.add(new SyntaxToken(line.substring(i), UiTheme.SYNTAX_COMMENT));
                break;
            }

            // 3. Strings ("...", '...', `...`)
            if (c == '"' || c == '\'' || c == '`') {
                char quote = c;
                int start = i++;
                boolean escaped = false;
                while (i < len) {
                    char current = line.charAt(i);
                    if (current == '\\' && !escaped) {
                        escaped = true;
                    } else if (current == quote && !escaped) {
                        i++;
                        break;
                    } else {
                        escaped = false;
                    }
                    i++;
                }
                tokens.add(new SyntaxToken(line.substring(start, i), UiTheme.SYNTAX_STRING));
                continue;
            }

            // 4. Annotations / Decorators (@Name)
            if (c == '@' && i + 1 < len && (Character.isJavaIdentifierStart(line.charAt(i + 1)))) {
                int start = i++;
                while (i < len && (Character.isJavaIdentifierPart(line.charAt(i)) || line.charAt(i) == '.')) {
                    i++;
                }
                tokens.add(new SyntaxToken(line.substring(start, i), UiTheme.SYNTAX_ANNOTATION));
                continue;
            }

            // 5. Numbers (123, 0x1A, 3.14, 100L)
            if (Character.isDigit(c) || (c == '.' && i + 1 < len && Character.isDigit(line.charAt(i + 1)))) {
                int start = i;
                if (c == '0' && i + 1 < len && (line.charAt(i + 1) == 'x' || line.charAt(i + 1) == 'X' || line.charAt(i + 1) == 'b')) {
                    i += 2;
                }
                while (i < len) {
                    char ch = line.charAt(i);
                    if (Character.isLetterOrDigit(ch) || ch == '.' || ch == '_') {
                        i++;
                    } else {
                        break;
                    }
                }
                tokens.add(new SyntaxToken(line.substring(start, i), UiTheme.SYNTAX_NUMBER));
                continue;
            }

            // 6. Identifiers (Keywords, Types, Booleans, Functions, Variables)
            if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                while (i < len && Character.isJavaIdentifierPart(line.charAt(i))) {
                    i++;
                }
                String word = line.substring(start, i);

                // Determine color
                if (KEYWORDS.contains(word)) {
                    tokens.add(new SyntaxToken(word, UiTheme.SYNTAX_KEYWORD));
                } else if (LITERALS.contains(word)) {
                    tokens.add(new SyntaxToken(word, UiTheme.SYNTAX_BOOLEAN));
                } else if (TYPES.contains(word) || (Character.isUpperCase(word.charAt(0)) && !word.toUpperCase().equals(word))) {
                    tokens.add(new SyntaxToken(word, UiTheme.SYNTAX_TYPE));
                } else {
                    // Check if it's a function call (next non-whitespace is '(')
                    int next = i;
                    while (next < len && Character.isWhitespace(line.charAt(next))) next++;
                    if (next < len && line.charAt(next) == '(') {
                        tokens.add(new SyntaxToken(word, UiTheme.SYNTAX_FUNCTION));
                    } else {
                        tokens.add(new SyntaxToken(word, UiTheme.SYNTAX_TEXT));
                    }
                }
                continue;
            }

            // 7. Operators and punctuation
            int start = i++;
            while (i < len && !Character.isWhitespace(line.charAt(i)) &&
                   !Character.isLetterOrDigit(line.charAt(i)) &&
                   line.charAt(i) != '"' && line.charAt(i) != '\'' && line.charAt(i) != '`' &&
                   line.charAt(i) != '@' && line.charAt(i) != '/' && line.charAt(i) != '#') {
                i++;
            }
            tokens.add(new SyntaxToken(line.substring(start, i), UiTheme.SYNTAX_OPERATOR));
        }

        return tokens;
    }
}
