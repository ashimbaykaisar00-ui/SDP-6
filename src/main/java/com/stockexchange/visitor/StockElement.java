package com.stockexchange.visitor;

/** A node of the portfolio tree. Its only behaviour is accepting a visitor (first dispatch). */
public interface StockElement {
    <R> R accept(StockVisitor<R> visitor);

    /** Ticker of a leaf; containers have none. Leaf records override it with their own accessor. */
    default String symbol() {
        return "";
    }
}
