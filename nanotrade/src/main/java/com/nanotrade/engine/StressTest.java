package com.nanotrade.engine;

import com.nanotrade.cli.TablePrinter;
import com.nanotrade.model.*;
import java.util.*;

public class StressTest {
    private MatchingEngine engine;
    private Random random;

    private static final String[] SYMBOLS = { "AAPL", "GOOGL", "MSFT", "TSLA", "AMZN" };
    private static final String[] TRADERS = { "alice", "bob", "carol", "dave", "eve" };
    private static final double BASE_PRICE = 150.0;
    private static final double SPREAD = 5.0;

    public StressTest(MatchingEngine engine) {
        this.engine = engine;
        this.random = new Random(42);
    }

    public StressTestResult run(int numOrders) {
        System.out.printf("%n  [STRESS TEST] Generating %d random orders...%n", numOrders);

        // Use dedicated books so stress test doesn't pollute live session
        Map<String, OrderBook> books = new HashMap<>();
        for (String sym : SYMBOLS)
            books.put(sym, new OrderBook(sym));

        PerformanceMonitor localMonitor = new PerformanceMonitor();
        long orderId = 1;
        int matched = 0;

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numOrders; i++) {
            String symbol = SYMBOLS[random.nextInt(SYMBOLS.length)];
            String trader = TRADERS[random.nextInt(TRADERS.length)];
            OrderType type = random.nextBoolean() ? OrderType.BUY : OrderType.SELL;
            int qty = (random.nextInt(10) + 1) * 10;
            double price = BASE_PRICE + (random.nextDouble() * SPREAD * 2) - SPREAD;
            price = Math.round(price * 100.0) / 100.0;

            Order order = new Order(orderId++, type, symbol, qty, price, trader);
            OrderBook book = books.get(symbol);

            localMonitor.recordOrder();
            long startNs = System.nanoTime();
            List<Match> matches = book.matchOrder(order);
            long latencyNs = System.nanoTime() - startNs;

            if (!matches.isEmpty()) {
                matched += matches.size();
                for (Match m : matches) {
                    localMonitor.recordMatch(m.getQuantity(), m.getExecutionPrice(), latencyNs);
                }
            }

            if (order.getRemainingQuantity() > 0) {
                book.addOrder(order);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        double throughput = elapsed > 0 ? (numOrders / (elapsed / 1000.0)) : numOrders * 1000.0;
        double matchRate = (matched * 100.0) / numOrders;

        TablePrinter t = new TablePrinter(46);
        System.out.println();
        t.top();
        t.rowCentered("Stress Test Results");
        t.mid();
        t.row(String.format("%-22s %d", "Orders Generated :", numOrders));
        t.row(String.format("%-22s %d", "Matches Found    :", matched));
        t.row(String.format("%-22s %.1f%%", "Match Rate       :", matchRate));
        t.row(String.format("%-22s %d ms", "Time Elapsed     :", elapsed));
        t.row(String.format("%-22s %.0f o/s", "Throughput       :", throughput));
        t.row(String.format("%-22s %.4f ms", "Avg per Order    :", elapsed / (double) numOrders));
        t.mid();
        t.row(String.format("%-10s %-10s %s", "Symbol", "Bids", "Asks"));
        t.mid();
        for (String sym : SYMBOLS) {
            OrderBook b = books.get(sym);
            t.row(String.format("%-10s %-10d %d", sym, b.getBidCount(), b.getAskCount()));
        }
        t.mid();
        t.rowCentered("Match Latency (local)");
        t.mid();
        t.row(String.format("%-10s %.4f ms", "Avg :", localMonitor.getAvgLatencyMs()));
        t.row(String.format("%-10s %.4f ms", "Min :", localMonitor.getMinLatencyMs()));
        t.row(String.format("%-10s %.4f ms", "Max :", localMonitor.getMaxLatencyMs()));
        t.bot();
        return new StressTestResult(numOrders, matched, elapsed);
    }
}