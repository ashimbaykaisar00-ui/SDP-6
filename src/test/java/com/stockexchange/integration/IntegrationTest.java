package com.stockexchange.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stockexchange.CompositionRoot;
import com.stockexchange.CompositionRoot.Exchange;
import com.stockexchange.observer.Event;
import com.stockexchange.observer.EventType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntegrationTest {

    @Test
    void alertVisitsOnlyTheAccountsHoldingTheSymbol() {
        Exchange exchange = CompositionRoot.assemble();
        List<Event> reports = new ArrayList<>();
        exchange.riskService().subscribe(EventType.RISK_REPORT, reports::add);

        exchange.priceFeed().tick("AAPL", new BigDecimal("260"));   // threshold is 200

        assertEquals(1, reports.size());                            // AAPL is only in ACC-GROWTH
        Event report = reports.get(0);
        assertEquals("ACC-GROWTH", report.source());
        assertEquals("AAPL", report.symbol());
        // 10 AAPL x 260 + 4 TSLA at cost 180 (no quote yet) + call strike 180 (100 x 80)
        assertEquals(0, new BigDecimal("11320").compareTo(report.get(Event.ACCOUNT_VALUE)));
        assertTrue(report.get(Event.RISK_SCORE).doubleValue() > 0);
    }

    @Test
    void theResultGoesBackIntoTheSystemAsANewEventAndIsLogged() {
        Exchange exchange = CompositionRoot.assemble();
        exchange.priceFeed().tick("MSFT", new BigDecimal("410"));   // threshold is 400

        List<String> log = exchange.logger().lines();
        assertEquals(2, log.size());
        assertTrue(log.get(0).startsWith("PRICE_ALERT"));
        assertTrue(log.get(1).startsWith("RISK_REPORT"));
        assertTrue(log.get(1).contains("ACC-BALANCED"));
    }

    @Test
    void noAlertBelowTheThresholdMeansNoInspection() {
        Exchange exchange = CompositionRoot.assemble();
        exchange.priceFeed().tick("AAPL", new BigDecimal("199"));
        assertTrue(exchange.logger().lines().isEmpty());
    }

    @Test
    void trackerIsUpdatedBeforeTheInspectionReadsIt() {
        Exchange exchange = CompositionRoot.assemble();
        exchange.priceFeed().tick("AAPL", new BigDecimal("260"));
        assertEquals(0, new BigDecimal("260").compareTo(exchange.tracker().priceOf("AAPL").orElseThrow()));
    }
}
