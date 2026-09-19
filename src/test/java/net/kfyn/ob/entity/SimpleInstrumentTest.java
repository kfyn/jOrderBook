package net.kfyn.ob.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import net.kfyn.ob.impl.*;

import static org.junit.jupiter.api.Assertions.*;

class SimpleInstrumentTest {

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        void rejectsBadSymbol() {
            assertThrows(IllegalArgumentException.class, () -> Instrument.of(null, "0.01", "0.001"));
            assertThrows(IllegalArgumentException.class, () -> Instrument.of("", "0.01", "0.001"));
            assertThrows(IllegalArgumentException.class, () -> Instrument.of(" ", "0.01", "0.001"));
        }

        @Test
        void rejectsZeroOrNegativeTick() {
            assertThrows(IllegalArgumentException.class, () -> Instrument.of("BTCUSDT", "0", "0.001"));
            assertThrows(IllegalArgumentException.class, () -> Instrument.of("BTCUSDT", "-0.01", "0.001"));
            assertThrows(IllegalArgumentException.class, () -> Instrument.of("BTCUSDT", "0.01", "0"));
        }

        @Test
        void rejectsScaleAbove18() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SimpleInstrument("X", 1, 19, 1, 0));
        }

        @Test
        void parsesDecimalTickStrings() {
            var i = Instrument.of("BTCUSDT", "0.10", "0.00100");
            assertEquals(1L, i.pxTickMantissa());
            assertEquals(1, i.pxScale());
            assertEquals(1L, i.qtyTickMantissa());
            assertEquals(3, i.qtyScale());
        }

        @Test
        void parsesIntegerTickStrings() {
            var i = Instrument.of("JPY", "1", "1");
            assertEquals(1L, i.pxTickMantissa());
            assertEquals(0, i.pxScale());
        }

        @Test
        void parsesScientificNotationTicks() {
            assertEquals(3, Instrument.of("X", "1E-3", "1").pxScale());
            assertEquals(1000L, Instrument.of("X", "1E+3", "1").pxTickMantissa());
            assertEquals(1L, Instrument.of("X", "10E-1", "1").pxTickMantissa());
        }
    }

    @Nested
    @DisplayName("boundary conversion")
    class Conversion {

        final Instrument btc = Instrument.of("BTCUSDT", "0.10", "0.001");
        final Instrument pepe = Instrument.of("PEPEUSDT", "0.00000001", "1");

        @Test
        void exactOnTickValues() {
            assertEquals(50000L, btc.pxTicks(new BigDecimal("5000.00")));
            assertEquals(25000L, btc.pxTicks(new BigDecimal("2500")));
            assertEquals(123_000L, btc.qtyTicks(new BigDecimal("123.000")));
        }

        @Test
        void negativePxAllowed() {
            long t = btc.pxTicks(new BigDecimal("-5000.00"));
            assertEquals(-50000L, t);
            assertEquals(0, new BigDecimal("-5000.00").compareTo(btc.pxValue(t)));
        }

        @Test
        void rejectsOffTick() {
            assertThrows(IllegalArgumentException.class,
                    () -> btc.pxTicks(new BigDecimal("5000.05")));
            assertThrows(IllegalArgumentException.class,
                    () -> btc.qtyTicks(new BigDecimal("123.0005")));
        }

        @Test
        void rejectsNonPositiveQty() {
            assertThrows(IllegalArgumentException.class, () -> btc.qtyTicks(BigDecimal.ZERO));
            assertThrows(IllegalArgumentException.class, () -> btc.qtyTicks(new BigDecimal("-1")));
            assertEquals(1L, btc.qtyTicks(new BigDecimal("0.001")));
        }

        @Test
        void rejectsNullValue() {
            assertThrows(NullPointerException.class, () -> btc.pxTicks(null));
            assertThrows(NullPointerException.class, () -> btc.qtyTicks(null));
        }

        @Test
        void fineGrainedTicks() {
            assertEquals(7L, pepe.pxTicks(new BigDecimal("0.00000007")));
            assertEquals(3L, pepe.qtyTicks(new BigDecimal("3")));
        }

        @Test
        void valueRoundTrip() {
            long[] pxTicks = {1, 2, 50000, -50000, 999_999_999_999L};
            for (long t : pxTicks) assertEquals(t, btc.pxTicks(btc.pxValue(t)));
            long[] qtyTicks = {1, 2, 123_000};
            for (long t : qtyTicks) assertEquals(t, btc.qtyTicks(btc.qtyValue(t)));
        }

        @Test
        void valueIsExactDecimal() {
            assertEquals(0, new BigDecimal("5000.00").compareTo(btc.pxValue(50000L)));
            assertEquals(new BigDecimal("123.000"), btc.qtyValue(123_000L));
            assertEquals(new BigDecimal("0.00000007"), pepe.pxValue(7L));
        }

        @Test
        void ticksComparisonEqualsValueComparison() {
            var a = btc.pxTicks(new BigDecimal("4999.90"));
            var b = btc.pxTicks(new BigDecimal("5000.00"));
            assertTrue(a < b);
            assertEquals(Integer.signum(btc.pxValue(a).compareTo(btc.pxValue(b))),
                    Integer.signum(Long.compare(a, b)));
        }
    }

    @Nested
    @DisplayName("overflow regression")
    class Overflow {

        @Test
        void valueDoesNotWrapOnLongOverflow() {
            var i = Instrument.of("X", "2", "1");   // tickM=2
            long t = i.pxTicks(new BigDecimal("10000000000000000000"));  // 5e18
            assertThrows(ArithmeticException.class, () -> i.pxValue(t));  // 1e19 overflows: fail, never wrap
        }

        @Test
        void valueSafeAtBoundary() {
            var i = Instrument.of("X", "2", "1");
            long lastSafe = Long.MAX_VALUE / 2;
            assertEquals(0, new BigDecimal("9223372036854775806")
                    .compareTo(i.pxValue(lastSafe)));
        }

        @Test
        void ticksTooLargeThrowsIaeNotArithmetic() {
            var i = Instrument.of("X", "2", "1");
            assertThrows(IllegalArgumentException.class,
                    () -> i.pxTicks(new BigDecimal("1E30")));
            assertThrows(IllegalArgumentException.class,
                    () -> i.pxTicks(new BigDecimal("-1E30")));
        }
    }
}