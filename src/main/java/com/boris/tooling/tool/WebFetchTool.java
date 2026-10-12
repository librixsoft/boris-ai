package com.boris.tooling.tool;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.*;
import com.boris.tooling.ToolDefinition;
import com.boris.tooling.ToolProgressNotifier;

/**
 * Fetch and extract content from a specific URL using Playwright.
 * Can extract text content, code blocks, or raw HTML from any webpage.
 *
 * Features:
 * - Direct URL navigation
 * - Code block extraction (for documentation pages)
 * - Main content extraction (removes navigation, ads, etc.)
 * - GitHub README support
 * - Progress notifications to UI
 */
public class WebFetchTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Properties PROPERTIES = loadProperties();
    private static final boolean HEADLESS = Boolean.parseBoolean(PROPERTIES.getProperty("web.fetch.headless", "true"));
    private static final int TIMEOUT_MS = Integer.parseInt(PROPERTIES.getProperty("web.fetch.timeout.ms", "30000"));
    private static final int MAX_CONTENT_LENGTH = Integer.parseInt(PROPERTIES.getProperty("web.fetch.max.content.length", "50000"));

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream input = WebFetchTool.class.getClassLoader().getResourceAsStream("test.properties")) {
            if (input != null) {
                props.load(input);
            }
        } catch (IOException e) {
            // Use defaults
        }
        return props;
    }

    public static ToolDefinition web_fetch() {
        var urlProp = Map.of("type", "string", "description", "URL to fetch content from");
        var selectorProp = Map.of("type", "string", "description", "Optional CSS selector to extract specific content (e.g., 'article', '.content', '#readme')");
        var extractCodeProp = Map.of("type", "boolean", "description", "If true, extract code blocks from the page (useful for documentation)");
        var properties = new LinkedHashMap<String, Object>();
        properties.put("url", urlProp);
        properties.put("selector", selectorProp);
        properties.put("extract_code", extractCodeProp);
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return ToolDefinition.of(
                "web_fetch",
                "Fetch and extract content from a URL. Can extract text, code blocks, or specific sections from documentation pages.",
                schema);
    }

    public static String execute(Map<String, Object> args) {
        WebFetchTool tool = new WebFetchTool();
        return tool.fetch(args);
    }

    String fetch(Map<String, Object> args) {
        String url = (String) args.get("url");
        String selector = (String) args.getOrDefault("selector", null);
        Boolean extractCode = (Boolean) args.getOrDefault("extract_code", false);

        if (url == null || url.isBlank()) {
            return formatError("url is required");
        }

        ToolProgressNotifier.notify("web_fetch", "Navegando a: " + url);

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(HEADLESS)
                .setArgs(java.util.List.of("--no-sandbox")));

            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"));

            Page page = context.newPage();

            try {
                ToolProgressNotifier.notify("web_fetch", "Cargando página...");
                page.navigate(url, new Page.NavigateOptions().setTimeout(TIMEOUT_MS));

                // Wait for page to stabilize
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
                page.waitForTimeout(1000);

                String content;
                String title = page.title();

                if (extractCode != null && extractCode) {
                    ToolProgressNotifier.notify("web_fetch", "Extrayendo bloques de código...");
                    content = extractCodeBlocks(page);
                } else if (selector != null && !selector.isBlank()) {
                    ToolProgressNotifier.notify("web_fetch", "Extrayendo contenido con selector: " + selector);
                    content = extractWithSelector(page, selector);
                } else {
                    ToolProgressNotifier.notify("web_fetch", "Extrayendo contenido principal...");
                    content = extractMainContent(page, url);
                }

                if (content == null || content.isBlank()) {
                    return formatError("No se pudo extraer contenido de la página");
                }

                // Truncate if too long
                if (content.length() > MAX_CONTENT_LENGTH) {
                    content = content.substring(0, MAX_CONTENT_LENGTH) + "\n\n[... contenido truncado ...]";
                }

                ToolProgressNotifier.notify("web_fetch", "Contenido extraído (" + content.length() + " caracteres)");
                return formatSuccess(url, title, content);
            } finally {
                browser.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return formatError("Error al obtener URL: " + e.getMessage());
        }
    }

    private String extractCodeBlocks(Page page) {
        Object result = page.evaluate("""
            () => {
                const codeBlocks = [];

                // Pre tags (most common for code)
                document.querySelectorAll('pre').forEach(pre => {
                    const code = pre.querySelector('code');
                    const text = code ? code.textContent : pre.textContent;
                    if (text && text.trim().length > 0) {
                        const lang = code ? (code.className.match(/language-(\\w+)/) || [])[1] : '';
                        codeBlocks.push({lang: lang || '', code: text.trim()});
                    }
                });

                // Standalone code tags not in pre
                document.querySelectorAll('code:not(pre code)').forEach(code => {
                    const text = code.textContent;
                    if (text && text.trim().length > 50) { // Only significant code blocks
                        codeBlocks.push({lang: '', code: text.trim()});
                    }
                });

                return codeBlocks.map(b => (b.lang ? '```' + b.lang + '\\n' : '```\\n') + b.code + '\\n```').join('\\n\\n');
            }
        """);
        return result != null ? result.toString() : "";
    }

    private String extractWithSelector(Page page, String selector) {
        try {
            Locator element = page.locator(selector).first();
            if (element.count() > 0) {
                return element.textContent();
            }
        } catch (Exception e) {
            // Selector not found
        }
        return null;
    }

    private String extractMainContent(Page page, String url) {
        // Special handling for GitHub
        if (url.contains("github.com")) {
            return extractGitHubContent(page);
        }

        // Generic content extraction
        Object result = page.evaluate("""
            () => {
                // Priority selectors for main content
                const selectors = [
                    'article',
                    'main',
                    '[role="main"]',
                    '.markdown-body',
                    '.content',
                    '.post-content',
                    '.entry-content',
                    '.documentation',
                    '.docs-content',
                    '#content',
                    '#main-content'
                ];

                for (const sel of selectors) {
                    const el = document.querySelector(sel);
                    if (el && el.textContent.trim().length > 100) {
                        return el.innerText || el.textContent;
                    }
                }

                // Fallback: get body text removing nav, header, footer, sidebar
                const body = document.body.cloneNode(true);
                ['nav', 'header', 'footer', 'aside', '.sidebar', '.navigation', '.menu', '.ads', '.advertisement'].forEach(sel => {
                    body.querySelectorAll(sel).forEach(el => el.remove());
                });

                return body.innerText || body.textContent;
            }
        """);
        return result != null ? cleanText(result.toString()) : "";
    }

    private String extractGitHubContent(Page page) {
        Object result = page.evaluate("""
            () => {
                // README content
                const readme = document.querySelector('#readme article') ||
                               document.querySelector('.markdown-body') ||
                               document.querySelector('[data-target="readme-toc.content"]');
                if (readme) {
                    return readme.innerText || readme.textContent;
                }

                // Repository description
                const about = document.querySelector('.f4.my-3');
                const description = about ? about.textContent.trim() : '';

                // Topics
                const topics = Array.from(document.querySelectorAll('.topic-tag')).map(t => t.textContent.trim()).join(', ');

                // File listing (for repos without README visible)
                const files = Array.from(document.querySelectorAll('.js-navigation-item .content a')).map(a => a.textContent.trim()).slice(0, 20);

                let content = '';
                if (description) content += 'Description: ' + description + '\\n\\n';
                if (topics) content += 'Topics: ' + topics + '\\n\\n';
                if (files.length > 0) content += 'Files:\\n' + files.join('\\n');

                return content || document.body.innerText.substring(0, 5000);
            }
        """);
        return result != null ? cleanText(result.toString()) : "";
    }

    private String cleanText(String text) {
        if (text == null) return "";

        // Remove excessive whitespace
        text = text.replaceAll("\\s*\\n\\s*\\n\\s*\\n+", "\n\n");
        text = text.replaceAll("[ \\t]+", " ");
        text = text.trim();

        return text;
    }

    private String formatSuccess(String url, String title, String content) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", true);
            node.put("url", url);
            node.put("title", title != null ? title : "");
            node.put("content", content);
            node.putNull("error");
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new com.boris.exceptions.BorisException("Failed to format output", e);
        }
    }

    private String formatError(String message) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", false);
            node.put("url", "");
            node.put("title", "");
            node.put("content", "");
            node.put("error", message);
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new com.boris.exceptions.BorisException("Failed to format output", e);
        }
    }
}
