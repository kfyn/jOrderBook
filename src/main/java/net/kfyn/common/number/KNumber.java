package net.kfyn.common.number;

import java.math.BigDecimal;

public record KNumber(long mantissa, KExponent exponent) implements Comparable<KNumber> {
    public KNumber {
        if (exponent == null) throw new IllegalArgumentException("exponent is null");
        if (mantissa < 0) throw new IllegalArgumentException("invalid mantissa: " + mantissa);
        // value = mantissa * 10^exponent must not exceed 10^10
        if (mantissa > exponent.maxMantissa())
            throw new IllegalArgumentException(
                    "value exceeds 10^10: " + mantissa + "E" + exponent.exponent());
    }

    double toDouble() {
        return Double.parseDouble(mantissa + "E" + exponent().exponent());
    }

    BigDecimal toBigDecimal() {
        return BigDecimal.valueOf(mantissa()).scaleByPowerOfTen(exponent().exponent());
    }

    @Override public int compareTo(KNumber o) {
        long m = mantissa, om = o.mantissa;
        KExponent e = exponent, oe = o.exponent;
        if (m == om && e.equals(oe)) return 0;
        if (m == 0 || om == 0) return m == om ? 0 : (m == 0 ? -1 : 1);
        if (e.equals(oe)) return Long.compare(m, om);
        long k1 = e.magnitudeKey(m), k2 = oe.magnitudeKey(om);
        if (k1 != k2) return Long.compare(k1, k2);
        // same magnitude class: |e - oe| <= 18 by the KExponent bounds, so factorTo() is in range;
        // the 10^10 value bound keeps any scaled product under 10^18, so the overflow guards below
        // are defense-in-depth and cannot fire for legal instances
        if (e.exponent() < oe.exponent()) {
            long p = e.factorTo(oe);
            if (om > Long.MAX_VALUE / p) return -1;   // om*10^p overflows ⇒ om*10^p > m
            return Long.compare(m, om * p);
        } else {
            long p = oe.factorTo(e);
            if (m > Long.MAX_VALUE / p) return 1;    // m*10^p overflows ⇒ m*10^p > om
            return Long.compare(m * p, om);
        }
    }
}