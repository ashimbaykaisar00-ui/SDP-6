package com.stockexchange.visitor.leaf;

import com.stockexchange.visitor.StockElement;
import com.stockexchange.visitor.StockVisitor;
import java.math.BigDecimal;
import java.util.Objects;

public record Stock(String symbol, int shares, BigDecimal avgCost) implements StockElement {
    public Stock {
        Objects.requireNonNull(symbol);
        Objects.requireNonNull(avgCost);
    }

    @Override
    public <R> R accept(StockVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
