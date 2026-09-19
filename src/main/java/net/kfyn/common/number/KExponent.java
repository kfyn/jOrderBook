package net.kfyn.common.number;

/**
 * Decimal exponent in [-8, 10], with the scaling math for the bounded domain.
 * The order of magnitude of a KNumber is fully determined by its exponent and
 * mantissa digit count; this type owns the power-of-ten table and guarantees
 * every table index derived from legal exponents is in range.
 */
public record KExponent(int exponent) {
    public static final int MIN = -8;
    public static final int MAX = 10;

    public KExponent {
        if (exponent < MIN || exponent > MAX) {
            throw new IllegalArgumentException(
                    "exponent out of range [" + MIN + ", " + MAX + "]: " + exponent);
        }
    }

    /** Largest mantissa a KNumber with this exponent may carry without exceeding 10^10. */
    public long maxMantissa() {
        return POW10[MAX - exponent];
    }

    /** Order of magnitude of {@code mantissa * 10^exponent}: its decimal digit position. */
    public long magnitudeKey(long mantissa) {
        return (long) digits(mantissa) + exponent;
    }

    /**
     * Scaling factor 10^(target.exponent - exponent) to shift a mantissa from this
     * exponent up to {@code target}. Requires target's exponent >= this one; the
     * bounded domain keeps the table index in [0, 18].
     */
    public long factorTo(KExponent target) {
        int delta = target.exponent - exponent;
        if (delta < 0) throw new IllegalArgumentException("target exponent below this: " + delta);
        return POW10[delta];
    }

    /** POW10[i] == 10^i, for i in 0..18. Index range is guaranteed by MIN/MAX: |e1 - e2| <= 18. */
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
    private static int digits(long x) {
        int lo = 0, hi = POW10.length - 1;   // invariant: POW10[lo] <= x
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (POW10[mid] <= x) lo = mid; else hi = mid - 1;
        }
        return lo + 1;
    }
}