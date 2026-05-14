package com.boris.librixsoft.server.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class HardwareService {

    private final SystemInfo systemInfo = new SystemInfo();

    public Map<String, Object> getGpuInfo() {
        try {
            HardwareAbstractionLayer hal = systemInfo.getHardware();
            List<GraphicsCard> cards = hal.getGraphicsCards();
            
            if (cards == null || cards.isEmpty()) {
                return Map.of("name", "N/A", "vram", "0 GB");
            }

            // 1. Intentamos buscar la tarjeta con más VRAM (asumiendo que es la dedicada)
            GraphicsCard selectedCard = cards.stream()
                    .max((c1, c2) -> Long.compare(c1.getVRam(), c2.getVRam()))
                    .orElse(cards.get(0)); // Si la lista está vacía, caerá en el if de arriba

            // 2. Si la "mejor" sigue teniendo 0 de VRAM, tomamos la primera de la lista por seguridad
            if (selectedCard.getVRam() <= 0) {
                selectedCard = cards.get(0);
            }

            long vramBytes = selectedCard.getVRam();
            double vramGb = vramBytes / (1024.0 * 1024.0 * 1024.0);
            
            // Formateo de VRAM
            String vramStr = vramBytes > 0 ? String.format("%.1f GB", vramGb) : "Shared/Unknown";
            
            return Map.of(
                "name", selectedCard.getName(),
                "vram", vramStr
            );
        } catch (Exception e) {
            log.error("Error retrieving GPU info: {}", e.getMessage());
            return Map.of("name", "Unknown", "vram", "N/A");
        }
    }

}
