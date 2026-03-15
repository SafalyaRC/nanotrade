package com.nanotrade.market;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MarketData implements Serializable {
    private static final long serialVersionUID = 1L;

    private String symbol;

    // OHLCV for current session
    private double openPrice;
    private double highPrice;
    private double lowPrice;
    private double closePrice;   // last traded price
    private long   totalVolume;  // total shares traded
    private double totalValue;   // total $ traded

    // Trade count
    private int tradeCount;

    // Price history — last 20 trades
    private List<PricePoint> priceHistory;

    private long sessionStartTime;
    private boolean hasTraded;

    public MarketData(String symbol) {
        this.symbol           = symbol;
        this.priceHistory     = new ArrayList<>();
        this.sessionStartTime = System.currentTimeMillis();
        this.hasTraded        = false;
        this.highPrice        = Double.MIN_VALUE;
        this.lowPrice         = Double.MAX_VALUE;
    }

    public void recordTrade(double price, int quantity) {
        if (!hasTraded) {
            this.openPrice = price;
            this.hasTraded = true;
        }
        this.closePrice   = price;
        this.totalVolume += quantity;
        this.totalValue  += price * quantity;
        this.tradeCount++;

        if (price > highPrice) highPrice = price;
        if (price < lowPrice)  lowPrice  = price;

        // Keep last 20 price points
        priceHistory.add(new PricePoint(price, quantity));
        if (priceHistory.size() > 20) {
            priceHistory.remove(0);
        }
    }

    public double getVWAP() {
        if (totalVolume == 0) return 0.0;
        return totalValue / totalVolume;
    }

    public double getPriceChange() {
        if (!hasTraded) return 0.0;
        return closePrice - openPrice;
    }

    public double getPriceChangePct() {
        if (!hasTraded || openPrice == 0) return 0.0;
        return (getPriceChange() / openPrice) * 100.0;
    }

    public double getLastPrice() { return hasTraded ? closePrice : 0.0; }

    public List<PricePoint> getPriceHistory() { return priceHistory; }

    public boolean hasTraded() { return hasTraded; }

    public void display() {
        com.nanotrade.cli.TablePrinter t =
            new com.nanotrade.cli.TablePrinter(46);

        t.top();
        t.rowCentered(symbol + " Market Data");
        t.mid();

        if (!hasTraded) {
            t.row("No trades executed yet for " + symbol);
            t.bot();
            return;
        }

        String changeStr = String.format("%s%.2f (%s%.2f%%)",
            getPriceChange() >= 0 ? "+" : "",
            getPriceChange(),
            getPriceChangePct() >= 0 ? "+" : "",
            getPriceChangePct());

        t.row(String.format("%-18s $%.2f", "Last Price :", closePrice));
        t.row(String.format("%-18s %s",    "Change     :", changeStr));
        t.mid();
        t.row(String.format("%-18s $%.2f", "Open  :", openPrice));
        t.row(String.format("%-18s $%.2f", "High  :", highPrice));
        t.row(String.format("%-18s $%.2f", "Low   :", lowPrice));
        t.row(String.format("%-18s $%.2f", "Close :", closePrice));
        t.mid();
        t.row(String.format("%-18s %d shares", "Volume     :", totalVolume));
        t.row(String.format("%-18s $%.2f",     "Value      :", totalValue));
        t.row(String.format("%-18s $%.2f",     "VWAP       :", getVWAP()));
        t.row(String.format("%-18s %d",        "Trades     :", tradeCount));
        t.mid();
        t.rowCentered("Recent Price History");
        t.mid();

        // Mini ASCII price chart — last 10 trades
        List<PricePoint> recent = priceHistory.subList(
            Math.max(0, priceHistory.size() - 10), priceHistory.size());

        for (int i = 0; i < recent.size(); i++) {
            PricePoint pp = recent.get(i);
            String dir = "";
            if (i > 0) {
                dir = pp.price > recent.get(i-1).price ? " ▲" :
                      pp.price < recent.get(i-1).price ? " ▼" : " —";
            }
            t.row(String.format("  Trade #%-4d $%.2f  qty=%-6d%s",
                tradeCount - recent.size() + i + 1,
                pp.price, pp.quantity, dir));
        }
        t.bot();
    }

    // Getters for circuit breaker
    public double getOpenPrice()  { return openPrice; }
    public double getHighPrice()  { return highPrice; }
    public double getLowPrice()   { return lowPrice; }
    public double getClosePrice() { return closePrice; }
    public long   getTotalVolume(){ return totalVolume; }
    public int    getTradeCount() { return tradeCount; }
    public String getSymbol()     { return symbol; }

    // ── Inner class ──────────────────────────────────────────────────
    public static class PricePoint implements Serializable {
        private static final long serialVersionUID = 1L;
        public final double price;
        public final int    quantity;
        public final long   timestamp;

        public PricePoint(double price, int quantity) {
            this.price     = price;
            this.quantity  = quantity;
            this.timestamp = System.currentTimeMillis();
        }
    }
}