import java.io.File;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Experimental hooks scoped to this laboratory JVM, not part of the mod. */
public final class SpikeHooks {
    static Path root;
    static volatile boolean exitRequested;
    static volatile int exitStatus;
    static final List<Thread> hooks = new CopyOnWriteArrayList<>();
    static final List<ExecutorService> executors = new CopyOnWriteArrayList<>();

    // The server assumes the JVM dies after stop, so executors made through
    // Executors.new* (Util.DIMENSION_DATA_IO_POOL, CraftAsyncScheduler management)
    // are never shut down. Track them so the host can retire them.
    public static ExecutorService track(ExecutorService executor) { executors.add(executor); return executor; }
    public static ScheduledExecutorService track(ScheduledExecutorService executor) { executors.add(executor); return executor; }

    /** Returns how many tracked executors did not terminate within the timeout. */
    public static int shutdownExecutors(long seconds) throws InterruptedException {
        for (ExecutorService executor : executors) executor.shutdownNow();
        int stuck = 0;
        for (ExecutorService executor : executors)
            if (!executor.awaitTermination(seconds, TimeUnit.SECONDS)) stuck++;
        return stuck;
    }

    public static Path redirect(Path path) {
        return path.isAbsolute() ? path : root.resolve(path).normalize();
    }

    public static Path path(String first, String... rest) {
        return redirect(Path.of(first, rest));
    }

    public static Path path(URI uri) {
        return redirect(Path.of(uri));
    }

    public static Path path(FileSystem fs, String first, String... rest) {
        Path path = fs.getPath(first, rest);
        return fs.equals(java.nio.file.FileSystems.getDefault()) ? redirect(path) : path;
    }

    public static String property(String key) {
        return "user.dir".equals(key) ? root.toString() : System.getProperty(key);
    }

    public static String property(String key, String fallback) {
        return "user.dir".equals(key) ? root.toString() : System.getProperty(key, fallback);
    }

    public static void exit(int status) {
        exitStatus = status;
        exitRequested = true;
        System.out.println("SPIKE server.exit.intercepted=" + status);
    }

    public static void exit(Runtime runtime, int status) { exit(status); }
    public static void halt(Runtime runtime, int status) { exit(status); }
    public static void addShutdownHook(Runtime runtime, Thread hook) { hooks.add(hook); }
    public static boolean removeShutdownHook(Runtime runtime, Thread hook) { return hooks.remove(hook); }

    @SuppressWarnings("serial")
    public static final class ServerFile extends File {
        public ServerFile(String path) { super(redirect(Path.of(path)).toString()); }
        public ServerFile(String parent, String child) {
            super(redirect(new File(parent, child).toPath()).toString());
        }
        public ServerFile(File parent, String child) {
            super(redirect(new File(parent, child).toPath()).toString());
        }
        public ServerFile(URI uri) { super(uri); }
    }
}
