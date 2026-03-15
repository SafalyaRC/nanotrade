package com.nanotrade.model;

import java.io.Serializable;

public class Match implements Serializable {
    private static final long serialVersionUID = 1L;
    private long matchId;
    private Order buyerOrder;
    private Order sellerOrder;
    private int quantity;
    private double executionPrice;
    private long timestamp;

    public Match(Order buyer, Order seller, int quantity, double executionPrice) {
        this.matchId = System.nanoTime();
        this.buyerOrder = buyer;
        this.sellerOrder = seller;
        this.quantity = quantity;
        this.executionPrice = executionPrice;
        this.timestamp = System.currentTimeMillis();
    }

    public void printExecutionReport() {
        System.out.println("\n  [MATCH EXECUTED]");
        System.out.printf("  Buyer  : Order #%d @ $%.2f%n", buyerOrder.getOrderId(), buyerOrder.getLimitPrice());
        System.out.printf("  Seller : Order #%d @ $%.2f%n", sellerOrder.getOrderId(), sellerOrder.getLimitPrice());
        System.out.printf("  Qty    : %d shares%n", quantity);
        System.out.printf("  Price  : $%.2f  |  Total: $%.2f%n", executionPrice, executionPrice * quantity);
    }

    // Getters
    public long getMatchId()           { return matchId; }
    public Order getBuyerOrder()       { return buyerOrder; }
    public Order getSellerOrder()      { return sellerOrder; }
    public int getQuantity()           { return quantity; }
    public double getExecutionPrice()  { return executionPrice; }
    public long getTimestamp()         { return timestamp; }
}