package com.boris.e2e;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for Playwright-based web search.
 * This test is disabled by default and can be enabled when needed.
 */
@EnabledIf("isE2ETestEnabled")
public class PlaywrightSearchE2ETest {

    @Test
    void playwrightBingSearch_retrievesResults() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)  // Back to headless now that it works
                .setArgs(java.util.List.of("--no-sandbox")));
            Page page = browser.newPage();
            
            try {
                System.out.println("Navigating to Bing...");
                page.navigate("https://www.bing.com/search?q=java+programming&count=5", 
                    new Page.NavigateOptions().setTimeout(15000));
                
                System.out.println("Page loaded, waiting for results...");
                page.waitForSelector("li.b_algo", new Page.WaitForSelectorOptions().setTimeout(10000));
                
                System.out.println("Extracting results...");
                Object resultObj = page.evaluate("""
                    () => {
                        const items = document.querySelectorAll('li.b_algo');
                        return Array.from(items).slice(0, 5).map(item => {
                            const link = item.querySelector('a');
                            const title = link ? link.textContent : '';
                            const url = link ? link.href : '';
                            const snippetEl = item.querySelector('.b_caption');
                            const snippet = snippetEl ? snippetEl.textContent : '';
                            return { title, url, snippet };
                        });
                    }
                """);
                
                @SuppressWarnings("unchecked")
                List<Map<String, String>> results = (List<Map<String, String>>) resultObj;
                
                System.out.println("Results found: " + results.size());
                
                assertFalse(results.isEmpty(), "Should find at least one result");
                assertTrue(results.size() <= 5, "Should not exceed 5 results");
                
                for (Map<String, String> result : results) {
                    System.out.println("Title: " + result.get("title"));
                    System.out.println("URL: " + result.get("url"));
                    System.out.println("Snippet: " + result.get("snippet"));
                    System.out.println("---");
                    
                    assertFalse(result.get("title").isEmpty(), "Title should not be empty");
                    assertFalse(result.get("url").isEmpty(), "URL should not be empty");
                }
                
            } finally {
                browser.close();
            }
        }
    }

    static boolean isE2ETestEnabled() {
        String enabled = System.getProperty("enableE2ETests", System.getenv().getOrDefault("ENABLE_E2E_TESTS", "false"));
        return Boolean.parseBoolean(enabled);
    }
}