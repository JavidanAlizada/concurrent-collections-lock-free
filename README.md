# concurrent-collections-lock-free

Concurrent and lock-free data structures for the JVM, built from first
principles on `VarHandle` rather than wrapping `java.util.concurrent`. The
point isn't to reinvent `ConcurrentLinkedQueue` — it's to make the CAS
algorithms and Java Memory Model reasoning behind structures like it explicit,
provable, and benchmarked against the JDK's own implementations.

Status: early — Milestone 1 (Treiber lock-free stack) in progress. See
[CHANGELOG.md](CHANGELOG.md) for what's actually landed.

## Why VarHandle instead of AtomicReference

Wrapping `AtomicReference` would hide the exact thing this project exists to
demonstrate: hand-rolled compare-and-set, with explicit control over access
mode (plain / opaque / acquire-release / volatile) instead of `Atomic*`'s
fixed volatile semantics everywhere. Where a JDK class is used anywhere in
this repo, the reason and the trade-off it introduces will be stated —
see `docs/adr/`.

## Build & test

Requires JDK 21.

```bash
./gradlew compileJava checkstyleMain spotbugsMain   # compile + static analysis
./gradlew test                                       # unit + concurrent tests
./gradlew jacocoTestReport                            # coverage report
./gradlew jmh                                          # benchmarks (smoke profile locally)
```

CI runs the same pipeline on every push/PR to `main` (`.github/workflows/pr.yml`).
A nightly job runs the full JMH matrix with real warmup/measurement settings
(`.github/workflows/nightly.yml`).

## Structures

| Structure | Status |
|---|---|
| Treiber lock-free stack | in progress |
| MPSC queue | not started |
| MPMC queue | not started |
| Ring buffer | not started |
| Concurrent skip list | not started |
| Work-stealing queue | not started |
| Blocking / priority / delay queue | not started |

## Documentation

- `docs/architecture/` — system context and component structure
- `docs/adr/` — architecture decision records
- `docs/design/` — cross-cutting JMM, CAS, ABA, memory-ordering reasoning
- `docs/algorithms/` — per-structure algorithm write-ups and linearization proofs
- `docs/benchmarks/` — raw JMH results and analysis
- `docs/performance/` — capacity planning and performance characteristics
- `docs/security/` — security considerations
- `docs/operations/` — deployment, scaling, troubleshooting, recovery

## License

MIT — see [LICENSE](LICENSE).
