package com.boris.librixsoft.util;

import lombok.extern.slf4j.Slf4j;
import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Utilidad para resolver rutas relativas al HOME del usuario y asegurar su existencia.
 */
@Slf4j
public class PathResolver {

    /**
     * Resuelve una ruta y crea los directorios necesarios si no existen.
     * Rutas que empiezan con '/' se resuelven directamente a user.home/
     * 
     * @param path Ruta a resolver (puede empezar por '/', ser absoluta o relativa)
     * @param isFolder Indica si la ruta final es una carpeta o un archivo (para crear el padre)
     * @return Ruta absoluta resuelta
     */
    public static String resolveAndCreate(String path, boolean isFolder) {
        if (path == null || path.isBlank()) return "";
        String userHome = getUserHome();

        File resolved;
        if (path.startsWith("/")) {
            resolved = new File(userHome, path.substring(1));
        } else if (new File(path).isAbsolute()) {
            resolved = new File(path);
        } else {
            resolved = new File(path);
        }

        File dirToCreate = isFolder ? resolved : resolved.getParentFile();
        if (dirToCreate != null && !dirToCreate.exists()) {
            log.info("[PathResolver] Creating directory: {}", dirToCreate.getAbsolutePath());
            dirToCreate.mkdirs();
        }

        return resolved.getAbsolutePath().replace('\\', '/');
    }

    /**
     * Obtiene el directorio HOME del usuario con separadores normalizados a '/'.
     *
     * @return Ruta del home del usuario con slashes Unix
     */
    public static String getUserHome() {
        return System.getProperty("user.home").replace('\\', '/');
    }

    /**
     * Resuelve una ruta relativa o absoluta contra el workspacePrefix.
     * Si el path ya es absoluto, lo devuelve normalizado.
     * Si es relativo, lo concatena con workspacePrefix (que puede ser relativo a user.home o absoluto).
     *
     * @param path Ruta a resolver (relativa o absoluta)
     * @param workspacePrefix Prefijo del workspace (ej: "/.boris/workspace" o "C:/workspace")
     * @return Ruta absoluta normalizada con '/'
     */
    public static String resolveWorkspacePath(String path, String workspacePrefix) {
        if (path == null || path.isBlank()) return "";
        String normalizedPath = path.replace('\\', '/');
        // Si ya es absoluta, devolver normalizada
        if (Paths.get(normalizedPath).isAbsolute()) {
            return normalizedPath;
        }
        // Relativa: necesitamos workspacePrefix
        String ws = (workspacePrefix != null && !workspacePrefix.isBlank())
                ? workspacePrefix
                : "/.boris/workspace";
        ws = ws.replace('\\', '/');
        // Si ws empieza con '/', es relativo a user.home
        if (ws.startsWith("/")) {
            ws = getUserHome() + ws;
        } else if (!Paths.get(ws).isAbsolute()) {
            // Si no es absoluto ni empieza con '/', asumir relativo a user.home
            ws = getUserHome() + "/" + ws;
        }
        if (!ws.endsWith("/")) ws += "/";
        return (ws + normalizedPath).replace('\\', '/');
    }

    /**
     * Abre un diálogo de selección de archivo o carpeta.
     *
     * @param directoryOnly Si es true, permite solo carpetas. Si es false, solo archivos.
     * @param initialPath Ruta inicial para el diálogo.
     * @return Ruta seleccionada o string vacío si se cancela.
     */
    public static String browse(boolean directoryOnly, String initialPath) {
        final String[] selectedPath = {null};
        String os = System.getProperty("os.name").toLowerCase();
        
        String cleanInitialPath = (initialPath != null) ? initialPath.replace("\"", "").replace("'", "").trim() : "";
        File initialDir = null;
        if (!cleanInitialPath.isEmpty()) {
            File f = new File(cleanInitialPath);
            if (f.exists()) {
                initialDir = f.isDirectory() ? f : f.getParentFile();
            }
        }

        if (os.contains("win")) {
            try {
                String psInitialDir = (initialDir != null) ? initialDir.getAbsolutePath().replace('\\', '/') : getUserHome();
                
                String script;
                if (directoryOnly) {
                    script = String.format(
                        "Add-Type -AssemblyName System.Windows.Forms; " +
                        "$f = New-Object System.Windows.Forms.OpenFileDialog; " +
                        "$f.Title = 'Seleccionar Carpeta para Boris'; " +
                        "$f.InitialDirectory = '%s'; " +
                        "$f.ValidateNames = $false; " +
                        "$f.CheckFileExists = $false; " +
                        "$f.CheckPathExists = $true; " +
                        "$f.FileName = 'Seleccionar Carpeta'; " +
                        "if($f.ShowDialog() -eq 'OK') { Split-Path $f.FileName }; " +
                        "$f.Dispose()", 
                        psInitialDir.replace("'", "''"));
                } else {
                    script = String.format(
                        "Add-Type -AssemblyName System.Windows.Forms; " +
                        "$f = New-Object System.Windows.Forms.OpenFileDialog; " +
                        "$f.Title = 'Seleccionar Ejecutable Llama Server'; " +
                        "$f.InitialDirectory = '%s'; " +
                        "if($f.ShowDialog() -eq 'OK') { $f.FileName }; " +
                        "$f.Dispose()", 
                        psInitialDir.replace("'", "''"));
                }
                
                log.info("[PathResolver] Running PowerShell script: {}", script);
                
                String psExec = "powershell";
                // Try to find powershell.exe in common locations if it might not be in PATH
                if (new File("C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe").exists()) {
                    psExec = "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";
                }

                ProcessBuilder pb = new ProcessBuilder(psExec, "-NoProfile", "-Command", script);
                Process p = pb.start();
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        selectedPath[0] = line.trim().replace('\\', '/');
                    }
                }
                
                boolean finished = p.waitFor(2, TimeUnit.MINUTES);
                if (!finished) {
                    p.destroyForcibly();
                    log.warn("[PathResolver] PowerShell process timed out and was killed.");
                }
                
                return selectedPath[0] != null ? selectedPath[0] : "";
            } catch (Exception e) {
                log.error("PowerShell native browse failed, falling back to Swing", e);
            }
        }

        // Fallback a JFileChooser for non-Windows or if PowerShell failed
        try {
            final File finalInitialDir = initialDir;
            SwingUtilities.invokeAndWait(() -> {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    JFileChooser chooser = new JFileChooser();
                    chooser.setFileSelectionMode(directoryOnly ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
                    if (finalInitialDir != null && finalInitialDir.exists()) {
                        chooser.setCurrentDirectory(finalInitialDir);
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        selectedPath[0] = chooser.getSelectedFile().getAbsolutePath().replace('\\', '/');
                    }
                } catch (Exception e) {
                    selectedPath[0] = "error: " + e.getMessage();
                }
            });
        } catch (Exception e) {
            log.error("Swing browse failed", e);
        }
        
        return selectedPath[0] != null ? selectedPath[0] : "";
    }
}
