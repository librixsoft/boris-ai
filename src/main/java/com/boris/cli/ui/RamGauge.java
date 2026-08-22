package com.boris.cli.ui;

import java.lang.management.ManagementFactory;
import java.util.function.LongSupplier;

public class RamGauge {

    private static final int BAR_WIDTH = 10;
    private static final long REFRESH_INTERVAL_MS = 2000;
    private static final double CHARS_PER_TOKEN = 3.5d;
    private static final int BYTES_PER_CHAR = 2;

    private final LongSupplier persistedTokensSupplier;
    private final long totalRamBytes;
    private final long capacityTokens;

    private volatile long cachedPersistedTokens;
    private volatile long lastRefreshMs;

    public RamGauge(LongSupplier persistedTokensSupplier) {
        if (persistedTokensSupplier == null) {
            throw new IllegalArgumentException("persistedTokensSupplier is required");
        }
        this.persistedTokensSupplier = persistedTokensSupplier;
        com.sun.management.OperatingSystemMXBean os =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        this.totalRamBytes = os.getTotalMemorySize();
        long freeRamBytes = Math.max(0, os.getFreeMemorySize());
        double bytesPerToken = CHARS_PER_TOKEN * BYTES_PER_CHAR;
        this.capacityTokens = (long) Math.floor(freeRamBytes / bytesPerToken);
        this.cachedPersistedTokens = persistedTokensSupplier.getAsLong();
        this.lastRefreshMs = System.currentTimeMillis();
    }

    public long capacityTokens() {
        return capacityTokens;
    }

    public long remainingTokens() {
        return Math.max(0, capacityTokens - persistedTokens());
    }

    public long persistedTokens() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshMs >= REFRESH_INTERVAL_MS) {
            cachedPersistedTokens = persistedTokensSupplier.getAsLong();
            lastRefreshMs = now;
        }
        return cachedPersistedTokens;
    }

    public String render() {
        String bar = buildBar(persistedTokens());
        return bar + " (aprox " + formatCompact(remainingTokens()) + " tokens / " + formatGb(totalRamBytes) + " gb ram)";
    }

    private String buildBar(long used) {
        int filled = 0;
        if (capacityTokens > 0) {
            double ratio = Math.min(1.0d, Math.max(0.0d, (double) used / (double) capacityTokens));
            filled = (int) Math.round(ratio * BAR_WIDTH);
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < BAR_WIDTH; i++) {
            sb.append(i < filled ? '█' : '░');
        }
        sb.append("]");
        return sb.toString();
    }

    private String formatCompact(long tokens) {
        if (tokens >= 1_000_000_000L) {
            return trimOneDecimal(tokens / 1_000_000_000.0d) + "G";
        }
        if (tokens >= 1_000_000L) {
            return trimOneDecimal(tokens / 1_000_000.0d) + "M";
        }
        if (tokens >= 1_000L) {
            return trimOneDecimal(tokens / 1_000.0d) + "k";
        }
        return String.valueOf(tokens);
    }

    private String trimOneDecimal(double value) {
        long rounded = Math.round(value * 10);
        if (rounded % 10 == 0) {
            return String.valueOf(rounded / 10);
        }
        return String.valueOf(rounded / 10.0);
    }

    private String formatGb(long bytes) {
        return String.valueOf(bytes / (1024L * 1024L * 1024L));
    }
}
