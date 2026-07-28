package dev.concurrentcollections.queue;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bounded multi-producer/single-consumer queue - Vyukov's intrusive MPSC
 * design. Producers coordinate through one atomic exchange, no CAS retry
 * loop; the consumer needs no synchronization on its own head pointer at
 * all. See {@code docs/algorithms/mpsc-queue.md} for the full write-up.
 *
 * <p>Uses {@code AtomicReference}/{@code AtomicInteger} rather than
 * {@code VarHandle} like {@code TreiberStack} - a deliberate comparison
 * across the portfolio, see ADR-010, not a claim one is better.
 */
public final class MpscQueue<T> {

    // sentinel: "can't yet tell empty from mid-publish" - never leaves this class
    private static final Node<?> UNRESOLVED = new Node<>(null);

    private final int capacity;
    private final Node<T> stub;
    private final AtomicReference<Node<T>> producerTail;
    private final AtomicInteger count = new AtomicInteger();
    private Node<T> consumerHead;

    /** Creates a queue that holds at most {@code capacity} elements. */
    public MpscQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.stub = new Node<>(null);
        this.producerTail = new AtomicReference<>(stub);
        this.consumerHead = stub;
    }

    /** Offers a value; rejects null. Returns false if the queue is at capacity. */
    public boolean offer(T value) {
        Objects.requireNonNull(value, "value cannot be null");
        int reserved = count.getAndIncrement();
        if (reserved >= capacity) {
            count.getAndDecrement();
            return false;
        }
        Node<T> node = new Node<>(value);
        Node<T> prev = producerTail.getAndSet(node); // exchange, never retries
        prev.next = node; // publishes node.value to the consumer
        return true;
    }

    /** Removes and returns the head of the queue, or null if empty. */
    public T poll() {
        return dequeue(true);
    }

    /** Returns the head of the queue without removing it, or null if empty. */
    public T peek() {
        return dequeue(false);
    }

    /** True if the queue currently has no elements. */
    public boolean isEmpty() {
        return size() == 0;
    }

    /** O(1) via the same counter offer() uses for capacity checks; may transiently over-count. */
    public int size() {
        return count.get();
    }

    private T dequeue(boolean consume) {
        Node<T> result = resolve(consume);
        if (result == UNRESOLVED) {
            if (count.get() == 0) {
                return null; // genuinely empty
            }
            pushStub();
            result = resolve(consume);
        }
        return result == UNRESOLVED ? null : result.value;
    }

    // Finds the current head, or UNRESOLVED if a producer might be mid-publish
    // (getAndSet on producerTail landed but prev.next = node hasn't yet).
    @SuppressWarnings("unchecked")
    private Node<T> resolve(boolean consume) {
        Node<T> tail = consumerHead;
        Node<T> next = tail.next;
        if (tail == stub) {
            if (next == null) {
                return (Node<T>) UNRESOLVED;
            }
            consumerHead = next; // stub carries no value, this is bookkeeping
            tail = next;
            next = next.next;
        }
        if (next == null) {
            return (Node<T>) UNRESOLVED;
        }
        if (consume) {
            consumerHead = next;
            count.getAndDecrement();
        }
        return tail;
    }

    // Re-links the stub via the same exchange producers use, so a stalled
    // producer's prev.next gap can resolve on the next resolve() call.
    private void pushStub() {
        stub.next = null; // reused node - clear the link from last time
        Node<T> prev = producerTail.getAndSet(stub);
        prev.next = stub;
    }

    private static final class Node<T> {
        final T value;
        volatile Node<T> next;

        Node(T value) {
            this.value = value;
        }
    }
}
