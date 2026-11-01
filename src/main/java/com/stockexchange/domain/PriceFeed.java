package com.stockexchange.domain;

import com.stockexchange.observer.Event;
import com.stockexchange.observer.EventType;
import com.stockexchange.observer.Subject;
import java.math.BigDecimal;
import java.util.Map;

/** Subject 1: the stream of market prices. */
public final class PriceFeed extends Subject<Event> {
    private static final String SOURCE = "price-feed";

    public void tick(String symbol, BigDecimal price) {
        publish(new Event(EventType.PRICE_TICK, symbol, SOURCE, Map.of(Event.PRICE, price)));
    }
}
