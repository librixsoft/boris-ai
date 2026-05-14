package com.boris.librixsoft.server.service;

import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.util.PathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlamaServerDownloadService {

    private final BorisProperties borisProperties;
    private final HardwareService hardwareService;

    public void downloadAndExtract() throws Exception {
        String core = borisProperties.getLlamaServerCore();
        if (core == null || core.isEmpty()) {
            throw new IllegalArgumentException("llama-server-core is not configured");
        }

        String osName = System.getProperty("os.name").toLowerCase();
        Map<String, Object> gpuInfo = hardwareService.getGpuInfo();
        String gpuName = gpuInfo.get("name").toString().toLowerCase();
        
        java.util.List<String> downloadUrls = new java.util.ArrayList<>();
        boolean isZip = true;
        boolean hasGpu = !gpuName.equals("n/a") && !gpuName.equals("unknown");

        if (osName.contains("win")) {
            if (hasGpu) {
                downloadUrls.add("https://github.com/ggml-org/llama.cpp/releases/download/" + core + "/llama-" + core + "-bin-win-vulkan-x64.zip");
            } else {
                downloadUrls.add("https://github.com/ggml-org/llama.cpp/releases/download/" + core + "/llama-" + core + "-bin-win-cpu-x64.zip");
            }
        } else if (osName.contains("mac")) {
            String arch = System.getProperty("os.arch").toLowerCase();
            if (arch.contains("aarch64") || arch.contains("arm")) {
                downloadUrls.add("https://github.com/ggml-org/llama.cpp/releases/download/" + core + "/llama-" + core + "-bin-macos-arm64.tar.gz");
            } else {
                downloadUrls.add("https://github.com/ggml-org/llama.cpp/releases/download/" + core + "/llama-" + core + "-bin-macos-x64.tar.gz");
            }
            isZip = false;
        } else {
            if (hasGpu) {
                downloadUrls.add("https://github.com/ggml-org/llama.cpp/releases/download/" + core + "/llama-" + core + "-bin-ubuntu-vulkan-x64.tar.gz");
            } else {
                downloadUrls.add("https://github.com/ggml-org/llama.cpp/releases/download/" + core + "/llama-" + core + "-bin-ubuntu-x64.tar.gz");
            }
            isZip = false;
        }

        String destinationDirPath = PathResolver.resolveAndCreate(borisProperties.getLlamaServerPath(), true);
        Path targetDir = Paths.get(destinationDirPath);

        for (String url : downloadUrls) {
            log.info("Downloading from {}", url);
            try (InputStream in = new URL(url).openStream()) {
                if (isZip) {
                    try (ZipInputStream zis = new ZipInputStream(in)) {
                        ZipEntry entry;
                        while ((entry = zis.getNextEntry()) != null) {
                            String name = entry.getName();
                            boolean isLibrary = name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib");
                            
                            // Solo extraer librerías, absolutamente ningún binario (.exe)
                            if (!entry.isDirectory() && isLibrary) {
                                String fileName = Paths.get(name).getFileName().toString();
                                Path newPath = targetDir.resolve(fileName);
                                Files.createDirectories(newPath.getParent());
                                Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                            }
                            zis.closeEntry();
                        }
                    }
                } else {
                    try (GzipCompressorInputStream gzipIn = new GzipCompressorInputStream(in);
                         TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn)) {
                        TarArchiveEntry entry;
                        while ((entry = tarIn.getNextEntry()) != null) {
                            String name = entry.getName();
                            boolean isLibrary = name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib");
                            
                            // Solo extraer librerías, absolutamente ningún binario
                            if (!entry.isDirectory() && isLibrary) {
                                String fileName = Paths.get(name).getFileName().toString();
                                Path newPath = targetDir.resolve(fileName);
                                Files.createDirectories(newPath.getParent());
                                Files.copy(tarIn, newPath, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                }
            }
        }
        log.info("Extraction complete to {}", destinationDirPath);
    }

    public boolean isInstalled() {
        String destinationDirPath = PathResolver.resolveAndCreate(borisProperties.getLlamaServerPath(), true);
        Path targetDir = Paths.get(destinationDirPath);
        String osName = System.getProperty("os.name").toLowerCase();

        // Check for library files (DLLs on Windows, .so on Linux, .dylib on Mac)
        // since the download only extracts libraries, not the executable
        try {
            return Files.list(targetDir)
                .anyMatch(path -> {
                    String name = path.getFileName().toString().toLowerCase();
                    return name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib");
                });
        } catch (Exception e) {
            return false;
        }
    }
}
