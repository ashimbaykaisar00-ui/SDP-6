package com.stockexchange.visitor;

import com.stockexchange.visitor.composite.Account;
import com.stockexchange.visitor.composite.Portfolio;
import com.stockexchange.visitor.leaf.Bond;
import com.stockexchange.visitor.leaf.Fund;
import com.stockexchange.visitor.leaf.OptionContract;
import com.stockexchange.visitor.leaf.Stock;

/** One visit method per concrete element: the second dispatch of double dispatch. */
public interface StockVisitor<R> {
    R visit(Portfolio portfolio);

    R visit(Account account);

    R visit(Stock stock);

    R visit(Bond bond);

    R visit(Fund fund);

    R visit(OptionContract option);
}
