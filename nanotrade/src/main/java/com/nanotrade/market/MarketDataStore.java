package com.nanotrade.market;

import java.io.Serializable;
import java.util.*;

public class MarketDataStore implements Serializable {
    private static final long serialVersionUID = 1L;

    private Map<String, MarketData> dataMap;

    public MarketDataStore() {
        this.dataMap = new HashMap<>();
    }

    public void recordTrade(String symbol, double price, int quantity) {
        dataMap.computeIfAbsent(symbol.toUpperCase(), MarketData::new)
               .recordTrade(price, quantity);
    }

    public MarketData get(String symbol) {
        return dataMap.get(symbol.toUpperCase());
    }

    public MarketData getOrCreate(String symbol) {
        return dataMap.computeIfAbsent(symbol.toUpperCase(), MarketData::new);
    }

    public void showAll() {
        if (dataMap.isEmpty()) {
            System.out.println("  No market data available yet.");
            return;
        }
        for (MarketData md : dataMap.values()) {
            md.display();
            System.out.println();
        }
    }

    public Map<String, MarketData> getAll() { return dataMap; }
}