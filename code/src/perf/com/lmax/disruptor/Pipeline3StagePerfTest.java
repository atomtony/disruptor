package com.lmax.disruptor;

import com.lmax.disruptor.support.Function;
import com.lmax.disruptor.support.FunctionEntry;
import com.lmax.disruptor.support.FunctionHandler;
import com.lmax.disruptor.support.FunctionQueueConsumer;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.*;

/**
 * <pre>
 * Pipeline a series of stages from a producer to ultimate consumer.
 * Each consumer depends on the output of the previous consumer.
 *
 * +----+    +----+    +----+    +----+
 * | P0 |--->| C0 |--->| C1 |--->| C2 |
 * +----+    +----+    +----+    +----+
 *
 * Queue Based:
 * ============
 *
 *        put      take       put      take       put      take
 * +----+    +----+    +----+    +----+    +----+    +----+    +----+
 * | P0 |--->| Q0 |<---| C0 |--->| Q1 |<---| C1 |--->| Q2 |<---| C2 |
 * +----+    +----+    +----+    +----+    +----+    +----+    +----+
 *
 * P0 - Producer 0
 * Q0 - Queue 0
 * C0 - Consumer 0
 * Q1 - Queue 1
 * C1 - Consumer 1
 * Q2 - Queue 2
 * C2 - Consumer 1
 *
 * Disruptor:
 * ==========
 *                   track to prevent wrap
 *             +-----------------------------+---------------------+--------------------+
 *             |                             |                     |                    |
 *             |                             v                     v                    v
 * +----+    +----+    +----+    +-----+    +----+    +-----+    +----+    +-----+    +----+
 * | P0 |--->| PB |--->| RB |    | CB0 |<---| C0 |<---| CB1 |<---| C1 |<---| CB2 |<---| C2 |
 * +----+    +----+    +----+    +-----+    +----+    +-----+    +----+    +-----+    +----+
 *                claim   ^  get   |   waitFor           |  waitFor           |  waitFor
 *                        |        |                     |                    |
 *                        +--------+---------------------+--------------------+
 *
 *
 * P0  - Producer 0
 * PB  - ProducerBarrier
 * RB  - RingBuffer
 * CB0 - ConsumerBarrier 0
 * C0  - Consumer 0
 * CB1 - ConsumerBarrier 1
 * C1  - Consumer 1
 * CB2 - ConsumerBarrier 2
 * C2  - Consumer 2
 *
 * </pre>
 */
public final class Pipeline3StagePerfTest
{
    private static final int NUM_CONSUMERS = 3;
    private static final int SIZE = 8192;
    private static final long ITERATIONS = 1000 * 1000 * 50;
    private final ExecutorService EXECUTOR = Executors.newFixedThreadPool(NUM_CONSUMERS);

    private static final long OPERAND_TWO_INITIAL_VALUE = 777L;
    private final long expectedResult;

