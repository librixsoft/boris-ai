package com.boris.task;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class TaskQueue {

    private final ArrayDeque<SubTask> items = new ArrayDeque<>();

    public synchronized void enqueue(SubTask task) {
        if (task == null) {
            throw new com.boris.exceptions.BorisException("Cannot enqueue null SubTask");
        }
        items.addLast(task);
    }

    public synchronized SubTask peek() {
        return items.peekFirst();
    }

    public synchronized SubTask poll() {
        return items.pollFirst();
    }

    public synchronized boolean isEmpty() {
        return items.isEmpty();
    }

    public synchronized int size() {
        return items.size();
    }

    public synchronized List<SubTask> snapshot() {
        return new ArrayList<>(items);
    }

    public synchronized void clear() {
        items.clear();
    }
}
