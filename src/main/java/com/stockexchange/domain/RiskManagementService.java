package com.stockexchange.domain;

import com.stockexchange.observer.Event;
import com.stockexchange.observer.EventType;
import com.stockexchange.observer.Observer;
import com.stockexchange.observer.Subject;
import com.stockexchange.visitor.composite.Account;
import com.stockexchange.visitor.composite.Portfolio;
import com.stockexchange.visitor.visitors.AssetFilterVisitor;
import com.stockexchange.visitor.visitors.RiskScoreVisitor;
import com.stockexchange.visitor.visitors.TotalValueVisitor;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Listener 5, the integration link: PRICE_ALERT (Observer side) -> visitors on the affected
 * accounts (Visitor side) -> RISK_REPORT (a new event). Only accounts that hold the alerted
 * symbol are visited, each as its own subtree, never the whole portfolio.
 */
public final class RiskManagementService extends Subject<Event> implements Observer<Event> {
    private static final String SOURCE = "risk-management";

    private final Portfolio portfolio;
    private final TotalValueVisitor valuation;

    public RiskManagementService(Portfolio portfolio, TotalValueVisitor valuation) {
        this.portfolio = portfolio;
        this.valuation = valuation;
    }

    @Override
    public void onEvent(Event alert) {
        for (Account account : portfolio.accounts()) {
            if (account.accept(AssetFilterVisitor.bySymbol(alert.symbol())).isEmpty()) {
                continue;
            }
            double score = account.accept(new RiskScoreVisitor(valuation));
            BigDecimal value = account.accept(valuation);
            publish(new Event(EventType.RISK_REPORT, alert.symbol(), account.id(),
                    Map.of(Event.RISK_SCORE, BigDecimal.valueOf(score), Event.ACCOUNT_VALUE, value)));
        }
    }
}
