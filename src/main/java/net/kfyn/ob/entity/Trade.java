package net.kfyn.ob.entity;

public record Trade(long bidOrderId, long askOrderId, long pxTicks, long qtyTicks) {
    public Trade {
        if (qtyTicks <= 0) throw new IllegalArgumentException("qtyTicks must be positive: " + qtyTicks);
    }
}