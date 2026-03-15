package com.nanotrade.engine;

import com.nanotrade.cli.TablePrinter;
import com.nanotrade.model.*;

import java.io.Serializable;
import java.util.*;

public class OrderBook implements Serializable {
    private static final long serialVersionUID = 1L;
    private String symbol;
    private TreeMap<Double, Queue<Order>> bids; // descending — highest first
    private TreeMap<Double, Queue<Order>> asks; // ascending — lowest first
    private HashMap<Long, Order> orderMap;
    private List<Match> executedMatches;

    public OrderBook(String symbol) {
        this.symbol = symbol;
        this.bids = new TreeMap<>(Collections.reverseOrder());
        this.asks = new TreeMap<>();
        this.orderMap = new HashMap<>();
        this.executedMatches = new ArrayList<>();
    }

    // O(log n)
    public void addOrder(Order order) {
        orderMap.put(order.getOrderId(), order);
        if (order.getType() == OrderType.BUY) {
            bids.computeIfAbsent(order.getLimitPrice(), k -> new LinkedList<>()).add(order);
        } else {
            asks.computeIfAbsent(order.getLimitPrice(), k -> new LinkedList<>()).add(order);
        }
    }

    // O(1) lookup, O(log n) removal
    public boolean cancelOrder(long orderId) {
        Order order = orderMap.get(orderId);
        if (order == null || order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.FILLED) {
            return false;
        }
        order.cancel();
        if (order.getType() == OrderType.BUY) {
            removeFromBook(bids, order);
        } else {
            removeFromBook(asks, order);
        }
        return true;
    }

    private void removeFromBook(TreeMap<Double, Queue<Order>> book, Order order) {
        Queue<Order> queue = book.get(order.getLimitPrice());
        if (queue != null) {
            queue.remove(order);
            if (queue.isEmpty())
                book.remove(order.getLimitPrice());
        }
    }

    // Greedy match — O(log n * m)
    public List<Match> matchOrder(Order incoming) {
        List<Match> matches = (incoming.getType() == OrderType.BUY)
                ? matchBuyOrder(incoming)
                : matchSellOrder(incoming);
        executedMatches.addAll(matches);
        return matches;
    }

    private List<Match> matchBuyOrder(Order buyOrder) {
        List<Match> matches = new ArrayList<>();
        for (Double askPrice : asks.keySet()) {
            if (askPrice > buyOrder.getLimitPrice())
                break;
            Queue<Order> sellers = asks.get(askPrice);
            while (buyOrder.getRemainingQuantity() > 0 && !sellers.isEmpty()) {
                Order seller = sellers.peek();
                int qty = Math.min(buyOrder.getRemainingQuantity(), seller.getRemainingQuantity());
                Match match = new Match(buyOrder, seller, qty, buyOrder.getLimitPrice());
                matches.add(match);
                buyOrder.fill(qty);
                seller.fill(qty);
                if (seller.getRemainingQuantity() == 0)
                    sellers.poll();
            }
            if (buyOrder.getRemainingQuantity() == 0)
                break;
        }
        // Clean up empty price levels
        asks.entrySet().removeIf(e -> e.getValue().isEmpty());
        return matches;
    }

    private List<Match> matchSellOrder(Order sellOrder) {
        List<Match> matches = new ArrayList<>();
        for (Double bidPrice : bids.keySet()) {
            if (bidPrice < sellOrder.getLimitPrice())
                break;
            Queue<Order> buyers = bids.get(bidPrice);
            while (sellOrder.getRemainingQuantity() > 0 && !buyers.isEmpty()) {
                Order buyer = buyers.peek();
                int qty = Math.min(sellOrder.getRemainingQuantity(), buyer.getRemainingQuantity());
                Match match = new Match(buyer, sellOrder, qty, sellOrder.getLimitPrice());
                matches.add(match);
                sellOrder.fill(qty);
                buyer.fill(qty);
                if (buyer.getRemainingQuantity() == 0)
                    buyers.poll();
            }
            if (sellOrder.getRemainingQuantity() == 0)
                break;
        }
        bids.entrySet().removeIf(e -> e.getValue().isEmpty());
        return matches;
    }

    public void display() {
        TablePrinter t = new TablePrinter(46);
        t.top();
        t.rowCentered(symbol + " Order Book");
        t.mid();
        t.row(String.format("%-22s %s", "BID (Buyers)", "ASK (Sellers)"));
        t.mid();

        List<Double> bidPrices = new ArrayList<>(bids.keySet());
        List<Double> askPrices = new ArrayList<>(asks.keySet());
        int rows = Math.max(bidPrices.size(), askPrices.size());

        if (rows == 0) {
            t.row("(empty)");
        } else {
            for (int i = 0; i < rows; i++) {
                String bid = "", ask = "";
                if (i < bidPrices.size()) {
                    Queue<Order> q = bids.get(bidPrices.get(i));
                    int total = q.stream().mapToInt(Order::getRemainingQuantity).sum();
                    bid = String.format("%d @ $%.2f", total, bidPrices.get(i));
                }
                if (i < askPrices.size()) {
                    Queue<Order> q = asks.get(askPrices.get(i));
                    int total = q.stream().mapToInt(Order::getRemainingQuantity).sum();
                    ask = String.format("%d @ $%.2f", total, askPrices.get(i));
                }
                t.row(String.format("%-22s %s", bid, ask));
            }
        }
        t.bot();
    }

    public Order getOrder(long orderId) {
        return orderMap.get(orderId);
    }

    public List<Match> getExecutedMatches() {
        return executedMatches;
    }

    public String getSymbol() {
        return symbol;
    }

    public long getBidCount() {
        return bids.values().stream().mapToLong(Queue::size).sum();
    }

    public long getAskCount() {
        return asks.values().stream().mapToLong(Queue::size).sum();
    }

    public List<Order> getAllOrders() {
        List<Order> all = new ArrayList<>(orderMap.values());
        return all;
    }
}