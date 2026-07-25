# Performance

This project does not run JMH benchmarks. That was a deliberate scope
decision, not an oversight — it applies to Milestone 1 and, unless
revisited, to the rest of this project.

No throughput, latency (p50/p95/p99), CPU, or allocation-rate numbers are
claimed anywhere in this repo's docs as a result. Where trade-offs between
designs are discussed (see `docs/adr/ADR-002.md` and `docs/adr/ADR-005.md`),
they're reasoned about qualitatively — from the algorithm and the Java
Memory Model — and say so explicitly rather than imply measurement.

The `me.champeau.jmh` Gradle plugin and the "Full JMH benchmark matrix" step
in `.github/workflows/nightly.yml` are still wired in from the initial build
scaffolding. They're unused: no benchmark classes exist, and the nightly job
step doesn't correspond to anything meaningful running. They're left in
place rather than removed in case a future milestone changes course, not
because benchmarking is happening.

If this decision is ever reversed, this file gets replaced with real JMH
output (throughput, p50/p95/p99, CPU, allocation rate) per structure and
thread count, per the project's own standard for performance claims.