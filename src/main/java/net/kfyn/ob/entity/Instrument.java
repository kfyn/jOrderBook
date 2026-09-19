package net.kfyn.ob.entity;

import java.math.BigDecimal;

public interface Instrument {
    String symbol();

    long pxTickMantissa();

    int pxScale();

    long qtyTickMantissa();

    int qtyScale();

    long pxTicks(BigDecimal v);

    long qtyTicks(BigDecimal v);

    BigDecimal pxValue(long t);

    BigDecimal qtyValue(long t);

    static Instrument of(String symbol, String pxTick, String qtyTick) {
        return net.kfyn.ob.simple.SimpleInstrument.of(symbol, pxTick, qtyTick);
    }
}