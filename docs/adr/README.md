# Architecture Decision Records

One ADR per architecturally significant decision — not every decision, just
the ones with real trade-offs a future reader would otherwise have to
reverse-engineer from the code or the git history.

Template: Context, Constraints, Options, Decision, Rationale, Trade-offs,
Consequences, Alternatives.

Milestone 1: ADR-002 (why VarHandle over AtomicReference) and ADR-005
(lock-free vs lock-based).

Milestone 2: ADR-004 (bounded vs unbounded queues), ADR-006 (queue node
representation), ADR-007 (false sharing mitigation), ADR-010 (VarHandle vs
Atomic* concurrency primitives — not in the original brief's ADR list,
added when `MpscQueue` diverged from ADR-002's convention; see that ADR's
amendment note).

JMH benchmarking was dropped for this project (see the Milestone 1
implementation plan's scope-change note), so every ADR reasons about
trade-offs qualitatively — from the algorithm and the JMM — rather than
citing measured numbers.
