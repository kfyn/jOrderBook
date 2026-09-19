package net.kfyn.ob.engine;

import net.kfyn.ob.entity.Order;

public interface MatchingEngine {
    MatchResult submit(Order incoming);

    boolean cancel(long orderId);

    boolean isOpen(long orderId);

    int openCount();
}