    {
        long temp = 0L;
        long operandTwo = OPERAND_TWO_INITIAL_VALUE;

        for (long i = 0; i < ITERATIONS; i++)
        {
            long stageOneResult = i + operandTwo--;
            long stageTwoResult = stageOneResult + 3;

            if ((stageTwoResult & 4L) == 4L)
            {
                ++temp;
            }
        }

        expectedResult = temp;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final BlockingQueue<long[]> stepOneQueue = new ArrayBlockingQueue<long[]>(SIZE);
    private final BlockingQueue<Long> stepTwoQueue = new ArrayBlockingQueue<Long>(SIZE);
    private final BlockingQueue<Long> stepThreeQueue = new ArrayBlockingQueue<Long>(SIZE);

    private final FunctionQueueConsumer stepOneQueueConsumer =
        new FunctionQueueConsumer(Function.STEP_ONE, stepOneQueue, stepTwoQueue, stepThreeQueue);
    private final FunctionQueueConsumer stepTwoQueueConsumer =
        new FunctionQueueConsumer(Function.STEP_TWO, stepOneQueue, stepTwoQueue, stepThreeQueue);
    private final FunctionQueueConsumer stepThreeQueueConsumer =
        new FunctionQueueConsumer(Function.STEP_THREE, stepOneQueue, stepTwoQueue, stepThreeQueue);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<FunctionEntry> ringBuffer =
        new RingBuffer<FunctionEntry>(FunctionEntry.ENTRY_FACTORY, SIZE,
                                      ClaimStrategy.Option.SINGLE_THREADED,
                                      WaitStrategy.Option.YIELDING);

    private final ConsumerBarrier<FunctionEntry> stepOneConsumerBarrier = ringBuffer.createConsumerBarrier();
    private final FunctionHandler stepOneFunctionHandler = new FunctionHandler(Function.STEP_ONE);
    private final BatchConsumer<FunctionEntry> stepOneBatchConsumer =
        new BatchConsumer<FunctionEntry>(stepOneConsumerBarrier, stepOneFunctionHandler);

    private final ConsumerBarrier<FunctionEntry> stepTwoConsumerBarrier = ringBuffer.createConsumerBarrier(stepOneBatchConsumer);
    private final FunctionHandler stepTwoFunctionHandler = new FunctionHandler(Function.STEP_TWO);
    private final BatchConsumer<FunctionEntry> stepTwoBatchConsumer =
        new BatchConsumer<FunctionEntry>(stepTwoConsumerBarrier, stepTwoFunctionHandler);

    private final ConsumerBarrier<FunctionEntry> stepThreeConsumerBarrier = ringBuffer.createConsumerBarrier(stepTwoBatchConsumer);
    private final FunctionHandler stepThreeFunctionHandler = new FunctionHandler(Function.STEP_THREE);
    private final BatchConsumer<FunctionEntry> stepThreeBatchConsumer =
        new BatchConsumer<FunctionEntry>(stepThreeConsumerBarrier, stepThreeFunctionHandler);

    private final ProducerBarrier<FunctionEntry> producerBarrier = ringBuffer.createProducerBarrier(0, stepThreeBatchConsumer);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void shouldCompareDisruptorVsQueues()
        throws Exception
    {
        final int RUNS = 3;
        long disruptorOps = 0L;
        long queueOps = 0L;

        for (int i = 0; i < RUNS; i++)
        {
            System.gc();

            disruptorOps = runDisruptorPass();
            queueOps = runQueuePass();


            System.out.format("%s OpsPerSecond run %d: BlockingQueues=%d, Disruptor=%d\n",
                              getClass().getSimpleName(), Integer.valueOf(i), Long.valueOf(queueOps), Long.valueOf(disruptorOps));
        }

        Assert.assertTrue("Performance degraded", disruptorOps > queueOps);
    }

    private long runDisruptorPass()
    {
        stepThreeFunctionHandler.reset();

        EXECUTOR.submit(stepOneBatchConsumer);
        EXECUTOR.submit(stepTwoBatchConsumer);
        EXECUTOR.submit(stepThreeBatchConsumer);

        long start = System.currentTimeMillis();

        long operandTwo = OPERAND_TWO_INITIAL_VALUE;
        for (long i = 0; i < ITERATIONS; i++)
        {
            FunctionEntry entry = producerBarrier.claimNext();
            entry.setOperandOne(i);
            entry.setOperandTwo(operandTwo--);
            entry.commit();
        }

        final long expectedSequence = ringBuffer.getCursor();
        while (stepThreeBatchConsumer.getSequence() < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);

        stepOneBatchConsumer.halt();
        stepTwoBatchConsumer.halt();
        stepThreeBatchConsumer.halt();

        Assert.assertEquals(expectedResult, stepThreeFunctionHandler.getStepThreeCounter());

        return opsPerSecond;
    }

    private long runQueuePass() throws Exception
    {
        stepThreeQueueConsumer.reset();

        Future[] futures = new Future[NUM_CONSUMERS];
        futures[0] = EXECUTOR.submit(stepOneQueueConsumer);
        futures[1] = EXECUTOR.submit(stepTwoQueueConsumer);
        futures[2] = EXECUTOR.submit(stepThreeQueueConsumer);

        long start = System.currentTimeMillis();

        long operandTwo = OPERAND_TWO_INITIAL_VALUE;
        for (long i = 0; i < ITERATIONS; i++)
        {
            long[] values = new long[2];
            values[0] = i;
            values[1] = operandTwo--;
            stepOneQueue.put(values);
        }

        final long expectedSequence = ITERATIONS - 1;
        while (stepThreeQueueConsumer.getSequence() < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);

        stepOneQueueConsumer.halt();
        stepTwoQueueConsumer.halt();
        stepThreeQueueConsumer.halt();

        for (Future future : futures)
        {
            future.cancel(true);
        }

        Assert.assertEquals(expectedResult, stepThreeQueueConsumer.getStepThreeCounter());

        return opsPerSecond;
    }
}