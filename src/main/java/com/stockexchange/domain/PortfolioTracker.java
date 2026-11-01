package com.stockexchange.domain;

import com.stockexchange.observer.Event;
import com.stockexchange.observer.Observer;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Listener 2: portfolio monitoring. Keeps the latest price and the latest moving average per symbol.
 * Visitors read their prices from here. Subscribes to PRICE_TICK and AVERAGE_UPDATED.
 */
public final class PortfolioTracker implements Observer<Event> {
    private final Map<String, BigDecimal> prices = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> averages = new ConcurrentHashMap<>();
    private final LongAdder ticksSeen = new LongAdder();

    @Override
    public void onEvent(Event event) {
        switch (event.type()) {
            case PRICE_TICK -> {
                prices.put(event.symbol(), event.get(Event.PRICE));
                ticksSeen.increment();
            }
            case AVERAGE_UPDATED -> averages.put(event.symbol(), event.get(Event.AVERAGE));
            default -> { }
        }
    }

    public Optional<BigDecimal> priceOf(String symbol) {
        return Optional.ofNullable(prices.get(symbol));
    }

    public Optional<BigDecimal> averageOf(String symbol) {
        return Optional.ofNullable(averages.get(symbol));
    }

    public long ticksSeen() {
        return ticksSeen.sum();
    }
}
