package net.kfyn.ob.impl;

import net.kfyn.ob.entity.Trade;

public record SimpleTrade(long bidOrderId, long askOrderId, long pxTicks, long qtyTicks) implements Trade {
    public SimpleTrade {
        if (qtyTicks <= 0) throw new IllegalArgumentException("qtyTicks must be positive: " + qtyTicks);
    }
}