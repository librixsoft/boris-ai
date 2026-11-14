package com.boris.context;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Resuelve la ruta del archivo de log de sesión en ~/.boris/logs.
 * Fuente única de verdad para la ubicación de los logs de sesión.
 */
public final class SessionLogResolver {

    private SessionLogResolver() {
    }

    public static final String BORIS_HOME_DIR = ".boris";
    public static final String LOGS_DIR = "logs";
    public static final String LOG_FILE_PATTERN = "yyyy-MM-dd_HH-mm-ss";

    /**
     * Devuelve el directorio de logs: {user.home}/.boris/logs.
     */
    public static Path logsDirectory() {
        return Path.of(System.getProperty("user.home"))
                .resolve(BORIS_HOME_DIR)
                .resolve(LOGS_DIR);
    }

    /**
     * Devuelve el archivo de log de la sesión actual:
     * {logsDir}/{timestamp}_{sessionId}.log.
     *
     * @throws BorisException si no hay sessionId seteada
     */
    public static Path sessionLogFile() {
        String sessionId = SearchSessionContext.requireSessionId();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern(LOG_FILE_PATTERN));
        String fileName = timestamp + "_" + sessionId + ".log";
        return logsDirectory().resolve(fileName);
    }
}
