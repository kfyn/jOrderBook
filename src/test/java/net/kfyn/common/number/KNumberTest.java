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
        void rejectsNullExponent() {
            assertThrows(IllegalArgumentException.class, () -> new KNumber(1, null));
        }

        @Test
        void rejectsNegativeMantissa() {
            var ex = assertThrows(IllegalArgumentException.class,
                    () -> new KNumber(-1, new KExponent(0)));
            assertTrue(ex.getMessage().contains("-1"));
        }

        @Test
        void rejectsExponentOutsideBounds() {
            assertThrows(IllegalArgumentException.class, () -> new KExponent(KExponent.MIN - 1));
            assertThrows(IllegalArgumentException.class, () -> new KExponent(KExponent.MAX + 1));
            assertDoesNotThrow(() -> new KExponent(KExponent.MIN));
            assertDoesNotThrow(() -> new KExponent(KExponent.MAX));
        }

        @Test
        void rejectsValueAbove10ToThe10() {
            // mantissa just above the cap for its exponent
            assertThrows(IllegalArgumentException.class,
                    () -> new KNumber(10_000_000_001L, new KExponent(0)));
            assertThrows(IllegalArgumentException.class,
                    () -> new KNumber(2, new KExponent(10)));
            assertThrows(IllegalArgumentException.class,
                    () -> new KNumber(1_000_000_000_000_000_001L, new KExponent(-8)));
            // exactly at the cap: legal
            assertDoesNotThrow(() -> new KNumber(10_000_000_000L, new KExponent(0)));
            assertDoesNotThrow(() -> new KNumber(1, new KExponent(10)));
            assertDoesNotThrow(() -> new KNumber(1_000_000_000_000_000_000L, new KExponent(-8)));
        }

        @Test
        void acceptsZeroWithAnyLegalExponent() {
            assertDoesNotThrow(() -> new KNumber(0, new KExponent(KExponent.MIN)));
            assertDoesNotThrow(() -> new KNumber(0, new KExponent(KExponent.MAX)));
        }

        @Test
        void magnitudeKeyDecidesAcrossExponents() {
            var big = new KNumber(1, new KExponent(10));       // 1e10
            var smaller = new KNumber(9_999_999_999L, new KExponent(0)); // ~1e10, same key class
            assertEquals(1, Integer.signum(big.compareTo(smaller)));
            assertEquals(-1, Integer.signum(smaller.compareTo(big)));
        }
    }

    @Nested
    @DisplayName("toBigDecimal")
    class ToBigDecimal {

        @Test
        void negativeExponentGivesPositiveScale() {
            var v = new KNumber(1234, new KExponent(-2)).toBigDecimal();
            assertEquals(new BigDecimal("12.34"), v);
            assertEquals(2, v.scale());
        }

        @Test
        void positiveExponentGivesNegativeScale() {
            var v = new KNumber(1, new KExponent(2)).toBigDecimal();
            assertEquals(new BigDecimal("1E+2"), v);
            assertEquals(-2, v.scale());
            assertEquals(0, v.compareTo(new BigDecimal("100")));
            assertNotEquals(new BigDecimal("100"), v);   // scale-sensitive equals: 1E+2 != 100
        }

        @Test
        void zeroKeepsExponentInScale() {
            var v = new KNumber(0, new KExponent(5)).toBigDecimal();
            assertEquals(0, v.compareTo(BigDecimal.ZERO));
            assertEquals(-5, v.scale());
        }

        @Test
        void maxMantissaIsPreservedExactly() {
            var v = new KNumber(999_999_999_999_999_999L, new KExponent(-8)).toBigDecimal();
            assertEquals(new BigDecimal("9999999999.99999999"), v);
        }
    }

    @Nested
    @DisplayName("toDouble")
    class ToDouble {

        @Test
        void exactWhenRepresentable() {
            assertEquals(1.5, new KNumber(15, new KExponent(-1)).toDouble());
            assertEquals(12.34, new KNumber(1234, new KExponent(-2)).toDouble());
        }

        @Test
        void zeroForAnyLegalExponent() {
            assertEquals(0.0, new KNumber(0, new KExponent(KExponent.MIN)).toDouble());
            assertEquals(0.0, new KNumber(0, new KExponent(KExponent.MAX)).toDouble());
        }

        @Test
        void extremeLegalValuesConvertExactly() {
            assertEquals(1.0E10, new KNumber(1, new KExponent(10)).toDouble());
            assertEquals(1.0E-8, new KNumber(1, new KExponent(-8)).toDouble());
            // 19-significant-digit max value: not exactly representable as a double, so oracle-compare
            assertEquals(decimal(999_999_999_999_999_999L, -8).doubleValue(),
                    new KNumber(999_999_999_999_999_999L, new KExponent(-8)).toDouble());
        }

        @Test
        void agreesWithBigDecimalConversion() {
            long[] mantissas = {1, 7, 1234, 999_999_999_999_999_999L};
            int[] exponents = {-8, -5, 0, 7, 10};
            for (long m : mantissas) {
                for (int e : exponents) {
                    if (m > pow10(10 - e)) continue;   // outside value bound
                    assertEquals(decimal(m, e).doubleValue(),
                            new KNumber(m, new KExponent(e)).toDouble(),
                            () -> m + "E" + e);
                }
            }
        }

        @Nested
        @DisplayName("equality semantics (as posted: representational)")
        class Equality {
            @Test
            void identicalComponentsAreEqual() {
                assertEquals(new KNumber(1, new KExponent(2)), new KNumber(1, new KExponent(2)));
                assertEquals(new KNumber(1, new KExponent(2)).hashCode(),
                        new KNumber(1, new KExponent(2)).hashCode());
            }

            @Test
            void sameValueDifferentPairIsNotEqual() {
                // FLIP THIS to assertEquals once you canonicalise in the compact constructor
                assertNotEquals(new KNumber(10, new KExponent(1)), new KNumber(1, new KExponent(2)));
            }

            @Test
            void treeAndHashDisagreeWhileUncanonicalised() {
                var ten = new KNumber(10, new KExponent(1));
                var alsoTen = new KNumber(1, new KExponent(2));
                assertEquals(2, new HashSet<>(List.of(ten, alsoTen)).size());
                assertEquals(1, new TreeSet<>(List.of(ten, alsoTen)).size());
            }
        }

        @Nested
        @DisplayName("compareTo")
        class Comparison {

            @Test
            void sameExponentComparesMantissas() {
                assertTrue(new KNumber(2, new KExponent(3)).compareTo(new KNumber(1, new KExponent(3))) > 0);
            }

            @Test
            void numericallyEqualPairsAreEqual() {
                assertEquals(0, new KNumber(10, new KExponent(1))
                        .compareTo(new KNumber(1, new KExponent(2))));
                assertEquals(0, new KNumber(0, new KExponent(10))
                        .compareTo(new KNumber(0, new KExponent(-8))));
            }

            @Test
            void magnitudeKeyDecidesAcrossExponents() {
                assertTrue(new KNumber(1, new KExponent(10))
                        .compareTo(new KNumber(99, new KExponent(0))) > 0);   // 1e10 vs 99
            }

            @Test
            void exactPathWhenKeysTie() {
                assertTrue(new KNumber(99, new KExponent(0))
                        .compareTo(new KNumber(1, new KExponent(1))) > 0);    // 99 vs 10, same key
            }

            @Test
            void zeroIsSmallest() {
                assertTrue(new KNumber(0, new KExponent(10))
                        .compareTo(new KNumber(1, new KExponent(-8))) < 0);
            }

            @Test
            void agreesWithBigDecimalOracle() {
                var rnd = new Random(20260915L);
                for (int i = 0; i < 10_000; i++) {
                    int e1 = rnd.nextInt(KExponent.MIN, KExponent.MAX + 1);
                    int e2 = rnd.nextInt(KExponent.MIN, KExponent.MAX + 1);
                    long m1 = rnd.nextLong(0, pow10(10 - e1) + 1);
                    long m2 = rnd.nextLong(0, pow10(10 - e2) + 1);
                    var a = new KNumber(m1, new KExponent(e1));
                    var b = new KNumber(m2, new KExponent(e2));
                    assertEquals(Integer.signum(decimal(m1, e1).compareTo(decimal(m2, e2))),
                            Integer.signum(a.compareTo(b)), () -> a + " vs " + b);
                    assertEquals(-Integer.signum(a.compareTo(b)), Integer.signum(b.compareTo(a)),
                            () -> "antisymmetry: " + a + " vs " + b);
                }
            }
        }
    }

    private static long pow10(int n) {
        long v = 1;
        for (int i = 0; i < n; i++) v *= 10;
        return v;
    }
}