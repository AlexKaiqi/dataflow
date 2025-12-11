package com.tencent.dataflow.domain.service;

import com.tencent.dataflow.domain.event.Event;
import com.tencent.dataflow.domain.node.Node;

/**
 * ControlPlaneService - 控制平面服务接口
 * <p>
 * 负责核心的控制流逻辑：
 * 1. 接收事件
 * 2. 评估节点的控制策略 (ControlPolicy)
 * 3. 触发相应的行为 (Action)
 * </p>
 */
public interface ControlPlaneService {

    /**
     * 处理事件并评估控制策略
     * <p>
     * 当系统收到任何事件时调用此方法。
     * </p>
     * @param event 发生的事件
     */
    void onEvent(Event event);

    /**
     * 手动执行节点行为
     * @param node 目标节点
     * @param actionName 行为名称
     * @param params 参数
     */
    void executeAction(Node node, String actionName, java.util.Map<String, Object> params);

    /**
     * 评估单个节点的策略
     * <p>
     * 针对特定事件，检查该节点是否需要执行任何操作。
     * </p>
     * 
     * @param node 目标节点
     * @param event 触发事件
     */
    void evaluateNodePolicy(Node node, Event event);
}
