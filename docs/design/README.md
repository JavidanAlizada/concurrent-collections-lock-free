# Design

Cross-cutting reasoning that applies across multiple structures, not any one
structure specifically: Java Memory Model guarantees this project relies on,
CAS semantics, the ABA problem and when it does/doesn't apply, memory-ordering
choices (plain vs opaque vs acquire-release vs volatile access), false sharing.

First doc expected: `aba-problem.md`, in Milestone 1.
