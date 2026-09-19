package net.kfyn.ob;

/**
 * jOrderBook sim harness: resting book → opening call auction. Prints a summary of the
 * deterministic scenario (see {@link Simulation}).
 */
public class Main {

    static void main(String[] args) {
        Simulation.Outcome out = Simulation.run();

        printReqExample();
        System.out.println();

        System.out.printf("instrument : %s%n", out.instrument());
        System.out.printf("auction    : price=%s volume=%d trades=%d%n",
                price(out.auction().priceTicks()), out.auction().volumeTicks(), out.auction().trades().size());
        System.out.printf("engine     : %d trade(s) after auction, %d orders open%n",
                out.engineTrades().size(), out.openOrders());
        System.out.printf("book       : bestBid=%s bestAsk=%s%n",
                price(out.bestBidTicks()), price(out.bestAskTicks()));
        System.out.printf("flow       : submitted=%d open=%d traded=%d  (submitted == open + 2*traded: %s)%n",
                out.submittedQtyTicks(), out.openQtyTicks(), out.tradedQtyTicks(),
                out.submittedQtyTicks() == out.openQtyTicks() + 2 * out.tradedQtyTicks());
    }

    private static void printReqExample() {
        var r = ReqExample.uncrossBook();
        System.out.printf("book (buy qty / px / sell px / sell qty): 102@50000 | 1000@99 | 700@98  //  100@100 | 200@99 | 500@96%n");
        System.out.printf("matching auction price : %s%n",
                r.priceTicks().isPresent() ? ReqExample.BHP.pxValue(r.priceTicks().getAsLong()) : "none (no cross)");
        System.out.printf("total matched volume   : %s share(s)%n", ReqExample.BHP.qtyValue(r.volumeTicks()));
        System.out.printf("trades                  : %d, all at the clearing price%n", r.trades().size());
    }

    private static String price(java.util.OptionalLong ticks) {
        return ticks.isPresent() ? String.valueOf(ticks.getAsLong()) : "—";
    }
}