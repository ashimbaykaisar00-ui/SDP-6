package com.stockexchange.visitor.leaf;

import com.stockexchange.visitor.StockElement;
import com.stockexchange.visitor.StockVisitor;
import java.math.BigDecimal;
import java.util.Objects;

/** An ETF / investment fund. equityShare is the fraction (0..1) invested in stocks. */
public record Fund(String symbol, int units, BigDecimal costBasis, double equityShare) implements StockElement {
    public Fund {
        Objects.requireNonNull(symbol);
        Objects.requireNonNull(costBasis);
        if (equityShare < 0 || equityShare > 1) {
            throw new IllegalArgumentException("equityShare must be in [0,1]: " + equityShare);
        }
    }

    @Override
    public <R> R accept(StockVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
