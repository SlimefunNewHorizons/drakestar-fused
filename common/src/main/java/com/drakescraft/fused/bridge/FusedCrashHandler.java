package com.drakescraft.fused.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Intercepta fallos críticos en la inyección de Bukkit / Slimefun y genera automáticamente
 * un enlace directo a GitHub Issue / PR con el reporte de error y los logs adjuntos.
 */
public class FusedCrashHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(FusedCrashHandler.class);
    private static final String GITHUB_REPO_ISSUES = "https://github.com/SlimefunNewHorizons/drakestar-fused/issues/new";

    public static void handleCrash(String context, Throwable throwable, Path logPath) {
        LOGGER.error("💥 [DrakeStar Fused] Error crítico en {}: {}", context, throwable.getMessage(), throwable);

        try {
            String logSnippet = "";
            if (logPath != null && Files.exists(logPath)) {
                try {
                    logSnippet = Files.readString(logPath);
                    if (logSnippet.length() > 3000) {
                        logSnippet = logSnippet.substring(logSnippet.length() - 3000);
                    }
                } catch (Exception ignored) {}
            }

            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            String stackTrace = sw.toString();

            String title = String.format("[Crash Report] %s: %s", context, throwable.getClass().getSimpleName());
            String body = String.format(
                "### 🚨 Crash & Error Report\n\n" +
                "**Timestamp:** `%s`\n" +
                "**Context:** `%s`\n" +
                "**Java Version:** `%s`\n" +
                "**OS:** `%s`\n\n" +
                "#### 💥 Stack Trace\n```\n%s\n```\n\n" +
                "#### 📜 Log Snippet\n```\n%s\n```\n\n" +
                "*(Generated automatically by DrakeStar Fused Crash Telemetry)*",
                Instant.now(),
                context,
                System.getProperty("java.version"),
                System.getProperty("os.name"),
                stackTrace.length() > 2000 ? stackTrace.substring(0, 2000) + "\n...[truncated]" : stackTrace,
                logSnippet.isEmpty() ? "No external log file available" : logSnippet
            );

            String issueUrl = String.format(
                "%s?title=%s&body=%s&labels=crash-report,automated-telemetry",
                GITHUB_REPO_ISSUES,
                URLEncoder.encode(title, StandardCharsets.UTF_8),
                URLEncoder.encode(body, StandardCharsets.UTF_8)
            );

            LOGGER.info("🌐 Enlace de reporte generado: {}", issueUrl);

            // Intentar abrir el navegador en entornos de escritorio
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(issueUrl));
            }
        } catch (Exception e) {
            LOGGER.error("No se pudo abrir el navegador para el reporte de error: ", e);
        }
    }
}
