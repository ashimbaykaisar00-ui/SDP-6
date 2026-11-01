package com.stockexchange.visitor.visitors;

import com.stockexchange.visitor.StockElement;
import com.stockexchange.visitor.StockVisitor;
import com.stockexchange.visitor.composite.Account;
import com.stockexchange.visitor.composite.Portfolio;
import com.stockexchange.visitor.leaf.Bond;
import com.stockexchange.visitor.leaf.Fund;
import com.stockexchange.visitor.leaf.OptionContract;
import com.stockexchange.visitor.leaf.Stock;
import java.util.List;
import java.util.function.Predicate;

/** Filter visitor: returns every leaf in the subtree that satisfies the condition. */
public final class AssetFilterVisitor implements StockVisitor<List<StockElement>> {
    private final Predicate<StockElement> condition;

    public AssetFilterVisitor(Predicate<StockElement> condition) {
        this.condition = condition;
    }

    public static AssetFilterVisitor bySymbol(String symbol) {
        return new AssetFilterVisitor(leaf -> leaf.symbol().equals(symbol));
    }

    @Override
    public List<StockElement> visit(Portfolio portfolio) {
        return collect(portfolio.accounts());
    }

    @Override
    public List<StockElement> visit(Account account) {
        return collect(account.holdings());
    }

    @Override
    public List<StockElement> visit(Stock stock) {
        return keepIfMatches(stock);
    }

    @Override
    public List<StockElement> visit(Bond bond) {
        return keepIfMatches(bond);
    }

    @Override
    public List<StockElement> visit(Fund fund) {
        return keepIfMatches(fund);
    }

    @Override
    public List<StockElement> visit(OptionContract option) {
        return keepIfMatches(option);
    }

    private List<StockElement> keepIfMatches(StockElement leaf) {
        return condition.test(leaf) ? List.of(leaf) : List.of();
    }

    private List<StockElement> collect(List<? extends StockElement> children) {
        return children.stream().flatMap(child -> child.accept(this).stream()).toList();
    }
}
