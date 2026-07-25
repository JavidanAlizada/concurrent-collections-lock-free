# Benchmarks

Raw JMH output and the analysis of it, per structure. Every performance claim
made anywhere else in this repo (README, PERFORMANCE.md, ADRs) should trace
back to a result recorded here — never a bare "this is fast."

Expect `results.md` (throughput, p50/p95/p99, allocation rate, across thread
counts 1/2/4/8/16/32/64) and `analysis.md` (interpretation, bottleneck
reasoning) per structure, starting with the Treiber stack in Milestone 1.
