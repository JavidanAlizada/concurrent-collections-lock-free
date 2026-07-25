/**
 * Concurrent and lock-free data structures implemented from first principles
 * using {@link java.lang.invoke.VarHandle} rather than wrapping
 * {@code java.util.concurrent} classes, so the underlying Compare-And-Set
 * algorithms and Java Memory Model guarantees are explicit rather than
 * hidden behind an existing implementation.
 *
 * <p>See {@code docs/algorithms/} in the repository root for the algorithm
 * write-up and linearization-point proof of each structure, and
 * {@code docs/design/} for the cross-cutting Java Memory Model, CAS, ABA,
 * and memory-ordering documentation that applies to all of them.
 */
package io.github.javidanalizada.concurrent;
