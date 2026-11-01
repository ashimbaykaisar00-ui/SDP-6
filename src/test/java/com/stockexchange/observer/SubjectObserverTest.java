package com.stockexchange.observer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stockexchange.domain.MovingAverageCalculator;
import com.stockexchange.domain.OrderBook;
import com.stockexchange.domain.PortfolioTracker;
import com.stockexchange.domain.PriceAlertNotifier;
import com.stockexchange.domain.PriceFeed;
import com.stockexchange.domain.TradeLogger;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubjectObserverTest {
    private PriceFeed feed;

    @BeforeEach
    void setUp() {
        feed = new PriceFeed();
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, d(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    void subscriberReceivesEventsOfItsType() {
        List<Event> received = new ArrayList<>();
        feed.subscribe(EventType.PRICE_TICK, received::add);
        feed.tick("AAPL", d("10"));
        assertEquals(1, received.size());
        assertEquals("AAPL", received.get(0).symbol());
        assertDecimal("10", received.get(0).get(Event.PRICE));
    }

    @Test
    void subscriberDoesNotReceiveOtherEventTypes() {
        List<Event> received = new ArrayList<>();
        feed.subscribe(EventType.ORDER_PLACED, received::add);
        feed.tick("AAPL", d("10"));
        assertTrue(received.isEmpty());
    }

    @Test
    void unsubscribeStopsDelivery() {
        List<Event> received = new ArrayList<>();
        Observer<Event> observer = received::add;
        feed.subscribe(EventType.PRICE_TICK, observer);
        feed.tick("AAPL", d("1"));
        feed.unsubscribe(EventType.PRICE_TICK, observer);
        feed.tick("AAPL", d("2"));
        assertEquals(1, received.size());
    }

    @Test
    void observerCanUnsubscribeItselfInsideItsOwnHandler() {
        List<String> calls = new ArrayList<>();
        List<Observer<Event>> self = new ArrayList<>();
        Observer<Event> once = event -> {
            calls.add("once");
            feed.unsubscribe(EventType.PRICE_TICK, self.get(0));
        };
        self.add(once);
        feed.subscribe(EventType.PRICE_TICK, once);
        feed.subscribe(EventType.PRICE_TICK, event -> calls.add("always"));
        feed.tick("AAPL", d("1"));
        feed.tick("AAPL", d("1"));
        assertEquals(List.of("once", "always", "always"), calls);
    }

    @Test
    void observerSubscribedInsideHandlerStartsWithTheNextEvent() {
        List<String> calls = new ArrayList<>();
        Observer<Event> late = event -> calls.add("late");
        List<Boolean> added = new ArrayList<>();
        feed.subscribe(EventType.PRICE_TICK, event -> {
            calls.add("first");
            if (added.isEmpty()) {
                added.add(true);
                feed.subscribe(EventType.PRICE_TICK, late);
            }
        });
        feed.tick("AAPL", d("1"));
        assertEquals(List.of("first"), calls);
        feed.tick("AAPL", d("1"));
        assertEquals(List.of("first", "first", "late"), calls);
    }

    @Test
    void failingObserverDoesNotStopTheOthers() {
        List<String> calls = new ArrayList<>();
        feed.subscribe(EventType.PRICE_TICK, event -> {
            throw new IllegalStateException("boom");
        });
        feed.subscribe(EventType.PRICE_TICK, event -> calls.add("healthy"));
        feed.tick("AAPL", d("1"));
        assertEquals(List.of("healthy"), calls);
    }

    @Test
    void eventsAreImmutable() {
        Map<String, BigDecimal> mutable = new HashMap<>(Map.of(Event.PRICE, d("10")));
        Event event = new Event(EventType.PRICE_TICK, "AAPL", "test", mutable);
        mutable.put(Event.PRICE, d("999"));
        assertDecimal("10", event.get(Event.PRICE));
        assertThrows(UnsupportedOperationException.class, () -> event.data().put("x", d("1")));
    }

    @Test
    void trackerKeepsLatestPrice() {
        PortfolioTracker tracker = new PortfolioTracker();
        feed.subscribe(EventType.PRICE_TICK, tracker);
        feed.tick("AAPL", d("10"));
        feed.tick("AAPL", d("12"));
        assertDecimal("12", tracker.priceOf("AAPL").orElseThrow());
        assertTrue(tracker.priceOf("MSFT").isEmpty());
        assertEquals(2, tracker.ticksSeen());
    }

    @Test
    void movingAverageChainRepublishesAggregatedData() {
        MovingAverageCalculator average = new MovingAverageCalculator(3, Set.of("AAPL"));
        PortfolioTracker tracker = new PortfolioTracker();
        List<BigDecimal> published = new ArrayList<>();
        feed.subscribe(EventType.PRICE_TICK, average);
        average.subscribe(EventType.AVERAGE_UPDATED, tracker);
        average.subscribe(EventType.AVERAGE_UPDATED, event -> published.add(event.get(Event.AVERAGE)));

        for (String price : List.of("10", "20", "30", "40")) {
            feed.tick("AAPL", d(price));
        }
        // windows: [10] [10,20] [10,20,30] [20,30,40]
        assertDecimal("10", published.get(0));
        assertDecimal("15", published.get(1));
        assertDecimal("20", published.get(2));
        assertDecimal("30", published.get(3));
        assertDecimal("30", tracker.averageOf("AAPL").orElseThrow());
    }

    @Test
    void alertFiresOncePerUpwardCrossing() {
        PriceAlertNotifier alerts = new PriceAlertNotifier(Map.of("AAPL", d("100")));
        List<Event> fired = new ArrayList<>();
        feed.subscribe(EventType.PRICE_TICK, alerts);
        alerts.subscribe(EventType.PRICE_ALERT, fired::add);

        for (String price : List.of("90", "110", "120", "80", "105")) {
            feed.tick("AAPL", d(price));
        }
        assertEquals(2, fired.size());
        assertDecimal("110", fired.get(0).get(Event.PRICE));
        assertDecimal("105", fired.get(1).get(Event.PRICE));
    }

    @Test
    void tradeLoggerCountsQuantityAndKeepsLines() {
        OrderBook book = new OrderBook();
        TradeLogger logger = new TradeLogger();
        book.subscribe(EventType.ORDER_PLACED, logger);
        book.placeOrder("A", "AAPL", 10, d("1"));
        book.placeOrder("B", "MSFT", 5, d("1"));
        assertEquals(15, logger.tradedQuantity());
        assertEquals(2, logger.lines().size());
        assertThrows(IllegalArgumentException.class, () -> book.placeOrder("A", "AAPL", 0, d("1")));
    }
}
