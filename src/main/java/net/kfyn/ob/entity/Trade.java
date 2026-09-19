package net.kfyn.ob.entity;

public record Trade(long bidOrderId, long askOrderId, long pxT, long qtyT) {
    public Trade {
        if (qtyT <= 0) throw new IllegalArgumentException("qtyT must be positive: " + qtyT);
    }
}