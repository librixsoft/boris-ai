package com.boris.queue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class MessageQueueService {

    public interface MessageProcessor {
        void process(ChatMessage message, Consumer<String> onChunk, Runnable onComplete);
    }

    public interface QueueListener {
        void onMessageEnqueued(ChatMessage message, int queueSize);
        void onMessageStarted(ChatMessage message);
        void onMessageCompleted(ChatMessage message);
        void onMessageFailed(ChatMessage message, Exception error);
        void onQueueEmpty();
    }

    private final BlockingQueue<ChatMessage> queue = new LinkedBlockingQueue<>();
    private final CopyOnWriteArrayList<QueueListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private Thread consumerThread;
    private MessageProcessor processor;

    public void setProcessor(MessageProcessor processor) {
        this.processor = processor;
    }

    public void addListener(QueueListener listener) {
        listeners.add(listener);
    }

    public void removeListener(QueueListener listener) {
        listeners.remove(listener);
    }

    public ChatMessage enqueue(String content) {
        ChatMessage message = ChatMessage.create(content);
        queue.offer(message);
        int size = queue.size();
        listeners.forEach(l -> l.onMessageEnqueued(message, size));
        return message;
    }

    public int getQueueSize() {
        return queue.size();
    }

    public boolean isProcessing() {
        return processing.get();
    }

    public boolean hasMessages() {
        return !queue.isEmpty() || processing.get();
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            consumerThread = new Thread(this::consumeLoop, "message-queue-consumer");
            consumerThread.setDaemon(true);
            consumerThread.start();
        }
    }

    public void stop() {
        running.set(false);
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
    }

    public void clear() {
        queue.clear();
        processing.set(false);
        listeners.forEach(QueueListener::onQueueEmpty);
    }

    private void consumeLoop() {
        while (running.get()) {
            try {
                ChatMessage message = queue.take();
                processMessage(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void processMessage(ChatMessage message) {
        if (processor == null) {
            listeners.forEach(l -> l.onMessageFailed(message, new IllegalStateException("No processor configured")));
            return;
        }

        processing.set(true);
        ChatMessage processingMsg = message.withStatus(MessageStatus.PROCESSING);
        listeners.forEach(l -> l.onMessageStarted(processingMsg));

        CountDownLatch latch = new CountDownLatch(1);

        try {
            processor.process(
                processingMsg,
                chunk -> {},
                () -> {
                    processing.set(false);
                    ChatMessage completed = message.withStatus(MessageStatus.COMPLETED);
                    listeners.forEach(l -> l.onMessageCompleted(completed));
                    if (queue.isEmpty()) {
                        listeners.forEach(QueueListener::onQueueEmpty);
                    }
                    latch.countDown();
                }
            );
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            processing.set(false);
        } catch (Exception e) {
            processing.set(false);
            ChatMessage failed = message.withStatus(MessageStatus.FAILED);
            listeners.forEach(l -> l.onMessageFailed(failed, e));
            if (queue.isEmpty()) {
                listeners.forEach(QueueListener::onQueueEmpty);
            }
            latch.countDown();
        }
    }
}
