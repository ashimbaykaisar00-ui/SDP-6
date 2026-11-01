package com.stockexchange.visitor.leaf;

import com.stockexchange.visitor.StockElement;
import com.stockexchange.visitor.StockVisitor;
import java.math.BigDecimal;
import java.util.Objects;

/** Option on the stock {@code symbol} (100 shares per contract). call=true is a call, false a put. */
public record OptionContract(String symbol, int contracts, BigDecimal strike, boolean call)
        implements StockElement {
    public OptionContract {
        Objects.requireNonNull(symbol);
        Objects.requireNonNull(strike);
    }

    @Override
    public <R> R accept(StockVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
