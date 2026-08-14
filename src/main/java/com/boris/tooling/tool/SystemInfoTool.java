package com.boris.tooling.tool;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.InetAddress;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.boris.tooling.ToolDefinition;

public class SystemInfoTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ToolDefinition get_system_info() {
        return ToolDefinition.of(
                "get_system_info",
                "Get system information including OS name, memory, CPU cores, and hostname.");
    }

    public static String get_system_info(Map<String, Object> args) {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        String osName = System.getProperty("os.name", "unknown");
        String osVersion = System.getProperty("os.version", "unknown");
        String osArch = System.getProperty("os.arch", "unknown");

        long totalMemory = 0;
        long freeMemory = 0;
        try {
            totalMemory = getTotalStorageBytes();
            freeMemory = getFreeStorageBytes();
        } catch (Exception e) {
            throw new com.boris.exceptions.BorisException("Failed to get storage information", e);
        }

        int availableProcessors = Runtime.getRuntime().availableProcessors();

        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            throw new com.boris.exceptions.BorisException("Failed to get hostname", e);
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("os", osName);
        info.put("os_version", osVersion);
        info.put("arch", osArch);
        info.put("hostname", hostname);
        info.put("available_processors", availableProcessors);
        info.put("total_storage_bytes", totalMemory);
        info.put("free_storage_bytes", freeMemory);

        try {
            return MAPPER.writeValueAsString(info);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private static long getTotalStorageBytes() throws Exception {
        long total = 0;
        Iterable<FileStore> stores = java.nio.file.FileSystems.getDefault().getFileStores();
        for (FileStore store : stores) {
            total += store.getTotalSpace();
        }
        return total;
    }

    private static long getFreeStorageBytes() throws Exception {
        long free = 0;
        Iterable<FileStore> stores = java.nio.file.FileSystems.getDefault().getFileStores();
        for (FileStore store : stores) {
            free += store.getUsableSpace();
        }
        return free;
    }
}
