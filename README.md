# jOrderBook

Java solution to the exchange-auction order-book exercise: build a limit order
book, let participants **submit, amend and cancel** orders during the auction
phase (no matching while the auction runs), then uncross the book at a single
clearing price found by the **Maximum Volume Matching** algorithm — the price
that maximizes the number of shares traded. The total matched volume at that
price is reported as well.

## Requirements

- JDK 25 (Gradle wrapper included; no other setup)

## Build & test

```sh
./gradlew test        # unit tests + JaCoCo coverage (gate: 90% line coverage)
./gradlew jmhSmoke    # quick benchmark smoke run (CI gate)
./gradlew jmh         # full benchmark suite
```

## Run the demo

```sh
./gradlew run
```

Prints the order book from the problem statement, the matching auction price,
the total matched volume, every trade executed at the clearing price, and the
unfilled (leftover) orders:

```
book: bids 102@50000, 1000@99, 700@98 | asks 100@100, 200@99, 500@96
matching auction price : 99
total matched volume   : 700 share(s)
imbalance at the price : 1102 buy vs 700 sell -> 402 extra buy share(s)
trades                 : 3, all at the clearing price
  trade    : bid #1 <-> ask #5, 102 share(s) @ 99
  ...
unfilled (leftover) orders : 3
  ...
```

## How the auction price is found

For each candidate price `p` (every price that appears on either side):

- `demand(p)` = total buy quantity priced >= `p`
- `supply(p)` = total sell quantity priced <= `p`
- `volume(p)` = `min(demand(p), supply(p))`

The clearing price is the candidate with the highest `volume(p)`; the total
matched volume is that volume. Buy orders priced >= the clearing price and
sell orders priced <= it are eligible; allocation is price-time (FIFO) at the
clearing price, and unexecuted remainder is reported as leftover orders.

Tie-break (several prices with the same maximum volume): prefer the price
with the smallest demand/supply imbalance; on equal imbalance the highest tied
price under pure buy pressure, otherwise the lowest. The problem statement
leaves this open — the rule is deterministic and covered by tests.

## Layout

| Path | Contents |
|---|---|
| `src/main/java/net/kfyn/ob/entity` | Order, Trade, Instrument, OrderBook abstractions |
| `src/main/java/net/kfyn/ob/engine` | Price/time order book, continuous matching engine, auction uncrossing engine |
| `src/main/java/net/kfyn/ob/Main.java` | Demo: uncrosses the book from the problem statement |
| `src/test/java` | JUnit 6 tests incl. a randomized auction-vs-reference property test |
| `src/jmh/java` | JMH benchmarks (separate source set; JMH never leaks into main/test code) |

External dependencies: JUnit 6 (test scope only) and JMH (benchmark source
set only). The main and test code uses only the JDK.

## CI

`.gitlab-ci.yml` runs `test` (JUnit + JaCoCo report, 90% line-coverage gate)
and a JMH smoke benchmark on every push; a full benchmark run is a manual job.