package com.nanotrade.model;

import java.io.Serializable;

public class Order implements Serializable {
    private static final long serialVersionUID = 1L;
    private long orderId;
    private OrderType type;
    private String symbol;
    private int originalQuantity;
    private int filledQuantity;
    private int remainingQuantity;
    private double limitPrice;
    private OrderStatus status;
    private long timestamp;
    private String traderId;

    public Order(long orderId, OrderType type, String symbol,
                 int quantity, double limitPrice, String traderId) {
        this.orderId = orderId;
        this.type = type;
        this.symbol = symbol;
        this.originalQuantity = quantity;
        this.remainingQuantity = quantity;
        this.filledQuantity = 0;
        this.limitPrice = limitPrice;
        this.status = OrderStatus.PENDING;
        this.timestamp = System.currentTimeMillis();
        this.traderId = traderId;
    }

    public void fill(int quantity) {
        this.filledQuantity += quantity;
        this.remainingQuantity -= quantity;
        this.status = (remainingQuantity == 0)
            ? OrderStatus.FILLED
            : OrderStatus.PARTIALLY_FILLED;
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }

    // Getters
    public long getOrderId()          { return orderId; }
    public OrderType getType()        { return type; }
    public String getSymbol()         { return symbol; }
    public int getOriginalQuantity()  { return originalQuantity; }
    public int getFilledQuantity()    { return filledQuantity; }
    public int getRemainingQuantity() { return remainingQuantity; }
    public double getLimitPrice()     { return limitPrice; }
    public OrderStatus getStatus()    { return status; }
    public long getTimestamp()        { return timestamp; }
    public String getTraderId()       { return traderId; }

    @Override
    public String toString() {
        return String.format("[#%d | %s | %s | %d/%d shares @ $%.2f | %s]",
            orderId, type, symbol, filledQuantity, originalQuantity,
            limitPrice, status);
    }
}