package net.kfyn.common.number;

import java.math.BigDecimal;

public record KNumber(long mantissa, KExponent exponent) implements Comparable<KNumber> {
    public KNumber {
        if (exponent == null) throw new IllegalArgumentException("exponent is null");
        if (mantissa < 0) throw new IllegalArgumentException("invalid mantissa: " + mantissa);
    }

    double toDouble() {
        return Double.parseDouble(mantissa + "E" + exponent().exponent());
    }

    BigDecimal toBigDecimal() {
        return BigDecimal.valueOf(mantissa()).scaleByPowerOfTen(exponent().exponent());
    }

    @Override public int compareTo(KNumber o) {
        long m = mantissa, om = o.mantissa;
        int e = exponent.exponent(), oe = o.exponent.exponent();
        if (m == om && e == oe) return 0;
        if (m == 0 || om == 0) return m == om ? 0 : (m == 0 ? -1 : 1);
        if (e == oe) return Long.compare(m, om);
        long k1 = digits(m) + e, k2 = digits(om) + oe;
        if (k1 != k2) return Long.compare(k1, k2);
        // same magnitude class: exponents differ by ≤ 18, scale the smaller one if it fits
        if (e < oe) { long p = POW10[oe - e]; if (m  <= Long.MAX_VALUE / p) return Long.compare(m * p, om); }
        else        { long p = POW10[e - oe]; if (om <= Long.MAX_VALUE / p) return Long.compare(m, om * p); }
        return BigDecimal.valueOf(m).scaleByPowerOfTen(e)
                .compareTo(BigDecimal.valueOf(om).scaleByPowerOfTen(oe));  // rare
    }

    /** POW10[i] == 10^i, for i in 0..18. 10^18 is the last power of ten that fits a long. */
    private static final long[] POW10 = {
            1L,
            10L,
            100L,
            1_000L,
            10_000L,
            100_000L,
            1_000_000L,
            10_000_000L,
            100_000_000L,
            1_000_000_000L,
            10_000_000_000L,
            100_000_000_000L,
            1_000_000_000_000L,
            10_000_000_000_000L,
            100_000_000_000_000L,
            1_000_000_000_000_000L,
            10_000_000_000_000_000L,
            100_000_000_000_000_000L,
            1_000_000_000_000_000_000L,
    };

    /** Number of decimal digits of x, for x >= 1. Returns 1..19. */
    static int digits(long x) {
        int lo = 0, hi = POW10.length - 1;   // invariant: POW10[lo] <= x
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (POW10[mid] <= x) lo = mid; else hi = mid - 1;
        }
        return lo + 1;
    }
}
