package com.stockexchange;

import com.stockexchange.domain.MovingAverageCalculator;
import com.stockexchange.domain.OrderBook;
import com.stockexchange.domain.PortfolioTracker;
import com.stockexchange.domain.PriceAlertNotifier;
import com.stockexchange.domain.PriceFeed;
import com.stockexchange.domain.RiskManagementService;
import com.stockexchange.domain.TradeLogger;
import com.stockexchange.observer.EventType;
import com.stockexchange.visitor.StockElement;
import com.stockexchange.visitor.composite.Account;
import com.stockexchange.visitor.composite.Portfolio;
import com.stockexchange.visitor.leaf.Bond;
import com.stockexchange.visitor.leaf.Fund;
import com.stockexchange.visitor.leaf.OptionContract;
import com.stockexchange.visitor.leaf.Stock;
import com.stockexchange.visitor.visitors.AssetFilterVisitor;
import com.stockexchange.visitor.visitors.RiskScoreVisitor;
import com.stockexchange.visitor.visitors.TotalValueVisitor;
import com.stockexchange.visitor.visitors.XmlExportVisitor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The only place where collaborators are created and wired (constructor injection only).
 * Also hosts the producer threads and the demo {@code main}.
 */
public final class CompositionRoot {
    private static final int AVERAGE_WINDOW = 5;
    private static final int ORDER_QUANTITY = 10;
    private static final long SHUTDOWN_SECONDS = 10;

    private static final Map<String, BigDecimal> BASE_PRICES = Map.of(
            "AAPL", new BigDecimal("150"),
            "MSFT", new BigDecimal("300"),
            "TSLA", new BigDecimal("200"),
            "UST10Y", new BigDecimal("100"),
            "VOO", new BigDecimal("400"));

    private static final Map<String, BigDecimal> ALERT_THRESHOLDS = Map.of(
            "AAPL", new BigDecimal("200"),
            "MSFT", new BigDecimal("400"),
            "TSLA", new BigDecimal("300"));

    /** The fully wired system. */
    public record Exchange(
            PriceFeed priceFeed,
            OrderBook orderBook,
            PortfolioTracker tracker,
            PriceAlertNotifier alerts,
            MovingAverageCalculator movingAverage,
            TradeLogger logger,
            RiskManagementService riskService,
            Portfolio portfolio,
            TotalValueVisitor valuation) {
    }

    private CompositionRoot() {
    }

    public static Exchange assemble() {
        PriceFeed priceFeed = new PriceFeed();
        OrderBook orderBook = new OrderBook();

        PortfolioTracker tracker = new PortfolioTracker();
        PriceAlertNotifier alerts = new PriceAlertNotifier(ALERT_THRESHOLDS);
        MovingAverageCalculator movingAverage = new MovingAverageCalculator(AVERAGE_WINDOW, BASE_PRICES.keySet());
        TradeLogger logger = new TradeLogger();

        Portfolio portfolio = samplePortfolio();
        TotalValueVisitor valuation = new TotalValueVisitor(tracker::priceOf);
        RiskManagementService riskService = new RiskManagementService(portfolio, valuation);

        // Order matters on ticks: the tracker must hold the new price before alerts are inspected.
        priceFeed.subscribe(EventType.PRICE_TICK, tracker);
        priceFeed.subscribe(EventType.PRICE_TICK, movingAverage);
        priceFeed.subscribe(EventType.PRICE_TICK, alerts);
        movingAverage.subscribe(EventType.AVERAGE_UPDATED, tracker);
        orderBook.subscribe(EventType.ORDER_PLACED, logger);
        alerts.subscribe(EventType.PRICE_ALERT, logger);
        alerts.subscribe(EventType.PRICE_ALERT, riskService);
        riskService.subscribe(EventType.RISK_REPORT, logger);

        return new Exchange(priceFeed, orderBook, tracker, alerts, movingAverage, logger,
                riskService, portfolio, valuation);
    }

