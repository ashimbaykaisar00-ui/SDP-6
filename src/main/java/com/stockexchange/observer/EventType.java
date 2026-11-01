package com.stockexchange.observer;

/** Every kind of event in the system. Listeners subscribe per type. */
public enum EventType {
    PRICE_TICK,
    ORDER_PLACED,
    PRICE_ALERT,
    AVERAGE_UPDATED,
    RISK_REPORT
}
