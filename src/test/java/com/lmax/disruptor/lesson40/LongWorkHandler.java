package com.lmax.disruptor.lesson40;

import com.lmax.disruptor.WorkHandler;

public class LongWorkHandler implements WorkHandler<LongEvent> {
    @Override
    public void onEvent(LongEvent event) throws Exception {
        System.out.println(event.getValue());
    }
}
