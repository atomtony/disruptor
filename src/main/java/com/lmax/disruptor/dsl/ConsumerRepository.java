/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor.dsl;

import com.lmax.disruptor.*;

import java.util.*;

/**
 * Provides a repository mechanism to associate {@link EventHandler}s with {@link EventProcessor}s
 *
 * @param <T> the type of the {@link EventHandler}
 */
class ConsumerRepository<T> implements Iterable<ConsumerInfo>
{
    private final Map<EventHandler<?>, EventProcessorInfo<T>> eventProcessorInfoByEventHandler =
        new IdentityHashMap<>();
    private final Map<Sequence, ConsumerInfo> eventProcessorInfoBySequence =
        new IdentityHashMap<>();
    private final Collection<ConsumerInfo> consumerInfos = new ArrayList<>();

    public void add(
        final EventProcessor eventprocessor,
        final EventHandler<? super T> handler,
        final SequenceBarrier barrier)
    {
        // 包装消费者信息
        final EventProcessorInfo<T> consumerInfo = new EventProcessorInfo<>(eventprocessor, handler, barrier);
        // 以事件具柄为key保存消费者信息
        eventProcessorInfoByEventHandler.put(handler, consumerInfo);
        // 以消费者读序列为key保存消费者信息
        eventProcessorInfoBySequence.put(eventprocessor.getSequence(), consumerInfo);
        // 添加消费者信息大集合中
        consumerInfos.add(consumerInfo);
    }

    public void add(final EventProcessor processor)
    {
        final EventProcessorInfo<T> consumerInfo = new EventProcessorInfo<>(processor, null, null);
        eventProcessorInfoBySequence.put(processor.getSequence(), consumerInfo);
        consumerInfos.add(consumerInfo);
    }

    public void add(final WorkerPool<T> workerPool, final SequenceBarrier sequenceBarrier)
    {
        final WorkerPoolInfo<T> workerPoolInfo = new WorkerPoolInfo<>(workerPool, sequenceBarrier);
        consumerInfos.add(workerPoolInfo);
        for (Sequence sequence : workerPool.getWorkerSequences())
        {
            eventProcessorInfoBySequence.put(sequence, workerPoolInfo);
        }
    }

    public boolean hasBacklog(long cursor, boolean includeStopped)
    {
        // 遍历所有消费者
        for (ConsumerInfo consumerInfo : consumerInfos)
        {
            // 逻辑与之前判断消费者正在运行，逻辑与之后判断消费者为链的终端。
            if ((includeStopped || consumerInfo.isRunning()) && consumerInfo.isEndOfChain())
            {
                // 获取链终端消费者读写序列数组
                final Sequence[] sequences = consumerInfo.getSequences();

                for (Sequence sequence : sequences)
                {
                    // 如果生产者序列值大于消费者序列值，说明还未消费完，返回true
                    if (cursor > sequence.get())
                    {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * @deprecated this function should no longer be used to determine the existence
     * of a backlog, instead use hasBacklog
     */
    @Deprecated
    public Sequence[] getLastSequenceInChain(boolean includeStopped)
    {
        List<Sequence> lastSequence = new ArrayList<>();
        for (ConsumerInfo consumerInfo : consumerInfos)
        {
            if ((includeStopped || consumerInfo.isRunning()) && consumerInfo.isEndOfChain())
            {
                final Sequence[] sequences = consumerInfo.getSequences();
                Collections.addAll(lastSequence, sequences);
            }
        }

        return lastSequence.toArray(new Sequence[lastSequence.size()]);
    }

    public EventProcessor getEventProcessorFor(final EventHandler<T> handler)
    {
        final EventProcessorInfo<T> eventprocessorInfo = getEventProcessorInfo(handler);
        if (eventprocessorInfo == null)
        {
            throw new IllegalArgumentException("The event handler " + handler + " is not processing events.");
        }

        return eventprocessorInfo.getEventProcessor();
    }

    public Sequence getSequenceFor(final EventHandler<T> handler)
    {
        return getEventProcessorFor(handler).getSequence();
    }

    public void unMarkEventProcessorsAsEndOfChain(final Sequence... barrierEventProcessors)
    {
        for (Sequence barrierEventProcessor : barrierEventProcessors)
        {
            getEventProcessorInfo(barrierEventProcessor).markAsUsedInBarrier();
        }
    }

    @Override
    public Iterator<ConsumerInfo> iterator()
    {
        return consumerInfos.iterator();
    }

    public SequenceBarrier getBarrierFor(final EventHandler<T> handler)
    {
        final ConsumerInfo consumerInfo = getEventProcessorInfo(handler);
        return consumerInfo != null ? consumerInfo.getBarrier() : null;
    }

    private EventProcessorInfo<T> getEventProcessorInfo(final EventHandler<T> handler)
    {
        return eventProcessorInfoByEventHandler.get(handler);
    }

    private ConsumerInfo getEventProcessorInfo(final Sequence barrierEventProcessor)
    {
        return eventProcessorInfoBySequence.get(barrierEventProcessor);
    }
}
