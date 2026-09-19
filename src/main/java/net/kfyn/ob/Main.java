package net.kfyn.ob;

/**
 * jOrderBook sim harness: resting book → opening call auction →
 * continuous matching over rested leftovers. Prints a summary of the
 * deterministic scenario (see {@link Simulation}).
 */
public class Main {

    public static void main(String[] args) {
        Simulation.Outcome out = Simulation.run();

        System.out.println("== jOrderBook sim ==");
        System.out.printf("instrument : %s%n", out.instrument());
        System.out.printf("auction    : price=%s volume=%d trades=%d%n",
                price(out.auction().priceTicks()), out.auction().volumeTicks(), out.auction().trades().size());
        System.out.println("auction    : leftovers rest on the book (opening policy)");
        System.out.printf("engine     : %d trade(s) after auction, %d orders open%n",
                out.engineTrades().size(), out.openOrders());
        System.out.printf("book       : bestBid=%s bestAsk=%s%n",
                price(out.bestBidTicks()), price(out.bestAskTicks()));
        System.out.printf("flow       : submitted=%d open=%d traded=%d  (submitted == open + 2*traded: %s)%n",
                out.submittedQtyTicks(), out.openQtyTicks(), out.tradedQtyTicks(),
                out.submittedQtyTicks() == out.openQtyTicks() + 2 * out.tradedQtyTicks());
        System.out.println("== end ==");
    }

    private static String price(java.util.OptionalLong ticks) {
        return ticks.isPresent() ? String.valueOf(ticks.getAsLong()) : "—";
    }
}