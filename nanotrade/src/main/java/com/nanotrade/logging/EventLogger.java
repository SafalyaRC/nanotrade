package com.nanotrade.logging;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class EventLogger {
    private static final String LOG_DIR  = "data";
    private static final String LOG_FILE = LOG_DIR + "/nanotrade.log";
    private final SimpleDateFormat sdf   = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private BufferedWriter writer;

    public EventLogger() {
        try {
            Files.createDirectories(Paths.get(LOG_DIR));
            // Append mode — don't overwrite previous sessions
            this.writer = new BufferedWriter(
                new FileWriter(LOG_FILE, true));
            log("SYSTEM", "SESSION_START", "nanotrade engine started");
        } catch (IOException e) {
            System.err.println("  [WARN] Could not initialise event log: " + e.getMessage());
        }
    }

    // ── Core log method ─────────────────────────────────────────────

    public synchronized void log(String category, String event, String detail) {
        if (writer == null) return;
        try {
            String line = String.format("[%s] %-10s %-20s %s",
                sdf.format(new Date()), category, event, detail);
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("  [WARN] Log write failed: " + e.getMessage());
        }
    }

    // ── Convenience methods ─────────────────────────────────────────

    public void logOrderAdded(long orderId, String type, int qty,
                               String symbol, double price, String trader) {
        log("ORDER", "ADD",
            String.format("#%d %s %d %s @ $%.2f trader=%s",
                orderId, type, qty, symbol, price, trader));
    }

    public void logOrderCancelled(long orderId, String symbol) {
        log("ORDER", "CANCEL",
            String.format("#%d symbol=%s", orderId, symbol));
    }

    public void logOrderRejected(long orderId, String reason) {
        log("ORDER", "REJECT",
            String.format("#%d reason=%s", orderId, reason));
    }

    public void logMatch(long buyId, long sellId, int qty,
                          double price, String symbol) {
        log("MATCH", "EXECUTE",
            String.format("buy=#%d sell=#%d qty=%d price=$%.2f symbol=%s",
                buyId, sellId, qty, price, symbol));
    }

    public void logMarketHalted(String symbol, String reason) {
        log("CIRCUIT", "HALT",
            String.format("symbol=%s reason=%s", symbol, reason));
    }

    public void logMarketResumed(String symbol) {
        log("CIRCUIT", "RESUME",
            String.format("symbol=%s", symbol));
    }

    public void logSessionEnd() {
        log("SYSTEM", "SESSION_END", "nanotrade engine stopped");
        log("SYSTEM", "─────────────", "");
        close();
    }

    public void logStressTest(int orders, int matches, long elapsedMs) {
        log("STRESS", "COMPLETE",
            String.format("orders=%d matches=%d elapsed=%dms throughput=%.0f o/s",
                orders, matches, elapsedMs,
                elapsedMs > 0 ? orders / (elapsedMs / 1000.0) : 0));
    }

    // ── Show log in terminal ────────────────────────────────────────

    public void tail(int lines) {
        File file = new File(LOG_FILE);
        if (!file.exists()) {
            System.out.println("  No log file found yet.");
            return;
        }
        try {
            // Read all lines then print last N
            java.util.List<String> allLines =
                Files.readAllLines(Paths.get(LOG_FILE));
            int start = Math.max(0, allLines.size() - lines);
            System.out.println("\n  Last " + Math.min(lines, allLines.size())
                + " log entries:");
            System.out.println("  " + TablePrinter.repeat("─", 72));
            for (int i = start; i < allLines.size(); i++) {
                System.out.println("  " + allLines.get(i));
            }
            System.out.println("  " + TablePrinter.repeat("─", 72));
            System.out.println("  Full log: " + LOG_FILE);
        } catch (IOException e) {
            System.err.println("  [WARN] Could not read log: " + e.getMessage());
        }
    }

    public void close() {
        if (writer != null) {
            try { writer.close(); } catch (IOException ignored) {}
        }
    }

    // Add this import reference for TablePrinter
    private static class TablePrinter {
        static String repeat(String c, int n) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) sb.append(c);
            return sb.toString();
        }
    }
}