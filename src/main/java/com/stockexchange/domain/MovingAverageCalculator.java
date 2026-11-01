package com.stockexchange.domain;

import com.stockexchange.observer.Event;
import com.stockexchange.observer.EventType;
import com.stockexchange.observer.Observer;
import com.stockexchange.observer.Subject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Listener 3, observer and subject at once (the chain): consumes PRICE_TICK, republishes
 * AVERAGE_UPDATED. Each symbol has its own window and its own lock, so symbols never block each
 * other, and the result is published after the lock is released.
 */
public final class MovingAverageCalculator extends Subject<Event> implements Observer<Event> {
    private static final String SOURCE = "moving-average";
    private static final int SCALE = 4;

    private final Map<String, Window> windows = new HashMap<>();

    public MovingAverageCalculator(int windowSize, Collection<String> symbols) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be positive: " + windowSize);
        }
        symbols.forEach(symbol -> windows.put(symbol, new Window(symbol, windowSize)));
    }

    @Override
    public void onEvent(Event tick) {
        Window window = windows.get(tick.symbol());
        if (window != null) {
            publish(window.add(tick.get(Event.PRICE)));
        }
    }

    private static final class Window {
        private final String symbol;
        private final int size;
        private final Deque<BigDecimal> prices = new ArrayDeque<>();
        private BigDecimal sum = BigDecimal.ZERO;

        private Window(String symbol, int size) {
            this.symbol = symbol;
            this.size = size;
        }

        private synchronized Event add(BigDecimal price) {
            prices.addLast(price);
            sum = sum.add(price);
            if (prices.size() > size) {
                sum = sum.subtract(prices.removeFirst());
            }
            BigDecimal average = sum.divide(BigDecimal.valueOf(prices.size()), SCALE, RoundingMode.HALF_UP);
            return new Event(EventType.AVERAGE_UPDATED, symbol, SOURCE,
                    Map.of(Event.AVERAGE, average, Event.SAMPLES, BigDecimal.valueOf(prices.size())));
        }
    }
}
