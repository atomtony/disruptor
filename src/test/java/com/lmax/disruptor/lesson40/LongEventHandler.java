package com.lmax.disruptor.lesson40;

import com.lmax.disruptor.*;

public class LongEventHandler implements EventHandler<LongEvent>, LifecycleAware, BatchStartAware, TimeoutHandler, SequenceReportingEventHandler<LongEvent> {
    public void onEvent(LongEvent event, long sequence, boolean endOfBatch) {
        System.out.println("Event: " + event);
    }

    @Override
    public void onBatchStart(long batchSize) {
        System.out.println(batchSize);
    }

    @Override
    public void onTimeout(long sequence) throws Exception {
        System.out.println(sequence);
    }

    @Override
    public void setSequenceCallback(Sequence sequenceCallback) {
        System.out.println(sequenceCallback.get());
    }

    @Override
    public void onStart() {
        System.out.println("handler start");
    }

    @Override
    public void onShutdown() {
        System.out.println("handler shutdown");
    }
}

