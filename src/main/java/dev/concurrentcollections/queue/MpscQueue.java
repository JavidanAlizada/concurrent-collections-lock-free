package dev.concurrentcollections.queue;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;

/**
 * Bounded multi-producer/single-consumer queue using Dmitry Vyukov's
 * intrusive MPSC design: producers coordinate via one atomic exchange (no
 * CAS-retry loop), and the single consumer thread needs no synchronization
 * on its own head pointer at all. See {@code docs/algorithms/mpsc-queue.md}
 * for the full algorithm write-up, including the producer-publish race
 * window and how {@link #resolve} resolves it.
 */
public final class MpscQueue<T> extends Pad2<T> {

    private static final VarHandle PRODUCER_TAIL;
    private static final VarHandle COUNT;

    static {
        try {
            PRODUCER_TAIL = MethodHandles.lookup()
                    .findVarHandle(ProducerTailRef.class, "producerTail", Node.class);
            COUNT = MethodHandles.lookup().findVarHandle(MpscQueue.class, "count", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Reference-equality sentinel: "we can't yet tell empty from mid-publish."
    // Never exposed outside this class, so its type-erased raw use is safe.
    private static final Node<?> UNRESOLVED = new Node<>(null);

    private final int capacity;
    private final Node<T> stub;
    private int count;

    /** Creates a queue that holds at most {@code capacity} elements. */
    public MpscQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.stub = new Node<>(null);
        this.producerTail = stub;
        this.consumerHead = stub;
    }

    /** Offers a value; rejects null. Returns false if the queue is at capacity. */
    @SuppressWarnings("unchecked")
    public boolean offer(T value) {
        Objects.requireNonNull(value, "value cannot be null");
        int reserved = (int) COUNT.getAndAdd(this, 1);
        if (reserved >= capacity) {
            COUNT.getAndAdd(this, -1);
            return false;
        }
        Node<T> node = new Node<>(value);
        // Unconditional exchange - unlike a CAS loop, this never retries.
        Node<T> prev = (Node<T>) PRODUCER_TAIL.getAndSet(this, node);
        prev.next = node; // release-publish node.value to the consumer's next read
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

    /**
     * O(1) via the same reservation counter used for capacity checks. May
     * transiently count a slot a producer has reserved but not yet linked
     * into the list - see docs/algorithms/mpsc-queue.md.
     */
    public int size() {
        return (int) COUNT.getVolatile(this);
    }

    private T dequeue(boolean consume) {
        Node<T> result = resolve(consume);
        if (result == UNRESOLVED) {
            if ((int) COUNT.getVolatile(this) == 0) {
                return null; // nothing reserved anywhere: genuinely empty
            }
            pushStub();
            result = resolve(consume);
        }
        return result == UNRESOLVED ? null : result.value;
    }

    /**
     * Identifies the current head node without consuming it if
     * {@code consume} is false. Returns {@link #UNRESOLVED} when
     * {@code consumerHead.next} is null and it isn't yet possible to tell
     * whether that means the queue is empty or a producer is mid-publish
     * (its {@code getAndSet} on {@code producerTail} landed, but its
     * follow-up {@code prev.next = node} hasn't run yet).
     */
    @SuppressWarnings("unchecked")
    private Node<T> resolve(boolean consume) {
        Node<T> tail = consumerHead;
        Node<T> next = tail.next;
        if (tail == stub) {
            if (next == null) {
                return (Node<T>) UNRESOLVED;
            }
            // Stub carries no value - advancing past it is bookkeeping, not a removal.
            consumerHead = next;
            tail = next;
            next = next.next;
        }
        if (next == null) {
            return (Node<T>) UNRESOLVED;
        }
        if (consume) {
            consumerHead = next;
            COUNT.getAndAdd(this, -1);
        }
        return tail;
    }

    /**
     * Re-links the stub node using the same atomic exchange producers use.
     * If the producer we're racing is the one whose {@code prev.next} write
     * we're waiting on, this closes the gap and the retry in
     * {@link #resolve} finds it; otherwise the retry still comes back
     * {@link #UNRESOLVED} and the caller defers to a later call.
     */
    @SuppressWarnings("unchecked")
    private void pushStub() {
        // Unlike a freshly allocated Node, the reused stub's `next` still
        // points at whatever it was last linked to - reset before
        // re-publishing it, or a stale link resurrects an already-consumed
        // node the next time consumerHead walks through the stub.
        stub.next = null;
        Node<T> prev = (Node<T>) PRODUCER_TAIL.getAndSet(this, stub);
        prev.next = stub;
    }
}

// Manual cache-line padding isolating producerTail (written by every
// producer) from consumerHead (written only by the consumer) and from the
// queue's other fields. Package-private top-level classes, not nested
// inside MpscQueue: a class's own extends clause can't reference one of
// its own member types. See docs/design/false-sharing-mpsc.md.

abstract class Pad0 {
    long p00;
    long p01;
    long p02;
    long p03;
    long p04;
    long p05;
    long p06;
}

abstract class ProducerTailRef<T> extends Pad0 {
    Node<T> producerTail;
}

abstract class Pad1<T> extends ProducerTailRef<T> {
    long p10;
    long p11;
    long p12;
    long p13;
    long p14;
    long p15;
    long p16;
}

abstract class ConsumerHeadRef<T> extends Pad1<T> {
    Node<T> consumerHead;
}

abstract class Pad2<T> extends ConsumerHeadRef<T> {
    long p20;
    long p21;
    long p22;
    long p23;
    long p24;
    long p25;
    long p26;
}

final class Node<T> {
    final T value;
    volatile Node<T> next;

    Node(T value) {
        this.value = value;
    }
}
