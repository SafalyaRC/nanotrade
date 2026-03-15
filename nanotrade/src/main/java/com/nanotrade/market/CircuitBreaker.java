package com.nanotrade.market;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CircuitBreaker implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final double HALT_THRESHOLD_PCT = 5.0;
    public static final long WINDOW_MS = 60_000;
    public static final long COOLDOWN_MS = 30_000;

    private String symbol;
    private boolean halted;
    private double referencePrice;
    private long windowStartTime;
    private long haltTime;
    private String haltReason;
    private int haltCount;

    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

    public CircuitBreaker(String symbol) {
        this.symbol = symbol;
        this.halted = false;
        this.referencePrice = 0;
        this.windowStartTime = System.currentTimeMillis();
        this.haltCount = 0;
    }

    public boolean checkAndUpdate(double newPrice) {
        long now = System.currentTimeMillis();

        // If halted don't process prices
        if (halted)
            return true;

        // Reset window if expired
        if (now - windowStartTime >= WINDOW_MS) {
            referencePrice = newPrice;
            windowStartTime = now;
            return false;
        }

        // First trade — just set reference
        if (referencePrice == 0) {
            referencePrice = newPrice;
            return false;
        }

        // Check % move from reference
        double pctMove = Math.abs(
                (newPrice - referencePrice) / referencePrice) * 100.0;

        if (pctMove >= HALT_THRESHOLD_PCT) {
            halt(String.format("price moved %.2f%% (ref=$%.2f, now=$%.2f)",
                    pctMove, referencePrice, newPrice));
            return true;
        }

        return false;
    }

    // Called from CircuitBreakerManager.isHalted() without a price
    public void checkAutoResume() {
        if (!halted)
            return;
        long now = System.currentTimeMillis();
        if (now - haltTime >= COOLDOWN_MS) {
            resume("auto-resume after cooldown");
        }
    }

    public void halt(String reason) {
        this.halted = true;
        this.haltTime = System.currentTimeMillis();
        this.haltReason = reason;
        this.haltCount++;

        com.nanotrade.cli.TablePrinter t = new com.nanotrade.cli.TablePrinter(52);
        System.out.println();
        t.top();
        t.rowCentered("!! CIRCUIT BREAKER TRIGGERED: " + symbol);
        t.mid();
        // Split reason if too long
        if (reason.length() <= 50) {
            t.row(String.format("%-12s %s", "Reason :", reason));
        } else {
            System.out.println("  [DEBUG] reason='" + reason + "'");
            // Print reason lines manually to bypass TablePrinter's pad() truncation
            String prefix = "  ║ Reason :   ";
            String padding = "  ║             ";
            String close = " ║";
            int splitIdx = reason.indexOf("(ref=");

            if (splitIdx > 0) {
                String line1 = reason.substring(0, splitIdx).trim();
                String line2 = reason.substring(splitIdx).trim();
                // Pad each line to fill the box (inner width 52, minus prefix)
                int fill1 = 52 - prefix.length() + 2 - line1.length() - close.length();
                int fill2 = 52 - padding.length() + 2 - line2.length() - close.length();
                System.out.println(prefix + line1 +
                        com.nanotrade.cli.TablePrinter.repeat(" ", Math.max(0, fill1)) + close);
                System.out.println(padding + line2 +
                        com.nanotrade.cli.TablePrinter.repeat(" ", Math.max(0, fill2)) + close);
            } else {
                t.row(String.format("%-12s %s", "Reason :", reason));
            }
        }
        t.row(String.format("%-12s %s", "Time   :", sdf.format(new Date(haltTime))));
        t.row(String.format("%-12s %s", "Resumes:",
                "auto in " + (COOLDOWN_MS / 1000) + "s or RESUME_MARKET " + symbol));
        t.bot();
    }

    public void resume(String reason) {
        this.halted = false;
        this.referencePrice = 0;
        this.windowStartTime = System.currentTimeMillis();
        System.out.printf(
                "  [CIRCUIT] %s trading RESUMED — %s%n", symbol, reason);
    }

    public void display() {
        com.nanotrade.cli.TablePrinter t = new com.nanotrade.cli.TablePrinter(52);
        t.top();
        t.rowCentered(symbol + " Circuit Breaker Status");
        t.mid();
        t.row(String.format("%-20s %s", "Status     :", halted ? "HALTED" : "ACTIVE"));
        t.row(String.format("%-20s %.1f%%", "Threshold  :", HALT_THRESHOLD_PCT));
        t.row(String.format("%-20s %ds", "Window     :", WINDOW_MS / 1000));
        t.row(String.format("%-20s %ds", "Cooldown   :", COOLDOWN_MS / 1000));
        t.row(String.format("%-20s %d", "Halt Count :", haltCount));
        if (halted) {
            t.mid();
            t.row(String.format("%-20s %s", "Halted At  :",
                    sdf.format(new Date(haltTime))));
            // Split long reason across two lines if needed
            String reason = haltReason != null ? haltReason : "";
            if (reason.length() <= 30) {
                t.row(String.format("%-20s %s", "Reason     :", reason));
            } else {
                t.row(String.format("%-20s %s", "Reason     :", reason.substring(0, 30)));
                t.row(String.format("%-20s %s", "", reason.substring(30)));
            }
            long remaining = COOLDOWN_MS - (System.currentTimeMillis() - haltTime);
            t.row(String.format("%-20s %ds", "Auto-resume in :",
                    Math.max(0, remaining / 1000)));
        }
        t.bot();
    }

    public boolean isHalted() {
        return halted;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getHaltReason() {
        return haltReason;
    }

    public int getHaltCount() {
        return haltCount;
    }
}