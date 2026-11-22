import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Paso 2: entrypoint de cliente Fabric que arranca el monolito con Embed
 * (modo isolated) en un hilo propio y bloquea hasta que el servidor responde.
 */
public final class SpikeClientMod implements net.fabricmc.api.ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Path server = Path.of(System.getProperty("fused.spike.server")).toAbsolutePath();
        String jar = System.getProperty("fused.spike.jar");
        String flags = System.getProperty("fused.spike.flags", "");
        var args = new java.util.ArrayList<String>(java.util.List.of(jar, server.toString(), "isolated"));
        if (!flags.isBlank()) args.addAll(java.util.List.of(flags.split(",")));
        long started = System.nanoTime();
        Thread host = new Thread(() -> {
            try { Embed.main(args.toArray(String[]::new)); }
            catch (Throwable error) {
                System.out.println("SPIKE-CLIENT embed.failed=" + error);
                error.printStackTrace();
            }
        }, "Fused-Embedded-Host");
        host.setDaemon(true);
        host.start();
        try {
            var props = new Properties();
            try (var in = Files.newInputStream(server.resolve("server.properties"))) { props.load(in); }
            int port = Integer.parseInt(props.getProperty("server-port"));
            Path log = server.resolve("logs/latest.log");
            long deadline = System.nanoTime() + 240_000_000_000L;
            while (System.nanoTime() < deadline) {
                if (Files.exists(log) && Files.readString(log).contains("Done (") && open(port)) {
                    System.out.println("SPIKE-CLIENT server.ready port=" + port + " seconds="
                        + (System.nanoTime() - started) / 1_000_000_000.0);
                    return;
                }
                Thread.sleep(250);
            }
            System.out.println("SPIKE-CLIENT server.ready=false (timeout)");
        } catch (Exception error) {
            System.out.println("SPIKE-CLIENT wait.failed=" + error);
        }
    }

    private static boolean open(int port) {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
            return true;
        } catch (java.io.IOException closed) {
            return false;
        }
    }
}
