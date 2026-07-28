package dev.concurrentcollections.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Single-threaded correctness: FIFO order, capacity, empty behavior, null handling. */
class MpscQueueTest {

    @Test
    void newQueueIsEmpty() {
        MpscQueue<String> queue = new MpscQueue<>(4);
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
    }

    @Test
    void pollOnEmptyQueueReturnsNull() {
        assertNull(new MpscQueue<String>(4).poll());
    }

    @Test
    void peekOnEmptyQueueReturnsNull() {
        assertNull(new MpscQueue<String>(4).peek());
    }

    @Test
    void offerThenPollReturnsSameValue() {
        MpscQueue<String> queue = new MpscQueue<>(4);
        assertTrue(queue.offer("a"));
        assertEquals("a", queue.poll());
        assertTrue(queue.isEmpty());
    }

    @Test
    void pollFollowsFifoOrder() {
        MpscQueue<Integer> queue = new MpscQueue<>(4);
        queue.offer(1);
        queue.offer(2);
        queue.offer(3);
        assertEquals(1, queue.poll());
        assertEquals(2, queue.poll());
        assertEquals(3, queue.poll());
        assertNull(queue.poll());
    }

    @Test
    void peekDoesNotRemoveElement() {
        MpscQueue<Integer> queue = new MpscQueue<>(4);
        queue.offer(42);
        assertEquals(42, queue.peek());
        assertEquals(42, queue.peek());
        assertEquals(1, queue.size());
    }

    @Test
    void peekReturnsHeadNotLastOffered() {
        MpscQueue<Integer> queue = new MpscQueue<>(4);
        queue.offer(1);
        queue.offer(2);
        assertEquals(1, queue.peek());
        assertEquals(1, queue.poll());
        assertEquals(2, queue.poll());
    }

    @Test
    void sizeReflectsElementCount() {
        MpscQueue<Integer> queue = new MpscQueue<>(8);
        for (int i = 0; i < 5; i++) {
            queue.offer(i);
        }
        assertEquals(5, queue.size());
        queue.poll();
        assertEquals(4, queue.size());
    }

    @Test
    void offerAtCapacityReturnsFalseAndDropsNothing() {
        MpscQueue<Integer> queue = new MpscQueue<>(2);
        assertTrue(queue.offer(1));
        assertTrue(queue.offer(2));
        assertFalse(queue.offer(3));
        assertEquals(2, queue.size());
        assertEquals(1, queue.poll());
        assertEquals(2, queue.poll());
        assertNull(queue.poll());
    }

    @Test
    void offerSucceedsAgainAfterPollFreesCapacity() {
        MpscQueue<Integer> queue = new MpscQueue<>(1);
        assertTrue(queue.offer(1));
        assertFalse(queue.offer(2));
        queue.poll();
        assertTrue(queue.offer(2));
        assertEquals(2, queue.poll());
    }

    @Test
    void offerNullThrowsNpe() {
        assertThrows(NullPointerException.class, () -> new MpscQueue<String>(4).offer(null));
    }

    @Test
    void nonPositiveCapacityThrows() {
        assertThrows(IllegalArgumentException.class, () -> new MpscQueue<String>(0));
        assertThrows(IllegalArgumentException.class, () -> new MpscQueue<String>(-1));
    }

    @Test
    void drainingEverythingEmptiesTheQueue() {
        MpscQueue<Integer> queue = new MpscQueue<>(4);
        queue.offer(1);
        queue.offer(2);
        queue.poll();
        queue.poll();
        assertTrue(queue.isEmpty());
        assertFalse(queue.size() > 0);
    }

    @Test
    void repeatedFullDrainCyclesDoNotResurrectStaleValues() {
        // regression: stale stub.next resurrected an old value across drain cycles
        MpscQueue<Integer> queue = new MpscQueue<>(4);
        for (int cycle = 0; cycle < 5; cycle++) {
            queue.offer(cycle * 10 + 1);
            queue.offer(cycle * 10 + 2);
            queue.offer(cycle * 10 + 3);
            assertEquals(cycle * 10 + 1, queue.poll());
            assertEquals(cycle * 10 + 2, queue.poll());
            assertEquals(cycle * 10 + 3, queue.poll());
            assertNull(queue.poll());
            assertTrue(queue.isEmpty());
        }
    }
}

