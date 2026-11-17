import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/** Laboratory host: the server runs in this JVM, never through java -jar. */
public final class Embed {
    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]).toAbsolutePath();
        Path server = Path.of(args[1]).toAbsolutePath();
        boolean isolated = args.length > 2 && args[2].equals("isolated");
        SpikeHooks.root = server;
        String cwd = Path.of("").toAbsolutePath().toString();
        String original = System.getProperty("user.dir");
        if (!isolated) System.setProperty("user.dir", server.toString());
        System.setProperty("bundlerRepoDir", server.toString());
        System.setProperty("com.mojang.eula.agree", "true");
        System.setProperty("Purpur.IReallyDontWantStartupCommands", "true");
        System.out.println("SPIKE pid=" + ProcessHandle.current().pid());
        System.out.println("SPIKE user.dir.original=" + original);
        System.out.println("SPIKE user.dir.server=" + System.getProperty("user.dir"));
        System.out.println("SPIKE nio.cwd=" + Path.of("").toAbsolutePath());
        System.out.println("SPIKE io.cwd=" + new File("").getAbsolutePath());
        System.out.println("SPIKE cwd.unchanged=" + cwd.equals(Path.of("").toAbsolutePath().toString()));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { Files.writeString(server.resolve("host-shutdown.txt"), "host shutdown\n"); }
            catch (java.io.IOException error) { error.printStackTrace(); }
            System.out.println("SPIKE host.shutdown");
        }, "Spike-Host-Shutdown"));

        var failure = new AtomicReference<Throwable>();
        var loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()},
            ClassLoader.getPlatformClassLoader());
        // Paperclip extraction has an explicit absolute repository directory.
        // Its generated server loader must be replaced to transform server code.
        URLClassLoader serverLoader;
        String mainClass;
        if (isolated) {
            var paperclip = Class.forName("io.papermc.paperclip.Paperclip", true, loader);
            var setup = paperclip.getDeclaredMethod("setupClasspath");
            setup.setAccessible(true);
            var urls = (java.net.URL[]) setup.invoke(null);
            var findMain = paperclip.getDeclaredMethod("findMainClass");
            findMain.setAccessible(true);
            mainClass = (String) findMain.invoke(null);
            serverLoader = new SpikeLoader(urls, server.resolve("log4j2.xml"));
        } else {
            serverLoader = loader;
            mainClass = "io.papermc.paperclip.Main";
        }
        try {
            Class.forName("net.minecraft.server.Main", false, Embed.class.getClassLoader());
            throw new AssertionError("Server classes leaked to host");
        } catch (ClassNotFoundException expected) {
            System.out.println("SPIKE host.minecraft.absent=true");
        }
        var launch = new Thread(() -> {
            try {
                Class.forName(mainClass, true, serverLoader)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[]{
                        "--nogui", "--universe", server.resolve("worlds").toString(),
                        "--plugins", server.resolve("plugins").toString(),
                        "--config", server.resolve("server.properties").toString(),
                        "--bukkit-settings", server.resolve("bukkit.yml").toString(),
                        "--commands-settings", server.resolve("commands.yml").toString(),
                        "--spigot-settings", server.resolve("spigot.yml").toString(),
                        "--paper-dir", server.resolve("config").toString(),
                        "--purpur-settings", server.resolve("purpur.yml").toString()
                    });
            } catch (Throwable error) {
                failure.set(error);
                try { Files.writeString(server.resolve("startup-failed.txt"), error.toString()); }
                catch (java.io.IOException ignored) { }
                error.printStackTrace();
            }
        }, "Spike-Embedded-Launch");
        launch.setContextClassLoader(serverLoader);
        launch.start();

        // Keep the host alive after stop, if the server permits it. The supervisor
        // creates this marker only after issuing stop and waiting for its log.
        Path afterStop = server.resolve("check-host-alive");
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(300).toNanos();
        while (System.nanoTime() < deadline) {
            if (failure.get() != null) throw new AssertionError("Paperclip failed", failure.get());
            if (Files.exists(afterStop)) {
                if (isolated) {
                    if (!SpikeHooks.exitRequested) { Thread.sleep(100); continue; }
                    for (Thread hook : SpikeHooks.hooks) {
                        hook.start();
                        hook.join(10000);
                        if (hook.isAlive()) throw new AssertionError("Shutdown hook remains alive");
                    }
                    boolean running = Thread.getAllStackTraces().keySet().stream()
                        .anyMatch(t -> t.isAlive() && t.getName().equals("Server thread"));
                    if (running) throw new AssertionError("Server thread remains alive");
                    if (!original.equals(System.getProperty("user.dir")))
                        throw new AssertionError("Host user.dir changed");
                    System.out.println("SPIKE server.thread.stopped=true");
                    System.out.println("SPIKE server.exit.status=" + SpikeHooks.exitStatus);
                }
                System.out.println("SPIKE host.alive.after.stop=true");
                Files.writeString(server.resolve("host-alive-after-stop.txt"), "host alive after stop\n");
                serverLoader.close();
                loader.close();
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Host deadline exceeded");
    }
}
