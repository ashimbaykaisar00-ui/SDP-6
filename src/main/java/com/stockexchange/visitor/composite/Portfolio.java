package com.stockexchange.visitor.composite;

import com.stockexchange.visitor.StockElement;
import com.stockexchange.visitor.StockVisitor;
import java.util.List;
import java.util.Objects;

/** Level 1 container (root). */
public record Portfolio(String owner, List<Account> accounts) implements StockElement {
    public Portfolio {
        Objects.requireNonNull(owner);
        accounts = List.copyOf(accounts);
    }

    @Override
    public <R> R accept(StockVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
