# False sharing in MpscQueue, and why it isn't mitigated here

## What it is, in general

CPU cache coherence moves data between cores in fixed-size lines (64 bytes
on essentially all current x86/ARM hardware), not individual fields. If two
fields live on the same cache line and different threads write to each of
them, every write from one thread invalidates the other thread's cached
copy of the *whole line* — even though the two fields have nothing to do
with each other logically. That's false sharing: contention caused by
memory layout, not by an actual shared variable.

## Why `MpscQueue` was a candidate

`producerTail` is written by every producer thread on every `offer()`.
`consumerHead` is written only by the consumer, on every `poll()`/`peek()`.
Declared as ordinary adjacent fields, they're natural candidates to end up
on the same cache line — the exact pattern that turns single-writer,
single-reader fields into a source of cross-thread cache invalidation
neither role's logic actually requires.

## What would fix it, and why it isn't applied

The standard mitigation is manual cache-line padding: surround the hot
field with enough unused bytes (commonly implemented as a run of `long`
padding fields before and after it) that it can't share a line with
anything else, mirroring what `jdk.internal.vm.annotation.Contended` does
internally for JDK classes like `Striped64` — an annotation restricted to
the JDK's own code, not usable from application code without
`-XX:-RestrictContended`, which isn't a flag a library should require its
consumers to set.

That technique works by isolating the memory location that actually holds
the contended value. Under `VarHandle` — the approach `TreiberStack` uses,
and what this queue's design proposal originally assumed too — `producerTail`
would be a field on `MpscQueue` itself, holding the `Node` reference
directly, and CAS/exchange operations would act on that exact field. Padding
around it directly isolates the hot memory.

This class uses `AtomicReference<Node<T>>` instead (see ADR-010).
`producerTail` on `MpscQueue` is a reference to a separate
`AtomicReference` object; the field that actually receives the
`getAndSet` traffic lives *inside* that object, at a memory offset chosen
by the JDK, not by this code. Padding `MpscQueue.producerTail` would
isolate a pointer that's written once at construction and essentially
never touched again — not the thing under contention. There's no
supported way to reach into `AtomicReference`'s internal layout from
application code to pad around its actual value field.

## Current state

Not mitigated. This is a direct, structural consequence of choosing
`AtomicReference` over `VarHandle` for this milestone (ADR-010), not an
oversight — the trade-off is real and worth stating plainly rather than
silently dropping it. A `VarHandle`-based rewrite of this class could
apply the same padding technique `TreiberStack` doesn't currently need
(single-CAS-point algorithms don't have this producer/consumer field
split to isolate). No benchmark backs any of this either way — this
project doesn't run JMH (see the Milestone 1 scope-change note) — so
nothing here is a performance claim, only a description of what's
possible to control at each layer.
