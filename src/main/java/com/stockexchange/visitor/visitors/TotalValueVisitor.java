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
import java.util.Optional;
import java.util.function.Function;

/**
 * Numeric visitor: market value of any subtree. Stateless. Prices come from an injected lookup;
 * when a symbol has no quote yet, stocks and funds fall back to cost, bonds to par, options to strike.
 */
public final class TotalValueVisitor implements StockVisitor<BigDecimal> {
    private static final BigDecimal PAR = BigDecimal.valueOf(100);
    private static final BigDecimal CONTRACT_SIZE = BigDecimal.valueOf(100);

    private final Function<String, Optional<BigDecimal>> priceLookup;

    public TotalValueVisitor(Function<String, Optional<BigDecimal>> priceLookup) {
        this.priceLookup = priceLookup;
    }

    @Override
    public BigDecimal visit(Portfolio portfolio) {
        return sum(portfolio.accounts());
    }

    @Override
    public BigDecimal visit(Account account) {
        return sum(account.holdings());
    }

    @Override
    public BigDecimal visit(Stock stock) {
        return priceOr(stock.symbol(), stock.avgCost()).multiply(BigDecimal.valueOf(stock.shares()));
    }

    @Override
    public BigDecimal visit(Bond bond) {
        BigDecimal percentOfFace = priceOr(bond.symbol(), PAR);
        return bond.faceValue().multiply(BigDecimal.valueOf(bond.units())).multiply(percentOfFace).movePointLeft(2);
    }

    @Override
    public BigDecimal visit(Fund fund) {
        return priceOr(fund.symbol(), fund.costBasis()).multiply(BigDecimal.valueOf(fund.units()));
    }

    /** Intrinsic value: call = max(0, spot - strike), put = max(0, strike - spot), times 100 per contract. */
    @Override
    public BigDecimal visit(OptionContract option) {
        BigDecimal spot = priceOr(option.symbol(), option.strike());
        BigDecimal intrinsic = option.call() ? spot.subtract(option.strike()) : option.strike().subtract(spot);
        return intrinsic.max(BigDecimal.ZERO).multiply(CONTRACT_SIZE).multiply(BigDecimal.valueOf(option.contracts()));
    }

    private BigDecimal priceOr(String symbol, BigDecimal fallback) {
        return priceLookup.apply(symbol).orElse(fallback);
    }

    private BigDecimal sum(List<? extends StockElement> children) {
        return children.stream().map(child -> child.accept(this)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
