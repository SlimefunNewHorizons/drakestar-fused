import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/** Laboratory host: the server runs in this JVM, never through java -jar. */
public final class Embed {
    /** Stands in for the game's loader; kept open so its natives stay bound. */
    static URLClassLoader clientNatives;
    static URLClassLoader clientRustLoader;
    static Class<?> clientRustProbe;
    static URLClassLoader neoforgeModLoader;
    static URLClassLoader forgeModLoader;
    static URLClassLoader fabricModLoader;
    static final java.util.List<String> loaderStartLines = new java.util.ArrayList<>();

    static URLClassLoader createModClassLoader(String name, Path modJar, Path stubsDir, java.net.URL[] paperclipUrls) throws Exception {
        var urls = new java.util.ArrayList<java.net.URL>();
        urls.add(modJar.toUri().toURL());
        urls.add(stubsDir.toUri().toURL());
        for (java.net.URL u : paperclipUrls) {
            if (u.getPath().matches(".*/slf4j-api-[^/]*\\.jar")) {
                urls.add(u);
            }
        }
        return new URLClassLoader(name, urls.toArray(java.net.URL[]::new), ClassLoader.getPlatformClassLoader());
    }

    static String inspectLoaderState(String loaderId, URLClassLoader cl, String annotationInfo) throws Exception {
        var serverClass = Class.forName("com.drakescraft.fused.bridge.FusedCraftServer", true, cl);
        Object instance = serverClass.getMethod("getActiveInstance").invoke(null);
        boolean initialized = instance != null && (boolean) serverClass.getMethod("isInitialized").invoke(instance);
        Object gameDir = instance != null ? serverClass.getMethod("getGameDir").invoke(instance) : null;
        Object pluginsDir = instance != null ? serverClass.getMethod("getPluginsDir").invoke(instance) : null;
        var rustBridge = Class.forName("com.drakescraft.fused.rust.SlimefunRustNativeBridge", true, cl);
        boolean rustLoaded = (boolean) rustBridge.getMethod("isLoaded").invoke(null);
        Object loadedPath = rustBridge.getMethod("getLoadedPath").invoke(null);
        Object lastError = rustBridge.getMethod("getLastError").invoke(null);
        return "loader=" + loaderId
            + " annotation=" + annotationInfo
            + " initialized=" + initialized
            + " gameDir=" + gameDir
            + " pluginsDirExists=" + (pluginsDir instanceof Path p && Files.isDirectory(p))
            + " rustLoaded=" + rustLoaded
            + " loadedPath=" + loadedPath
            + " lastError=" + lastError;
    }

