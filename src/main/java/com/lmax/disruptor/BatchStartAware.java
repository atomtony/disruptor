package com.lmax.disruptor;

public interface BatchStartAware
{

    /**
     * 批量开始
     * @param batchSize 处理个数
     */
    void onBatchStart(long batchSize);
}
