package com.tencent.dataflow.domain.event;

/**
 * EventListener - 事件监听接口
 * <p>
 * 定义如何处理接收到的事件。
 * </p>
 */
public interface EventListener {
    /**
     * 处理事件
     * @param event 事件对象
     */
    void onEvent(Event event);
}