    static void startLoaderMods(java.net.URL[] paperclipUrls, Path server, boolean jniConflict) throws Exception {
        System.setProperty("drakestar.fused.gameDir", server.toString());
        System.setProperty("drakestar.fused.jniDirectPath", Boolean.toString(jniConflict));
        String jarsProp = System.getProperty("drakestar.fused.loaderJars", "");
        String stubsProp = System.getProperty("drakestar.fused.loaderStubs", "");
        if (jarsProp.isBlank() || stubsProp.isBlank()) {
            throw new IllegalArgumentException("Missing drakestar.fused.loaderJars or drakestar.fused.loaderStubs");
        }
        String[] jarParts = jarsProp.split(File.pathSeparator);
        if (jarParts.length != 3) {
            throw new IllegalArgumentException("Expected 3 loader jars (fabric, neoforge, forge), got " + jarParts.length);
        }
        Path fabricJar = Path.of(jarParts[0]).toAbsolutePath();
        Path neoforgeJar = Path.of(jarParts[1]).toAbsolutePath();
        Path forgeJar = Path.of(jarParts[2]).toAbsolutePath();
        Path stubsRoot = Path.of(stubsProp).toAbsolutePath();

        // 1. NeoForge universal mod jar
        neoforgeModLoader = createModClassLoader("spike-loader-neoforge", neoforgeJar, stubsRoot.resolve("neoforge"), paperclipUrls);
        var neoModClass = Class.forName("com.drakescraft.fused.neoforge.DrakeStarFusedNeoForge", true, neoforgeModLoader);
        var neoModAnnClass = Class.forName("net.neoforged.fml.common.Mod", true, neoforgeModLoader).asSubclass(java.lang.annotation.Annotation.class);
        var neoAnn = neoModClass.getAnnotation(neoModAnnClass);
        String neoModId = (String) neoModAnnClass.getMethod("value").invoke(neoAnn);
        var neoBusClass = Class.forName("net.neoforged.bus.api.IEventBus", true, neoforgeModLoader);
        var neoForgeClass = Class.forName("net.neoforged.neoforge.common.NeoForge", true, neoforgeModLoader);
        Object neoEventBus = neoForgeClass.getField("EVENT_BUS").get(null);
        neoModClass.getConstructor(neoBusClass).newInstance(neoEventBus);
        var neoStartEventClass = Class.forName("net.neoforged.neoforge.event.server.ServerStartingEvent", true, neoforgeModLoader);
        var mcServerNeo = Class.forName("net.minecraft.server.MinecraftServer", true, neoforgeModLoader);
        Object neoStartEvent = neoStartEventClass.getConstructor(mcServerNeo).newInstance((Object) null);
        neoBusClass.getMethod("post", Class.forName("net.neoforged.bus.api.Event", true, neoforgeModLoader)).invoke(neoEventBus, neoStartEvent);
        String neoLine = inspectLoaderState("neoforge", neoforgeModLoader, "@Mod(" + neoModId + ")");
        loaderStartLines.add("start." + neoLine);
        System.out.println("SPIKE loader.start." + neoLine);

        // 2. Forge universal mod jar
        forgeModLoader = createModClassLoader("spike-loader-forge", forgeJar, stubsRoot.resolve("forge"), paperclipUrls);
        var forgeModClass = Class.forName("com.drakescraft.fused.forge.DrakeStarFusedForge", true, forgeModLoader);
        var forgeModAnnClass = Class.forName("net.minecraftforge.fml.common.Mod", true, forgeModLoader).asSubclass(java.lang.annotation.Annotation.class);
        var forgeAnn = forgeModClass.getAnnotation(forgeModAnnClass);
        String forgeModId = (String) forgeModAnnClass.getMethod("value").invoke(forgeAnn);
        forgeModClass.getConstructor().newInstance();
        var forgeBusClass = Class.forName("net.minecraftforge.eventbus.api.IEventBus", true, forgeModLoader);
        var mcForgeClass = Class.forName("net.minecraftforge.common.MinecraftForge", true, forgeModLoader);
        Object forgeEventBus = mcForgeClass.getField("EVENT_BUS").get(null);
        var forgeStartEventClass = Class.forName("net.minecraftforge.event.server.ServerStartingEvent", true, forgeModLoader);
        var mcServerForge = Class.forName("net.minecraft.server.MinecraftServer", true, forgeModLoader);
        Object forgeStartEvent = forgeStartEventClass.getConstructor(mcServerForge).newInstance((Object) null);
        forgeBusClass.getMethod("post", Class.forName("net.minecraftforge.eventbus.api.Event", true, forgeModLoader)).invoke(forgeEventBus, forgeStartEvent);
        String forgeLine = inspectLoaderState("forge", forgeModLoader, "@Mod(" + forgeModId + ")");
        loaderStartLines.add("start." + forgeLine);
        System.out.println("SPIKE loader.start." + forgeLine);

        // 3. Fabric universal mod jar
        fabricModLoader = createModClassLoader("spike-loader-fabric", fabricJar, stubsRoot.resolve("fabric"), paperclipUrls);
        var fabricClientClass = Class.forName("com.drakescraft.fused.DrakeStarFusedClient", true, fabricModLoader);
        Object fabricClient = fabricClientClass.getConstructor().newInstance();
        fabricClientClass.getMethod("onInitializeClient").invoke(fabricClient);
        var fabricModClass = Class.forName("com.drakescraft.fused.DrakeStarFusedMod", true, fabricModLoader);
        Object fabricMod = fabricModClass.getConstructor().newInstance();
        fabricModClass.getMethod("onInitialize").invoke(fabricMod);
        var lifecycleClass = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents", true, fabricModLoader);
        Object startingEvent = lifecycleClass.getField("SERVER_STARTING").get(null);
        Object startingInvoker = startingEvent.getClass().getMethod("invoker").invoke(startingEvent);
        var startingIface = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStarting", true, fabricModLoader);
        var mcServerFabric = Class.forName("net.minecraft.server.MinecraftServer", true, fabricModLoader);
        startingIface.getMethod("onServerStarting", mcServerFabric).invoke(startingInvoker, (Object) null);
        String fabricLine = inspectLoaderState("fabric", fabricModLoader, "ModInitializer(drakestar-fused)");
        loaderStartLines.add("start." + fabricLine);
        System.out.println("SPIKE loader.start." + fabricLine);
    }

