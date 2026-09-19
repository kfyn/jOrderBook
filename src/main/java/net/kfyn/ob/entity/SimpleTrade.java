package net.kfyn.ob.entity;

public record SimpleTrade(long bidOrderId, long askOrderId, long pxTicks, long qtyTicks) implements Trade {
    public SimpleTrade {
        if (qtyTicks <= 0) throw new IllegalArgumentException("qtyTicks must be positive: " + qtyTicks);
    }
}