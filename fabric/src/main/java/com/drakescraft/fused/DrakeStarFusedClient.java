package com.drakescraft.fused;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint de cliente. Con {@code -Ddrakestar.fused.embedded.jar=<monolito>} y
 * {@code -Ddrakestar.fused.embedded.server=<dir>} (Java 25+, Minecraft 26.2) arranca el monolito DrakesPurpur
 * dentro de esta JVM con el host del spike ({@code Embed}, modo isolated) y bloquea hasta que el servidor responde,
 * para que el quickplay del launcher entre directo. Sin esas propiedades solo registra que Fused esta activo.
 */
public class DrakeStarFusedClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("drakestar-fused-client");
    private static final String MARK = "FUSED-EMBEDDED ";

    @Override
    public void onInitializeClient() {
        String jar = System.getProperty("drakestar.fused.embedded.jar");
        if (jar == null || jar.isBlank()) {
            LOGGER.info("DrakeStar Fused activo en el cliente.");
            return;
        }
        if (Runtime.version().feature() < 25) {
            System.out.println(MARK + "embed.failed=java " + Runtime.version().feature() + " < 25");
            return;
        }
        Path server = Path.of(System.getProperty("drakestar.fused.embedded.server", "fused-server")).toAbsolutePath();
        List<String> args = new ArrayList<>(List.of(jar, server.toString(), "isolated"));
        String flags = System.getProperty("drakestar.fused.embedded.flags", "");
        if (!flags.isBlank()) args.addAll(List.of(flags.split(",")));
        long started = System.nanoTime();
        Thread host = new Thread(() -> {
            try {
                // Embed se compila aparte con --release 25 (java.lang.classfile); por eso se llama por reflexion.
                Class.forName("Embed").getMethod("main", String[].class).invoke(null, (Object) args.toArray(new String[0]));
            } catch (Throwable error) {
                System.out.println(MARK + "embed.failed=" + error);
                error.printStackTrace();
            }
        }, "Fused-Embedded-Host");
        host.setDaemon(true);
        host.start();
        try {
            Properties props = new Properties();
            try (var in = Files.newInputStream(server.resolve("server.properties"))) { props.load(in); }
            int port = Integer.parseInt(props.getProperty("server-port", "25565").trim());
            Path log = server.resolve("logs/latest.log");
            long deadline = System.nanoTime() + Long.getLong("drakestar.fused.embedded.timeout", 240L) * 1_000_000_000L;
            while (System.nanoTime() < deadline && host.isAlive()) {
                if (Files.exists(log) && Files.readString(log).contains("Done (") && open(port)) {
                    System.out.println(MARK + "server.ready port=" + port + " seconds="
                        + (System.nanoTime() - started) / 1_000_000_000.0);
                    return;
                }
                Thread.sleep(250);
            }
            System.out.println(MARK + "server.ready=false (" + (host.isAlive() ? "timeout" : "host ended") + ")");
        } catch (Exception error) {
            System.out.println(MARK + "wait.failed=" + error);
        }
    }

    private static boolean open(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
            return true;
        } catch (java.io.IOException closed) {
            return false;
        }
    }
}
