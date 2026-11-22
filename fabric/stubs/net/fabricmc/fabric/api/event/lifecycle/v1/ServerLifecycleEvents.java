package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ServerLifecycleEvents {
    private ServerLifecycleEvents() {
    }

    public static final Event<ServerStarting> SERVER_STARTING = new Event<ServerStarting>() {
        private final List<ServerStarting> listeners = new CopyOnWriteArrayList<>();
        {
            this.invoker = server -> {
                for (ServerStarting l : listeners) {
                    l.onServerStarting(server);
                }
            };
        }

        @Override
        public void register(ServerStarting listener) {
            listeners.add(listener);
        }
    };

    public static final Event<ServerStopping> SERVER_STOPPING = new Event<ServerStopping>() {
        private final List<ServerStopping> listeners = new CopyOnWriteArrayList<>();
        {
            this.invoker = server -> {
                for (ServerStopping l : listeners) {
                    l.onServerStopping(server);
                }
            };
        }

        @Override
        public void register(ServerStopping listener) {
            listeners.add(listener);
        }
    };

    @FunctionalInterface
    public interface ServerStarting {
        void onServerStarting(MinecraftServer server);
    }

    @FunctionalInterface
    public interface ServerStopping {
        void onServerStopping(MinecraftServer server);
    }
}
