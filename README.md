# nanotrade

[![Java](https://img.shields.io/badge/Java-8-ED8B00?logo=openjdk&logoColor=white)](https://java.com/)
[![Maven](https://img.shields.io/badge/Maven-3.9-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-2E86C1)](LICENSE)
[![Build](https://img.shields.io/badge/Build-Passing-27AE60)]()
[![Lines of Code](https://img.shields.io/badge/Lines_of_Code-1500+-8E44AD)]()

A production-grade stock market order matching engine built in pure Java 8 — no frameworks, no infrastructure, no shortcuts. The same core architecture that powers Nasdaq, NYSE, and Binance, implemented from scratch with deliberate data structure choices, greedy matching algorithms, real-time market data tracking, automated circuit breakers, session persistence, and a full audit trail.

This is not a toy project. It is a complete, working system built to the same design principles used by real financial exchanges.

---

## Table of Contents

- [What It Does](#what-it-does)
- [Why It Exists](#why-it-exists)
- [Architecture Overview](#architecture-overview)
- [Data Structure Decisions](#data-structure-decisions)
- [The Matching Algorithm](#the-matching-algorithm)
- [Complexity Analysis](#complexity-analysis)
- [Features](#features)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Command Reference](#command-reference)
- [Stress Test Results](#stress-test-results)
- [Circuit Breaker Design](#circuit-breaker-design)
- [Persistence Model](#persistence-model)
- [Audit Log](#audit-log)
- [Tech Stack](#tech-stack)
- [What Could Come Next](#what-could-come-next)

---

## What It Does

nanotrade accepts buy and sell limit orders from traders, maintains a live order book per trading symbol, and matches orders using a greedy price-time priority algorithm. When a new order arrives, the engine immediately checks the opposite side of the book for compatible resting orders and executes all possible matches in a single pass — partially filling where needed, respecting FIFO fairness at the same price level, and leaving any unmatched remainder in the book.

Every match updates the portfolios of both counterparties, advances the OHLCV market data for the symbol, and runs a circuit breaker check. Every operation — order placement, cancellation, execution, halt, resume — is written to an append-only audit log with millisecond timestamps. The entire engine state serializes to disk after every mutation so sessions survive restarts with full continuity.

---

## Why It Exists

Most Java portfolio projects demonstrate CRUD operations against a database or call a third-party API. This demonstrates something fundamentally different: the ability to reason about algorithmic complexity, make deliberate data structure trade-offs, design a system with multiple interacting subsystems, and implement it correctly under constraints that matter in production.

Order matching is one of the most performance-critical problems in software engineering. At Nasdaq, a 1-millisecond increase in matching latency can cost millions in lost order flow. Every design decision in this codebase has a reason rooted in that reality — even at the scale of a terminal application.

The question this project answers in an interview is not "can you code?" It is "do you understand why things are designed the way they are?"

---
## Showcase

> All screenshots taken in Windows Terminal. State persists across every restart.

### Startup & Command Reference
![Startup](assets/ss-1.png)

### Building the Order Book
![Order Book](assets/ss-2.png)

### Greedy Matching — Multiple Price Levels
![Matching](assets/ss-3.png)

### Order Book After Matching
![Post Match Book](assets/ss-4.png)

### Trade History
![Trade History](assets/ss-5.png)

### Portfolio Tracking — Holdings & P&L
![Portfolio](assets/ss-6.png)

### Market Data — OHLCV, VWAP, Price History
![Market Data](assets/ss-7.png)

### Multi-Symbol Support
![Multi Symbol](assets/ss-8.png)

### Order Cancellation
![Cancellation](assets/ss-9.png)

### Circuit Breaker — Auto Halt
![Circuit Breaker Triggered](assets/ss-10.png)

### Circuit Breaker — Rejection & Manual Resume
![Circuit Breaker Resume](assets/ss=11.png)

### Live Session Performance Metrics
![Performance](assets/ss-12.png)

### Stress Test — 10,000 Orders
![Stress Test](assets/ss-13.png)

### Audit Log
![Audit Log](assets/ss-14.png)

### Persistence — Session End
![Session End](assets/ss-15-a.png)

### Persistence — State Restored on Restart
![Session Restored](assets/ss-15-b.png)
---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        CommandParser                            │
│              (interactive terminal, input validation)           │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                       MatchingEngine                            │
│         (orchestrator — wires all subsystems together)          │
└──┬──────────┬──────────┬──────────┬──────────┬──────────────────┘
   │          │          │          │          │
   ▼          ▼          ▼          ▼          ▼
OrderBook  Portfolio  MarketData  Circuit   EventLogger
(per sym)  (per      Store       Breaker   (audit log)
           trader)               Manager
   │
   ▼
TreeMap<Double, Queue<Order>>   ← bids (descending)
TreeMap<Double, Queue<Order>>   ← asks (ascending)
HashMap<Long, Order>            ← fast cancellation lookup
```

The engine is the single point of coordination. It owns the lifecycle of every subsystem — creating them, wiring them together, and ensuring state is persisted after every mutating operation. No subsystem calls another directly; all cross-cutting concerns flow through the engine. This separation means each subsystem can be tested, replaced, or extended independently.

---

## Data Structure Decisions

Every data structure choice in this codebase is deliberate. Here is the reasoning behind each one.

### `TreeMap<Double, Queue<Order>>` for the order book

The core requirement of an order book is: given a new buy order with limit price P, find all sell orders with price ≤ P, starting from the lowest. This is an ordered range scan — a problem that a `HashMap` fundamentally cannot solve efficiently.

`TreeMap` is a Red-Black Tree under the hood. It maintains keys in sorted order at all times, giving:

- O(log n) insertion and deletion
- O(1) `firstKey()` / `lastKey()` — instant best-price lookup
- O(k log n) iteration over k price levels during matching

The bid-side `TreeMap` is initialised with `Collections.reverseOrder()` so that `firstKey()` always returns the highest bid. The ask-side uses natural ordering so `firstKey()` always returns the lowest ask. This means best-price access is always O(1) regardless of book depth.

An `ArrayList` alternative would require O(n log n) re-sorting on every insertion and O(n) iteration to find matching prices — catastrophically slow at exchange scale.

### `Queue<Order>` at each price level

Multiple orders can rest at the same price simultaneously. The exchange must fill them in the order they arrived — this is called price-time priority or FIFO fairness, and it is a regulatory requirement on real exchanges (SEC Rule 611).

A `LinkedList` implementing `Queue` at each price level enforces this: `peek()` always returns the earliest-placed order, `poll()` removes it when fully filled. The time complexity for serving the front of the queue is O(1).

### `HashMap<Long, Order>` for cancellations

When a trader cancels order #4721, the engine needs to locate that order instantly without scanning the book. The `HashMap` keyed on `orderId` provides O(1) average-case lookup. Without it, cancellation would require scanning every price level in the `TreeMap` — O(n) in the worst case.

The slight memory overhead of maintaining both structures simultaneously is the correct trade-off. Memory is cheap; latency is not.

### Summary

```
Structure                           Purpose                      Complexity
─────────────────────────────────────────────────────────────────────────────
TreeMap<Double, Queue<Order>> bids  Sorted bid book (desc)       O(log n) ops
TreeMap<Double, Queue<Order>> asks  Sorted ask book (asc)        O(log n) ops
HashMap<Long, Order> orderMap       Fast cancellation lookup     O(1) lookup
List<Match> executedMatches         Trade history per symbol     O(1) append
List<Long> matchLatenciesNs         Latency tracking             O(1) append
```

---

## The Matching Algorithm

The algorithm is greedy: always take the best available match immediately, without deferring to wait for a potentially better future match. This is provably optimal for limit-order books — any deferred match is strictly worse for at least one counterparty.

### Matching a BUY order

```
matchBuyOrder(buyOrder):
  for askPrice in asks.keySet():          // iterates ascending (lowest first)
    if askPrice > buyOrder.limitPrice:
      break                               // no further matches possible
    sellers = asks.get(askPrice)
    while buyOrder.remaining > 0 and sellers not empty:
      seller = sellers.peek()             // FIFO: earliest order at this price
      qty = min(buyOrder.remaining, seller.remaining)
      execute match at buyOrder.limitPrice   // aggressive price wins
      buyOrder.fill(qty)
      seller.fill(qty)
      if seller.remaining == 0: sellers.poll()
    if buyOrder.remaining == 0: break
  clean up empty price levels
  return matches
```

### Matching a SELL order

```
matchSellOrder(sellOrder):
  for bidPrice in bids.descendingKeySet():  // iterates descending (highest first)
    if bidPrice < sellOrder.limitPrice:
      break                                 // no further matches possible
    buyers = bids.get(bidPrice)
    while sellOrder.remaining > 0 and buyers not empty:
      buyer = buyers.peek()                 // FIFO: earliest order at this price
      qty = min(sellOrder.remaining, buyer.remaining)
      execute match at sellOrder.limitPrice    // aggressive price wins
      sellOrder.fill(qty)
      buyer.fill(qty)
      if buyer.remaining == 0: buyers.poll()
    if sellOrder.remaining == 0: break
  clean up empty price levels
  return matches
```

### Price determination

The execution price is always the aggressive (incoming) order's limit price. This means:

- If a buy order arrives at $152 and matches a resting sell at $150, execution is at $152. The seller receives more than they asked.
- If a sell order arrives at $148 and matches a resting buy at $150, execution is at $148. The buyer pays less than their limit.

This is standard limit-order book behaviour and is favourable to the passive (resting) side.

### Partial fills

If a buy order for 200 shares arrives and only 150 shares are available across all matching sell orders, the engine fills 150 shares, returns the 3 matches, and adds the remaining 50-share buy order to the book at the original limit price. The order's status transitions from `PENDING` → `PARTIALLY_FILLED`. When the remaining 50 shares are eventually matched, status transitions to `FILLED`.

---

## Complexity Analysis

| Operation | nanotrade | Naïve ArrayList | Factor at 1M orders |
|-----------|-----------|-----------------|---------------------|
| Add order to book | O(log n) | O(n log n) | ~100× faster |
| Match order (m matches) | O(log n × m) | O(n²) | ~1000× faster |
| Cancel order | O(log n) | O(n) | ~1000× faster |
| Best bid / best ask | O(1) | O(n) | Infinite (scan vs constant) |
| FIFO at same price | O(1) | O(n) | Always correct |

At 1,000,000 resting orders: `log₂(1,000,000) ≈ 20`. Every operation costs roughly 20 comparisons. An O(n) operation costs 1,000,000. That is a 50,000× difference on a fully loaded book.

---

## Features

**Order Management**
- Limit orders with BUY / SELL sides
- Partial fills with status tracking (`PENDING` → `PARTIALLY_FILLED` → `FILLED`)
- Order cancellation with O(1) lookup
- Multi-symbol support — any ticker symbol, unlimited

**Matching Engine**
- Greedy price-time priority matching
- FIFO fairness at identical price levels
- Automatic best-price execution (aggressive order's price wins)
- Spread detection — matching triggers when bid ≥ ask

**Portfolio Tracking**
- Per-trader holdings and share counts
- Average cost basis calculation (updates on every buy)
- Realized P&L on sell (execution price vs average cost)
- Cash spent on buys and cash received from sales tracked separately

**Market Data**
- OHLCV (Open, High, Low, Close, Volume) per symbol per session
- VWAP (Volume Weighted Average Price) — `Σ(price × qty) / Σqty`
- Price change and percentage change from open
- Rolling price history for last 20 trades with direction indicators

**Circuit Breakers**
- Automatic halt when price moves >5% within a 60-second window
- 30-second cooldown with automatic resume
- Manual override via `RESUME_MARKET`
- Halt count tracking per symbol per session
- All halts and resumes logged to audit trail

**Persistence**
- Full engine state serialized to disk after every mutating operation
- Order books, portfolios, market data, and circuit breaker state all restored on restart
- Monotonically increasing order IDs across sessions (no collision)

**Audit Logging**
- Append-only log across sessions — previous session entries are never overwritten
- Every order, match, cancellation, halt, resume, and session boundary recorded
- Millisecond-precision timestamps

**Performance Monitoring**
- Live session: orders processed, matches executed, shares traded, total value
- Throughput in orders/second
- Match latency: average, minimum, maximum in milliseconds
- Stress test: isolated benchmark with its own local books and monitor

---

## Project Structure

```
nanotrade/
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── nanotrade/
│                   ├── model/
│                   │   ├── Order.java            # Core order entity
│                   │   ├── OrderType.java         # BUY | SELL
│                   │   ├── OrderStatus.java       # PENDING | PARTIALLY_FILLED | FILLED | CANCELLED
│                   │   └── Match.java             # Executed trade record
│                   ├── engine/
│                   │   ├── OrderBook.java         # TreeMap bid/ask + greedy matching logic
│                   │   ├── MatchingEngine.java    # Central orchestrator
│                   │   ├── PerformanceMonitor.java# Throughput and latency tracking
│                   │   ├── StressTest.java        # Benchmark order generator
│                   │   └── StressTestResult.java  # Result carrier
│                   ├── portfolio/
│                   │   └── Portfolio.java         # Holdings, cost basis, realized P&L
│                   ├── market/
│                   │   ├── MarketData.java        # OHLCV, VWAP, price history per symbol
│                   │   ├── MarketDataStore.java   # Symbol → MarketData registry
│                   │   ├── CircuitBreaker.java    # Per-symbol halt/resume logic
│                   │   └── CircuitBreakerManager.java
│                   ├── persistence/
│                   │   └── PersistenceManager.java# Java serialization to disk
│                   ├── logging/
│                   │   └── EventLogger.java       # Append-only audit log writer
│                   ├── cli/
│                   │   ├── CommandParser.java     # Interactive terminal interface
│                   │   └── TablePrinter.java      # Fixed-width box-drawing utility
│                   └── Main.java                  # Entry point
├── data/                                          # Auto-created on first run
│   ├── orderbooks.dat                             # Serialized order books
│   ├── portfolios.dat                             # Serialized portfolios
│   ├── marketdata.dat                             # Serialized market data
│   ├── circuitbreakers.dat                        # Serialized circuit breaker state
│   └── nanotrade.log                              # Append-only audit log
├── .gitignore
└── pom.xml
```

---

## Getting Started

**Prerequisites**

- Java 8 or higher (`java -version`)
- Apache Maven 3.x (`mvn -version`)

**Clone and run**

```bash
git clone https://github.com/YOUR_USERNAME/nanotrade.git
cd nanotrade
mvn compile
mvn exec:java
```

**Build a runnable JAR**

```bash
mvn package
java -jar target/nanotrade.jar
```

**Recommended terminal:** Windows Terminal or any terminal with Cascadia Code / a Unicode-capable monospace font for correct box-drawing character rendering. VS Code's integrated terminal works but may render certain Unicode symbols incorrectly.

**First session example**

```
ADD_ORDER BUY  100 AAPL 150.00 alice
ADD_ORDER BUY   50 AAPL 150.50 bob
SHOW_BOOK AAPL
ADD_ORDER SELL  75 AAPL 149.50 carol
SHOW_BOOK AAPL
SHOW_TRADE_HISTORY AAPL
SHOW_PORTFOLIO alice
SHOW_MARKET_DATA AAPL
EXIT
```

Restart the engine — `SHOW_BOOK AAPL` will show the same state from the previous session.

---

## Command Reference

| Command | Arguments | Description |
|---------|-----------|-------------|
| `ADD_ORDER` | `<BUY\|SELL> <qty> <symbol> <price> <traderId>` | Place a limit order |
| `CANCEL_ORDER` | `<orderId> <symbol>` | Cancel a resting order |
| `SHOW_BOOK` | `<symbol>` | Display live bid/ask order book |
| `SHOW_ORDER` | `<orderId> <symbol>` | Order status, fill progress, details |
| `SHOW_PORTFOLIO` | `<traderId>` | Holdings, average cost, realized P&L |
| `SHOW_TRADE_HISTORY` | `<symbol>` | All executed trades for a symbol |
| `SHOW_MARKET_DATA` | `<symbol\|ALL>` | OHLCV, VWAP, price change, trade history |
| `SHOW_CIRCUIT` | `<symbol\|ALL>` | Circuit breaker status and halt history |
| `SHOW_PERFORMANCE` | — | Live session throughput and latency metrics |
| `SHOW_LOG` | `[numLines]` | Last N audit log entries (default 20) |
| `LIST_SYMBOLS` | — | All active trading symbols |
| `RESUME_MARKET` | `<symbol>` | Manually resume a halted symbol |
| `STRESS_TEST` | `<numOrders>` | Benchmark with N random orders (max 1,000,000) |
| `CLEAR_MARKET` | `<symbol>` | Reset a symbol's order book |
| `CLEAR_DATA` | — | Wipe all persisted state and start fresh |
| `HELP` | — | Print command reference |
| `EXIT` | — | Flush audit log and shut down cleanly |

---

## Stress Test Results

Tested on a standard laptop (Intel i5, 16GB RAM, Windows 11).

**10,000 orders**
```
Orders Generated  :  10,000
Matches Found     :   6,962
Match Rate        :   69.6%
Time Elapsed      :    66 ms
Throughput        : 151,515 orders/sec
Avg per Order     :  0.0066 ms
Avg Match Latency :  0.0055 ms
Min Match Latency :  0.0007 ms
Max Match Latency :  1.0598 ms
```

**100,000 orders**
```
Orders Generated  : 100,000
Matches Found     :  70,740
Match Rate        :   70.7%
Time Elapsed      :   262 ms
Throughput        : 381,679 orders/sec
Avg per Order     :  0.0026 ms
Avg Match Latency :  0.0028 ms
Min Match Latency :  0.0005 ms
Max Match Latency :  0.9814 ms
```

The benchmark uses a fixed random seed for reproducibility. Orders are distributed across 5 symbols (AAPL, GOOGL, MSFT, TSLA, AMZN) and 5 traders with a ±5% price spread around a $150 base price — wide enough to generate realistic partial fills and resting orders, tight enough to produce a ~70% match rate.

The stress test uses isolated order books separate from the live session to avoid polluting the trader portfolios and market data of an active session.

---

## Circuit Breaker Design

The circuit breaker monitors every trade execution and halts trading on a symbol if price volatility exceeds a defined threshold within a rolling time window. This mirrors real exchange mechanisms — NYSE's Limit Up/Limit Down (LULD) rules and Nasdaq's Market-Wide Circuit Breakers operate on the same principle.

**Parameters**

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| Halt threshold | 5% price move | Consistent with Tier 2 LULD bands for mid-cap stocks |
| Measurement window | 60 seconds | Rolling window resets on expiry |
| Auto-resume cooldown | 30 seconds | Allows market to stabilize before reopening |
| Manual override | `RESUME_MARKET <symbol>` | Operator can force-resume at any time |

**Lifecycle**

```
Trade executes
      │
      ▼
CircuitBreakerManager.onTrade(symbol, price)
      │
      ▼
CircuitBreaker.checkAndUpdate(price)
      │
      ├── First trade → set reference price, return false
      │
      ├── Window expired → reset reference price to current, return false
      │
      ├── |Δprice / reference| < 5% → return false
      │
      └── |Δprice / reference| >= 5% → halt(), return true
                                              │
                                              ▼
                                     Reject all incoming orders
                                     Log halt to audit trail
                                     Start 30s cooldown timer
                                              │
                                              ▼
                               Auto-resume (timer) or RESUME_MARKET
                                              │
                                              ▼
                                     Reset reference price
                                     Reset window timer
                                     Log resume to audit trail
```

The circuit breaker state is persisted to disk so a halted symbol remains halted across engine restarts. This prevents a market manipulation scenario where a halt is circumvented by simply restarting the engine.

---

## Persistence Model

The engine uses Java's built-in object serialization to persist state. All core data classes implement `Serializable`. The `PersistenceManager` writes four independent files:

| File | Contents |
|------|----------|
| `data/orderbooks.dat` | All `OrderBook` instances including resting orders and trade history |
| `data/portfolios.dat` | All `Portfolio` instances including holdings and P&L |
| `data/marketdata.dat` | All `MarketData` instances including OHLCV and price history |
| `data/circuitbreakers.dat` | All `CircuitBreaker` instances including halt state and cooldown |

State is written after every mutating operation — order placement, cancellation, and market clear. The write is synchronous and completes before the command prompt returns, so no state is ever lost even if the process is killed immediately after a command.

On startup, the engine detects saved state, deserializes all four files, and reconstructs the `nextOrderId` counter as `max(existing order IDs) + 1` to guarantee monotonically increasing IDs across sessions with no risk of collision.

The audit log is intentionally kept separate from the serialized state. It is an append-only `BufferedWriter` that accumulates entries across sessions without ever being overwritten — a permanent record of everything that happened in the engine's lifetime.

---

## Audit Log

Every operation produces a structured log entry in `data/nanotrade.log`:

```
[2026-03-15 17:21:30.001] SYSTEM     SESSION_START        nanotrade engine started
[2026-03-15 17:21:34.210] ORDER      ADD                  #1 BUY 100 AAPL @ $150.00 trader=alice
[2026-03-15 17:21:38.442] ORDER      ADD                  #2 SELL 100 AAPL @ $149.00 trader=bob
[2026-03-15 17:21:38.451] MATCH      EXECUTE              buy=#1 sell=#2 qty=100 price=$149.00 symbol=AAPL
[2026-03-15 17:21:44.823] ORDER      ADD                  #3 BUY 100 AAPL @ $158.00 trader=alice
[2026-03-15 17:21:46.330] ORDER      ADD                  #4 SELL 100 AAPL @ $157.00 trader=carol
[2026-03-15 17:21:46.341] MATCH      EXECUTE              buy=#3 sell=#4 qty=100 price=$157.00 symbol=AAPL
[2026-03-15 17:21:46.343] CIRCUIT    HALT                 symbol=AAPL reason=price moved 5.37% (ref=$149.00, now=$157.00)
[2026-03-15 17:21:47.001] ORDER      REJECT               #5 reason=AAPL circuit breaker active
[2026-03-15 17:22:16.344] CIRCUIT    RESUME               symbol=AAPL
[2026-03-15 17:22:45.300] STRESS     COMPLETE             orders=10000 matches=6962 elapsed=66ms throughput=151515 o/s
[2026-03-15 17:23:10.001] SYSTEM     SESSION_END          nanotrade engine stopped
[2026-03-15 17:23:10.001] SYSTEM     ─────────────
```

The log format is: `[timestamp] CATEGORY   EVENT                detail`. Session boundaries are visually separated by a divider line. The `SHOW_LOG [n]` command displays the last N entries inline in the terminal.

In a real exchange context, this log would be the basis for regulatory reporting, dispute resolution, and post-trade analysis. The design deliberately mirrors the structure of FIX execution reports.

---

## Tech Stack

| Layer | Technology | Notes |
|-------|------------|-------|
| Language | Java 8 | Chosen for strict type safety and performance predictability |
| Build tool | Apache Maven 3.9 | Standard dependency management and fat JAR packaging |
| Core data structures | `TreeMap`, `HashMap`, `LinkedList`, `ArrayList` | All from `java.util`, no third-party collections |
| Persistence | Java Object Serialization (`ObjectOutputStream`) | Zero-dependency, sufficient for single-node state |
| Logging | `BufferedWriter` with `FileWriter(file, true)` | Append mode, flush-on-write for durability |
| Terminal interface | `Scanner` + `System.out` | Pure stdio, no curses or TUI libraries |
| Testing framework | JUnit 4 | Structure in place, test coverage is a noted extension |
| Packaging | `maven-assembly-plugin` | Produces a single runnable fat JAR |

**Zero external runtime dependencies.** No Spring, no Hibernate, no Jackson, no Guava, no message queues, no embedded databases. Every subsystem — matching, persistence, logging, circuit breaking, portfolio tracking, market data — is hand-written. This is intentional: it demonstrates understanding of how these systems work rather than how to configure a library that implements them.

---

## What Could Come Next

The following extensions would each add meaningful depth to the project and represent natural interview conversation topics.

**Algorithmic**
- **Market orders** — Execute immediately at the best available price regardless of limit. Requires handling the edge case where the book is empty or has insufficient depth.
- **Time-In-Force order types** — IOC (Immediate Or Cancel), FOK (Fill Or Kill), GTD (Good Till Date). Each changes the post-matching behaviour for unfilled remainders.
- **Pro-rata matching** — Instead of FIFO, distribute fills proportionally across all orders at the same price level. Used by CME Group for options. Requires replacing the `Queue` with a weighted allocation structure.
- **Iceberg orders** — Large orders with a visible quantity and a hidden reserve. The book shows only the tip; the reserve refills automatically as the visible portion is consumed.

**Systems**
- **Thread safety and concurrency** — Add a `ReentrantLock` per symbol so multiple threads can submit orders to different symbols simultaneously without contention. This is the first step toward a genuinely concurrent matching engine.
- **Self-match prevention** — Reject or cancel orders where the incoming order would match against a resting order from the same trader. Required by regulation on all US exchanges.
- **WebSocket API** — Wrap the engine in a Netty or Jetty server. Clients connect via WebSocket to submit orders and receive real-time execution reports. Transforms this from a CLI tool into a real networked service.
- **FIX Protocol parsing** — Financial Information eXchange (FIX) is the actual industry-standard messaging protocol for order submission. Parsing even a basic subset (NewOrderSingle, ExecutionReport, OrderCancelRequest) is an exceptionally strong signal on a Java fintech resume.

**Reliability**
- **Write-ahead log** — Before executing a match, write the intended operation to a WAL. On restart, replay the WAL to recover from a crash mid-operation. This gives crash-safe durability without a full database.
- **Snapshot + replay** — Instead of serializing full state, write incremental events and periodically take a full snapshot. Restart by loading the latest snapshot and replaying events since it. Standard technique in event-sourced systems.

**Testing**
- **JUnit test suite** — The test directory structure is already in place. Unit tests for partial fills, FIFO ordering, empty book edge cases, self-match scenarios, and circuit breaker threshold boundary conditions would demonstrate engineering discipline and are often a pass/fail requirement in take-home assessments.

---

*Built in pure Java 8. Every line written by hand.*