    static void stopLoaderMods(Path server) throws Exception {
        var lines = new java.util.ArrayList<String>(loaderStartLines);

        if (neoforgeModLoader != null) {
            var neoBusClass = Class.forName("net.neoforged.bus.api.IEventBus", true, neoforgeModLoader);
            var neoForgeClass = Class.forName("net.neoforged.neoforge.common.NeoForge", true, neoforgeModLoader);
            Object neoEventBus = neoForgeClass.getField("EVENT_BUS").get(null);
            var neoStopEventClass = Class.forName("net.neoforged.neoforge.event.server.ServerStoppingEvent", true, neoforgeModLoader);
            var mcServerNeo = Class.forName("net.minecraft.server.MinecraftServer", true, neoforgeModLoader);
            Object neoStopEvent = neoStopEventClass.getConstructor(mcServerNeo).newInstance((Object) null);
            neoBusClass.getMethod("post", Class.forName("net.neoforged.bus.api.Event", true, neoforgeModLoader)).invoke(neoEventBus, neoStopEvent);
            lines.add("stop." + inspectLoaderState("neoforge", neoforgeModLoader, "@Mod(drakestar_fused)"));
        }

        if (forgeModLoader != null) {
            var forgeBusClass = Class.forName("net.minecraftforge.eventbus.api.IEventBus", true, forgeModLoader);
            var mcForgeClass = Class.forName("net.minecraftforge.common.MinecraftForge", true, forgeModLoader);
            Object forgeEventBus = mcForgeClass.getField("EVENT_BUS").get(null);
            var forgeStopEventClass = Class.forName("net.minecraftforge.event.server.ServerStoppingEvent", true, forgeModLoader);
            var mcServerForge = Class.forName("net.minecraft.server.MinecraftServer", true, forgeModLoader);
            Object forgeStopEvent = forgeStopEventClass.getConstructor(mcServerForge).newInstance((Object) null);
            forgeBusClass.getMethod("post", Class.forName("net.minecraftforge.eventbus.api.Event", true, forgeModLoader)).invoke(forgeEventBus, forgeStopEvent);
            lines.add("stop." + inspectLoaderState("forge", forgeModLoader, "@Mod(drakestar_fused)"));
        }

        if (fabricModLoader != null) {
            var lifecycleClass = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents", true, fabricModLoader);
            Object stoppingEvent = lifecycleClass.getField("SERVER_STOPPING").get(null);
            Object stoppingInvoker = stoppingEvent.getClass().getMethod("invoker").invoke(stoppingEvent);
            var stoppingIface = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStopping", true, fabricModLoader);
            var mcServerFabric = Class.forName("net.minecraft.server.MinecraftServer", true, fabricModLoader);
            stoppingIface.getMethod("onServerStopping", mcServerFabric).invoke(stoppingInvoker, (Object) null);
            lines.add("stop." + inspectLoaderState("fabric", fabricModLoader, "ModInitializer(drakestar-fused)"));
        }

        Files.write(server.resolve("loader-mods-after-stop.txt"), lines);
        if (neoforgeModLoader != null) neoforgeModLoader.close();
        if (forgeModLoader != null) forgeModLoader.close();
        if (fabricModLoader != null) fabricModLoader.close();
    }

    /** Helper class defined inside a dedicated host URLClassLoader to test FFM/JNI across distinct loaders. */
    public static final class HostRustProbe {
        public static SymbolLookup hybridLookup;
        public static SymbolLookup odysseiaLookup;
        public static SymbolLookup slimefunLookup;

