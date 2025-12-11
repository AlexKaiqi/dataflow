# **《Node 节点》**

## 概述

**Node（节点）** 是 Pipeline 中的编排单元，代表工作流中的一个执行点。它不仅定义了任务的静态配置（TaskConfig），还通过 **ControlPolicy（控制策略）** 定义了任务在运行时的动态行为（如停止、重启、重试等）。

### 核心职责

- **任务定义**：通过 `TaskConfig` 指定任务类型和参数。
- **触发机制**：通过 `startWhen` 定义节点启动的条件（事件驱动）。
- **数据映射**：通过 `startPayload` 将事件数据映射为任务输入。
- **反应式控制**：通过 `ControlPolicy` 声明式地定义运行时控制逻辑（Stop/Restart/Retry）。

---

## 领域模型结构

```yaml
Node:
  # ==== 1. 基础信息 ====
  id: string                                # 节点 ID (Pipeline 内唯一)
  name: string                              # 节点名称 (可读性更好)
  description: string                       # 描述信息

  # ==== 2. 任务配置 ====
  taskConfig: TaskConfig                    # 定义"做什么"
    taskType: string                        # 引用 TaskSchema.type (如 "flink_job", "spark_batch")
    config: object                          # 任务具体参数 (符合 TaskSchema 定义的 schema)
    taskDefinitionRef: string               # (可选) 引用预定义的 TaskDefinition

  # ==== 3. 启动机制 (Trigger) ====
  startWhen: string                         # 启动条件表达式 (SpEL)
                                            # 上下文变量:
                                            # 1. event: 当前触发事件 (Event对象)
                                            #    - event.type: 事件类型 (如 'task.succeeded')
                                            #    - event.source: 事件源
                                            #    - event.payload: 事件负载
                                            # 2. {nodeId}: 直接通过节点ID访问该节点对象 (NodeStateWrapper)
                                            #    - {nodeId}.status: 节点状态 (如 'RUNNING', 'SUCCEEDED')
                                            #    - {nodeId}.succeeded: 快捷判断 status == 'SUCCEEDED'
                                            #    - {nodeId}.failed: 快捷判断 status == 'FAILED'
                                            #    - {nodeId}.outputs: 节点输出数据 (Map)
                                            #                        [来源] 上游任务成功事件(task.succeeded)的 payload.outputs
                                            # 3. pipeline: 流水线全局信息

                                            # 示例 1 (依赖状态): "node_A.succeeded && node_B.succeeded"
                                            # 示例 2 (依赖输出): "approval_node.outputs['result'] == 'pass'"
                                            # 示例 3 (混合): "event.type == 'signal' && node_A.succeeded"

  startPayload: Map<String, String>         # 启动参数映射
                                            # Key: 任务输入参数名
                                            # Value: SpEL 表达式 (从 event 中提取值)
                                            # 示例: { "inputPath": "{{ event.payload.outputPath }}" }

  # ==== 4. 控制策略 (Reactive Control) ====
  controlPolicy: ControlPolicy              # 定义"怎么控制"
    stopWhen: string                        # 停止条件 (SpEL)
                                            # 示例: "event.type == 'maintenance.window.start'"

    restartWhen: string                     # 重启条件 (SpEL)
                                            # 示例: "event.type == 'config.updated'"

    retryWhen: string                       # 重试条件 (SpEL, 用于 Batch)
                                            # 示例: "error.code == 'NETWORK_TIMEOUT' && retryCount < 3"

    skipWhen: string                        # 跳过条件 (SpEL)
                                            # 示例: "context.isHoliday == true"

    alertWhen: string                       # 告警条件 (SpEL)
                                            # 示例: "metrics.lag > 10000"

    customRules: List<PolicyRule>           # 自定义规则 (Event -> Action)
      - condition: string                   # SpEL 表达式
        action: string                      # 触发的 Action 名称 (需在 TaskSchema 中定义)
        params: Map<String, String>         # Action 参数
```

### 字段详解

#### 1. startWhen (启动条件)

定义节点何时被实例化并执行。
- **类型**: SpEL 表达式 (String)
- **上下文**:
    - `event`: 当前触发评估的事件对象。
    - `{nodeId}`: 动态注入所有节点的状态包装器。
- **示例**:
  - **串行依赖**: `prev_node.succeeded`
  - **并行汇聚**: `node_A.succeeded && node_B.succeeded`
  - **条件分支**: `approval_node.succeeded && approval_node.outputs['decision'] == 'approve'`
  - **事件驱动**: `event.type == 'external.signal' && event.payload.code == 200`

#### 2. startPayload (输入映射)

定义如何准备任务的输入数据。
- **类型**: Map<String, String>
- **机制**: 在任务启动前求值，结果作为 `TaskInstance.inputs`。
- **示例**:
  ```json
  {
    "sourceTable": "{{ event.payload.tableName }}",
    "partition": "{{ event.payload.partitionDate }}"
  }
  ```

#### 3. ControlPolicy (控制策略)

这是 **ControlFlow** 区别于传统工作流引擎的核心特性。它允许节点在运行过程中响应外部事件。

- **stopWhen**: 适用于流式任务或长运行任务。当表达式为真时，控制平面向任务发送 `stop` 指令。
- **restartWhen**: 适用于流式任务。当配置变更或需要重新加载时触发。
- **customRules**: 扩展能力。例如，定义一个 "scale_out" 规则：
  ```yaml
  customRules:
    - condition: "event.type == 'metrics.high_load' && event.payload.cpu > 80"
      action: "scale"
      params:
        replicas: "5"
  ```
  前提是该节点的 `TaskSchema` 中定义了名为 `scale` 的 Action。

---

## 示例

### 场景：混合流批处理 (Hybrid Pipeline)

```yaml
nodes:
  # 1. 流式采集节点 (长期运行)
  - id: "stream_ingest"
    taskConfig:
      taskType: "flink_cdc"
      config:
        source: "mysql_db"
    startWhen: "event.type == 'pipeline.started'"
    controlPolicy:
      # 收到维护窗口事件时暂停
      stopWhen: "event.type == 'maintenance.start'"
      # 收到恢复事件时重启
      restartWhen: "event.type == 'maintenance.end'"

  # 2. 批处理清洗节点 (每天触发)
  - id: "batch_clean"
    taskConfig:
      taskType: "spark_batch"
    # 每天 00:00 触发 (假设有定时事件源)
    startWhen: "event.type == 'scheduler.trigger' && event.payload.cron == 'daily'"
    startPayload:
      date: "{{ event.payload.date }}"
    controlPolicy:
      # 失败重试策略
      retryWhen: "retryCount < 3"
```
