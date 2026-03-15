package com.nanotrade.engine;

public class StressTestResult {
    public final int  orders;
    public final int  matches;
    public final long elapsedMs;

    public StressTestResult(int orders, int matches, long elapsedMs) {
        this.orders    = orders;
        this.matches   = matches;
        this.elapsedMs = elapsedMs;
    }
}