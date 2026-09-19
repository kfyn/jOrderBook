package net.kfyn.ob.entity;

public record Order(long id, Side side, long pxTicks, long qtyTicks, OrderType orderType) {
    public Order {
        if (qtyTicks <= 0) throw new IllegalArgumentException("qtyTicks must be positive: " + qtyTicks);
        if (side == null) throw new IllegalArgumentException("side is null");
        if (orderType == null) throw new IllegalArgumentException("orderType is null");
    }
}