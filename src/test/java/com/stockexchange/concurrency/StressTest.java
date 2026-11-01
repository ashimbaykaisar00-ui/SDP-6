package com.stockexchange.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stockexchange.CompositionRoot;
import com.stockexchange.CompositionRoot.Exchange;
import com.stockexchange.domain.PriceFeed;
import com.stockexchange.observer.Event;
import com.stockexchange.observer.EventType;
import com.stockexchange.observer.Observer;
import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StressTest {

    @Test
    void fourProducersTimesThousandEventsGiveExactCounts() throws Exception {
        Exchange exchange = CompositionRoot.assemble();
        AtomicInteger ticks = new AtomicInteger();
        AtomicInteger averages = new AtomicInteger();
        AtomicInteger orders = new AtomicInteger();
        exchange.priceFeed().subscribe(EventType.PRICE_TICK, event -> ticks.incrementAndGet());
        exchange.movingAverage().subscribe(EventType.AVERAGE_UPDATED, event -> averages.incrementAndGet());
        exchange.orderBook().subscribe(EventType.ORDER_PLACED, event -> orders.incrementAndGet());

        CompositionRoot.runProducers(exchange, 4, 1000);

        assertEquals(4000, ticks.get());
        assertEquals(4000, averages.get());                         // one average per tick: the chain loses nothing
        assertEquals(4000, orders.get());
        assertEquals(4000, exchange.tracker().ticksSeen());
        assertEquals(4000, exchange.logger().lines().size());       // no alerts fire: prices stay below thresholds
        assertEquals(4000L * CompositionRoot.orderQuantity(), exchange.logger().tradedQuantity());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 8})
    void countsAreExactForAnyConfiguredThreadCount(int threads) throws Exception {
        Exchange exchange = CompositionRoot.assemble();
        CompositionRoot.runProducers(exchange, threads, 250);
        assertEquals(threads * 250L, exchange.tracker().ticksSeen());
        assertEquals(threads * 250L * CompositionRoot.orderQuantity(), exchange.logger().tradedQuantity());
    }

    @Test
    void noProducerThreadIsLeftRunningAfterShutdown() throws Exception {
        CompositionRoot.runProducers(CompositionRoot.assemble(), 3, 100);
        // The pool is terminated when runProducers returns; the JVM may need a moment to retire the
        // worker threads themselves, so wait (bounded) instead of checking instantly.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (producerThreadsAlive() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertFalse(producerThreadsAlive(), "producer threads must be gone after graceful shutdown");
    }

    private static boolean producerThreadsAlive() {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(t -> t.getName().startsWith("producer-") && t.isAlive());
    }

    @Test
    void subscribingAndUnsubscribingWhilePublishingNeverBreaksOtherListeners() throws Exception {
        PriceFeed feed = new PriceFeed();
        AtomicLong stable = new AtomicLong();
        feed.subscribe(EventType.PRICE_TICK, event -> stable.incrementAndGet());

        ExecutorService pool = Executors.newFixedThreadPool(5);
        AtomicInteger churnFailures = new AtomicInteger();
        for (int p = 0; p < 4; p++) {
            pool.submit(() -> {
                for (int i = 0; i < 1000; i++) {
                    feed.tick("AAPL", BigDecimal.ONE);
                }
            });
        }
        pool.submit(() -> {
            try {
                for (int i = 0; i < 2000; i++) {
                    Observer<Event> temporary = event -> { };
                    feed.subscribe(EventType.PRICE_TICK, temporary);
                    feed.unsubscribe(EventType.PRICE_TICK, temporary);
                }
            } catch (RuntimeException e) {
                churnFailures.incrementAndGet();
            }
        });
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(0, churnFailures.get());
        assertEquals(4000, stable.get());
    }
}
