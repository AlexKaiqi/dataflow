package com.tencent.dataflow.domain.executor;

import com.tencent.dataflow.domain.node.Node;
import com.tencent.dataflow.domain.taskschema.ActionDefinition;
import com.tencent.dataflow.domain.taskschema.StateDefinition;

import java.util.Map;

/**
 * TaskExecutor - 任务执行器接口
 * <p>
 * 负责实际执行 TaskSchema 中定义的 Action 和 State 查询。
 * 在生产环境中，这会通过 HTTP/gRPC 调用远程服务。
 * 在测试环境中，可以使用 Mock 实现。
 * </p>
 */
public interface TaskExecutor {

    /**
     * 执行行为
     * @param node 目标节点
     * @param action 行为定义
     * @param params 行为参数
     * @return 执行结果
     */
    Object executeAction(Node node, ActionDefinition action, Map<String, Object> params);

    /**
     * 获取状态
     * @param node 目标节点
     * @param state 状态定义
     * @return 状态值
     */
    Object getState(Node node, StateDefinition state);
}
