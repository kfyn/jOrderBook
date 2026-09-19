package net.kfyn.ob.entity;

public record Trade(long bidOrderId, long askOrderId, long pxT, long qtyT) {
}