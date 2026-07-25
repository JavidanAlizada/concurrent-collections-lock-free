# Architecture Decision Records

One ADR per architecturally significant decision — not every decision, just
the ones with real trade-offs a future reader would otherwise have to
reverse-engineer from the code or the git history.

Template: Context, Constraints, Options, Decision, Rationale, Trade-offs,
Consequences, Alternatives.

First two expected: ADR-002 (why VarHandle over AtomicReference) and ADR-005
(lock-free vs lock-based), both written after Milestone 1's benchmarks exist
so the trade-offs section can cite real numbers instead of asserting them.
