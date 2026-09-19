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
book: bids 100@100, 1000@99, 500@96 | asks 50000@102, 200@99, 700@98
matching auction price : 99
total matched volume   : 900 share(s)
imbalance at the price : 1100 buy vs 900 sell -> 200 extra buy share(s)
trades                 : 3, all at the clearing price
  trade    : bid #1 <-> ask #5, 100 share(s) @ 99
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

An uncrossed book (best bid below best ask) cannot trade at any price, so the
engine returns the no-cross result without aggregating or sweeping; the touch
case (best bid == best ask) still crosses.

Tie-break (several prices with the same maximum volume): prefer the price
with the smallest demand/supply imbalance; on equal imbalance the highest tied
price under pure buy pressure, otherwise the lowest. The problem statement
leaves this open — the rule is deterministic and covered by tests.

Orders are identified by their id, and the book/engine treat an order as equal
to any value-equal instance (the `Order` records have no hidden identity).
Passing a value-equal instance where the resting order is expected is
supported; callers that create their own instances must keep ids unique.

`PriceTimeOrderBook` runs the same uncross directly on the resting book:
`uncross()` is a pure query (no state change); `close()` additionally settles
the book to the auction outcome — fully filled orders removed, partially
filled orders reduced (new instances, same id), unfilled orders left resting —
and returns the result.

## Layout

| Path | Contents |
|---|---|
| `src/main/java/net/kfyn/ob/entity` | Order, Trade, Instrument, OrderBook abstractions |
| `src/main/java/net/kfyn/ob/engine` | Price/time order book and auction uncrossing engine |
| `src/main/java/net/kfyn/ob/Main.java` | Demo: uncrosses the book from the problem statement |
| `src/test/java` | JUnit 6 tests incl. a randomized auction-vs-reference property test |
| `src/jmh/java` | JMH benchmarks of the auction uncross (separate source set; JMH never leaks into main/test code) |

External dependencies: JUnit 6 (test scope only) and JMH (benchmark source
set only). The main and test code uses only the JDK.

## Interpreting the JMH results

The numbers in `build/jmh/results.json` are **relative** micro-benchmark results, not
absolute capacity or latency figures. They are only meaningful when comparing builds,
commits or code changes measured **on the same host, with the same JDK build, the same
JVM flags and under comparable load**. Do not compare scores across machines, CI
runners, cloud instances or JDK vendors/builds, and do not quote an "ops/s" figure as
a service guarantee: on this project the same engine measured roughly twice as fast on
a local machine as on a shared CI runner (e.g. `uncrossNoCross` 256 levels: ~545k
vs ~300k ops/s for the same commit).

Reading a result: `primaryMetric.score` is throughput (ops/s) and `scoreError` is the
half-width of the confidence interval — if two runs' `score ± scoreError` intervals
overlap, the difference is not statistically significant. Results from `-prof gc`
(`gc.count`, `gc.time`, and `gc.alloc.rate.norm` = bytes allocated per operation where
the running JDK build exposes per-thread allocation counters) are the memory evidence.

`./gradlew jmhSmoke` is the CI regression gate: short rounds (`-wi 2 -i 3 -r 1s -w 1s`)
and two forks, good for catching large regressions, too noisy for capacity planning.
`./gradlew jmh` is the fuller run (3 warmup + 5 measurement iterations, 2 forks); pass
`-PjmhArgs="..."` for extra JMH flags. Treat both as "is this commit faster or slower
than the last one on this host?", never as a hardware specification.

## CI

`.gitlab-ci.yml` runs `test` (JUnit + JaCoCo report, 90% line-coverage gate)
and a JMH smoke benchmark on every push; a full benchmark run is a manual job.