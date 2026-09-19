package net.kfyn.ob.engine;

import net.kfyn.ob.entity.Order;

import java.util.List;

/**
 * Call auction (uncrossing): computes the single clearing price and
 * traded volume for a batch of identical-instrument orders, then
 * executes at that price. Implementations own the price-discovery rule
 * (max volume over the union of bid and ask prices) and the allocation
 * discipline. Stateless and single-threaded.
 */
public interface AuctionEngine {

    AuctionResult uncross(List<Order> bids, List<Order> asks);

    static AuctionEngine priceTime() {
        return new PriceTimeAuctionEngine();
    }
}