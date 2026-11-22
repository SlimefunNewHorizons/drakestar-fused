package net.minecraftforge.common;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class MinecraftForge {
    public static final IEventBus EVENT_BUS = new IEventBus() {
        private final List<Consumer<? extends Event>> listeners = new CopyOnWriteArrayList<>();

        @Override
        public <T extends Event> void addListener(Consumer<T> consumer) {
            listeners.add(consumer);
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean post(Event event) {
            for (Consumer<? extends Event> raw : listeners) {
                try {
                    ((Consumer<Event>) raw).accept(event);
                } catch (ClassCastException ignored) {
                }
            }
            return false;
        }
    };
}
