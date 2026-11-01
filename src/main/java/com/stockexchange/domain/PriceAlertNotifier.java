package com.stockexchange.domain;

import com.stockexchange.observer.Event;
import com.stockexchange.observer.EventType;
import com.stockexchange.observer.Observer;
import com.stockexchange.observer.Subject;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Listener 1, and a subject (chain link): raises one PRICE_ALERT each time a price crosses its
 * threshold upwards and re-arms once the price falls below it again.
 */
public final class PriceAlertNotifier extends Subject<Event> implements Observer<Event> {
    private static final String SOURCE = "price-alert";

    private final Map<String, Rule> rules = new HashMap<>();

    public PriceAlertNotifier(Map<String, BigDecimal> thresholds) {
        thresholds.forEach((symbol, threshold) -> rules.put(symbol, new Rule(threshold)));
    }

    @Override
    public void onEvent(Event tick) {
        Rule rule = rules.get(tick.symbol());
        if (rule == null) {
            return;
        }
        BigDecimal price = tick.get(Event.PRICE);
        if (price.compareTo(rule.threshold) >= 0) {
            if (rule.above.compareAndSet(false, true)) {
                publish(new Event(EventType.PRICE_ALERT, tick.symbol(), SOURCE,
                        Map.of(Event.PRICE, price, Event.THRESHOLD, rule.threshold)));
            }
        } else {
            rule.above.set(false);
        }
    }

    private static final class Rule {
        private final BigDecimal threshold;
        private final AtomicBoolean above = new AtomicBoolean(false);

        private Rule(BigDecimal threshold) {
            this.threshold = threshold;
        }
    }
}
