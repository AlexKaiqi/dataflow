# **《Event 事件》**

## 概述

**Event（事件）** 是 **ControlFlow** 平台中节点状态变化和控制指令的载体。它遵循 **CloudEvents** 规范的核心语义，用于驱动流水线的编排执行和反应式控制。

### 核心职责

1. **状态通知**：节点执行状态变更（如 `task.succeeded`, `task.failed`）。
2. **控制指令**：控制平面下发的指令（如 `control.stop`, `control.restart`）。
3. **数据传递**：通过 `payload` 传递任务输出或配置参数。
4. **逻辑关联**：通过 `correlationId` 串联跨节点的业务逻辑。

---

## 领域模型结构

```yaml
Event:
  # ==== 1. CloudEvents 核心属性 ====
  id: string                                # 事件唯一标识 (UUID)
  type: string                              # 事件类型
                                            # 格式: {domain}.{entity}.{action}
                                            # 示例: "task.node.succeeded", "system.maintenance.start"

  source: string                            # 事件源 (URI)
                                            # 示例: "/pipelines/p-123/nodes/node-A"

  time: Instant                             # 事件发生时间

  # ==== 2. 上下文属性 (Context) ====
  pipelineId: string                        # 所属 Pipeline ID

  executionId: string                       # 执行实例 ID (物理范围)
                                            # Batch: Run ID (每次运行不同)
                                            # Stream: Deployment ID (重启后可能改变)

  correlationId: string                     # 逻辑关联 ID (逻辑范围)
                                            # 用于跨 Execution 串联业务。
                                            # 示例: 数据分区 ID, 业务事务 ID

  # ==== 3. 数据负载 (Data) ====
  payload: Map<String, Object>              # 业务数据
                                            # 任务输出: { "path": "s3://...", "count": 100 }
                                            # 告警信息: { "cpu": 90, "threshold": 80 }

  # ==== 4. 扩展属性 (Extensions) ====
  attributes: Map<String, String>           # 元数据 (Headers)
                                            # 示例: { "traceId": "...", "priority": "high" }
```

### 关键字段说明

#### 1. executionId vs correlationId

这是混合编排（Hybrid Orchestration）场景下的关键设计：

- **executionId (物理执行 ID)**:
  - 标识**一次具体的代码运行**。
  - 对于批处理任务，每次调度生成一个新的 `executionId`。
  - 对于流处理任务，每次部署或重启生成一个新的 `executionId`。
  - **作用**: 用于日志检索、资源隔离、状态追踪。

- **correlationId (逻辑关联 ID)**:
  - 标识**一次逻辑上的业务处理流程**。
  - 可以跨越多个 `executionId`。
  - 例如：一个批处理任务生成了数据版本 `v1` (executionId=A)，触发了一个流处理任务 (executionId=B) 进行处理。它们共享同一个 `correlationId=v1`。
  - **作用**: 用于业务全链路追踪、跨节点数据关联。

#### 2. type (事件类型)

建议遵循分层命名规范：

- **任务生命周期事件**:
  - `task.node.started`
  - `task.node.succeeded`
  - `task.node.failed`
  - `task.node.skipped`

- **控制平面事件**:
  - `control.command.stop`
  - `control.command.restart`
  - `control.policy.triggered` (如自动扩缩容触发)

- **系统/环境事件**:
  - `system.maintenance.window.start`
  - `system.resource.exhausted`

---

## 事件流转示例

### 场景：上游批处理触发下游流处理

1. **Event A (批处理成功)**
   ```json
   {
     "type": "task.node.succeeded",
     "source": "/pipelines/p1/nodes/batch-loader",
     "executionId": "run-20231027-001",
     "correlationId": "batch-20231027",
     "payload": {
       "outputPath": "s3://data/2023/10/27"
     }
   }
   ```

2. **Control Plane (评估)**
   - 收到 Event A。
   - 检查下游节点 `stream-processor` 的 `startWhen` 表达式。
   - 表达式: `event.type == 'task.node.succeeded' && event.source.endsWith('batch-loader')`
   - 结果: True -> 启动 `stream-processor`。

3. **Event B (流处理启动)**
   ```json
   {
     "type": "task.node.started",
     "source": "/pipelines/p1/nodes/stream-processor",
     "executionId": "deploy-v5",  // 新的执行 ID
     "correlationId": "batch-20231027", // 继承上游的逻辑 ID
     "payload": {
       "config": { "sourcePath": "s3://data/2023/10/27" }
     }
   }
   ```
