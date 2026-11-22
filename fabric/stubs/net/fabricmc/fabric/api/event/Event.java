package net.fabricmc.fabric.api.event;

public abstract class Event<T> {
    protected volatile T invoker;

    public T invoker() {
        return invoker;
    }

    public abstract void register(T listener);
}
