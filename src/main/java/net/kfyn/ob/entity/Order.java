package net.kfyn.ob.entity;

import net.kfyn.common.number.KNumber;

public record Order(long id, Side side, KNumber px, KNumber qty, OrderType orderType) {
    public Order {
        if (qty == null) throw new IllegalArgumentException("invalid qty: " + qty);
        if (px == null) throw new IllegalArgumentException("invalid px: " + px);
        if (orderType == null) throw new IllegalArgumentException("orderType is null");
    }
}
