package com.boris.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Pattern;

import com.boris.exceptions.VersionException;

import picocli.CommandLine.IVersionProvider;

public final class BorisVersion implements IVersionProvider {

    private static final String VERSION_RESOURCE = "/version.properties";
    private static final String VERSION_KEY = "version";
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+(?:[-+].*)?$");

    @Override
    public String[] getVersion() {
        return new String[]{"boris " + current()};
    }

    public static String current() {
        Properties properties = new Properties();
        try (InputStream input = BorisVersion.class.getResourceAsStream(VERSION_RESOURCE)) {
            if (input == null) {
                throw new VersionException("No se encontro el recurso " + VERSION_RESOURCE);
            }
            properties.load(input);
        } catch (IOException e) {
            throw new VersionException("No se pudo leer el recurso " + VERSION_RESOURCE, e);
        }

        String version = properties.getProperty(VERSION_KEY);
        if (version == null || version.isBlank()) {
            throw new VersionException("La propiedad '" + VERSION_KEY + "' no esta definida en " + VERSION_RESOURCE);
        }

        String resolved = version.strip();
        if (!VERSION_PATTERN.matcher(resolved).matches()) {
            throw new VersionException(
                    "La version '" + resolved + "' no fue resuelta por Maven; construi el proyecto con Maven para filtrar " + VERSION_RESOURCE);
        }
        return resolved;
    }
}
