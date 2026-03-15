package com.nanotrade.engine;

import com.nanotrade.cli.TablePrinter;
import com.nanotrade.market.MarketData;
import com.nanotrade.market.MarketDataStore;
import com.nanotrade.logging.EventLogger;
import com.nanotrade.model.*;
import com.nanotrade.persistence.PersistenceManager;
import com.nanotrade.portfolio.Portfolio;
import com.nanotrade.market.CircuitBreakerManager;
import java.util.*;

public class MatchingEngine {
    private EventLogger logger;
    private Map<String, OrderBook> books;
    private MarketDataStore marketData;
    private Map<String, Portfolio> portfolios;
    private PerformanceMonitor monitor;
    private PersistenceManager persistence;
    private CircuitBreakerManager circuitBreakers;
    private long nextOrderId;

    public MatchingEngine() {
        this.monitor = new PerformanceMonitor();
        this.persistence = new PersistenceManager();
        this.logger = new EventLogger();
        this.nextOrderId = 1;

        // Try to load saved state
        if (persistence.hasSavedData()) {
            this.books = persistence.loadBooks();
            this.portfolios = persistence.loadPortfolios();
            this.marketData = persistence.loadMarketData();
            this.circuitBreakers = persistence.loadCircuitBreakers();
            // Set nextOrderId to max existing + 1 to avoid ID collisions
            this.nextOrderId = books.values().stream()
                    .flatMap(b -> b.getAllOrders().stream())
                    .mapToLong(Order::getOrderId)
                    .max().orElse(0L) + 1;
            System.out.println("  [PERSISTENCE] Session restored from disk.");
            System.out.printf("  [PERSISTENCE] %d symbol(s) loaded, next order ID: #%d%n",
                    books.size(), nextOrderId);
        } else {
            this.books = new HashMap<>();
            this.circuitBreakers = new CircuitBreakerManager();
            this.portfolios = new HashMap<>();
            this.marketData = new MarketDataStore();
        }
    }

    private OrderBook getOrCreateBook(String symbol) {
        return books.computeIfAbsent(symbol.toUpperCase(), OrderBook::new);
    }

    private Portfolio getOrCreatePortfolio(String traderId) {
        return portfolios.computeIfAbsent(traderId, Portfolio::new);
    }

    public void addOrder(OrderType type, String symbol, int quantity,
            double price, String traderId) {
        symbol = symbol.toUpperCase();
        // Check circuit breaker before accepting order
        if (circuitBreakers.isHalted(symbol)) {
            System.out.printf("  [REJECTED] %s trading is HALTED. " +
                    "Use RESUME_MARKET %s to override.%n", symbol, symbol);
            logger.logOrderRejected(-1,
                    symbol + " circuit breaker active");
            return;
        }
        Order order = new Order(nextOrderId++, type, symbol, quantity, price, traderId);
        OrderBook book = getOrCreateBook(symbol);

        System.out.printf("%n  [ORDER ADDED] #%d %s %d %s @ $%.2f%n",
                order.getOrderId(), type, quantity, symbol, price);

        monitor.recordOrder();
        logger.logOrderAdded(order.getOrderId(), type.toString(), quantity, symbol, price, traderId);
        long startNs = System.nanoTime();
        List<Match> matches = book.matchOrder(order);
        long latencyNs = System.nanoTime() - startNs;

        if (matches.isEmpty()) {
            book.addOrder(order);
            System.out.println("  No matches found — order resting in book.");
        } else {
            for (Match match : matches) {
                match.printExecutionReport();
                monitor.recordMatch(match.getQuantity(), match.getExecutionPrice(), latencyNs);
                updatePortfolios(match);
                logger.logMatch(
                        match.getBuyerOrder().getOrderId(),
                        match.getSellerOrder().getOrderId(),
                        match.getQuantity(),
                        match.getExecutionPrice(),
                        symbol);
            }
            if (order.getRemainingQuantity() > 0) {
                book.addOrder(order);
                System.out.printf("  Partially filled — %d shares still resting.%n",
                        order.getRemainingQuantity());
            } else {
                System.out.println("  Order fully filled.");
            }
        }
        save();
    }

    private void updatePortfolios(Match match) {
        Order buyer = match.getBuyerOrder();
        Order seller = match.getSellerOrder();
        int qty = match.getQuantity();
        double price = match.getExecutionPrice();
        String sym = buyer.getSymbol();

        getOrCreatePortfolio(buyer.getTraderId())
                .recordTrade(OrderType.BUY, sym, qty, price);
        getOrCreatePortfolio(seller.getTraderId())
                .recordTrade(OrderType.SELL, sym, qty, price);

        // Record in market data
        marketData.recordTrade(sym, price, qty);
        // Check circuit breaker after each trade
        circuitBreakers.onTrade(sym, price);
    }