        public static String preload(Path dir, boolean jniSamePath) throws Throwable {
            var linker = Linker.nativeLinker();
            var intDesc = FunctionDescriptor.of(ValueLayout.JAVA_INT);
            Path hybrid = dir.resolve("libdrakes_hybrid_ffi.so").toAbsolutePath();
            Path odysseia = dir.resolve("libodysseia_ffi.so").toAbsolutePath();
            Path slimefun = dir.resolve("libslimefun_ffi.so").toAbsolutePath();

            hybridLookup = SymbolLookup.libraryLookup(hybrid, Arena.global());
            int hybridAbi = (int) linker.downcallHandle(
                hybridLookup.find("drakes_hybrid_abi_version").orElseThrow(), intDesc).invokeExact();
            var pack = linker.downcallHandle(
                hybridLookup.find("drakes_bits_pack").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
                Linker.Option.critical(true));
            int[] values = new int[]{1, 2, 3, 4, 5, 6, 7, 8};
            long[] packed = new long[1];
            int packWords = (int) pack.invokeExact(4, values.length,
                MemorySegment.ofArray(values), MemorySegment.ofArray(packed), packed.length);

            odysseiaLookup = SymbolLookup.libraryLookup(odysseia, Arena.global());
            int odysseiaAbi = (int) linker.downcallHandle(
                odysseiaLookup.find("odysseia_abi_version").orElseThrow(), intDesc).invokeExact();

            slimefunLookup = SymbolLookup.libraryLookup(slimefun, Arena.global());
            int slimefunAbi = (int) linker.downcallHandle(
                slimefunLookup.find("slimefun_abi_version").orElseThrow(), intDesc).invokeExact();
            var sum = linker.downcallHandle(
                slimefunLookup.find("slimefun_sum_saturating").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
                Linker.Option.critical(true));
            int sumVal = (int) sum.invokeExact(MemorySegment.ofArray(new int[]{10, 20, 30}), 3L);

            if (jniSamePath) {
                // Deliberate conflict control: load the exact same file path via JNI System.load in this loader.
                System.load(slimefun.toString());
            } else {
                // Safe JNI isolation: copy to a loader-unique path before System.load, while FFM shares the original path.
                Path hostDir = dir.getParent().resolve("client-natives");
                Files.createDirectories(hostDir);
                Path hostCopy = hostDir.resolve("libslimefun_ffi.so");
                Files.copy(slimefun, hostCopy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                System.load(hostCopy.toAbsolutePath().toString());
            }
            return "hybrid.abi=" + hybridAbi + " hybrid.packWords=" + packWords
                + " odysseia.abi=" + odysseiaAbi + " slimefun.abi=" + slimefunAbi
                + " slimefun.sum=" + sumVal + " jniSamePath=" + jniSamePath;
        }

        public static String verifyAfterStop() throws Throwable {
            var linker = Linker.nativeLinker();
            var intDesc = FunctionDescriptor.of(ValueLayout.JAVA_INT);
            int hybridAbi = (int) linker.downcallHandle(
                hybridLookup.find("drakes_hybrid_abi_version").orElseThrow(), intDesc).invokeExact();
            int odysseiaAbi = (int) linker.downcallHandle(
                odysseiaLookup.find("odysseia_abi_version").orElseThrow(), intDesc).invokeExact();
            int slimefunAbi = (int) linker.downcallHandle(
                slimefunLookup.find("slimefun_abi_version").orElseThrow(), intDesc).invokeExact();
            var sum = linker.downcallHandle(
                slimefunLookup.find("slimefun_sum_saturating").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
                Linker.Option.critical(true));
            int sumVal = (int) sum.invokeExact(MemorySegment.ofArray(new int[]{40, 50, 60}), 3L);
            return "hybrid.abi=" + hybridAbi + " odysseia.abi=" + odysseiaAbi
                + " slimefun.abi=" + slimefunAbi + " slimefun.sum=" + sumVal;
        }
    }

    static void preloadRustFfm(Path server, boolean jniSamePath) throws Exception {
        byte[] bytes;
        try (var in = Embed.class.getClassLoader().getResourceAsStream("Embed$HostRustProbe.class")) {
            bytes = in.readAllBytes();
        }
        clientRustLoader = new URLClassLoader("spike-client-rust", new java.net.URL[0],
            ClassLoader.getPlatformClassLoader()) {
            Class<?> defineProbe(byte[] b) {
                return defineClass("Embed$HostRustProbe", b, 0, b.length);
            }
        };
        var define = clientRustLoader.getClass().getDeclaredMethod("defineProbe", byte[].class);
        define.setAccessible(true);
        clientRustProbe = (Class<?>) define.invoke(clientRustLoader, (Object) bytes);
        if (clientRustProbe.getClassLoader() != clientRustLoader)
            throw new AssertionError("HostRustProbe was not defined in spike-client-rust");
        String summary = (String) clientRustProbe
            .getMethod("preload", Path.class, boolean.class)
            .invoke(null, server.resolve("natives"), jniSamePath);
        System.out.println("SPIKE host.rust.ffm=" + summary);
    }

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

