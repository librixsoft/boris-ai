package com.boris.ui.automation;

import java.nio.file.Path;
import java.util.List;

import com.boris.exceptions.BrowserLaunchFailedException;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UiBrowserSessionManagerTest {

    private static final Path TEST_PROFILE_DIR = Path.of("/tmp/boris-test-profile");

    @Test
    void constructorNullProfileDirThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
            new UiBrowserSessionManager(null, 1440, 900));
    }

    @Test
    void getOrCreatePageLaunchFailureThrowsBrowserLaunchFailedException() {
        try (MockedStatic<Playwright> mocked = Mockito.mockStatic(Playwright.class)) {
            Playwright mockPlaywright = mock(Playwright.class);
            BrowserType mockChromium = mock(BrowserType.class);

            mocked.when(Playwright::create).thenReturn(mockPlaywright);
            when(mockPlaywright.chromium()).thenReturn(mockChromium);
            when(mockChromium.launchPersistentContext(
                    any(Path.class),
                    any(BrowserType.LaunchPersistentContextOptions.class)))
                .thenThrow(new PlaywrightException("profile locked"));

            UiBrowserSessionManager manager = new UiBrowserSessionManager(TEST_PROFILE_DIR, 1440, 900);

            assertThrows(BrowserLaunchFailedException.class, () ->
                manager.getOrCreatePage(null));
            verify(mockPlaywright).close();
        }
    }

    @Test
    void getOrCreatePageWithoutDomainLaunchesOnceAndReturnsSamePage() {
        try (MockedStatic<Playwright> mocked = Mockito.mockStatic(Playwright.class)) {
            Playwright mockPlaywright = mock(Playwright.class);
            BrowserType mockChromium = mock(BrowserType.class);
            BrowserContext mockContext = mock(BrowserContext.class);
            Page mockPage = mock(Page.class);

            mocked.when(Playwright::create).thenReturn(mockPlaywright);
            when(mockPlaywright.chromium()).thenReturn(mockChromium);
            when(mockChromium.launchPersistentContext(
                    any(Path.class),
                    any(BrowserType.LaunchPersistentContextOptions.class)))
                .thenReturn(mockContext);
            when(mockContext.pages()).thenReturn(List.of(mockPage));

            UiBrowserSessionManager manager = new UiBrowserSessionManager(TEST_PROFILE_DIR, 1440, 900);

            Page result = manager.getOrCreatePage(null);

            assertSame(mockPage, result);
            verify(mockChromium, times(1)).launchPersistentContext(
                any(Path.class),
                any(BrowserType.LaunchPersistentContextOptions.class));
            verify(mockPage).bringToFront();
        }
    }

    @Test
    void getOrCreatePageCalledTwiceReusesSingleLaunch() {
        try (MockedStatic<Playwright> mocked = Mockito.mockStatic(Playwright.class)) {
            Playwright mockPlaywright = mock(Playwright.class);
            BrowserType mockChromium = mock(BrowserType.class);
            BrowserContext mockContext = mock(BrowserContext.class);
            Page mockPage = mock(Page.class);

            mocked.when(Playwright::create).thenReturn(mockPlaywright);
            when(mockPlaywright.chromium()).thenReturn(mockChromium);
            when(mockChromium.launchPersistentContext(
                    any(Path.class),
                    any(BrowserType.LaunchPersistentContextOptions.class)))
                .thenReturn(mockContext);
            when(mockContext.pages()).thenReturn(List.of(mockPage));

            UiBrowserSessionManager manager = new UiBrowserSessionManager(TEST_PROFILE_DIR, 1440, 900);

            Page first = manager.getOrCreatePage(null);
            Page second = manager.getOrCreatePage(null);

            assertSame(first, second);
            verify(mockChromium, times(1)).launchPersistentContext(
                any(Path.class),
                any(BrowserType.LaunchPersistentContextOptions.class));
        }
    }

    @Test
    void getOrCreatePageWithDomainNavigatesWhenCurrentUrlDoesNotMatch() {
        try (MockedStatic<Playwright> mocked = Mockito.mockStatic(Playwright.class)) {
            Playwright mockPlaywright = mock(Playwright.class);
            BrowserType mockChromium = mock(BrowserType.class);
            BrowserContext mockContext = mock(BrowserContext.class);
            Page mockPage = mock(Page.class);

            mocked.when(Playwright::create).thenReturn(mockPlaywright);
            when(mockPlaywright.chromium()).thenReturn(mockChromium);
            when(mockChromium.launchPersistentContext(
                    any(Path.class),
                    any(BrowserType.LaunchPersistentContextOptions.class)))
                .thenReturn(mockContext);
            when(mockContext.pages()).thenReturn(List.of(mockPage));
            when(mockPage.url()).thenReturn("https://example.com");

            UiBrowserSessionManager manager = new UiBrowserSessionManager(TEST_PROFILE_DIR, 1440, 900);

            assertSame(mockPage, manager.getOrCreatePage("linkedin.com"));
            verify(mockPage).navigate("https://linkedin.com");
        }
    }

    @Test
    void getOrCreatePageWithDomainAlreadyMatchingDoesNotNavigate() {
        try (MockedStatic<Playwright> mocked = Mockito.mockStatic(Playwright.class)) {
            Playwright mockPlaywright = mock(Playwright.class);
            BrowserType mockChromium = mock(BrowserType.class);
            BrowserContext mockContext = mock(BrowserContext.class);
            Page mockPage = mock(Page.class);

            mocked.when(Playwright::create).thenReturn(mockPlaywright);
            when(mockPlaywright.chromium()).thenReturn(mockChromium);
            when(mockChromium.launchPersistentContext(
                    any(Path.class),
                    any(BrowserType.LaunchPersistentContextOptions.class)))
                .thenReturn(mockContext);
            when(mockContext.pages()).thenReturn(List.of(mockPage));
            when(mockPage.url()).thenReturn("https://www.linkedin.com/feed");

            UiBrowserSessionManager manager = new UiBrowserSessionManager(TEST_PROFILE_DIR, 1440, 900);

            assertSame(mockPage, manager.getOrCreatePage("linkedin.com"));
            verify(mockPage, never()).navigate(anyString());
        }
    }

    @Test
    void getOrCreatePageWithoutDomainDoesNotNavigate() {
        try (MockedStatic<Playwright> mocked = Mockito.mockStatic(Playwright.class)) {
            Playwright mockPlaywright = mock(Playwright.class);
            BrowserType mockChromium = mock(BrowserType.class);
            BrowserContext mockContext = mock(BrowserContext.class);
            Page mockPage = mock(Page.class);

            mocked.when(Playwright::create).thenReturn(mockPlaywright);
            when(mockPlaywright.chromium()).thenReturn(mockChromium);
            when(mockChromium.launchPersistentContext(
                    any(Path.class),
                    any(BrowserType.LaunchPersistentContextOptions.class)))
                .thenReturn(mockContext);
            when(mockContext.pages()).thenReturn(List.of(mockPage));

            UiBrowserSessionManager manager = new UiBrowserSessionManager(TEST_PROFILE_DIR, 1440, 900);

            manager.getOrCreatePage(null);

            verify(mockPage, never()).navigate(anyString());
        }
    }

    @Test
    void closeClosesContextAndPlaywright() {
        try (MockedStatic<Playwright> mocked = Mockito.mockStatic(Playwright.class)) {
            Playwright mockPlaywright = mock(Playwright.class);
            BrowserType mockChromium = mock(BrowserType.class);
            BrowserContext mockContext = mock(BrowserContext.class);
            Page mockPage = mock(Page.class);

            mocked.when(Playwright::create).thenReturn(mockPlaywright);
            when(mockPlaywright.chromium()).thenReturn(mockChromium);
            when(mockChromium.launchPersistentContext(
                    any(Path.class),
                    any(BrowserType.LaunchPersistentContextOptions.class)))
                .thenReturn(mockContext);
            when(mockContext.pages()).thenReturn(List.of(mockPage));

            UiBrowserSessionManager manager = new UiBrowserSessionManager(TEST_PROFILE_DIR, 1440, 900);
            manager.getOrCreatePage(null);
            manager.close();

            verify(mockContext).close();
            verify(mockPlaywright).close();
        }
    }
}
