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
        void acceptsZeroAndExtremeComponents() {
            assertDoesNotThrow(() -> new KNumber(0, new KExponent(Integer.MIN_VALUE)));
            assertDoesNotThrow(() -> new KNumber(Long.MAX_VALUE, new KExponent(Integer.MAX_VALUE)));
        }

        @Test
        void extremeExponentsDoNotOverflowTheKey() {
            var big = new KNumber(1, new KExponent(Integer.MAX_VALUE));
            var smaller = new KNumber(9, new KExponent(Integer.MAX_VALUE - 100));
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
        void fullLongIsPreservedExactly() {
            var v = new KNumber(Long.MAX_VALUE, new KExponent(-19)).toBigDecimal();
            assertEquals(new BigDecimal("0.9223372036854775807"), v);
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
        void zeroForAnyExponent() {
            assertEquals(0.0, new KNumber(0, new KExponent(-1000)).toDouble());
            assertEquals(0.0, new KNumber(0, new KExponent(1000)).toDouble());
        }

        @Test
        void saturatesInsteadOfThrowing() {
            assertTrue(Double.isInfinite(new KNumber(Long.MAX_VALUE, new KExponent(300)).toDouble()));
            assertTrue(Double.isInfinite(
                    new KNumber(1, new KExponent(Integer.MAX_VALUE)).toDouble()));
            assertEquals(0.0, new KNumber(1, new KExponent(-1000)).toDouble());
        }

        @Test
        void agreesWithBigDecimalConversion() {
            long[] mantissas = {1, 7, 1234, 999_999_999_999_999_999L, Long.MAX_VALUE};

            int[] exponents = {-25, -7, 0, 3, 22};
            for (long m : mantissas) {
                for (int e : exponents) {
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
                assertEquals(0, new KNumber(0, new KExponent(99))
                        .compareTo(new KNumber(0, new KExponent(-99))));
            }

            @Test
            void magnitudeKeyDecidesAcrossExponents() {
                assertTrue(new KNumber(1, new KExponent(17))
                        .compareTo(new KNumber(99, new KExponent(0))) > 0);   // 1e17 vs 99
            }

            @Test
            void exactPathWhenKeysTie() {
                assertTrue(new KNumber(99, new KExponent(0))
                        .compareTo(new KNumber(1, new KExponent(1))) > 0);    // 99 vs 10, same key
            }

            @Test
            void overflowFallbackPath() {
                // 999999999999999999e0 vs 1e17: equal keys, m*10^17 overflows a long
                assertTrue(new KNumber(999_999_999_999_999_999L, new KExponent(0))
                        .compareTo(new KNumber(1, new KExponent(17))) > 0);
            }

            @Test
            void zeroIsSmallest() {
                assertTrue(new KNumber(0, new KExponent(500))
                        .compareTo(new KNumber(1, new KExponent(-500))) < 0);
            }

            @Test
            void agreesWithBigDecimalOracle() {
                var rnd = new Random(20260915L);
                for (int i = 0; i < 10_000; i++) {
                    long m1 = rnd.nextLong(0, 1_000_000_000_000_000_000L);
                    long m2 = rnd.nextLong(0, 1_000_000_000_000_000_000L);
                    int e1 = rnd.nextInt(-25, 26);
                    int e2 = rnd.nextInt(-25, 26);
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
}




