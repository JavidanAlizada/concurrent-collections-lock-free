# Operations

For a library (not a deployed service), "operations" adapts to: how to
consume this in a larger system, what to watch for under production load
(CAS retry rate climbing under contention, GC pressure from allocation-heavy
structures), and troubleshooting guidance for anyone embedding it.
