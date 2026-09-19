package net.kfyn.ob;

/**
 * Prints a summary of the deterministic scenario (see {@link Simulation}).
 */
public class Main {

    static void main(String[] args) {
        printReqExample();
        System.out.println();
    }

    private static void printReqExample() {
        var r = ReqExample.uncrossBook();
        System.out.printf("book (buy qty / px / sell px / sell qty): 102@50000 | 1000@99 | 700@98  //  100@100 | 200@99 | 500@96%n");
        System.out.printf("matching auction price : %s%n",
                r.priceTicks().isPresent() ? ReqExample.BHP.pxValue(r.priceTicks().getAsLong()) : "none (no cross)");
        System.out.printf("total matched volume   : %s share(s)%n", ReqExample.BHP.qtyValue(r.volumeTicks()));
        System.out.printf("trades                  : %d, all at the clearing price%n", r.trades().size());
    }
}