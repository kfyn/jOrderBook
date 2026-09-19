package net.kfyn.common.number;

import java.math.BigDecimal;

/**
 * Scale-8 fixed-point decimal: ticks = value * 10^8, value in [0, 10^10].
 * Signed long comparison equals value comparison.
 */
public record KNumber(long ticks) implements Comparable<KNumber> {

    private static final long[] POW10 = new long[19];

    static {
        POW10[0] = 1;
        for (int i = 1; i < 19; i++) POW10[i] = POW10[i - 1] * 10;
    }

    public KNumber {
        if (ticks < 0 || ticks > POW10[18])
            throw new IllegalArgumentException("ticks out of range: " + ticks);
    }

    public static KNumber of(long mantissa, int exponent) {
        if (mantissa < 0) throw new IllegalArgumentException("mantissa: " + mantissa);
        if (exponent < -8) throw new IllegalArgumentException("exponent: " + exponent);
        if (exponent > 10 || mantissa > POW10[10 - exponent])
            throw new IllegalArgumentException("value > 10^10: " + mantissa + "E" + exponent);
        return new KNumber(exponent <= -8 ? mantissa : mantissa * POW10[exponent + 8]);
    }

    @Override public int compareTo(KNumber o) {
        return Long.compare(ticks, o.ticks);
    }

    public BigDecimal toBigDecimal() {
        return BigDecimal.valueOf(ticks, 8);
    }

    public double toDouble() {
        return toBigDecimal().doubleValue();
    }
}