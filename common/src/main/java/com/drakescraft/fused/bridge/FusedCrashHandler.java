package com.drakescraft.fused.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Intercepta fallos criticos de Fused y genera un enlace para abrir un issue en GitHub.
 *
 * Privacidad: el reporte NO incluye logs ni rutas del equipo (solo el error, Java y el sistema operativo) y el
 * enlace NO se abre solo: hay que activarlo con -Ddrakestar.fused.openIssue=true. El launcher DrakeStar recoge
 * los logs por su cuenta (con datos personales tachados) cuando el jugador lo permite.
 */
public class FusedCrashHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(FusedCrashHandler.class);
    private static final String GITHUB_REPO_ISSUES = "https://github.com/SlimefunNewHorizons/drakestar-fused/issues/new";

    /** {@code logPath} se conserva por compatibilidad y no se lee. */
    public static void handleCrash(String context, Throwable throwable, Path logPath) {
        LOGGER.error("[DrakeStar Fused] Error critico en {}: {}", context, throwable.getMessage(), throwable);

        try {
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            String stackTrace = sw.toString();
            if (stackTrace.length() > 1500) {
                stackTrace = stackTrace.substring(0, 1500) + "\n...[truncated]";
            }

            String title = String.format("[Crash Report] %s: %s", context, throwable.getClass().getSimpleName());
            String body = String.format(
                "**Timestamp:** `%s`\n**Context:** `%s`\n**Java Version:** `%s`\n**OS:** `%s`\n\n#### Stack Trace\n```\n%s\n```\n",
                Instant.now(), context, System.getProperty("java.version"), System.getProperty("os.name"), stackTrace);

            String issueUrl = String.format(
                "%s?title=%s&body=%s&labels=crash-report",
                GITHUB_REPO_ISSUES,
                URLEncoder.encode(title, StandardCharsets.UTF_8),
                URLEncoder.encode(body, StandardCharsets.UTF_8));

            LOGGER.info("Enlace para reportar este fallo (no se abre solo): {}", issueUrl);

            if (Boolean.getBoolean("drakestar.fused.openIssue") && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(issueUrl));
            }
        } catch (Exception e) {
            LOGGER.error("No se pudo generar el reporte de error: ", e);
        }
    }
}
