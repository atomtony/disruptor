package com.lmax.disruptor.dsl;

import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;

import java.util.concurrent.Executor;

interface ConsumerInfo
{
    // 返回消费者序列
    Sequence[] getSequences();

    SequenceBarrier getBarrier();

    // 消费者是否为链末端
    boolean isEndOfChain();

    // 启动消费者
    void start(Executor executor);

    // 停止消费者
    void halt();

    // 标记消费者不为链末端
    void markAsUsedInBarrier();

    // 消费者是否正在运行
    boolean isRunning();
}
