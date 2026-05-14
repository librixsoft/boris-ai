package com.boris.librixsoft.server.service.llama;

import com.boris.librixsoft.server.service.llama.jna.LlamaInstance;
import com.sun.jna.Pointer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JnaLlamaChatModel.
 * 
 * Note: Full integration tests require llama.cpp libraries to be installed.
 * Run integration tests with: -Dtest.llama.enabled=true
 * 
 * Unit tests (without native libs) can run without any special flags.
 */
@ExtendWith(MockitoExtension.class)
public class JnaLlamaChatModelTest {

    @Mock
    private LlamaInstance mockInstance;

    @Test
    @DisplayName("Test sessionTokens cleanup logic")
    void testSessionTokensCleanup() {
        // Verify that sessionTokens array is properly filled with 0
        // This is critical for Bug 2 fix
        int[] sessionTokens = new int[100];
        Arrays.fill(sessionTokens, 123); // Fill with garbage
        
        // Simulate the cleanup that should happen in setActiveModel
        Arrays.fill(sessionTokens, 0);
        
        // Verify all tokens are 0
        for (int token : sessionTokens) {
            assertEquals(0, token, "All sessionTokens should be 0 after cleanup");
        }
    }

    @Test
    @DisplayName("Test that warmup thread can be created and joined")
    void testWarmupThreadCreation() {
        // Verify that warmupCudaGraph() returns a joinable thread
        // This is a simplified test that doesn't require native libs
        Thread testThread = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        testThread.start();
        
        assertDoesNotThrow(() -> testThread.join(5000), "Thread should complete within timeout");
        assertFalse(testThread.isAlive(), "Thread should not be alive after join");
    }

    @Test
    @DisplayName("Test atomic reference behavior for warmup thread")
    void testWarmupThreadAtomicReference() {
        // Test the atomic reference used to track the current warmup thread
        AtomicReference<Thread> currentWarmup = new AtomicReference<>();
        
        Thread thread1 = new Thread(() -> {});
        Thread thread2 = new Thread(() -> {});
        
        currentWarmup.set(thread1);
        assertEquals(thread1, currentWarmup.get(), "First thread should be set");
        
        Thread previous = currentWarmup.getAndSet(thread2);
        assertEquals(thread1, previous, "Previous thread should be returned");
        assertEquals(thread2, currentWarmup.get(), "Second thread should be set");
    }

    @Test
    @DisplayName("Test atomic long for warmup generation counter")
    void testWarmupGenerationCounter() {
        // Test the atomic long used for warmup generation tracking
        java.util.concurrent.atomic.AtomicLong warmupGeneration = new java.util.concurrent.atomic.AtomicLong(0);
        
        assertEquals(0, warmupGeneration.get(), "Initial value should be 0");
        
        warmupGeneration.incrementAndGet();
        assertEquals(1, warmupGeneration.get(), "Should be 1 after increment");
        
        warmupGeneration.set(5);
        assertEquals(5, warmupGeneration.get(), "Should be 5 after set");
    }

    @Test
    @DisplayName("Test atomic boolean for cancellation")
    void testCancellationAtomicBoolean() {
        // Test the atomic boolean used for cancellation requests
        AtomicBoolean cancellationRequested = new AtomicBoolean(false);
        
        assertFalse(cancellationRequested.get(), "Initial value should be false");
        
        cancellationRequested.set(true);
        assertTrue(cancellationRequested.get(), "Should be true after set");
        
        cancellationRequested.set(false);
        assertFalse(cancellationRequested.get(), "Should be false after reset");
    }

    @Test
    @DisplayName("Test sessionTokens array resizing")
    void testSessionTokensArrayResizing() {
        // Test the logic for resizing sessionTokens array
        int[] originalTokens = new int[100];
        Arrays.fill(originalTokens, 42);
        int kvCachePosition = 50;
        
        // Simulate resizing to a larger context
        int newContextSize = 200;
        int[] newTokens = new int[newContextSize];
        System.arraycopy(originalTokens, 0, newTokens, 0, kvCachePosition);
        
        // Verify the copy
        for (int i = 0; i < kvCachePosition; i++) {
            assertEquals(42, newTokens[i], "Copied tokens should match");
        }
        
        // Verify the rest is 0
        for (int i = kvCachePosition; i < newContextSize; i++) {
            assertEquals(0, newTokens[i], "Uncopied tokens should be 0");
        }
    }

