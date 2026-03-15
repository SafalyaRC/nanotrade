package com.nanotrade.market;

import java.io.Serializable;
import java.util.*;

public class CircuitBreakerManager implements Serializable {
    private static final long serialVersionUID = 1L;

    private Map<String, CircuitBreaker> breakers;

    public CircuitBreakerManager() {
        this.breakers = new HashMap<>();
    }

    // Called after every trade executes
    public boolean onTrade(String symbol, double price) {
        symbol = symbol.toUpperCase();
        CircuitBreaker cb = breakers.computeIfAbsent(
                symbol, CircuitBreaker::new);
        return cb.checkAndUpdate(price);
    }

    // Called before accepting an order
    public boolean isHalted(String symbol) {
        symbol = symbol.toUpperCase();
        CircuitBreaker cb = breakers.get(symbol);
        if (cb == null)
            return false;
        // Check time-based auto-resume without passing a fake price
        cb.checkAutoResume();
        return cb.isHalted();
    }

    public boolean resumeManually(String symbol) {
        symbol = symbol.toUpperCase();
        CircuitBreaker cb = breakers.get(symbol);
        if (cb == null || !cb.isHalted())
            return false;
        cb.resume("manual override");
        return true;
    }

    public void showStatus(String symbol) {
        symbol = symbol.toUpperCase();
        CircuitBreaker cb = breakers.get(symbol);
        if (cb == null) {
            System.out.println("  No circuit breaker data for: " + symbol);
            return;
        }
        cb.display();
    }

    public void showAll() {
        if (breakers.isEmpty()) {
            System.out.println("  No circuit breaker data yet.");
            return;
        }
        for (CircuitBreaker cb : breakers.values()) {
            cb.display();
            System.out.println();
        }
    }

    public Map<String, CircuitBreaker> getBreakers() {
        return breakers;
    }
}