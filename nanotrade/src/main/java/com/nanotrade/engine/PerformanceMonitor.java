package com.nanotrade.engine;

import java.util.ArrayList;
import java.util.List;

import com.nanotrade.cli.TablePrinter;

public class PerformanceMonitor {
    private long totalOrdersProcessed;
    private long totalMatchesExecuted;
    private long totalSharesTraded;
    private double totalValueTraded;
    private List<Long> matchLatenciesNs;
    private long engineStartTime;

    public PerformanceMonitor() {
        this.totalOrdersProcessed = 0;
        this.totalMatchesExecuted = 0;
        this.totalSharesTraded = 0;
        this.totalValueTraded = 0.0;
        this.matchLatenciesNs = new ArrayList<>();
        this.engineStartTime = System.currentTimeMillis();
    }

    public void recordOrder() {
        totalOrdersProcessed++;
    }

    public void recordMatch(int quantity, double price, long latencyNs) {
        totalMatchesExecuted++;
        totalSharesTraded += quantity;
        totalValueTraded += quantity * price;
        matchLatenciesNs.add(latencyNs);
    }

    public void display() {
        long uptimeMs = System.currentTimeMillis() - engineStartTime;
        double uptimeSec = uptimeMs / 1000.0;
        double throughput = uptimeSec > 0 ? totalOrdersProcessed / uptimeSec : 0;

        TablePrinter t = new TablePrinter(46);
        t.top();
        t.rowCentered("Live Session Performance");
        t.mid();
        t.row(String.format("%-20s %s", "Uptime :", formatUptime(uptimeMs)));
        t.mid();
        t.row(String.format("%-20s %d", "Orders (ADD) :", totalOrdersProcessed));
        t.row(String.format("%-20s %d", "Matches :", totalMatchesExecuted));
        t.row(String.format("%-20s %d", "Shares Traded :", totalSharesTraded));
        t.row(String.format("%-20s $%.2f", "Value Traded :", totalValueTraded));
        t.mid();
        t.row(String.format("%-20s %.2f o/s", "Throughput :", throughput));
        t.row(String.format("%-20s %.4f ms", "Avg Latency :", getAvgLatencyMs()));
        t.row(String.format("%-20s %.4f ms", "Min Latency :", getMinLatencyMs()));
        t.row(String.format("%-20s %.4f ms", "Max Latency :", getMaxLatencyMs()));
        t.bot();
        System.out.println("  (STRESS_TEST results are tracked separately)");
    }

    private String formatUptime(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%dm %ds (%dms)", minutes, seconds, ms);
    }

    public long getTotalOrdersProcessed() {
        return totalOrdersProcessed;
    }

    public long getTotalMatchesExecuted() {
        return totalMatchesExecuted;
    }

    public long getTotalSharesTraded() {
        return totalSharesTraded;
    }

    public double getTotalValueTraded() {
        return totalValueTraded;
    }

    public double getAvgLatencyMs() {
        if (matchLatenciesNs.isEmpty())
            return 0.0;
        long sum = 0;
        for (long l : matchLatenciesNs)
            sum += l;
        return (sum / matchLatenciesNs.size()) / 1_000_000.0;
    }

    public double getMinLatencyMs() {
        if (matchLatenciesNs.isEmpty())
            return 0.0;
        long min = Long.MAX_VALUE;
        for (long l : matchLatenciesNs)
            if (l < min)
                min = l;
        return min / 1_000_000.0;
    }

    public double getMaxLatencyMs() {
        if (matchLatenciesNs.isEmpty())
            return 0.0;
        long max = Long.MIN_VALUE;
        for (long l : matchLatenciesNs)
            if (l > max)
                max = l;
        return max / 1_000_000.0;
    }
}