package com.nanotrade.persistence;

import com.nanotrade.engine.OrderBook;
import com.nanotrade.market.CircuitBreakerManager;
import com.nanotrade.market.MarketDataStore;
import com.nanotrade.portfolio.Portfolio;
import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

public class PersistenceManager {
    private static final String DATA_DIR = "data";
    private static final String BOOKS_FILE = DATA_DIR + "/orderbooks.dat";
    private static final String PORTFOLIO_FILE = DATA_DIR + "/portfolios.dat";
    private static final String MARKET_FILE = DATA_DIR + "/marketdata.dat";
    private static final String CIRCUIT_FILE = DATA_DIR + "/circuitbreakers.dat";

    public void saveCircuitBreakers(CircuitBreakerManager manager) {
        saveObject(manager, CIRCUIT_FILE, "circuit breakers");
    }

    public CircuitBreakerManager loadCircuitBreakers() {
        Object obj = loadObject(CIRCUIT_FILE);
        if (obj == null)
            return new CircuitBreakerManager();
        return (CircuitBreakerManager) obj;
    }

    public void saveMarketData(MarketDataStore store) {
        saveObject(store, MARKET_FILE, "market data");
    }

    public MarketDataStore loadMarketData() {
        Object obj = loadObject(MARKET_FILE);
        if (obj == null)
            return new MarketDataStore();
        return (MarketDataStore) obj;
    }

    public PersistenceManager() {
        // Create data directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(DATA_DIR));
        } catch (IOException e) {
            System.err.println("  [WARN] Could not create data directory: " + e.getMessage());
        }
    }

    // ── Save ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public void saveBooks(Map<String, OrderBook> books) {
        saveObject(books, BOOKS_FILE, "order books");
    }

    public void savePortfolios(Map<String, Portfolio> portfolios) {
        saveObject(portfolios, PORTFOLIO_FILE, "portfolios");
    }

    private void saveObject(Object obj, String path, String label) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path))) {
            oos.writeObject(obj);
        } catch (IOException e) {
            System.err.println("  [WARN] Failed to save " + label + ": " + e.getMessage());
        }
    }

    // ── Load ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Map<String, OrderBook> loadBooks() {
        Object obj = loadObject(BOOKS_FILE);
        if (obj == null)
            return new HashMap<>();
        return (Map<String, OrderBook>) obj;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Portfolio> loadPortfolios() {
        Object obj = loadObject(PORTFOLIO_FILE);
        if (obj == null)
            return new HashMap<>();
        return (Map<String, Portfolio>) obj;
    }

    private Object loadObject(String path) {
        File file = new File(path);
        if (!file.exists())
            return null;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("  [WARN] Failed to load " + path + ": " + e.getMessage());
            return null;
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────

    public boolean hasSavedData() {
        return new File(BOOKS_FILE).exists() ||
                new File(PORTFOLIO_FILE).exists() || new File(CIRCUIT_FILE).exists() ||
                new File(MARKET_FILE).exists();
    }

    public void clearSavedData() {
        new File(BOOKS_FILE).delete();
        new File(PORTFOLIO_FILE).delete();
        new File(MARKET_FILE).delete();
        new File(CIRCUIT_FILE).delete();
        System.out.println("  [PERSISTENCE] Saved data cleared.");
    }

    public String getDataDir() {
        return DATA_DIR;
    }
}