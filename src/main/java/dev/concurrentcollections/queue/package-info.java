/**
 * Concurrent and lock-free queue implementations, using {@link
 * java.lang.invoke.VarHandle} directly rather than wrapping existing
 * {@code java.util.concurrent} classes.
 *
 * <p>See {@code docs/algorithms/} in the repository root for the algorithm
 * write-up and progress-guarantee analysis of each structure, and
 * {@code docs/design/} for cross-cutting Java Memory Model and
 * cache-locality documentation that applies to all of them.
 */
package dev.concurrentcollections.queue;
