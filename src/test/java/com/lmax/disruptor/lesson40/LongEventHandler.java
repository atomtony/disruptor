package com.lmax.disruptor.lesson40;

import com.lmax.disruptor.*;

public class LongEventHandler implements EventHandler<LongEvent>, LifecycleAware, BatchStartAware, TimeoutHandler, SequenceReportingEventHandler<LongEvent> {
    public void onEvent(LongEvent event, long sequence, boolean endOfBatch) {
        System.out.println("Event: " + event.getValue() + "\tsequence: " + sequence);
    }

    @Override
    public void onBatchStart(long batchSize) {

    }

    @Override
    public void onTimeout(long sequence) throws Exception {

    }

    @Override
    public void setSequenceCallback(Sequence sequenceCallback) {

    }

    @Override
    public void onStart() {

    }

    @Override
    public void onShutdown() {

    }
}

