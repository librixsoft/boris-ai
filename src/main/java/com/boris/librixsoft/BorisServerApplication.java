package com.boris.librixsoft;

import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BorisServerApplication {

    public static void main(String[] args) {
        // Habilitar soporte para diálogos nativos (UI) en Spring Boot
        System.setProperty("java.awt.headless", "false");
        
        org.springframework.boot.SpringApplication app = new org.springframework.boot.SpringApplication(BorisServerApplication.class);
        app.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
        app.run(args);
    }

}
