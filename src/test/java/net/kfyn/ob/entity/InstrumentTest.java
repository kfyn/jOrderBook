package net.kfyn.ob.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class InstrumentTest {

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
                    () -> new Instrument("X", 1, 19, 1, 0));
        }

        @Test
        void parsesDecimalTickStrings() {
            var i = Instrument.of("BTCUSDT", "0.10", "0.00100");
            assertEquals(1L, i.pxTickM());
            assertEquals(1, i.pxScale());
            assertEquals(1L, i.qtyTickM());
            assertEquals(3, i.qtyScale());
        }

        @Test
        void parsesIntegerTickStrings() {
            var i = Instrument.of("JPY", "1", "1");
            assertEquals(1L, i.pxTickM());
            assertEquals(0, i.pxScale());
            assertEquals(1L, i.qtyTickM());
            assertEquals(0, i.qtyScale());
        }
    }

    @Nested
    @DisplayName("boundary conversion")
    class Conversion {

        Instrument btc = Instrument.of("BTCUSDT", "0.10", "0.001");
        Instrument pepe = Instrument.of("PEPEUSDT", "0.00000001", "1");

        @Test
        void exactOnTickValues() {
            assertEquals(50000L, btc.pxTicks(new BigDecimal("5000.00")));
            assertEquals(25000L, btc.pxTicks(new BigDecimal("2500")));
            assertEquals(123_000L, btc.qtyTicks(new BigDecimal("123.000")));
        }

        @Test
        void rejectsOffTick() {
            assertThrows(IllegalArgumentException.class,
                    () -> btc.pxTicks(new BigDecimal("5000.05")));
            assertThrows(IllegalArgumentException.class,
                    () -> btc.qtyTicks(new BigDecimal("123.0005")));
        }

        @Test
        void rejectsNegative() {
            assertThrows(IllegalArgumentException.class,
                    () -> btc.pxTicks(new BigDecimal("-1")));
            assertThrows(NullPointerException.class,
                    () -> btc.pxTicks(null));
        }

        @Test
        void fineGrainedTicks() {
            assertEquals(7L, pepe.pxTicks(new BigDecimal("0.00000007")));
            assertEquals(3L, pepe.qtyTicks(new BigDecimal("3")));
        }

        @Test
        void valueRoundTrip() {
            long[] pxT = {1, 2, 50000, 999_999_999_999L};
            for (long t : pxT) {
                assertEquals(t, btc.pxTicks(btc.pxValue(t)));
                assertEquals(t, btc.qtyTicks(btc.qtyValue(t)));
            }
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
}