    /**
     * Runs {@code producers} threads; each publishes {@code eventsPerProducer} ticks and as many orders.
     * Events are delivered synchronously, so when this returns nothing is in flight. Shutdown is
     * graceful: finish queued work, wait, force-stop only after a timeout.
     */
    public static void runProducers(Exchange exchange, int producers, int eventsPerProducer)
            throws InterruptedException {
        if (producers <= 0) {
            throw new IllegalArgumentException("producers must be positive: " + producers);
        }
        List<String> symbols = BASE_PRICES.keySet().stream().sorted().toList();
        ExecutorService pool = Executors.newFixedThreadPool(producers, namedThreads("producer-"));
        try {
            List<Future<?>> running = new ArrayList<>();
            for (int producer = 0; producer < producers; producer++) {
                int id = producer;
                running.add(pool.submit(() -> produce(exchange, id, eventsPerProducer, symbols)));
            }
            for (Future<?> future : running) {
                try {
                    future.get();
                } catch (ExecutionException failure) {
                    throw new IllegalStateException("Producer failed", failure.getCause());
                }
            }
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    public static int orderQuantity() {
        return ORDER_QUANTITY;
    }

    private static void produce(Exchange exchange, int producerId, int count, List<String> symbols) {
        for (int i = 0; i < count; i++) {
            String symbol = symbols.get((producerId + i) % symbols.size());
            BigDecimal price = BASE_PRICES.get(symbol).add(BigDecimal.valueOf(i % 5));
            exchange.priceFeed().tick(symbol, price);
            exchange.orderBook().placeOrder("ACC-" + producerId, symbol, ORDER_QUANTITY, price);
        }
    }

    private static ThreadFactory namedThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return task -> new Thread(task, prefix + counter.incrementAndGet());
    }

    private static Portfolio samplePortfolio() {
        Account growth = new Account("ACC-GROWTH", List.of(
                new Stock("AAPL", 10, new BigDecimal("100.00")),
                new Stock("TSLA", 4, new BigDecimal("180.00")),
                new OptionContract("AAPL", 1, new BigDecimal("180"), true)));
        Account balanced = new Account("ACC-BALANCED", List.of(
                new Stock("MSFT", 5, new BigDecimal("200.00")),
                new Bond("UST10Y", 2, new BigDecimal("1000"), new BigDecimal("0.04"), 5),
                new Fund("VOO", 20, new BigDecimal("350.00"), 0.5)));
        return new Portfolio("Alice", List.of(growth, balanced));
    }

    /** Demo. Arguments: producer threads (default 4), events per producer (default 1000). */
    public static void main(String[] args) throws InterruptedException {
        int producers = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int events = args.length > 1 ? Integer.parseInt(args[1]) : 1000;

        Exchange exchange = assemble();
        runProducers(exchange, producers, events);
        System.out.printf("%d producers x %d events done. Ticks: %d, traded quantity: %d%n",
                producers, events, exchange.tracker().ticksSeen(), exchange.logger().tradedQuantity());
        System.out.println("AAPL moving average: " + exchange.tracker().averageOf("AAPL").orElseThrow());

        exchange.priceFeed().tick("AAPL", new BigDecimal("260")); // crosses 200 -> alert -> inspection
        exchange.logger().lines().stream()
                .filter(line -> line.startsWith("PRICE_ALERT") || line.startsWith("RISK_REPORT"))
                .forEach(System.out::println);

        Portfolio portfolio = exchange.portfolio();
        RiskScoreVisitor risk = new RiskScoreVisitor(exchange.valuation());
        System.out.println("Portfolio value: " + portfolio.accept(exchange.valuation()));
        System.out.println("Portfolio risk score: " + portfolio.accept(risk) + " over " + risk.leafCount() + " leaves");
        List<StockElement> aapl = portfolio.accept(AssetFilterVisitor.bySymbol("AAPL"));
        System.out.println("AAPL holdings: " + aapl);
        System.out.println(portfolio.accept(new XmlExportVisitor()));
    }
}
