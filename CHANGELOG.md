# Changelog

All notable changes to this project are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/); entries are grouped
per milestone, not per commit.

## [Unreleased]

## [0.1.0] - 2026-07-25

### Milestone 1 — Treiber lock-free stack

- Gradle build scaffolding: Java 21 toolchain, Checkstyle, SpotBugs, JaCoCo,
  JMH plugin wiring, CI (PR pipeline + nightly job).
- Repo governance docs (README, CONTRIBUTING, SECURITY, LICENSE) and the
  `docs/` structure.
- `TreiberStack<T>`: lock-free generic stack (`push`/`pop`/`peek`/`size`/
  `isEmpty`) using `VarHandle` CAS on `head`, per ADR-002.
- Unit tests (single-threaded correctness, LIFO order, null-handling) and
  concurrent tests (N-producer/M-consumer, no lost/duplicated values).
- `docs/algorithms/treiber-stack.md` (algorithm, linearization points,
  progress-guarantee proof) and `docs/design/aba-problem.md` (why ABA
  doesn't apply to this baseline implementation).
- Base package renamed to `dev.concurrentcollections.stack`.
- ADR-002 (Why VarHandle?) and ADR-005 (Lock-Free vs Lock-Based).

### Scope note

JMH benchmarking was dropped for this project (not deferred) — a deliberate
call, not an oversight. No throughput/latency numbers are claimed anywhere
in this milestone's docs; ADR-002 and ADR-005 reason about trade-offs
qualitatively instead. The `jmh` plugin and nightly CI step remain wired
but unused, in case a future milestone changes course.