    /** Step 3 (log4j): the host's own log4j, configured like the vanilla launcher does. */
    static URLClassLoader clientLog;
    static Object clientLogger;

    static void clientLog(String message) throws Exception {
        Class.forName("org.apache.logging.log4j.Logger", true, clientLog)
            .getMethod("info", String.class).invoke(clientLogger, message);
    }

    static void hostLog4j(java.net.URL[] urls, Path server) throws Exception {
        var picked = java.util.Arrays.stream(urls)
            .filter(u -> u.getPath().matches(".*/log4j-(api|core)-[^/]*\\.jar"))
            .toArray(java.net.URL[]::new);
        // The vanilla launcher passes -Dlog4j.configurationFile=client-*.xml: a JVM-wide property.
        System.setProperty("log4j.configurationFile", server.resolve("client-log4j2.xml").toString());
        clientLog = new URLClassLoader("spike-client-log", picked, ClassLoader.getPlatformClassLoader());
        var manager = Class.forName("org.apache.logging.log4j.LogManager", true, clientLog);
        clientLogger = manager.getMethod("getLogger", String.class).invoke(null, "SpikeClient");
        System.out.println("SPIKE host.log4j.jars=" + picked.length);
        clientLog("SPIKE-CLIENT before server");
    }

    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]).toAbsolutePath();
        Path server = Path.of(args[1]).toAbsolutePath();
        boolean isolated = args.length > 2 && args[2].equals("isolated");
        var flags = java.util.Arrays.asList(args).subList(Math.min(3, args.length), args.length);
        boolean natives = flags.contains("natives");
        boolean log4j = flags.contains("log4j");
        boolean rust = flags.contains("rust");
        boolean rustJniConflict = flags.contains("rust-jni-conflict");
        boolean loaderMods = flags.contains("loaders");
        boolean loaderJniConflict = flags.contains("loader-jni-conflict");
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
            if (log4j) hostLog4j(urls, server);
            if (rust) preloadRustFfm(server, rustJniConflict);
            if (loaderMods) startLoaderMods(urls, server, loaderJniConflict);
        } else {
            serverLoader = loader;
            mainClass = "io.papermc.paperclip.Main";
        }
        // A Fabric client host ships its own vanilla net.minecraft.server.Main;
        // isolation then means the embedded server resolves a distinct class.
        Class<?> hostMain = null;
        try {
            hostMain = Class.forName("net.minecraft.server.Main", false, Embed.class.getClassLoader());
        } catch (ClassNotFoundException expected) {
            System.out.println("SPIKE host.minecraft.absent=true");
        }
        if (hostMain != null) {
            if (!isolated || Class.forName("net.minecraft.server.Main", false, serverLoader) == hostMain)
                throw new AssertionError("Server classes leaked to host");
            System.out.println("SPIKE host.minecraft.distinct=true");
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
                if (clientLog != null) {
                    clientLog("SPIKE-CLIENT after stop");
                    var context = Class.forName("org.apache.logging.log4j.LogManager", true, clientLog)
                        .getMethod("getContext", boolean.class).invoke(null, false);
                    Files.writeString(server.resolve("client-log-after-stop.txt"),
                        "context.started=" + context.getClass().getMethod("isStarted").invoke(context) + "\n");
                }
                if (clientRustProbe != null) {
                    var rustLines = new java.util.ArrayList<String>();
                    String hostAfter = (String) clientRustProbe.getMethod("verifyAfterStop").invoke(null);
                    rustLines.add("host.after.stop: " + hostAfter);

                    var drakesNative = Class.forName("org.purpurmc.purpur.nativelib.DrakesNative", true, serverLoader);
                    boolean nativeEnabled = (boolean) drakesNative.getMethod("enabled").invoke(null);
                    boolean tickAllowed = (boolean) drakesNative.getMethod("tickEngineAllowed").invoke(null);
                    String nativeStatus = (String) drakesNative.getMethod("status").invoke(null);
                    int sumResult = (int) drakesNative.getMethod("sumSaturating", int[].class, int.class)
                        .invoke(null, new int[]{100, 200, 300}, 3);
                    int[] charge = new int[]{10, 20};
                    int[] out2 = new int[2];
                    int filled = (int) drakesNative.getMethod("fillSequential", int.class, int[].class, int[].class, int.class, int[].class)
                        .invoke(null, 50, new int[]{100, 100}, charge, 2, out2);
                    int[] storeCharge = new int[]{10, 20};
                    int storeLeft = (int) drakesNative.getMethod("storeRemaining", int.class, int[].class, int[].class, int.class)
                        .invoke(null, 200, new int[]{100, 100}, storeCharge, 2);
                    double price = (double) drakesNative.getMethod("marketPrice",
                        double.class, long.class, double.class, double.class, double.class, double.class, double.class, double.class, double.class)
                        .invoke(null, 100.0, 0L, 0.0, 100_000_000.0, 0.85, 1.85, 0.02, 1.45, 1.0);
                    int auraHits = (int) drakesNative.getMethod("auraBatch",
                        double.class, double.class, double.class, double.class, int.class, double[].class, byte[].class)
                        .invoke(null, 0.0, 0.0, 0.0, 5.0, 2, new double[]{1.0, 0.0, 0.0, 9.0, 0.0, 0.0}, new byte[2]);
                    Object pulses = drakesNative.getMethod("pulses").invoke(null);
                    String pulseClass = pulses.getClass().getName();
                    rustLines.add("server.drakes_native: enabled=" + nativeEnabled
                        + " tickEngineAllowed=" + tickAllowed
                        + " status={" + nativeStatus + "}"
                        + " sum=" + sumResult
                        + " filled=" + filled
                        + " fillCharge=[" + charge[0] + "," + charge[1] + "]"
                        + " storeLeft=" + storeLeft
                        + " storeCharge=[" + storeCharge[0] + "," + storeCharge[1] + "]"
                        + " price=" + price
                        + " auraHits=" + auraHits
                        + " pulseCounter=" + pulseClass);

                    var hybridNative = Class.forName("org.purpurmc.purpur.nativelib.HybridNative", true, serverLoader);
                    boolean hybridLoaded = (boolean) hybridNative.getMethod("libraryLoaded").invoke(null);
                    boolean hybridBits = (boolean) hybridNative.getMethod("bitsEnabled").invoke(null);
                    String hybridStatus = (String) hybridNative.getMethod("status").invoke(null);
                    int[] vals = new int[2048];
                    for (int i = 0; i < vals.length; i++) vals[i] = i & 15;
                    long[] packed = new long[(int) hybridNative.getMethod("longCount", int.class, int.class).invoke(null, 4, vals.length)];
                    int words = (int) hybridNative.getMethod("packBits", int.class, int[].class, long[].class)
                        .invoke(null, 4, vals, packed);
                    int[] unpacked = new int[vals.length];
                    int unpackedCount = (int) hybridNative.getMethod("unpackBits", int.class, int.class, long[].class, int[].class)
                        .invoke(null, 4, vals.length, packed, unpacked);
                    boolean parityOk = java.util.Arrays.equals(vals, unpacked);
                    rustLines.add("server.hybrid_native: libraryLoaded=" + hybridLoaded
                        + " bitsEnabled=" + hybridBits
                        + " status={" + hybridStatus + "}"
                        + " packWords=" + words
                        + " unpackedCount=" + unpackedCount
                        + " parityOk=" + parityOk);

                    var kernelBridge = Class.forName("org.purpurmc.purpur.DrakesKernelBridge", true, serverLoader);
                    boolean kernelRustLoaded = (boolean) kernelBridge.getMethod("isRustLoaded").invoke(null);
                    var rustEngine = Class.forName("org.purpurmc.purpur.rust.SlimefunRustEngine", true, serverLoader);
                    rustEngine.getMethod("initPanamaBindings").invoke(null);
                    boolean engineLinked = (boolean) rustEngine.getMethod("isNativeLinked").invoke(null);
                    Object cycle = rustEngine.getMethod("executeTick").invoke(null);
                    rustLines.add("server.rust_engine: kernelBridge.isRustLoaded=" + kernelRustLoaded
                        + " SlimefunRustEngine.isNativeLinked=" + engineLinked
                        + " cycle=" + cycle);

                    Files.write(server.resolve("rust-ffm-after-stop.txt"), rustLines);
                }
                if (loaderMods) {
                    stopLoaderMods(server);
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
