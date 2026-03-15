package com.nanotrade.portfolio;

import java.io.Serializable;
import com.nanotrade.cli.TablePrinter;
import com.nanotrade.model.OrderType;
import java.util.HashMap;
import java.util.Map;

public class Portfolio implements Serializable {
    private static final long serialVersionUID = 1L;
    private String traderId;
    private Map<String, Integer> holdings;
    private Map<String, Double> totalCostBasis;
    private double realizedPnL;
    private double cashReceived; // from sells with no prior holdings
    private double cashSpent; // total spent on buys

    public Portfolio(String traderId) {
        this.traderId = traderId;
        this.holdings = new HashMap<>();
        this.totalCostBasis = new HashMap<>();
        this.realizedPnL = 0.0;
        this.cashReceived = 0.0;
        this.cashSpent = 0.0;
    }

    public void recordTrade(OrderType side, String symbol, int quantity, double executionPrice) {
        symbol = symbol.toUpperCase();
        double tradeValue = executionPrice * quantity;

        if (side == OrderType.BUY) {
            cashSpent += tradeValue;
            int current = holdings.getOrDefault(symbol, 0);
            double currentCost = totalCostBasis.getOrDefault(symbol, 0.0);
            holdings.put(symbol, current + quantity);
            totalCostBasis.put(symbol, currentCost + tradeValue);

        } else {
            int current = holdings.getOrDefault(symbol, 0);

            if (current <= 0) {
                // Seller never held this stock — just track cash received
                cashReceived += tradeValue;
            } else {
                double currentCost = totalCostBasis.getOrDefault(symbol, 0.0);
                double avgCost = currentCost / current;
                double pnl = (executionPrice - avgCost) * quantity;
                realizedPnL += pnl;
                cashReceived += tradeValue;

                int newHoldings = Math.max(0, current - quantity);
                double newCost = newHoldings * avgCost;
                holdings.put(symbol, newHoldings);
                totalCostBasis.put(symbol, newCost);
            }
        }
    }

    public void display() {
        TablePrinter t = new TablePrinter(46);
        t.top();
        t.row("Portfolio: " + traderId);
        t.mid();
        t.row(String.format("%-8s  %-8s  %-12s  %-8s", "Symbol", "Shares", "Avg Cost", "Status"));
        t.mid();

        boolean hasPositions = false;
        for (Map.Entry<String, Integer> entry : holdings.entrySet()) {
            String sym = entry.getKey();
            int shares = entry.getValue();
            if (shares <= 0)
                continue;
            hasPositions = true;
            double avg = totalCostBasis.getOrDefault(sym, 0.0) / shares;
            t.row(String.format("%-8s  %-8d  $%-11.2f  %-8s", sym, shares, avg, "OPEN"));
        }
        if (!hasPositions)
            t.row("No open positions.");

        t.mid();
        t.row(String.format("%-22s $%.2f", "Cash Spent on Buys :", cashSpent));
        t.row(String.format("%-22s $%.2f", "Cash from Sales    :", cashReceived));
        t.row(String.format("%-22s $%.2f", "Realized P&L       :", realizedPnL));
        t.bot();
    }

    public String getTraderId() {
        return traderId;
    }

    public int getHoldings(String symbol) {
        return holdings.getOrDefault(symbol.toUpperCase(), 0);
    }

    public double getRealizedPnL() {
        return realizedPnL;
    }

    public double getCashReceived() {
        return cashReceived;
    }

    public double getCashSpent() {
        return cashSpent;
    }
}