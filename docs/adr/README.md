# Architecture Decision Records

One ADR per architecturally significant decision — not every decision, just
the ones with real trade-offs a future reader would otherwise have to
reverse-engineer from the code or the git history.

Template: Context, Constraints, Options, Decision, Rationale, Trade-offs,
Consequences, Alternatives.

First two: ADR-002 (why VarHandle over AtomicReference) and ADR-005
(lock-free vs lock-based). JMH benchmarking was dropped for this project
(see the Milestone 1 implementation plan's scope-change note), so both
ADRs reason about trade-offs qualitatively — from the algorithm and the
JMM — rather than citing measured numbers.
