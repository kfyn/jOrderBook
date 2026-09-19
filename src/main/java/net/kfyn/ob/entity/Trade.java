package net.kfyn.ob.entity;

public interface Trade {
    long bidOrderId();

    long askOrderId();

    long pxTicks();

    long qtyTicks();
}