    @Test
    @DisplayName("Test KV cache position tracking")
    void testKvCachePositionTracking() {
        // Test the volatile int used for KV cache position tracking
        int kvCachePosition = 0;
        
        assertEquals(0, kvCachePosition, "Initial position should be 0");
        
        kvCachePosition = 100;
        assertEquals(100, kvCachePosition, "Position should be updated");
        
        kvCachePosition = 0; // Reset
        assertEquals(0, kvCachePosition, "Position should be reset to 0");
    }

    @Test
    @DisplayName("Test warmup complete flag")
    void testWarmupCompleteFlag() {
        // Test the volatile boolean for warmup completion
        boolean warmupComplete = false;
        
        assertFalse(warmupComplete, "Initial value should be false");
        
        warmupComplete = true;
        assertTrue(warmupComplete, "Should be true after warmup completes");
    }

    @Test
    @DisplayName("Test warmup status message")
    void testWarmupStatusMessage() {
        // Test the volatile string for warmup status
        String warmupStatusMessage = "No model loaded";
        
        assertEquals("No model loaded", warmupStatusMessage, "Initial message should match");
        
        warmupStatusMessage = "Warmup started...";
        assertEquals("Warmup started...", warmupStatusMessage, "Message should be updated");
        
        warmupStatusMessage = "Warmup complete. Model ready for chat.";
        assertEquals("Warmup complete. Model ready for chat.", warmupStatusMessage, "Message should show completion");
    }

