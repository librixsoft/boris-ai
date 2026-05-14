package com.boris.librixsoft.server.service;

import com.boris.librixsoft.config.BorisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BorisAppBrandingPrinter {

    private final BorisProperties properties;

    public void printSuccessCrates() {
        String blue = "\u001B[94m";
        String green = "\u001B[92m";
        String bold = "\u001B[1m";
        String reset = "\u001B[0m";
        String magenta = "\u001B[95m";
        log.info(green + "┌────────────────────────────────────────────────────────────┐" + reset);
        log.info(green + "│ " + bold + "🚀 BORIS AI CLIENT: " + reset + green + String.format("%-50s", properties.getAppUiUrl()) + reset + green + "│" + reset);
        log.info(green + "└────────────────────────────────────────────────────────────┘" + reset);
        log.info("\u001B[91m" + " Press Ctrl+C to exit" + reset + "\n");
    }

    public void printSplash() {
        String color = "\u001B[94m"; // Default Blue
        if ("RED".equalsIgnoreCase(properties.getLogoColor())) color = "\u001B[91m";
        if ("GREEN".equalsIgnoreCase(properties.getLogoColor())) color = "\u001B[92m";
        if ("YELLOW".equalsIgnoreCase(properties.getLogoColor())) color = "\u001B[93m";
        if ("MAGENTA".equalsIgnoreCase(properties.getLogoColor())) color = "\u001B[95m";

        String reset = "\u001B[0m";
        String bold = "\u001B[1m";
        String yellow = "\u001B[93m";
        String red = "\u001B[91m";

        String splash = String.format("""
                %s%s
                 ██████╗  ██████╗ ██████╗ ██╗███████╗ %s
                 ██╔══██╗██╔═══██╗██╔══██╗██║██╔════╝
                 ██████╔╝██║   ██║██████╔╝██║███████╗
                 ██╔══██╗██║   ██║██╔══██╗██║╚════██║
                 ██████╔╝╚██████╔╝██║  ██║██║███████║
                 ╚═════╝  ╚═════╝ ╚═╝  ╚═╝╚═╝╚══════╝%s
                 %s...I am invincible!%s
                 %s
                 %sHarness the power of local LLMs to run AI entirely on your machine.%s
                """, color, bold, properties.getVersion(), reset, yellow, reset, reset, yellow, reset, red, reset);
        System.out.println(splash);
    }
}
