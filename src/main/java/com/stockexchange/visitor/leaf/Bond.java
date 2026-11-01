package com.stockexchange.visitor.leaf;

import com.stockexchange.visitor.StockElement;
import com.stockexchange.visitor.StockVisitor;
import java.math.BigDecimal;
import java.util.Objects;

/** Quoted in percent of face value; couponRate is annual (0.04 = 4%). */
public record Bond(String symbol, int units, BigDecimal faceValue, BigDecimal couponRate, int yearsToMaturity)
        implements StockElement {
    public Bond {
        Objects.requireNonNull(symbol);
        Objects.requireNonNull(faceValue);
        Objects.requireNonNull(couponRate);
    }

    @Override
    public <R> R accept(StockVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
