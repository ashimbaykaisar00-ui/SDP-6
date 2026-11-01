package com.stockexchange.observer;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe listener registry and publisher. Listeners subscribe per {@link EventType}.
 *
 * <p>Thread safety without a lock on the whole subject: the map has one entry per event type and
 * is filled in the constructor, never changed afterwards; each entry is a copy-on-write list.
 * A publisher iterates over a snapshot, so listeners may subscribe or unsubscribe at any time,
 * including from inside their own handler or from another thread.
 *
 * <p>Sources extend this class; a listener that also republishes (a chain link) extends it too.
 */
public class Subject<E extends Event> {
    private static final Logger LOG = System.getLogger(Subject.class.getName());

    private final Map<EventType, List<Observer<? super E>>> observers = new EnumMap<>(EventType.class);

    public Subject() {
        for (EventType type : EventType.values()) {
            observers.put(type, new CopyOnWriteArrayList<>());
        }
    }

    public final void subscribe(EventType type, Observer<? super E> observer) {
        observers.get(type).add(observer);
    }

    public final void unsubscribe(EventType type, Observer<? super E> observer) {
        observers.get(type).remove(observer);
    }

    public final void publish(E event) {
        for (Observer<? super E> observer : observers.get(event.type())) {
            try {
                observer.onEvent(event);
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Observer failed on " + event.type(), failure);
            }
        }
    }
}
