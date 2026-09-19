package net.kfyn.common.number;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class KNumberTest {

    private static BigDecimal decimal(long mantissa, int exponent) {
        return BigDecimal.valueOf(mantissa).scaleByPowerOfTen(exponent);
    }

    @Nested
    @DisplayName("construction invariants")
    class Construction {

        @Test
        void rejectsNegativeMantissa() {
            assertThrows(IllegalArgumentException.class, () -> KNumber.of(-1, 0));
        }

        @Test
        void rejectsNegativeTicks() {
            assertThrows(IllegalArgumentException.class, () -> new KNumber(-1));
        }

        @Test
        void rejectsTicksAbove10ToThe18() {
            assertThrows(IllegalArgumentException.class,
                    () -> new KNumber(1_000_000_000_000_000_001L));
            assertDoesNotThrow(() -> new KNumber(1_000_000_000_000_000_000L));
        }

        @Test
        void rejectsExponentBelowMinus8() {
            assertThrows(IllegalArgumentException.class, () -> KNumber.of(1, -9));
            assertDoesNotThrow(() -> KNumber.of(1, -8));
        }

        @Test
        void rejectsValueAbove10ToThe10() {
            assertThrows(IllegalArgumentException.class, () -> KNumber.of(10_000_000_001L, 0));
            assertThrows(IllegalArgumentException.class, () -> KNumber.of(2, 10));
            assertThrows(IllegalArgumentException.class, () -> KNumber.of(1_000_000_000_000_000_001L, -8));
            assertDoesNotThrow(() -> KNumber.of(10_000_000_000L, 0));
            assertDoesNotThrow(() -> KNumber.of(1, 10));
            assertDoesNotThrow(() -> KNumber.of(1_000_000_000_000_000_000L, -8));
        }

        @Test
        void acceptsZeroWithAnyLegalExponent() {
            assertDoesNotThrow(() -> KNumber.of(0, -8));
            assertDoesNotThrow(() -> KNumber.of(0, 10));
        }

        @Test
        void scalesMantissaToTicks() {
            assertEquals(0L, KNumber.of(0, 5).ticks());
            assertEquals(150L, KNumber.of(150, -8).ticks());
            assertEquals(1_500_000_000_000L, KNumber.of(150, 2).ticks());
            assertEquals(1_000_000_000_000_000_000L, KNumber.of(1_000_000_000_000_000_000L, -8).ticks());
        }
    }

    @Nested
    @DisplayName("toBigDecimal")
    class ToBigDecimal {

        @Test
        void negativeExponentGivesScale8() {
            var v = KNumber.of(1234, -2).toBigDecimal();
            assertEquals(0, v.compareTo(new BigDecimal("12.34")));
            assertEquals(8, v.scale());
            assertEquals(new BigDecimal("12.34000000"), v);
        }

        @Test
        void positiveExponentGivesNegativeScale() {
            var v = KNumber.of(1, 2).toBigDecimal();
            assertEquals(0, v.compareTo(new BigDecimal("100")));
        }

        @Test
        void zero() {
            assertEquals(0, KNumber.of(0, 5).toBigDecimal().compareTo(BigDecimal.ZERO));
        }

        @Test
        void maxTicksIsPreservedExactly() {
            var v = KNumber.of(999_999_999_999_999_999L, -8).toBigDecimal();
            assertEquals(new BigDecimal("9999999999.99999999"), v);
        }
    }

    @Nested
    @DisplayName("toDouble")
    class ToDouble {

        @Test
        void exactWhenRepresentable() {
            assertEquals(1.5, KNumber.of(15, -1).toDouble());
            assertEquals(12.34, KNumber.of(1234, -2).toDouble());
        }

        @Test
        void zero() {
            assertEquals(0.0, KNumber.of(0, 10).toDouble());
        }

        @Test
        void extremeLegalValuesConvertExactly() {
            assertEquals(1.0E10, KNumber.of(1, 10).toDouble());
            assertEquals(1.0E-8, KNumber.of(1, -8).toDouble());
            assertEquals(decimal(999_999_999_999_999_999L, -8).doubleValue(),
                    KNumber.of(999_999_999_999_999_999L, -8).toDouble());
        }

        @Test
        void agreesWithBigDecimalConversion() {
            long[] mantissas = {1, 7, 1234, 999_999_999_999_999_999L};
            int[] exponents = {-8, -5, 0, 7, 10};
            for (long m : mantissas) {
                for (int e : exponents) {
                    if (m > pow10(10 - e)) continue;
                    assertEquals(decimal(m, e).doubleValue(),
                            KNumber.of(m, e).toDouble(), () -> m + "E" + e);
                }
            }
        }
    }

    @Nested
    @DisplayName("equality semantics")
    class Equality {

        @Test
        void sameValueDifferentRepresentationIsEqual() {
            assertEquals(KNumber.of(10, 1), KNumber.of(1, 2));
            assertEquals(KNumber.of(10, 1).hashCode(), KNumber.of(1, 2).hashCode());
        }

        @Test
        void hashAndTreeAgree() {
            var ten = KNumber.of(10, 1);
            var alsoTen = KNumber.of(1, 2);
            assertEquals(1, new HashSet<>(List.of(ten, alsoTen)).size());
            assertEquals(1, new TreeSet<>(List.of(ten, alsoTen)).size());
        }
    }

    @Nested
    @DisplayName("compareTo")
    class Comparison {

        @Test
        void sameTicksCompareDirectly() {
            assertTrue(KNumber.of(2, 3).compareTo(KNumber.of(1, 3)) > 0);
        }

        @Test
        void numericallyEqualPairsCompareEqual() {
            assertEquals(0, KNumber.of(10, 1).compareTo(KNumber.of(1, 2)));
            assertEquals(0, KNumber.of(0, 10).compareTo(KNumber.of(0, -8)));
        }

        @Test
        void magnitudeDecidesAcrossExponents() {
            assertTrue(KNumber.of(1, 10).compareTo(KNumber.of(99, 0)) > 0);
        }

        @Test
        void exactPathWhenMagnitudeTies() {
            assertTrue(KNumber.of(99, 0).compareTo(KNumber.of(1, 1)) > 0);
        }

        @Test
        void zeroIsSmallest() {
            assertTrue(KNumber.of(0, 10).compareTo(KNumber.of(1, -8)) < 0);
        }

        @Test
        void agreesWithBigDecimalOracle() {
            var rnd = new Random(20260915L);
            for (int i = 0; i < 10_000; i++) {
                int e1 = rnd.nextInt(-8, 11);
                int e2 = rnd.nextInt(-8, 11);
                long m1 = rnd.nextLong(0, pow10(10 - e1) + 1);
                long m2 = rnd.nextLong(0, pow10(10 - e2) + 1);
                var a = KNumber.of(m1, e1);
                var b = KNumber.of(m2, e2);
                assertEquals(Integer.signum(decimal(m1, e1).compareTo(decimal(m2, e2))),
                        Integer.signum(a.compareTo(b)), () -> a + " vs " + b);
                assertEquals(-Integer.signum(a.compareTo(b)), Integer.signum(b.compareTo(a)),
                        () -> "antisymmetry: " + a + " vs " + b);
            }
        }

        @Test
        void ticksComparisonEqualsBigDecimalOracle() {
            var rnd = new Random(20260919L);
            for (int i = 0; i < 10_000; i++) {
                long t1 = rnd.nextLong(0, 1_000_000_000_000_000_000L);
                long t2 = rnd.nextLong(0, 1_000_000_000_000_000_000L);
                assertEquals(Integer.signum(new KNumber(t1).compareTo(new KNumber(t2))),
                        Integer.signum(BigDecimal.valueOf(t1, 8)
                                .compareTo(BigDecimal.valueOf(t2, 8))));
            }
        }
    }

    private static long pow10(int n) {
        long v = 1;
        for (int i = 0; i < n; i++) v *= 10;
        return v;
    }
}