import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/** Laboratory host: the server runs in this JVM, never through java -jar. */
public final class Embed {
    /** Stands in for the game's loader; kept open so its natives stay bound. */
    static URLClassLoader clientNatives;

    /** Step 3: the host (like a client) loads netty-epoll, JNA and sqlite natives before the server does. */
    static URLClassLoader preloadNatives(java.net.URL[] urls) throws Exception {
        var picked = java.util.Arrays.stream(urls)
            .filter(u -> u.getPath().matches(".*/(netty-[^/]*|jna-[^/]*|sqlite-jdbc-[^/]*)\\.jar"))
            .filter(u -> !u.getPath().contains("osx") && !u.getPath().contains("aarch_64"))
            .toArray(java.net.URL[]::new);
        var client = new URLClassLoader("spike-client", picked, ClassLoader.getPlatformClassLoader());
        var epoll = Class.forName("io.netty.channel.epoll.Epoll", true, client);
        System.out.println("SPIKE host.native.epoll=" + epoll.getMethod("isAvailable").invoke(null)
            + " cause=" + epoll.getMethod("unavailabilityCause").invoke(null));
        var jna = Class.forName("com.sun.jna.Native", true, client);
        System.out.println("SPIKE host.native.jna.sizeof.long=" + jna.getMethod("getNativeSize", Class.class).invoke(null, long.class));
        var sqlite = Class.forName("org.sqlite.SQLiteJDBCLoader", true, client);
        System.out.println("SPIKE host.native.sqlite=" + sqlite.getMethod("initialize").invoke(null));
        try (var c = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");
             var r = c.createStatement().executeQuery("select sqlite_version()")) {
            r.next();
            System.out.println("SPIKE host.native.sqlite.version=" + r.getString(1));
        } catch (java.sql.SQLException error) {
            // DriverManager only sees drivers of the caller loader; load it explicitly.
            var driver = (java.sql.Driver) Class.forName("org.sqlite.JDBC", true, client).getDeclaredConstructor().newInstance();
            try (var c = driver.connect("jdbc:sqlite::memory:", new java.util.Properties());
                 var r = c.createStatement().executeQuery("select sqlite_version()")) {
                r.next();
                System.out.println("SPIKE host.native.sqlite.version=" + r.getString(1));
            }
        }
        return client;
    }

    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]).toAbsolutePath();
        Path server = Path.of(args[1]).toAbsolutePath();
        boolean isolated = args.length > 2 && args[2].equals("isolated");
        boolean natives = args.length > 3 && args[3].equals("natives");
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
            if (natives) clientNatives = preloadNatives(urls);
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
                    int stuck = SpikeHooks.shutdownExecutors(10);
                    System.out.println("SPIKE executors.tracked=" + SpikeHooks.executors.size() + " stuck=" + stuck);
                    // In-process report: jcmd attach would write .attach_pid into the watched host dir.
                    var leftover = Thread.getAllStackTraces().keySet().stream()
                        .filter(t -> t.isAlive() && !t.isDaemon() && t != Thread.currentThread()
                            && !t.getName().equals("DestroyJavaVM"))
                        .map(t -> t.getName() + " group=" + (t.getThreadGroup() == null ? "-" : t.getThreadGroup().getName()))
                        .sorted().toList();
                    var report = new java.util.ArrayList<String>();
                    report.add("# executors.tracked=" + SpikeHooks.executors.size() + " stuck=" + stuck);
                    report.addAll(leftover);
                    Files.write(server.resolve("non-daemon-threads-after-stop.txt"), report);
                    System.out.println("SPIKE nondaemon.after.stop=" + leftover.size());
                    System.out.println("SPIKE server.thread.stopped=true");
                    System.out.println("SPIKE server.exit.status=" + SpikeHooks.exitStatus);
                }
                System.out.println("SPIKE host.alive.after.stop=true");
                Files.writeString(server.resolve("host-alive-after-stop.txt"), "host alive after stop\n");
                if (clientNatives != null) {
                    var epoll = Class.forName("io.netty.channel.epoll.Epoll", true, clientNatives);
                    var lines = new java.util.ArrayList<String>();
                    lines.add("SPIKE host.native.epoll.after.stop=" + epoll.getMethod("isAvailable").invoke(null));
                    // Same native from the server's own loader (already loaded there if it used epoll).
                    var serverEpoll = Class.forName("io.netty.channel.epoll.Epoll", true, serverLoader);
                    lines.add("SPIKE server.native.epoll=" + serverEpoll.getMethod("isAvailable").invoke(null)
                        + " cause=" + serverEpoll.getMethod("unavailabilityCause").invoke(null));
                    // System.out may be closed by the server after stop.
                    Files.write(server.resolve("natives-after-stop.txt"), lines);
                }
                serverLoader.close();
                loader.close();
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Host deadline exceeded");
    }
}
