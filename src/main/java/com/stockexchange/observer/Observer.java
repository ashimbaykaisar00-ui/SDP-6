package com.stockexchange.observer;

/** Generic listener interface. */
@FunctionalInterface
public interface Observer<E extends Event> {
    void onEvent(E event);
}
