package dev.concurrentcollections.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Multi-producer/single-consumer correctness: every offered value is
 * observed exactly once, and each producer's own values arrive in the
 * order it sent them (FIFO only holds per-producer, not globally, since
 * interleaving across producers is inherently nondeterministic).
 */
class MpscQueueConcurrentTest {

    private record Msg(int producer, int seq) {
    }

    @Test
    void concurrentOffersThenDrainPreserveEveryValueAndPerProducerOrder() throws InterruptedException {
        int producers = 6;
        int perProducer = 5_000;
        int totalOffered = producers * perProducer;

        // capacity == totalOffered: offer() never fails, isolates producer-vs-producer races
        MpscQueue<Msg> queue = new MpscQueue<>(totalOffered);

        runConcurrently(producers, p -> {
            for (int seq = 0; seq < perProducer; seq++) {
                assertTrue(queue.offer(new Msg(p, seq)));
            }
        });

        Map<Integer, List<Integer>> consumedByProducer = new HashMap<>();
        Msg msg;
        while ((msg = queue.poll()) != null) {
            consumedByProducer.computeIfAbsent(msg.producer(), k -> new ArrayList<>()).add(msg.seq());
        }

        assertPerProducerOrderAndCompleteness(consumedByProducer, producers, perProducer);
        assertTrue(queue.isEmpty());
    }

    @Test
    void concurrentProducersAndConsumerRunningTogetherPreserveEveryValue() throws InterruptedException {
        int producers = 6;
        int perProducer = 5_000;
        int capacity = 64; // small vs. traffic - forces offer() to spin-retry against the consumer
        int totalOffered = producers * perProducer;

        MpscQueue<Msg> queue = new MpscQueue<>(capacity);
        AtomicBoolean producersDone = new AtomicBoolean(false);
        Map<Integer, List<Integer>> consumedByProducer = new HashMap<>();
        for (int p = 0; p < producers; p++) {
            consumedByProducer.put(p, new ArrayList<>());
        }

        ExecutorService producerPool = Executors.newFixedThreadPool(producers);
        ExecutorService consumerPool = Executors.newSingleThreadExecutor();
        try {
            List<Future<?>> producerTasks = IntStream.range(0, producers)
                    .mapToObj(p -> producerPool.submit(() -> {
                        for (int seq = 0; seq < perProducer; seq++) {
                            Msg msg = new Msg(p, seq);
                            while (!queue.offer(msg)) {
                                Thread.onSpinWait();
                            }
                        }
                    }))
                    .collect(Collectors.toList());

            Future<?> consumerTask = consumerPool.submit(() -> {
                while (!producersDone.get() || !queue.isEmpty()) {
                    Msg msg = queue.poll();
                    if (msg != null) {
                        consumedByProducer.get(msg.producer()).add(msg.seq());
                    }
                }
            });

            for (Future<?> task : producerTasks) {
                task.get();
            }
            producersDone.set(true);
            consumerTask.get();
        } catch (ExecutionException e) {
            throw new AssertionError(e);
        } finally {
            producerPool.shutdown();
            consumerPool.shutdown();
            assertTrue(consumerPool.awaitTermination(30, TimeUnit.SECONDS), "consumer did not finish in time");
        }

        assertPerProducerOrderAndCompleteness(consumedByProducer, producers, perProducer);
        assertEquals(totalOffered, consumedByProducer.values().stream().mapToInt(List::size).sum());
        assertTrue(queue.isEmpty());
    }

    private void assertPerProducerOrderAndCompleteness(
            Map<Integer, List<Integer>> consumedByProducer, int producers, int perProducer) {
        for (int p = 0; p < producers; p++) {
            List<Integer> seqs = consumedByProducer.get(p);
            assertEquals(perProducer, seqs.size(), "producer " + p + " lost or duplicated values");
            for (int i = 0; i < seqs.size(); i++) {
                assertEquals(i, seqs.get(i), "producer " + p + " values arrived out of order");
            }
        }
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

