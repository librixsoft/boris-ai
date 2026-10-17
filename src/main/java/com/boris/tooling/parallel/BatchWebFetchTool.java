package com.boris.tooling.parallel;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.boris.task.TaskAborter;
import com.boris.tooling.ToolProgressNotifier;

/**
 * Batch web fetch tool using Playwright with PARALLEL URL fetching.
 * Fetches multiple URLs simultaneously using Virtual Threads.
 *
 * Features:
 * - Parallel fetching of multiple URLs
 * - Single URL works exactly like old WebFetchTool
 * - Code block extraction for documentation
 * - Main content extraction with smart selectors
 * - GitHub README support
 * - Progress notifications to UI
 */
public class BatchWebFetchTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Properties PROPERTIES = loadProperties();
    private static final boolean HEADLESS = Boolean.parseBoolean(PROPERTIES.getProperty("web.fetch.headless", "true"));
    private static final int TIMEOUT_MS = Integer.parseInt(PROPERTIES.getProperty("web.fetch.timeout.ms", "30000"));
    private static final int MAX_CONTENT_LENGTH = Integer.parseInt(PROPERTIES.getProperty("web.fetch.max.content.length", "50000"));
    private static final int PARALLEL_TIMEOUT_MS = 45000;

    public record FetchSpec(String url, String selector, boolean extractCode) {}
    public record FetchResult(String url, boolean success, String title, String content, String error) {}

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream input = BatchWebFetchTool.class.getClassLoader().getResourceAsStream("test.properties")) {
            if (input != null) {
                props.load(input);
            }
        } catch (IOException e) {
            // Use defaults
        }
        return props;
    }

    public String execute(List<FetchSpec> specs, TaskAborter aborter) {
        if (specs == null || specs.isEmpty()) {
            return formatError("urls list is empty");
        }

        List<FetchSpec> uniqueSpecs = specs.stream().distinct().toList();
        int totalUrls = uniqueSpecs.size();

        if (totalUrls == 1) {
            ToolProgressNotifier.notify("web_fetch", "Navegando a: " + uniqueSpecs.get(0).url());
        } else {
            ToolProgressNotifier.notify("web_fetch", "Fetching " + totalUrls + " URLs en paralelo...");
        }

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<FetchResult>> futures = new ArrayList<>();

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(HEADLESS)
                .setArgs(java.util.List.of("--no-sandbox")));

            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"));

            try {
                for (FetchSpec spec : uniqueSpecs) {
                    Page page = context.newPage();
                    futures.add(executor.submit(() -> fetchUrl(page, spec, aborter)));
                }

                List<FetchResult> results = new ArrayList<>();
                int successCount = 0;
                int failCount = 0;

                for (Future<FetchResult> future : futures) {
                    try {
                        FetchResult result = future.get(PARALLEL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        results.add(result);
                        if (result.success()) {
                            successCount++;
                        } else {
                            failCount++;
                        }
                    } catch (Exception e) {
                        results.add(new FetchResult("unknown", false, "", "", "Fetch timed out or failed: " + e.getMessage()));
                        failCount++;
                    }
                }

                if (totalUrls == 1) {
                    FetchResult single = results.get(0);
                    if (single.success()) {
                        ToolProgressNotifier.notify("web_fetch", "Contenido extraído (" + single.content().length() + " caracteres)");
                    }
                    return formatSingleResult(single);
                } else {
                    ToolProgressNotifier.notify("web_fetch", "Completado: " + successCount + " exitosos, " + failCount + " fallidos");
                    return formatBatchResults(results, successCount, failCount);
                }
            } finally {
                browser.close();
            }
        } catch (Exception e) {
            return formatError("Error en fetch: " + e.getMessage());
        } finally {
            executor.shutdown();
        }
    }

    public String execute(List<FetchSpec> specs) {
        return execute(specs, null);
    }

    private FetchResult fetchUrl(Page page, FetchSpec spec, TaskAborter aborter) {
        if (aborter != null && aborter.isAborted()) {
            return new FetchResult(spec.url(), false, "", "", "Aborted");
        }

        try {
            page.navigate(spec.url(), new Page.NavigateOptions().setTimeout(TIMEOUT_MS));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(1000);

            String title = page.title();
            String content;

            if (spec.extractCode()) {
                content = extractCodeBlocks(page);
            } else if (spec.selector() != null && !spec.selector().isBlank()) {
                content = extractWithSelector(page, spec.selector());
            } else {
                content = extractMainContent(page, spec.url());
            }

            if (content == null || content.isBlank()) {
                return new FetchResult(spec.url(), false, title, "", "No se pudo extraer contenido");
            }

            if (content.length() > MAX_CONTENT_LENGTH) {
                content = content.substring(0, MAX_CONTENT_LENGTH) + "\n\n[... contenido truncado ...]";
            }

            return new FetchResult(spec.url(), true, title, content, null);
        } catch (Exception e) {
            return new FetchResult(spec.url(), false, "", "", "Error: " + e.getMessage());
        }
    }

    private String extractCodeBlocks(Page page) {
        Object result = page.evaluate("""
            () => {
                const codeBlocks = [];

                document.querySelectorAll('pre').forEach(pre => {
                    const code = pre.querySelector('code');
                    const text = code ? code.textContent : pre.textContent;
                    if (text && text.trim().length > 0) {
                        const lang = code ? (code.className.match(/language-(\\w+)/) || [])[1] : '';
                        codeBlocks.push({lang: lang || '', code: text.trim()});
                    }
                });

                document.querySelectorAll('code:not(pre code)').forEach(code => {
                    const text = code.textContent;
                    if (text && text.trim().length > 50) {
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
        if (url.contains("github.com")) {
            return extractGitHubContent(page);
        }

        Object result = page.evaluate("""
            () => {
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
                const readme = document.querySelector('#readme article') ||
                               document.querySelector('.markdown-body') ||
                               document.querySelector('[data-target="readme-toc.content"]');
                if (readme) {
                    return readme.innerText || readme.textContent;
                }

                const about = document.querySelector('.f4.my-3');
                const description = about ? about.textContent.trim() : '';

                const topics = Array.from(document.querySelectorAll('.topic-tag')).map(t => t.textContent.trim()).join(', ');

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
        text = text.replaceAll("\\s*\\n\\s*\\n\\s*\\n+", "\n\n");
        text = text.replaceAll("[ \\t]+", " ");
        text = text.trim();
        return text;
    }

    private String formatSingleResult(FetchResult result) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", result.success());
            node.put("url", result.url());
            node.put("title", result.title() != null ? result.title() : "");
            node.put("content", result.content());
            if (result.error() != null) {
                node.put("error", result.error());
            } else {
                node.putNull("error");
            }
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new com.boris.exceptions.BorisException("Failed to format output", e);
        }
    }

    private String formatBatchResults(List<FetchResult> results, int successCount, int failCount) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("totalUrls", results.size());
            root.put("successfulFetches", successCount);
            root.put("failedFetches", failCount);

            ArrayNode urlsArray = root.putArray("results");
            for (FetchResult r : results) {
                ObjectNode item = urlsArray.addObject();
                item.put("url", r.url());
                item.put("success", r.success());
                item.put("title", r.title() != null ? r.title() : "");
                if (r.success()) {
                    item.put("content", r.content());
                } else {
                    item.put("error", r.error());
                }
            }

            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new com.boris.exceptions.BorisException("Failed to format output", e);
        }
    }

    private String formatError(String message) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", false);
            node.put("error", message);
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new com.boris.exceptions.BorisException("Failed to format output", e);
        }
    }

    public void shutdown() {
        // No persistent executor to shut down
    }
}
