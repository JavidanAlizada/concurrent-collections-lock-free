package io.github.javidanalizada.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Multi-threaded correctness: every pushed value is observed exactly once,
 * whether producers and consumers run separately or interleaved.
 */
class TreiberStackConcurrentTest {

    @Test
    void concurrentPushesLoseNothing() throws InterruptedException {
        int threads = 8;
        int perThread = 5_000;
        TreiberStack<Integer> stack = new TreiberStack<>();

        runConcurrently(threads, t -> {
            int base = t * perThread;
            for (int i = 0; i < perThread; i++) {
                stack.push(base + i);
            }
        });

        Set<Integer> popped = ConcurrentHashMap.newKeySet();
        Integer value;
        while ((value = stack.pop()) != null) {
            assertTrue(popped.add(value), "duplicate value popped: " + value);
        }
        assertEquals(threads * perThread, popped.size());
    }

    @Test
    void concurrentProducersAndConsumersConserveEveryValue() throws InterruptedException {
        int producers = 6;
        int consumers = 4;
        int perProducer = 5_000;
        int totalPushed = producers * perProducer;

        TreiberStack<Integer> stack = new TreiberStack<>();
        AtomicInteger poppedCount = new AtomicInteger();
        AtomicBoolean producersDone = new AtomicBoolean(false);
        Set<Integer> popped = ConcurrentHashMap.newKeySet();

        ExecutorService producerPool = Executors.newFixedThreadPool(producers);
        ExecutorService consumerPool = Executors.newFixedThreadPool(consumers);
        try {
            List<Future<?>> producerTasks = IntStream.range(0, producers)
                    .mapToObj(t -> producerPool.submit(() -> {
                        int base = t * perProducer;
                        for (int i = 0; i < perProducer; i++) {
                            stack.push(base + i);
                        }
                    }))
                    .collect(Collectors.toList());

            for (int c = 0; c < consumers; c++) {
                consumerPool.submit(() -> {
                    while (!producersDone.get() || !stack.isEmpty()) {
                        Integer value = stack.pop();
                        if (value != null) {
                            popped.add(value);
                            poppedCount.incrementAndGet();
                        }
                    }
                });
            }

            for (Future<?> task : producerTasks) {
                task.get();
            }
            producersDone.set(true);
        } catch (ExecutionException e) {
            throw new AssertionError(e);
        } finally {
            producerPool.shutdown();
            consumerPool.shutdown();
            assertTrue(consumerPool.awaitTermination(30, TimeUnit.SECONDS), "consumers did not finish in time");
        }

        assertEquals(totalPushed, poppedCount.get(), "every pushed value should have been popped exactly once");
        assertEquals(totalPushed, popped.size(), "no value popped more than once");
        assertTrue(stack.isEmpty());
    }

    private void runConcurrently(int threads, IntConsumer task) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int t = 0; t < threads; t++) {
                int threadIndex = t;
                pool.submit(() -> {
                    ready.countDown();
                    await(start);
                    task.accept(threadIndex);
                });
            }
            ready.await();
            start.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "workers did not finish in time");
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
