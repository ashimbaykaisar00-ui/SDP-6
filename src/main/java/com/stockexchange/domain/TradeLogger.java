package com.stockexchange.domain;

import com.stockexchange.observer.Event;
import com.stockexchange.observer.EventType;
import com.stockexchange.observer.Observer;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

/** Listener 4: audit trail of orders, alerts and risk reports; also counts traded quantity. */
public final class TradeLogger implements Observer<Event> {
    private final Queue<String> lines = new ConcurrentLinkedQueue<>();
    private final LongAdder tradedQuantity = new LongAdder();

    @Override
    public void onEvent(Event event) {
        if (event.type() == EventType.ORDER_PLACED) {
            tradedQuantity.add(event.get(Event.QUANTITY).longValueExact());
        }
        lines.add(event.type() + " " + event);
    }

    public List<String> lines() {
        return List.copyOf(lines);
    }

    public long tradedQuantity() {
        return tradedQuantity.sum();
    }
}
