# MPSC Queue

`MpscQueue<T>` — a bounded, multi-producer/single-consumer queue based on
Dmitry Vyukov's intrusive MPSC design. Producers coordinate through one
atomic exchange (no CAS-retry loop); the single consumer thread needs no
synchronization on its own read/advance of `consumerHead` at all.

Unlike `TreiberStack` (Milestone 1, `VarHandle`-based), this class uses
`AtomicReference`/`AtomicInteger` — a deliberate choice to compare both
approaches across the portfolio, not a claim that one is generally better.
See ADR-010 for the full comparison.

## The algorithm

Two pointers, named for what touches them rather than Michael-Scott's
head/tail convention (to avoid confusion when cross-referencing both
algorithms in this repo):

- `producerTail` — where producers link new nodes in. Every producer thread
  exchanges this via `AtomicReference.getAndSet`, never a CAS loop.
- `consumerHead` — where the consumer reads from. Touched by exactly one
  thread, ever. Plain field, no synchronization primitive at all.
- A **stub node**, allocated once at construction, that both pointers
  initially reference and that gets reused for the rest of the queue's
  life. Its purpose is twofold: it means `producerTail` never needs a
  null-check special case for "queue was empty," and it's reused later to
  resolve the race window described below.

```
offer(value):                                  # producer, N threads
  requireNonNull(value)
  reserved = count.getAndIncrement()           # atomic reservation ticket
  if reserved >= capacity:
    count.getAndDecrement()                    # release the ticket, no space
    return false
  node = Node(value)
  prev = producerTail.getAndSet(node)          # unconditional exchange - never retries
  prev.next = node                             # publish the link (release write)
  return true

resolve(consume):                              # consumer, single thread, no CAS ever
  tail = consumerHead
  next = tail.next
  if tail is the stub:
    if next == null:
      return UNRESOLVED                        # see "The race window" below
    consumerHead = next                        # unlink the stub, advance onto it
    tail = next
    next = next.next
  if next == null:
    return UNRESOLVED
  if consume:
    consumerHead = next
    count.getAndDecrement()
  return tail

dequeue(consume):
  result = resolve(consume)
  if result == UNRESOLVED:
    if count.get() == 0:
      return null                              # nothing reserved anywhere: genuinely empty
    pushStub()                                 # reclaim tail as consumable
    result = resolve(consume)
  return result == UNRESOLVED ? null : result.value

poll() = dequeue(consume = true)
peek() = dequeue(consume = false)
```

`peek()` shares `resolve`/`dequeue` with `poll()` but passes
`consume = false`, so it can still advance `consumerHead` past a stub node
(bookkeeping, not a removal - the stub carries no value) without ever
decrementing `count` or moving past a real, unconsumed node.

## The race window, and how `count` resolves it

Between a producer's `producerTail.getAndSet(node)` and its following
`prev.next = node`, there's a real gap: the node is reachable from
`producerTail` (so the next producer correctly links after it) but not yet
reachable from `consumerHead`'s walk (`prev.next` is still `null`). If
`resolve()` runs during that exact gap, `tail.next == null` even though the
queue isn't actually empty — the classic ambiguity in this algorithm
between "empty" and "a producer is mid-publish."

