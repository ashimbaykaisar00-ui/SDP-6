package com.stockexchange.visitor.visitors;

import com.stockexchange.visitor.StockElement;
import com.stockexchange.visitor.StockVisitor;
import com.stockexchange.visitor.composite.Account;
import com.stockexchange.visitor.composite.Portfolio;
import com.stockexchange.visitor.leaf.Bond;
import com.stockexchange.visitor.leaf.Fund;
import com.stockexchange.visitor.leaf.OptionContract;
import com.stockexchange.visitor.leaf.Stock;
import java.math.BigDecimal;
import java.util.List;

/**
 * Stateful visitor: accumulates weighted risk, total value and leaf count across the whole tree.
 * Every leaf adds its (risk x value) to the running totals; a container returns the value-weighted
 * average of just its own subtree (the change in the totals while it walked its children).
 * Because it keeps state, use a NEW instance for every run.
 *
 * <p>Leaf risk 0..100: stock 80, bond 10 + 2 per year (max 50), fund 20 + 60 x equityShare, option 95.
 */
public final class RiskScoreVisitor implements StockVisitor<Double> {
    private static final double STOCK_RISK = 80.0;
    private static final double BOND_BASE_RISK = 10.0;
    private static final double BOND_RISK_PER_YEAR = 2.0;
    private static final double BOND_MAX_RISK = 50.0;
    private static final double FUND_BASE_RISK = 20.0;
    private static final double FUND_EQUITY_RISK = 60.0;
    private static final double OPTION_RISK = 95.0;

    private final TotalValueVisitor valuation;
    private double weightedRisk;
    private double totalValue;
    private int leafCount;

    public RiskScoreVisitor(TotalValueVisitor valuation) {
        this.valuation = valuation;
    }

    public double totalValue() {
        return totalValue;
    }

    public int leafCount() {
        return leafCount;
    }

    @Override
    public Double visit(Portfolio portfolio) {
        return scoreOfChildren(portfolio.accounts());
    }

    @Override
    public Double visit(Account account) {
        return scoreOfChildren(account.holdings());
    }

    @Override
    public Double visit(Stock stock) {
        return accumulate(stock, STOCK_RISK);
    }

    @Override
    public Double visit(Bond bond) {
        return accumulate(bond, Math.min(BOND_MAX_RISK, BOND_BASE_RISK + BOND_RISK_PER_YEAR * bond.yearsToMaturity()));
    }

    @Override
    public Double visit(Fund fund) {
        return accumulate(fund, FUND_BASE_RISK + FUND_EQUITY_RISK * fund.equityShare());
    }

    @Override
    public Double visit(OptionContract option) {
        return accumulate(option, OPTION_RISK);
    }

    private double accumulate(StockElement leaf, double risk) {
        BigDecimal value = leaf.accept(valuation);
        weightedRisk += risk * value.doubleValue();
        totalValue += value.doubleValue();
        leafCount++;
        return risk;
    }

    private double scoreOfChildren(List<? extends StockElement> children) {
        double riskBefore = weightedRisk;
        double valueBefore = totalValue;
        for (StockElement child : children) {
            child.accept(this);
        }
        double value = totalValue - valueBefore;
        return value == 0 ? 0.0 : (weightedRisk - riskBefore) / value;
    }
}
