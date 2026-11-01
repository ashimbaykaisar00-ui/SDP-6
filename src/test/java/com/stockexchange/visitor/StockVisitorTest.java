package com.stockexchange.visitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Fixture, prices AAPL 120, MSFT 240, UST10Y 102 (% of face), VOO 60:
 * ACC-1 = AAPL 10x120 (1200) + bond 2x1000x102% (2040) + call AAPL strike 100, 1 contract (100x(120-100) = 2000) = 5240
 * ACC-2 = MSFT 5x240 (1200) + VOO 20x60 (1200) = 2400 ; portfolio = 7640.
 */
class StockVisitorTest {
    private static final Map<String, BigDecimal> PRICES = Map.of(
            "AAPL", new BigDecimal("120"), "MSFT", new BigDecimal("240"),
            "UST10Y", new BigDecimal("102"), "VOO", new BigDecimal("60"));

    private final TotalValueVisitor valuation = new TotalValueVisitor(s -> Optional.ofNullable(PRICES.get(s)));

    private static Account accountOne() {
        return new Account("ACC-1", List.of(
                new Stock("AAPL", 10, new BigDecimal("100.00")),
                new Bond("UST10Y", 2, new BigDecimal("1000"), new BigDecimal("0.04"), 5),
                new OptionContract("AAPL", 1, new BigDecimal("100"), true)));
    }

    private static Account accountTwo() {
        return new Account("ACC-2", List.of(
                new Stock("MSFT", 5, new BigDecimal("200.00")),
                new Fund("VOO", 20, new BigDecimal("50.00"), 0.5)));
    }

    private static Portfolio portfolio() {
        return new Portfolio("Alice", List.of(accountOne(), accountTwo()));
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    void totalValueOfWholePortfolio() {
        assertDecimal("7640", portfolio().accept(valuation));
    }

    @Test
    void totalValueOfASingleAccountSubtree() {
        assertDecimal("5240", accountOne().accept(valuation));
        assertDecimal("2400", accountTwo().accept(valuation));
    }

    @Test
    void totalValueFallsBackWhenThereIsNoQuote() {
        TotalValueVisitor noQuotes = new TotalValueVisitor(s -> Optional.empty());
        // stock at cost 1000 + bond at par 2000 + call at spot = strike (intrinsic 0)
        assertDecimal("3000", accountOne().accept(noQuotes));
    }

    @Test
    void putOptionIsWorthItsIntrinsicValue() {
        OptionContract put = new OptionContract("AAPL", 2, new BigDecimal("150"), false);
        assertDecimal("6000", put.accept(valuation));          // 2 x 100 x (150 - 120)
        OptionContract worthless = new OptionContract("AAPL", 1, new BigDecimal("100"), false);
        assertDecimal("0", worthless.accept(valuation));
    }

    @Test
    void riskScoreIsValueWeightedAndAccumulatesAcrossTheTree() {
        // leaf risks: stock 80, bond 10+2*5 = 20, option 95, fund 20+60*0.5 = 50
        RiskScoreVisitor risk = new RiskScoreVisitor(valuation);
        double score = portfolio().accept(risk);
        // (80*1200 + 20*2040 + 95*2000 + 80*1200 + 50*1200) / 7640 = 482800 / 7640
        assertEquals(482800.0 / 7640.0, score, 1e-6);
        assertEquals(7640.0, risk.totalValue(), 1e-6);
        assertEquals(5, risk.leafCount());
    }

    @Test
    void riskScoreOfEachContainerIsComputedOnlyFromItsOwnLeaves() {
        RiskScoreVisitor risk = new RiskScoreVisitor(valuation);
        Portfolio portfolio = portfolio();
        risk.visit(portfolio.accounts().get(0));
        double second = risk.visit(portfolio.accounts().get(1));
        assertEquals(65.0, second, 1e-6);                           // (80*1200 + 50*1200) / 2400
        assertEquals(326800.0 / 5240.0, new RiskScoreVisitor(valuation).visit(accountOne()), 1e-6);
    }

    @Test
    void filterReturnsLeavesFromAnyDepth() {
        List<StockElement> aapl = portfolio().accept(AssetFilterVisitor.bySymbol("AAPL"));
        assertEquals(2, aapl.size());                               // the stock and the option
        assertTrue(aapl.get(0) instanceof Stock);
        assertTrue(aapl.get(1) instanceof OptionContract);
        assertTrue(portfolio().accept(AssetFilterVisitor.bySymbol("NONE")).isEmpty());
        assertTrue(accountOne().accept(AssetFilterVisitor.bySymbol("MSFT")).isEmpty());
    }

    @Test
    void filterAcceptsAnyCondition() {
        List<StockElement> bondsAndFunds = portfolio().accept(
                new AssetFilterVisitor(leaf -> leaf instanceof Bond || leaf instanceof Fund));
        assertEquals(2, bondsAndFunds.size());
    }

    @Test
    void xmlExportNestsContainersAndLeaves() {
        Portfolio small = new Portfolio("Bob", List.of(new Account("A-1", List.of(
                new Stock("AAPL", 2, new BigDecimal("10.50")),
                new OptionContract("AAPL", 1, new BigDecimal("100"), false)))));
        String expected = "<portfolio owner=\"Bob\"><account id=\"A-1\">"
                + "<stock symbol=\"AAPL\" shares=\"2\" avgCost=\"10.50\"/>"
                + "<option symbol=\"AAPL\" contracts=\"1\" strike=\"100\" type=\"put\"/>"
                + "</account></portfolio>";
        assertEquals(expected, small.accept(new XmlExportVisitor()));
    }

    @Test
    void xmlExportEscapesSpecialCharacters() {
        Account tricky = new Account("a&b <\"x\">", List.of());
        assertEquals("<account id=\"a&amp;b &lt;&quot;x&quot;&gt;\"></account>", tricky.accept(new XmlExportVisitor()));
    }

    @Test
    void xmlExportOfTheFullTreeIsBalanced() {
        String xml = portfolio().accept(new XmlExportVisitor());
        assertEquals(1, count(xml, "<portfolio"));
        assertEquals(1, count(xml, "</portfolio>"));
        assertEquals(2, count(xml, "<account"));
        assertEquals(2, count(xml, "</account>"));
        assertEquals(5, count(xml, "/>"));                          // five leaves
    }

    @Test
    void doubleDispatchPicksTheVisitMethodOfTheRuntimeClass() {
        StockElement element = new Stock("AAPL", 1, BigDecimal.ONE);   // static type is the interface
        String visited = element.accept(new StockVisitor<String>() {
            public String visit(Portfolio p) { return "portfolio"; }
            public String visit(Account a) { return "account"; }
            public String visit(Stock s) { return "stock"; }
            public String visit(Bond b) { return "bond"; }
            public String visit(Fund f) { return "fund"; }
            public String visit(OptionContract o) { return "option"; }
        });
        assertEquals("stock", visited);
    }

    private static int count(String text, String part) {
        return text.split(Pattern.quote(part), -1).length - 1;
    }
}
