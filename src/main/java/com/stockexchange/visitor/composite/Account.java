package com.stockexchange.visitor.composite;

import com.stockexchange.visitor.StockElement;
import com.stockexchange.visitor.StockVisitor;
import java.util.List;
import java.util.Objects;

/** Level 2 container. Holds leaves. */
public record Account(String id, List<StockElement> holdings) implements StockElement {
    public Account {
        Objects.requireNonNull(id);
        holdings = List.copyOf(holdings);
    }

    @Override
    public <R> R accept(StockVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
