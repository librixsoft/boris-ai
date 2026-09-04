package com.boris.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import com.boris.chat.ChatService;

/**
 * BorisUI — minimal terminal interface.
 * No JLine, no colors, no fancy UI. Just stdin/stdout.
 */
public class BorisUIMinimal {

    private final ChatService chatService;
    private final BufferedReader reader;

    public BorisUIMinimal(String settingsPath) throws Exception {
        this.reader = new BufferedReader(new InputStreamReader(System.in));
        this.chatService = ChatService.withTools(settingsPath, "boris");
    }

    public void start() throws Exception {
        System.out.println("boris");
        System.out.println();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                reader.close();
            } catch (Exception ignored) {}
        }));

        try {
            while (true) {
                System.out.print("> ");
                System.out.flush();

                String input = reader.readLine();
                if (input == null) {
                    break;
                }
                input = input.trim();
                if (input.isEmpty()) {
                    continue;
                }

                System.out.println();

                // Stream response
                AtomicBoolean firstChunk = new AtomicBoolean(true);
                AtomicReference<String> responseRef = new AtomicReference<>(null);
                AtomicReference<Exception> errorRef = new AtomicReference<>(null);
                StringBuilder fullResponse = new StringBuilder();
                CountDownLatch streamDone = new CountDownLatch(1);
                String finalInput = input;

                Thread taskThread = new Thread(() -> {
                    try {
                        chatService.sendMessageStream(
                            finalInput,
                            chunk -> {
                                if (chunk != null && !chunk.isEmpty()) {
                                    if (firstChunk.compareAndSet(true, false)) {
                                        // No label, just print chunks
                                    }
                                    synchronized (fullResponse) {
                                        fullResponse.append(chunk);
                                    }
                                    System.out.print(chunk);
                                    System.out.flush();
                                }
                            },
                            () -> {
                                synchronized (fullResponse) {
                                    responseRef.set(fullResponse.toString());
                                }
                                System.out.println();
                                streamDone.countDown();
                            }
                        );
                    } catch (Exception e) {
                        errorRef.set(e);
                        streamDone.countDown();
                    }
                });

                taskThread.setDaemon(true);
                taskThread.start();

                // Wait for completion
                boolean finished = false;
                while (!finished) {
                    finished = streamDone.await(100, TimeUnit.MILLISECONDS);
                }

                if (errorRef.get() != null) {
                    throw errorRef.get();
                }

                String response = responseRef.get();
                if (response != null) {
                    var fallbackResults = com.boris.tooling.fallback.ToolFallbackHandler.handleFallback(response);
                    for (var res : fallbackResults) {
                        if (res.executed()) {
                            if (res.success()) {
                                System.out.println("⚡ [Fallback Tool: " + res.toolName() + "] " + res.message());
                            } else {
                                System.out.println("✗ [Fallback Tool Error: " + res.toolName() + "] " + res.message());
                            }
                        }
                    }

                    if (ChatService.EXIT_COMMAND.equals(response)) {
                        break;
                    }
                }

                System.out.println();
            }
        } finally {
            reader.close();
        }
    }
}