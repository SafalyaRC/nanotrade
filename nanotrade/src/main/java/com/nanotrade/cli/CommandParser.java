package com.nanotrade.cli;

import com.nanotrade.engine.MatchingEngine;
import com.nanotrade.model.OrderType;
import java.util.Scanner;

public class CommandParser {
    private MatchingEngine engine;
    private Scanner scanner;

    public CommandParser(MatchingEngine engine) {
        this.engine = engine;
        this.scanner = new Scanner(System.in);
    }

    public void start() {
        printBanner();
        printHelp();

        while (true) {
            System.out.print("\nnanotrade> ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty())
                continue;

            String[] parts = input.split("\\s+");
            String command = parts[0].toUpperCase();

            try {
                switch (command) {

                    case "ADD_ORDER":
                        if (parts.length != 6) {
                            printUsage("ADD_ORDER <BUY|SELL> <qty> <symbol> <price> <traderId>");
                            break;
                        }
                        engine.addOrder(
                                OrderType.valueOf(parts[1].toUpperCase()),
                                parts[3],
                                Integer.parseInt(parts[2]),
                                Double.parseDouble(parts[4]),
                                parts[5]);
                        break;

                    case "CANCEL_ORDER":
                        if (parts.length != 3) {
                            printUsage("CANCEL_ORDER <orderId> <symbol>");
                            break;
                        }
                        engine.cancelOrder(Long.parseLong(parts[1]), parts[2]);
                        break;

                    case "SHOW_BOOK":
                        if (parts.length != 2) {
                            printUsage("SHOW_BOOK <symbol>");
                            break;
                        }
                        engine.showBook(parts[1]);
                        break;

                    case "SHOW_ORDER":
                        if (parts.length != 3) {
                            printUsage("SHOW_ORDER <orderId> <symbol>");
                            break;
                        }
                        engine.showOrder(Long.parseLong(parts[1]), parts[2]);
                        break;

                    case "SHOW_PORTFOLIO":
                        if (parts.length != 2) {
                            printUsage("SHOW_PORTFOLIO <traderId>");
                            break;
                        }
                        engine.showPortfolio(parts[1]);
                        break;

                    case "SHOW_TRADE_HISTORY":
                        if (parts.length != 2) {
                            printUsage("SHOW_TRADE_HISTORY <symbol>");
                            break;
                        }
                        engine.showTradeHistory(parts[1]);
                        break;

                    case "SHOW_PERFORMANCE":
                        engine.showPerformance();
                        break;

                    case "LIST_SYMBOLS":
                        engine.listSymbols();
                        break;

                    case "CLEAR_MARKET":
                        if (parts.length != 2) {
                            printUsage("CLEAR_MARKET <symbol>");
                            break;
                        }
                        engine.clearMarket(parts[1]);
                        break;

                    case "HELP":
                        printHelp();
                        break;

                    case "STRESS_TEST":
                        if (parts.length != 2) {
                            printUsage("STRESS_TEST <numOrders>");
                            break;
                        }
                        int n = Integer.parseInt(parts[1]);
                        if (n < 1 || n > 1_000_000) {
                            System.out.println("  [ERROR] Number of orders must be between 1 and 1,000,000.");
                            break;
                        }
                        engine.runStressTest(n);
                        break;

                    case "SHOW_MARKET_DATA":
                        // SHOW_MARKET_DATA <symbol|ALL>
                        if (parts.length != 2) {
                            printUsage("SHOW_MARKET_DATA <symbol|ALL>");
                            break;
                        }
                        engine.showMarketData(parts[1]);
                        break;

                    case "RESUME_MARKET":
                        if (parts.length != 2) {
                            printUsage("RESUME_MARKET <symbol>");
                            break;
                        }
                        engine.resumeMarket(parts[1]);
                        break;

                    case "SHOW_CIRCUIT":
                        if (parts.length != 2) {
                            printUsage("SHOW_CIRCUIT <symbol|ALL>");
                            break;
                        }
                        engine.showCircuitBreaker(parts[1]);
                        break;

                    case "CLEAR_DATA":
                        engine.clearAllSavedData();
                        break;

                    case "SHOW_LOG":
                        // SHOW_LOG optional number of lines, default 20
                        int lines = parts.length >= 2 ? Integer.parseInt(parts[1]) : 20;
                        engine.showLog(lines);
                        break;

                    case "EXIT":
                        engine.sessionEnd();
                        System.out.println("\n  Shutting down nanotrade. Goodbye.\n");
                        return;

                    default:
                        System.out.println("  [ERROR] Unknown command: " + command + ". Type HELP.");
                }

            } catch (IllegalArgumentException e) {
                System.out.println("  [ERROR] Invalid input — " + e.getMessage());
            } catch (Exception e) {
                System.out.println("  [ERROR] " + e.getMessage());
            }
        }
    }

    private void printBanner() {
        System.out.println();
        System.out.println("  ███╗   ██╗ █████╗ ███╗   ██╗ ██████╗ ████████╗██████╗  █████╗ ██████╗ ███████╗");
        System.out.println("  ████╗  ██║██╔══██╗████╗  ██║██╔═══██╗╚══██╔══╝██╔══██╗██╔══██╗██╔══██╗██╔════╝");
        System.out.println("  ██╔██╗ ██║███████║██╔██╗ ██║██║   ██║   ██║   ██████╔╝███████║██║  ██║█████╗  ");
        System.out.println("  ██║╚██╗██║██╔══██║██║╚██╗██║██║   ██║   ██║   ██╔══██╗██╔══██║██║  ██║██╔══╝  ");
        System.out.println("  ██║ ╚████║██║  ██║██║ ╚████║╚██████╔╝   ██║   ██║  ██║██║  ██║██████╔╝███████╗");
        System.out.println("  ╚═╝  ╚═══╝╚═╝  ╚═╝╚═╝  ╚═══╝ ╚═════╝    ╚═╝   ╚═╝  ╚═╝╚═╝  ╚═╝╚═════╝ ╚══════╝");
        System.out.println("  Stock Market Order Matching Engine  |  Pure Java 8");
        System.out.println();
    }

    private void printHelp() {
        System.out.println("  ┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("  │  COMMANDS                                                       │");
        System.out.println("  ├─────────────────────────────────────────────────────────────────┤");
        System.out.println("  │  ADD_ORDER <BUY|SELL> <qty> <symbol> <price> <traderId>         │");
        System.out.println("  │  CANCEL_ORDER <orderId> <symbol>                                │");
        System.out.println("  │  SHOW_BOOK <symbol>                                             │");
        System.out.println("  │  SHOW_ORDER <orderId> <symbol>                                  │");
        System.out.println("  │  SHOW_PORTFOLIO <traderId>                                      │");
        System.out.println("  │  SHOW_TRADE_HISTORY <symbol>                                    │");
        System.out.println("  │  SHOW_PERFORMANCE                                               │");
        System.out.println("  │  LIST_SYMBOLS                                                   │");
        System.out.println("  │  CLEAR_MARKET <symbol>                                          │");
        System.out.println("  │  HELP                                                           │");
        System.out.println("  │  SHOW_MARKET_DATA <symbol|ALL>                                  │");
        System.out.println("  │  STRESS_TEST <numOrders>                                        │");
        System.out.println("  │  CLEAR_DATA                                                     │");
        System.out.println("  │  RESUME_MARKET <symbol>                                         │");
        System.out.println("  │  SHOW_CIRCUIT <symbol|ALL>                                      │");
        System.out.println("  │  SHOW_LOG [numLines]                                            │");
        System.out.println("  │  EXIT                                                           │");
        System.out.println("  └─────────────────────────────────────────────────────────────────┘");
    }

    private void printUsage(String usage) {
        System.out.println("  [USAGE] " + usage);
    }
}