`count` (needed anyway for the capacity check) is authoritative
independent of the linked list, so `dequeue()` uses it to tell the two
cases apart: if `count > 0`, the list isn't really empty and this is the
race window, not the empty case. Rather than spin unboundedly (the window
is a single JMM store on the producer's thread), `dequeue()` pushes the
stub node back in (`pushStub()`) and retries `resolve()` once.

**This applies uniformly to both branches of `resolve()`.** An earlier
draft of this algorithm let the stub branch (`tail is the stub, next ==
null`) return "empty" unconditionally, on the reasoning that hitting the
stub with nothing after it looks like the base case. That's wrong — a
producer's `count` reservation can be registered before its node is
linked regardless of whether `consumerHead` currently sits on the stub or
on a real node, so the same ambiguity applies there too. Treating it as a
special case broke the queue's own contract that `poll()` returning `null`
always means genuinely empty. `resolve()` returns the same `UNRESOLVED`
sentinel from both branches, and `dequeue()` applies one retry policy to
both.

If, after `pushStub()`, `resolve()` still comes back `UNRESOLVED`, the
producer we raced against wasn't the one whose `prev` equals our stalled
`tail` — some other producer is further ahead in a multi-hop backlog.
`dequeue()` returns `null` in that case (true race loss this round) rather
than retrying again; the next call to `poll()`/`peek()` will pick up wherever
the list has settled by then.

### Reusing the stub node correctly

`pushStub()` reuses the same long-lived stub node for every race-window
recovery over the life of the queue, rather than allocating a fresh one
each time. A freshly constructed `Node` starts with `next == null` by
definition; the reused stub does not — it still points at whatever it was
last linked to. `pushStub()` resets `stub.next = null` before re-publishing
it via the exchange:

```
pushStub():
  stub.next = null
  prev = producerTail.getAndSet(stub)
  prev.next = stub
```

Skipping that reset resurrects an already-consumed node the next time
`consumerHead` walks through the stub — caught by
`MpscQueueTest.repeatedFullDrainCyclesDoNotResurrectStaleValues`, which
runs several full offer/drain cycles back to back (a single cycle doesn't
reuse the stub enough to expose it).

## Progress guarantee

- **Producers: lock-free, and stronger than Milestone 1's stack — no retry
  loop at all.** `getAndSet` on `producerTail` always succeeds in one step;
  there is no CAS failure/retry path for producers to lose to each other.
- **Consumer: not lock-free in the strict sense during the race window.**
  If a producer is paused by the scheduler after its `getAndSet` but before
  `prev.next = node`, the consumer cannot make progress on that specific
  element until the paused producer resumes and finishes publishing. This
  is a genuinely weaker guarantee than the Treiber stack's, worth stating
  plainly: single-consumer removes the CAS on the dequeue side, but it does
  not remove every form of cross-thread dependency.
- Bounded by construction: the window is a single store instruction between
  two operations on one producer thread — not an unbounded stall, but not a
  wait-free bound either.

## Memory model

- `producerTail` (`AtomicReference<Node<T>>`): needs at least a full
  exchange (`getAndSet`, volatile semantics) — every producer must see the
  latest value, unconditionally.
- `node.next` (`volatile Node<T> next`, written once via `prev.next =
  node`): needs release semantics so that once the consumer observes it
  (an acquire read, which a plain volatile read on `next` provides),
  everything about `node` — in particular its `value` — is guaranteed
  visible too. Same safe-publication argument as `TreiberStack.Node.next`,
  pointed the other direction. This field stays a plain `volatile`
  regardless of whether the surrounding class uses `VarHandle` or
  `Atomic*`: it is never the target of a compound atomic operation (no
  CAS, no exchange — one release-store and one acquire-load per node), so
  neither primitive would add anything over the language keyword.
- `consumerHead`: plain field, no atomic wrapper at all. Only the consumer
  thread ever reads or writes it, so there is no cross-thread visibility
  requirement to satisfy — unlike `TreiberStack.head`, which every thread
  reads. This is the single-consumer property paying off directly in the
  implementation, not just in the algorithm's shape.
- `count` (`AtomicInteger`): producers write via `getAndIncrement` (needs
  atomicity across producers); the consumer's `getAndDecrement` stays
  atomic too, even though only one thread ever performs it, because
  producers concurrently read `count` for the capacity check and need a
  consistent view.

## Contention characteristics

Producers contend with each other on `producerTail` (true sharing — every
`offer()` from every producer thread targets the same exchange, inherent to
a single-exchange-point algorithm) and on `count` (true sharing, needed for
capacity accounting). The consumer never contends with anyone on
`consumerHead`. See `docs/design/false-sharing-mpsc.md` for why the
false-sharing mitigation this milestone investigated (manual cache-line
padding around `producerTail`) turned out not to be achievable once
`producerTail` moved from a directly-CAS'd field to an `AtomicReference`
wrapper.

## `size()`

O(1) via the same `count` field used for capacity reservation — a genuine
divergence from `TreiberStack.size()`'s O(n) traversal, justified by the
single-consumer property making an O(1) counter both cheap and easy to keep
correct here. It can transiently over-report: `count` is incremented at
reservation time (before the node exists or is linked), so a slot a
producer has reserved but not yet published is already counted. This
doesn't affect `poll()`/`peek()` correctness — the race-window handling
above depends on exactly this property — but callers should treat `size()`
as an estimate under concurrent load, the same caveat `TreiberStack.size()`
carries for a different reason.
