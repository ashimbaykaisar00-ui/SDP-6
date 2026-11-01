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
import java.util.stream.Collectors;

/** Hand-written XML export (compact). Containers nest their children; leaves are empty elements. */
public final class XmlExportVisitor implements StockVisitor<String> {

    @Override
    public String visit(Portfolio portfolio) {
        return "<portfolio owner=\"" + escape(portfolio.owner()) + "\">" + children(portfolio.accounts()) + "</portfolio>";
    }

    @Override
    public String visit(Account account) {
        return "<account id=\"" + escape(account.id()) + "\">" + children(account.holdings()) + "</account>";
    }

    @Override
    public String visit(Stock stock) {
        return "<stock symbol=\"" + escape(stock.symbol()) + "\" shares=\"" + stock.shares()
                + "\" avgCost=\"" + stock.avgCost().toPlainString() + "\"/>";
    }

    @Override
    public String visit(Bond bond) {
        return "<bond symbol=\"" + escape(bond.symbol()) + "\" units=\"" + bond.units()
                + "\" faceValue=\"" + bond.faceValue().toPlainString()
                + "\" couponRate=\"" + bond.couponRate().toPlainString()
                + "\" yearsToMaturity=\"" + bond.yearsToMaturity() + "\"/>";
    }

    @Override
    public String visit(Fund fund) {
        return "<fund symbol=\"" + escape(fund.symbol()) + "\" units=\"" + fund.units()
                + "\" costBasis=\"" + fund.costBasis().toPlainString()
                + "\" equityShare=\"" + fund.equityShare() + "\"/>";
    }

    @Override
    public String visit(OptionContract option) {
        return "<option symbol=\"" + escape(option.symbol()) + "\" contracts=\"" + option.contracts()
                + "\" strike=\"" + option.strike().toPlainString()
                + "\" type=\"" + (option.call() ? "call" : "put") + "\"/>";
    }

    private String children(List<? extends StockElement> elements) {
        return elements.stream().map(element -> element.accept(this)).collect(Collectors.joining());
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
