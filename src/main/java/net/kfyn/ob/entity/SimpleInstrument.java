package net.kfyn.ob.entity;

import java.math.BigDecimal;
import java.util.Objects;

public record SimpleInstrument(String symbol, long pxTickMantissa, int pxScale,
                               long qtyTickMantissa, int qtyScale) implements Instrument {

    public SimpleInstrument {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol: " + symbol);
        if (pxTickMantissa <= 0 || qtyTickMantissa <= 0)
            throw new IllegalArgumentException("tick mantissa must be positive");
        if (pxScale < 0 || pxScale > 18 || qtyScale < 0 || qtyScale > 18)
            throw new IllegalArgumentException("tick scale out of range");
    }

    public static SimpleInstrument of(String symbol, String pxTick, String qtyTick) {
        return new SimpleInstrument(symbol, tickMantissa(pxTick), tickScale(pxTick),
                tickMantissa(qtyTick), tickScale(qtyTick));
    }

    @Override public long pxTicks(BigDecimal v) {
        return ticks(v, pxTickMantissa, pxScale, symbol);
    }

    @Override public long qtyTicks(BigDecimal v) {
        long t = ticks(v, qtyTickMantissa, qtyScale, symbol);
        if (t <= 0) throw new IllegalArgumentException(symbol + ": qty must be positive: " + v);
        return t;
    }

    @Override public BigDecimal pxValue(long t) {
        return value(t, pxTickMantissa, pxScale);
    }

    @Override public BigDecimal qtyValue(long t) {
        return value(t, qtyTickMantissa, qtyScale);
    }

    private static long ticks(BigDecimal v, long tickM, int scale, String symbol) {
        Objects.requireNonNull(v, "value");
        var qr = v.movePointRight(scale).divideAndRemainder(BigDecimal.valueOf(tickM));
        if (qr[1].signum() != 0)
            throw new IllegalArgumentException(symbol + ": off-tick: " + v);
        try {
            return qr[0].longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(symbol + ": value too large: " + v, e);
        }
    }

    private static BigDecimal value(long t, long tickM, int scale) {
        return BigDecimal.valueOf(Math.multiplyExact(t, tickM), scale);
    }

    private static long tickMantissa(String tick) {
        return new BigDecimal(tick).stripTrailingZeros().movePointRight(tickScale(tick)).longValueExact();
    }

    private static int tickScale(String tick) {
        return Math.max(0, new BigDecimal(tick).stripTrailingZeros().scale());
    }
}