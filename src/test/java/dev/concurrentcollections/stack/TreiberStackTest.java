package dev.concurrentcollections.stack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Single-threaded correctness: LIFO order, empty behavior, null handling. */
class TreiberStackTest {

    @Test
    void newStackIsEmpty() {
        TreiberStack<String> stack = new TreiberStack<>();
        assertTrue(stack.isEmpty());
        assertEquals(0, stack.size());
    }

    @Test
    void popOnEmptyStackReturnsNull() {
        assertNull(new TreiberStack<String>().pop());
    }

    @Test
    void peekOnEmptyStackReturnsNull() {
        assertNull(new TreiberStack<String>().peek());
    }

    @Test
    void pushThenPopReturnsSameValue() {
        TreiberStack<String> stack = new TreiberStack<>();
        stack.push("a");
        assertEquals("a", stack.pop());
        assertTrue(stack.isEmpty());
    }

    @Test
    void popFollowsLifoOrder() {
        TreiberStack<Integer> stack = new TreiberStack<>();
        stack.push(1);
        stack.push(2);
        stack.push(3);
        assertEquals(3, stack.pop());
        assertEquals(2, stack.pop());
        assertEquals(1, stack.pop());
        assertNull(stack.pop());
    }

    @Test
    void peekDoesNotRemoveElement() {
        TreiberStack<Integer> stack = new TreiberStack<>();
        stack.push(42);
        assertEquals(42, stack.peek());
        assertEquals(42, stack.peek());
        assertEquals(1, stack.size());
    }

    @Test
    void sizeReflectsElementCount() {
        TreiberStack<Integer> stack = new TreiberStack<>();
        for (int i = 0; i < 5; i++) {
            stack.push(i);
        }
        assertEquals(5, stack.size());
        stack.pop();
        assertEquals(4, stack.size());
    }

    @Test
    void pushNullThrowsNpe() {
        assertThrows(NullPointerException.class, () -> new TreiberStack<String>().push(null));
    }

    @Test
    void poppingEverythingEmptiesTheStack() {
        TreiberStack<Integer> stack = new TreiberStack<>();
        stack.push(1);
        stack.push(2);
        stack.pop();
        stack.pop();
        assertTrue(stack.isEmpty());
        assertFalse(stack.size() > 0);
    }
}
