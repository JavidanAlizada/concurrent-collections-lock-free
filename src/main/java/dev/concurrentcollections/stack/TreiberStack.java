package dev.concurrentcollections.stack;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;

/**
 * Lock-free stack: a singly linked list where {@code head} is only ever
 * mutated by a single CAS. Nulls are rejected on push, so pop/peek can use
 * null to mean "empty" unambiguously.
 */
public final class TreiberStack<T> {

    private static final VarHandle HEAD;

    static {
        try {
            HEAD = MethodHandles.lookup().findVarHandle(TreiberStack.class, "head", Node.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Node<T> head;

    /** Pushes a value onto the stack; rejects null. */
    public void push(T value) {
        Objects.requireNonNull(value, "value cannot be null");
        Node<T> newHead = new Node<>(value);
        Node<T> oldHead;
        do {
            oldHead = head();
            // safe to link before the CAS: newHead isn't visible to any other
            // thread until it gets published by a successful compareAndSet below
            newHead.next = oldHead;
        } while (!HEAD.compareAndSet(this, oldHead, newHead));
    }

    /** Removes and returns the top value, or null if the stack is empty. */
    public T pop() {
        Node<T> oldHead;
        Node<T> newHead;
        do {
            oldHead = head();
            if (oldHead == null) {
                return null;
            }
            newHead = oldHead.next;
        } while (!HEAD.compareAndSet(this, oldHead, newHead));
        return oldHead.value;
    }

    /** Returns the top value without removing it, or null if empty. */
    public T peek() {
        Node<T> current = head();
        return current == null ? null : current.value;
    }

    /** True if the stack currently has no elements. */
    public boolean isEmpty() {
        return head() == null;
    }

    /**
     * O(n), and only a weakly consistent estimate under concurrent mutation —
     * same trade-off {@code ConcurrentLinkedQueue.size()} makes.
     */
    public int size() {
        int count = 0;
        for (Node<T> current = head(); current != null; current = current.next) {
            count++;
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private Node<T> head() {
        return (Node<T>) HEAD.getVolatile(this);
    }

    private static final class Node<T> {
        final T value;
        Node<T> next;

        Node(T value) {
            this.value = value;
        }
    }
}
