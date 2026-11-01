package com.stockexchange.observer;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable event (push model): it carries every number a listener needs, so listeners never
 * call back into a mutable source. The data map is copied defensively, so the record is deeply immutable.
 */
public record Event(EventType type, String symbol, String source, Map<String, BigDecimal> data) {
    public static final String PRICE = "price";
    public static final String QUANTITY = "quantity";
    public static final String THRESHOLD = "threshold";
    public static final String AVERAGE = "average";
    public static final String SAMPLES = "samples";
    public static final String RISK_SCORE = "riskScore";
    public static final String ACCOUNT_VALUE = "accountValue";

    public Event {
        Objects.requireNonNull(type);
        Objects.requireNonNull(symbol);
        Objects.requireNonNull(source);
        data = Map.copyOf(data);
    }

    public BigDecimal get(String key) {
        BigDecimal value = data.get(key);
        if (value == null) {
            throw new IllegalStateException(type + " event has no '" + key + "'");
        }
        return value;
    }
}
