package com.lmax.disruptor.lesson40;

import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

public class LongEventMain {
    public static void main(String[] args) throws Exception {
        // The factory for the event
        LongEventFactory factory = new LongEventFactory();

        // Specify the size of the ring buffer, must be power of 2.
        int bufferSize = 16;

        // Construct the Disruptor
        final Disruptor<LongEvent> disruptor = new Disruptor<>(factory, bufferSize,
                DaemonThreadFactory.INSTANCE, ProducerType.MULTI, new BlockingWaitStrategy());

        // Connect the handler
        disruptor.handleEventsWith(new LongEventHandler());

//        disruptor.handleEventsWithWorkerPool(new LongWorkHandler()).handleEventsWithWorkerPool(new LongWorkHandler());

        // Start the Disruptor, starts all threads running
        disruptor.start();

        // Get the ring buffer from the Disruptor to be used for publishing.
        final RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("addShutdownHook end");
                disruptor.shutdown();
            }
        }));

       final AtomicInteger integer = new AtomicInteger(0);

        for (int i = 0; i < 1; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    LongEventProducer producer = new LongEventProducer(ringBuffer);
                    ByteBuffer bb = ByteBuffer.allocate(8);
                    for (long l = 0; true; l++) {
                        bb.putLong(0, integer.getAndIncrement());
                        producer.onData(bb);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();
        }


    }
}
