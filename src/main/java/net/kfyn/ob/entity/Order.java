package net.kfyn.ob.entity;

public interface Order {
    long id();

    Side side();

    long pxTicks();

    long qtyTicks();

    OrderType orderType();
}
