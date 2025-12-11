package com.tencent.dataflow.domain.event;

/**
 * EventMatcher - 事件匹配器接口
 * <p>
 * 用于判断一个事件是否符合特定的条件（如订阅规则）。
 * </p>
 */
public interface EventMatcher {
    /**
     * 判断事件是否匹配
     * @param event 待检查的事件
     * @return true 如果匹配
     */
    boolean matches(Event event);
}
