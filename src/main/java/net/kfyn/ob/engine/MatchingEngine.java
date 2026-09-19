package net.kfyn.ob.engine;

import net.kfyn.ob.entity.Order;
import net.kfyn.ob.entity.OrderBook;

public interface MatchingEngine {
    MatchResult submit(Order incoming);

    boolean cancel(long orderId);

    boolean isOpen(long orderId);

    int openCount();

    static MatchingEngine over(OrderBook book) {
        return new net.kfyn.ob.simple.SimpleMatchingEngine(book);
    }
}