    public void cancelOrder(long orderId, String symbol) {
        symbol = symbol.toUpperCase();
        OrderBook book = books.get(symbol);
        if (book == null) {
            System.out.println("  [ERROR] Symbol not found: " + symbol);
            return;
        }
        boolean cancelled = book.cancelOrder(orderId);
        if (cancelled) {
            System.out.printf("  [CANCELLED] Order #%d successfully cancelled.%n", orderId);
            logger.logOrderCancelled(orderId, symbol);
            save();
        } else {
            System.out.printf("  [ERROR] Order #%d not found or already filled/cancelled.%n", orderId);
        }
    }

    public void showBook(String symbol) {
        symbol = symbol.toUpperCase();
        OrderBook book = books.get(symbol);
        if (book == null) {
            System.out.println("  [ERROR] No order book found for: " + symbol);
            return;
        }
        book.display();
    }

    public void showPortfolio(String traderId) {
        Portfolio p = portfolios.get(traderId);
        if (p == null) {
            System.out.println("  [ERROR] No portfolio found for: " + traderId);
            return;
        }
        p.display();
    }

    public void showOrder(long orderId, String symbol) {
        symbol = symbol.toUpperCase();
        OrderBook book = books.get(symbol);
        if (book == null) {
            System.out.println("  [ERROR] Symbol not found: " + symbol);
            return;
        }
        Order order = book.getOrder(orderId);
        if (order == null) {
            System.out.println("  [ERROR] Order not found: #" + orderId);
            return;
        }
        System.out.println("  " + order);
    }

    public void showTradeHistory(String symbol) {
        symbol = symbol.toUpperCase();
        OrderBook book = books.get(symbol);
        if (book == null) {
            System.out.println("  [ERROR] Symbol not found: " + symbol);
            return;
        }
        List<Match> matches = book.getExecutedMatches();
        if (matches.isEmpty()) {
            System.out.println("  No trades executed for " + symbol + " yet.");
            return;
        }
        System.out.println("\n  Trade History for " + symbol + ":");
        System.out.println("  " + TablePrinter.repeat("─", 52));
        for (Match m : matches) {
            System.out.printf("  Buy #%-4d (%-8s) | Sell #%-4d (%-8s) | %d @ $%.2f%n",
                    m.getBuyerOrder().getOrderId(),
                    m.getBuyerOrder().getTraderId(),
                    m.getSellerOrder().getOrderId(),
                    m.getSellerOrder().getTraderId(),
                    m.getQuantity(),
                    m.getExecutionPrice());
        }
    }

    public void showPerformance() {
        monitor.display();
    }

    public void listSymbols() {
        if (books.isEmpty()) {
            System.out.println("  No symbols active yet.");
            return;
        }
        System.out.println("  Active symbols: " + String.join(", ", books.keySet()));
    }

    public void clearMarket(String symbol) {
        symbol = symbol.toUpperCase();
        books.remove(symbol);
        System.out.println("  [CLEARED] Order book for " + symbol + " reset.");
        save();
    }

    public void clearAllSavedData() {
        persistence.clearSavedData();
        books.clear();
        portfolios.clear();
        nextOrderId = 1;
        System.out.println("  [PERSISTENCE] All data cleared. Fresh session started.");
        logger.log("SYSTEM", "CLEAR_DATA", "all saved data wiped");
    }

    public void runStressTest(int numOrders) {
        StressTestResult result = new StressTest(this).run(numOrders);
        logger.logStressTest(result.orders, result.matches, result.elapsedMs);
    }

    // Auto-save after every mutating operation
    private void save() {
        persistence.saveBooks(books);
        persistence.savePortfolios(portfolios);
        persistence.saveMarketData(marketData);
        persistence.saveCircuitBreakers(circuitBreakers);
    }

    public Map<String, OrderBook> getBooks() {
        return books;
    }

    public Map<String, Portfolio> getPortfolios() {
        return portfolios;
    }

    public PerformanceMonitor getMonitor() {
        return monitor;
    }

    public void showLog(int lines) {
        logger.tail(lines);
    }

    public void sessionEnd() {
        logger.logSessionEnd();
    }

    public void showMarketData(String symbol) {
        if (symbol.equalsIgnoreCase("ALL")) {
            marketData.showAll();
            return;
        }
        MarketData md = marketData.get(symbol.toUpperCase());
        if (md == null) {
            System.out.println("  [ERROR] No market data for: " + symbol);
            return;
        }
        md.display();
    }

    public MarketDataStore getMarketData() {
        return marketData;
    }

    public void resumeMarket(String symbol) {
        boolean resumed = circuitBreakers.resumeManually(symbol.toUpperCase());
        if (!resumed) {
            System.out.println("  [ERROR] " + symbol +
                    " is not halted or has no circuit breaker data.");
        } else {
            logger.logMarketResumed(symbol.toUpperCase());
            save();
        }
    }

    public void showCircuitBreaker(String symbol) {
        if (symbol.equalsIgnoreCase("ALL")) {
            circuitBreakers.showAll();
        } else {
            circuitBreakers.showStatus(symbol.toUpperCase());
        }
    }
}