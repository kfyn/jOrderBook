package net.kfyn.ob.entity;

public record Order(long id, Side side, long pxT, long qtyT, OrderType orderType) {
    public Order {
        if (qtyT <= 0) throw new IllegalArgumentException("qtyT must be positive: " + qtyT);
        if (side == null) throw new IllegalArgumentException("side is null");
        if (orderType == null) throw new IllegalArgumentException("orderType is null");
    }
}