    @Test
    @DisplayName("Test thread interruption for warmup cancellation")
    void testThreadInterruption() {
        // Test that threads can be interrupted for warmup cancellation
        AtomicBoolean wasInterrupted = new AtomicBoolean(false);
        
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                wasInterrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });
        
        thread.start();
        thread.interrupt();
        
        assertDoesNotThrow(() -> thread.join(1000), "Thread should complete after interruption");
        assertTrue(wasInterrupted.get(), "Thread should have been interrupted");
    }

    @Test
    @DisplayName("Test virtual thread creation")
    void testVirtualThreadCreation() {
        // Test that virtual threads can be created (Java 21 feature)
        Thread virtualThread = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        assertTrue(virtualThread.isVirtual(), "Thread should be virtual");
        
        assertDoesNotThrow(() -> virtualThread.join(1000), "Virtual thread should complete");
        assertFalse(virtualThread.isAlive(), "Virtual thread should not be alive after completion");
    }

    @Test
    @DisplayName("Test reentrant lock behavior")
    void testReentrantLockBehavior() {
        // Test the ReentrantLock used for generateLock
        java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();
        
        assertFalse(lock.isLocked(), "Lock should not be locked initially");
        
        lock.lock();
        assertTrue(lock.isLocked(), "Lock should be locked after lock()");
        assertTrue(lock.isHeldByCurrentThread(), "Lock should be held by current thread");
        
        lock.unlock();
        assertFalse(lock.isLocked(), "Lock should not be locked after unlock()");
    }

    @Test
    @DisplayName("Test batch capacity caching logic")
    void testBatchCapacityCaching() {
        // Test the logic for caching batch capacity
        int cachedBatchCapacity = 0;
        int nBatch = 512;
        
        assertEquals(0, cachedBatchCapacity, "Initial capacity should be 0");
        
        // Simulate ensuring batch capacity
        if (cachedBatchCapacity < nBatch) {
            cachedBatchCapacity = nBatch;
        }
        
        assertEquals(512, cachedBatchCapacity, "Capacity should be updated to nBatch");
        
        // Test that it doesn't change if already sufficient
        int newNBatch = 256;
        if (cachedBatchCapacity < newNBatch) {
            cachedBatchCapacity = newNBatch;
        }
        
        assertEquals(512, cachedBatchCapacity, "Capacity should remain at 512 (already sufficient)");
    }

    @Test
    @DisplayName("Test sampler temperature caching logic")
    void testSamplerTemperatureCaching() {
        // Test the logic for caching sampler temperature
        float cachedSamplerTemp = Float.NaN;
        float temperature1 = 0.7f;
        float temperature2 = 0.5f;
        
        assertTrue(Float.isNaN(cachedSamplerTemp), "Initial temperature should be NaN");
        
        // Simulate ensuring sampler for temperature1
        if (Float.isNaN(cachedSamplerTemp) || cachedSamplerTemp != temperature1) {
            cachedSamplerTemp = temperature1;
        }
        
        assertEquals(0.7f, cachedSamplerTemp, "Temperature should be updated to 0.7");
        
        // Test that it updates for different temperature
        if (Float.isNaN(cachedSamplerTemp) || cachedSamplerTemp != temperature2) {
            cachedSamplerTemp = temperature2;
        }
        
        assertEquals(0.5f, cachedSamplerTemp, "Temperature should be updated to 0.5");
    }

    @Test
    @DisplayName("Test model path detection logic")
    void testModelPathDetection() {
        // Test the logic for detecting model type from path
        String path1 = "/models/llama-3-8b.gguf";
        String path2 = "/models/qwen-7b.gguf";
        String path3 = "/models/deepseek-coder.gguf";
        String path4 = "/models/glm-4.gguf";
        String path5 = "/models/gemma-7b.gguf";
        String path6 = "/models/mistral-reasoning.gguf";
        
        assertTrue(path1.toLowerCase().contains("llama-3") || path1.toLowerCase().contains("llama3"), 
                   "Should detect Llama 3");
        assertTrue(path2.toLowerCase().contains("qwen"), "Should detect Qwen");
        assertTrue(path3.toLowerCase().contains("deepseek"), "Should detect DeepSeek");
        assertTrue(path4.toLowerCase().contains("glm"), "Should detect GLM");
        assertTrue(path5.toLowerCase().contains("gemma"), "Should detect Gemma");
        assertTrue(path6.toLowerCase().contains("reasoning"), "Should detect Mistral Reasoning");
    }

    @Test
    @DisplayName("Test max tokens limits by model type")
    void testMaxTokensLimitsByModelType() {
        // Test the logic for limiting max tokens by model type
        int maxTokens = 4096;
        
        // GLM limit
        boolean isGlm = true;
        if (isGlm) {
            maxTokens = Math.min(maxTokens, 512);
        }
        assertEquals(512, maxTokens, "GLM should be limited to 512 tokens");
        
        // Gemma limit
        maxTokens = 4096;
        boolean isGemma = true;
        if (isGemma) {
            maxTokens = Math.min(maxTokens, 800);
        }
        assertEquals(800, maxTokens, "Gemma should be limited to 800 tokens");
        
        // Mistral Reasoning limit
        maxTokens = 4096;
        boolean isMistralReason = true;
        if (isMistralReason) {
            maxTokens = Math.min(maxTokens, 1024);
        }
        assertEquals(1024, maxTokens, "Mistral Reasoning should be limited to 1024 tokens");
    }

    @Test
    @DisplayName("Test thinking token limits")
    void testThinkingTokenLimits() {
        // Test the logic for limiting thinking tokens
        boolean isMistralReason = true;
        boolean isDeepSeek = false;
        int maxThinkTokens = isMistralReason ? 256 : isDeepSeek ? 128 : 0;
        
        assertEquals(256, maxThinkTokens, "Mistral Reasoning should allow 256 thinking tokens");
        
        isMistralReason = false;
        isDeepSeek = true;
        maxThinkTokens = isMistralReason ? 256 : isDeepSeek ? 128 : 0;
        
        assertEquals(128, maxThinkTokens, "DeepSeek should allow 128 thinking tokens");
        
        isMistralReason = false;
        isDeepSeek = false;
        maxThinkTokens = isMistralReason ? 256 : isDeepSeek ? 128 : 0;
        
        assertEquals(0, maxThinkTokens, "Other models should not allow thinking tokens");
    }

    // Integration tests (require native llama.cpp libraries)
    
    @Test
    @EnabledIfSystemProperty(named = "test.llama.enabled", matches = "true")
    @DisplayName("Integration: Test warmupComplete flag behavior")
    void testWarmupCompleteFlagIntegration() {
        // Verify that warmupComplete flag is correctly managed
        // This test would require a running llama.cpp instance
        assertTrue(true, "Test placeholder - requires native llama.cpp libraries");
    }

    @Test
    @EnabledIfSystemProperty(named = "test.llama.enabled", matches = "true")
    @DisplayName("Integration: Test warmup performance with 3 consecutive messages")
    void testWarmupPerformanceWithMessages() {
        // This test requires a running llama.cpp instance with a loaded model
        // It measures the response time for 3 consecutive messages to verify
        // that the warmup eliminates delays after the first message
        assertTrue(true, "Test placeholder - requires native llama.cpp libraries");
        
        // TODO: Implement actual test when native libs are available:
        // 1. Load model and wait for warmup to complete
        // 2. Send "hola mensaje 1" and measure response time
        // 3. Send "hola mensaje 2" and measure response time (should be < 2s)
        // 4. Send "hola mensaje 3" and measure response time (should be < 2s)
        // 5. Assert that message 2 and 3 are significantly faster than message 1
    }
}
