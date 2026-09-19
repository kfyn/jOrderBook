package net.kfyn.ob.entity;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Instrument tick configuration: the units authority for one OrderBook.
 * tick = tickM * 10^-scale; ticks are exact multiples of the tick.
 */
public record Instrument(String symbol, long pxTickM, int pxScale, long qtyTickM, int qtyScale) {

    public Instrument {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol: " + symbol);
        if (pxTickM <= 0 || qtyTickM <= 0) throw new IllegalArgumentException("tick mantissa must be positive");
        if (pxScale < 0 || pxScale > 18 || qtyScale < 0 || qtyScale > 18)
            throw new IllegalArgumentException("tick scale out of range");
    }

    public static Instrument of(String symbol, String pxTick, String qtyTick) {
        return new Instrument(symbol, tickM(pxTick), tickScale(pxTick), tickM(qtyTick), tickScale(qtyTick));
    }

    public long pxTicks(BigDecimal v) {
        return ticks(v, pxTickM, pxScale);
    }

    public long qtyTicks(BigDecimal v) {
        return ticks(v, qtyTickM, qtyScale);
    }

    public BigDecimal pxValue(long t) {
        return value(t, pxTickM, pxScale);
    }

    public BigDecimal qtyValue(long t) {
        return value(t, qtyTickM, qtyScale);
    }

    private static long ticks(BigDecimal v, long tickM, int scale) {
        Objects.requireNonNull(v, "value");
        if (v.signum() < 0) throw new IllegalArgumentException("negative: " + v);
        var qr = v.movePointRight(scale).divideAndRemainder(BigDecimal.valueOf(tickM));
        if (qr[1].signum() != 0) throw new IllegalArgumentException("off-tick: " + v);
        return qr[0].longValueExact();
    }

    private static BigDecimal value(long t, long tickM, int scale) {
        return BigDecimal.valueOf(t * tickM, scale);
    }

    private static long tickM(String tick) {
        return new BigDecimal(tick).stripTrailingZeros().movePointRight(tickScale(tick)).longValueExact();
    }

    private static int tickScale(String tick) {
        return Math.max(0, new BigDecimal(tick).stripTrailingZeros().scale());
    }
}