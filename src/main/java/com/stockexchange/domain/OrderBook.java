package com.stockexchange.domain;

import com.stockexchange.observer.Event;
import com.stockexchange.observer.EventType;
import com.stockexchange.observer.Subject;
import java.math.BigDecimal;
import java.util.Map;

/** Subject 2: the stream of executed orders. The event source is the account that placed the order. */
public final class OrderBook extends Subject<Event> {

    public void placeOrder(String accountId, String symbol, int quantity, BigDecimal price) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
        publish(new Event(EventType.ORDER_PLACED, symbol, accountId,
                Map.of(Event.QUANTITY, BigDecimal.valueOf(quantity), Event.PRICE, price)));
    }
}
