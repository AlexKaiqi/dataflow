package com.tencent.dataflow.domain.event;

/**
 * EventPublisher - 事件发布接口
 * <p>
 * 定义如何将事件发送到事件总线。
 * </p>
 */
public interface EventPublisher {
    /**
     * 发布一个事件
     * @param event 事件对象
     */
    void publish(Event event);
}
