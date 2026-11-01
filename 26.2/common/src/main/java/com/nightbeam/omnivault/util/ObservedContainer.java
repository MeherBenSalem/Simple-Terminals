package com.nightbeam.omnivault.util;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A {@link SimpleContainer} that tells its listeners whenever it changes, as vanilla's own one no longer does. */
public class ObservedContainer extends SimpleContainer {
    private final List<Consumer<Container>> listeners = new ArrayList<>();

    public ObservedContainer(int size) {
        super(size);
    }

    public void addListener(Consumer<Container> listener) {
        listeners.add(listener);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        for (Consumer<Container> listener : listeners) {
            listener.accept(this);
        }
    }
}
