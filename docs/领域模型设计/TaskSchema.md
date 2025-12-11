# **《TaskSchema 任务模式》**

## 概述

**TaskSchema（任务模式）** 是任务类型的元定义，定义了"这类任务能做什么"。它通过声明任务支持的**行为（actions）**、产生的**事件（events）**以及包含的**状态（states）**，建立任务类型的能力契约。

### 核心职责

- **定义行为契约**：声明任务类型支持哪些行为（actions），每个行为接受什么输入。
- **定义事件契约**：声明任务会产生哪些事件（events），每个事件包含什么输出。
- **定义状态契约**：声明任务包含哪些状态（states），以及如何查询这些状态。
- **定义配置规范**：通过 JSON Schema 约束任务配置的结构。

---

## 领域模型结构

```yaml
TaskSchema:
  # ==== 1. 基础信息 ====
  type: string                         # Schema 类型标识（全局唯一）
                                       # 如: "flink_streaming", "shell_script"
  description: string                  # 描述

  # ==== 2. 能力定义 ====
  actions: Map<String, ActionDefinition> # 支持的行为
  events: List<EventDefinition>          # 产生的事件
  states: Map<String, StateDefinition>   # 包含的状态

  # ==== 3. 执行配置 ====
  executor: ExecutorConfig             # 执行器配置 (BaseURL, Auth)
  executionConfigSchema: object        # 任务配置的 JSON Schema
```

### 1. ActionDefinition (行为定义)

定义任务支持的操作指令。

```yaml
ActionDefinition:
  name: string                         # 行为名称 (如 "start", "stop", "scale")
  description: string                  # 描述
  protocol: AccessProtocol             # 访问协议 (HTTP, GRPC, K8S, INTERNAL)
  endpoint: string?                    # 访问端点 (默认为 "/{name}")
  protocolConfig: Map<String, Object>  # 协议扩展配置
```

**标准行为常量**:
- `start`: 启动任务
- `stop`: 停止任务
- `restart`: 重启任务
- `retry`: 重试任务
- `pause`: 暂停任务
- `resume`: 恢复任务

### 2. EventDefinition (事件定义)

定义任务可能产生的信号。

```yaml
EventDefinition:
  name: string                         # 事件名称 (如 "started", "failed")
  description: string                  # 描述
```

**标准事件常量**:
- `started`: 任务已启动
- `succeeded`: 任务成功完成
- `failed`: 任务失败
- `stopped`: 任务已停止
- `paused`: 任务已暂停
- `resumed`: 任务已恢复

### 3. StateDefinition (状态定义)

定义任务可查询的状态属性。

```yaml
StateDefinition:
  name: string                         # 状态名称 (如 "status", "metrics")
  description: string                  # 描述
  type: string                         # 类型 (string, number, object)
  protocol: AccessProtocol             # 访问协议
  endpoint: string?                    # 获取端点
  valueSchema: object                  # 值结构定义 (JSON Schema)
  possibleValues: List<String>         # 枚举值列表
  terminal: boolean                    # 是否为终止状态
```

**标准状态常量**:
- `status`: 运行状态 (RUNNING, FAILED...)
- `progress`: 进度 (0-100)
- `metrics`: 性能指标
- `checkpoint`: 检查点信息
    event node.started(payload: {
      nodeId: string,
      executorJobId: string
    })

    // 事件：产生成功事件
    event node.succeeded(payload: {
      nodeId: string,
      outputs?: {...}
    })
  }

TaskDefinition = 配置模板（可复用）
  pyspark_etl_template:
    taskType: "pyspark"
    config:
      mainFile: "s3://bucket/etl.py"
      driver_memory: "2g"

Node = 实例（运行时）
  extract_node:
    taskConfig:
      taskType: "pyspark"
      config: { mainFile: "..." }
    startPayload:
      inputs:
        source_table: "users"
    startWhen: "event:trigger.started"
```

---

## 领域模型结构

```yaml
TaskSchema:
  # ==== 1. 唯一标识 ====
  type: string                              # 任务类型标识（全局唯一）
                                            # 例如: "pyspark", "sql", "approval", "trigger"

  # ==== 2. 基本信息 ====
  description: string                       # 任务类型描述
  category: enum                            # 任务类别: computation | control

  # ==== 3. 行为定义（Actions）====
  # 定义任务支持哪些行为，每个行为定义了触发该行为的事件类型和输入结构
  actions: List[ActionDefinition]
    ActionDefinition:
      action: string                        # 行为名称（如 start, stop, retry）
      description: string                   # 行为描述
      triggeredBy: string                   # 触发该行为的事件类型
                                            # 例如: "node.start_requested", "node.stop_requested"

      payload: PayloadDefinition            # 输入事件的 payload 结构定义
        PayloadDefinition:
          # 标准字段（所有任务类型通用）
          nodeId: string                    # 节点 ID
          correlationId: string             # 关联 ID（pipelineId:nodeId）
          config: object                    # 任务配置（结构由 executionConfigSchema 约束）

          # 可选字段（按任务能力）
          inputs?: object                   # 业务输入数据（自由结构，由用户脚本定义）
          params?: object                   # 参数（用于不需要inputs的简单任务，如 trigger）

  # ==== 4. 事件定义（Events）====
  # 定义任务会产生哪些事件，每个事件定义了输出结构
  events: List[EventDefinition]
    EventDefinition:
      eventType: string                     # 事件类型（带 node. 前缀）
                                            # 标准事件: node.started, node.succeeded, node.failed
                                            # 自定义事件: node.approved, node.rejected, node.metrics_updated

      description: string                   # 事件描述
      producedBy: string                    # 哪个行为产生此事件（action 名称）

      payload: PayloadDefinition            # 事件的 payload 结构定义
        PayloadDefinition:
          # 标准字段（所有事件通用）
          nodeId: string                    # 节点 ID
          correlationId: string             # 关联 ID
          timestamp: integer                # 事件时间戳（毫秒）

          # 执行器相关字段（可选）
          executorJobId?: string            # 执行器任务 ID
          duration?: integer                # 执行时长（毫秒）

          # 可选字段（按任务能力）
          outputs?: object                  # 业务输出数据（自由结构，由用户脚本填充）
          error?: object                    # 错误信息（失败事件）
          metrics?: object                  # 监控指标（自定义）

  # ==== 5. 配置规范（JSON Schema）====
  executionConfigSchema: JsonNode           # 任务配置的 JSON Schema
                                            # 约束 Node.taskConfig.config 的结构
                                            # 例如: PySpark 需要 mainFile, SQL 需要 sql 和 database

  # ==== 6. 元数据 ====
  createdBy: string
  createdAt: timestamp
```

### 重要设计原则

#### 1. 纯事件驱动

- **所有数据流通过事件 payload 传递**，不引入单独的变量系统
- **行为定义输入结构**：action.payload 定义触发事件的结构
- **事件定义输出结构**：event.payload 定义产生事件的结构

#### 2. Payload 结构自由度

- **标准字段固定**：nodeId, correlationId, timestamp 等由系统填充
- **inputs/outputs 自由**：结构由用户脚本定义，TaskSchema 只声明字段存在性


---

## 标准任务类型

### 1. Trigger（触发器）

**用途**：流水线入口节点，接收外部参数并透传给下游。

**特点**：

- 内置任务类型，无需执行器
- 头节点（无 startWhen，手动触发事件）
- 接收 params，立即发布 node.started 事件

**TaskSchema 定义**：

```yaml
TaskSchema:
  type: "trigger"
  description: "流水线触发器，接收外部参数并启动流水线"
  category: "control"

  actions:
    - action: "start"
      description: "启动触发器"
      triggeredBy: "node.start_requested"
      payload:
        nodeId: string
        correlationId: string
        config: {}                          # 触发器无需配置
        params: object                      # 外部传入的参数（自由结构）

  events:
    - eventType: "node.started"
      description: "触发器启动完成"
      producedBy: "start"
      payload:
        nodeId: string
        correlationId: string
        timestamp: integer
        params: object                      # 透传 params 到 payload

  executionConfigSchema:
    type: "object"
    properties: {}                          # 触发器无需配置
```

**使用示例**：

```yaml
nodes:
  # 头节点：触发器
  - id: trigger
    taskConfig:
      taskType: "trigger"
      config: {}
    # 无 startWhen，通过外部 API 触发
    # 无 startPayload（params 由 API 调用时提供）

  # 下游节点：引用触发器的输出
  - id: extract
    taskConfig:
      taskType: "pyspark"
      config:
        mainFile: "s3://scripts/extract.py"
    startPayload:
      inputs:
        source_table: "{{ event:trigger.started.payload.params.source_table }}"
        target_date: "{{ event:trigger.started.payload.params.target_date }}"
    startWhen: "event:trigger.started"
```

**外部 API 触发**：

```bash
POST /api/v1/pipelines/{pipelineId}/runs
Content-Type: application/json

{
  "params": {
    "source_table": "users",
    "target_date": "2025-01-15"
  }
}
```

---

### 2. PySpark Operator

**用途**：执行 PySpark 批处理任务。

**TaskSchema 定义**：

```yaml
TaskSchema:
  type: "pyspark"
  description: "PySpark 批处理任务"
  category: "computation"

  actions:
    - action: "start"
      description: "启动 PySpark 任务"
      triggeredBy: "node.start_requested"
      payload:
        nodeId: string
        correlationId: string
        config:
          mainFile: string                  # PySpark 脚本路径
          args: string[]?                   # 命令行参数
          driverMemory: string?             # 例如 "2g"
          executorMemory: string?           # 例如 "4g"
        inputs:                             # 业务输入（自由结构）
          # 例如: { source_path: "s3://...", table_name: "users" }

  events:
    - eventType: "node.started"
      description: "任务已开始执行"
      producedBy: "start"
      payload:
        nodeId: string
        correlationId: string
        timestamp: integer
        executorJobId: string               # Spark Job ID

    - eventType: "node.succeeded"
      description: "任务执行成功"
      producedBy: "start"
      payload:
        nodeId: string
        correlationId: string
        timestamp: integer
        executorJobId: string
        duration: integer                   # 执行时长（毫秒）
        outputs:                            # 业务输出（自由结构）
          # 例如: { output_path: "s3://...", record_count: 1000 }

    - eventType: "node.failed"
      description: "任务执行失败"
      producedBy: "start"
      payload:
        nodeId: string
        correlationId: string
        timestamp: integer
        executorJobId: string
        duration: integer
        error:
          message: string
          stackTrace: string

  executionConfigSchema:
    type: "object"
    required: ["mainFile"]
    properties:
      mainFile:
        type: "string"
        description: "PySpark 脚本路径"
      args:
        type: "array"
        items:
          type: "string"
      driverMemory:
        type: "string"
        pattern: "^[0-9]+(m|g|t)$"
      executorMemory:
        type: "string"
        pattern: "^[0-9]+(m|g|t)$"
```

**使用示例**：

```yaml
nodes:
  - id: extract
    taskConfig:
      taskType: "pyspark"
      config:
        mainFile: "s3://bucket/scripts/extract.py"
        driverMemory: "2g"
        executorMemory: "4g"

    startPayload:
      inputs:
        source_table: "{{ event:trigger.started.payload.params.source_table }}"
        output_path: "s3://bucket/extracted/{{ event:trigger.started.payload.params.target_date }}"

    startWhen: "event:trigger.started"
```

---

### 3. SQL Operator

**用途**：执行 SQL 查询或脚本。

**TaskSchema 定义**：

```yaml
TaskSchema:
  type: "sql"
  description: "SQL 查询任务"
  category: "computation"

  actions:
    - action: "start"
      description: "执行 SQL"
      triggeredBy: "node.start_requested"
      payload:
        nodeId: string
        correlationId: string
        config:
          sql: string                       # SQL 语句或脚本路径
          database: string                  # 数据库名称
          connectionId: string              # 连接 ID
        inputs:                             # SQL 参数（自由结构）
          # 例如: { table_name: "users", date: "2025-01-15" }

  events:
    - eventType: "node.started"
      description: "SQL 开始执行"
      producedBy: "start"
      payload:
        nodeId: string
        correlationId: string
        timestamp: integer
        executorJobId: string               # 查询 ID

    - eventType: "node.succeeded"
      description: "SQL 执行成功"
      producedBy: "start"
      payload:
        nodeId: string
        correlationId: string
        timestamp: integer
        executorJobId: string
        duration: integer
        outputs:
          rowsAffected: integer
          resultPath: string?               # 结果存储路径（SELECT 查询）

    - eventType: "node.failed"
      description: "SQL 执行失败"
      producedBy: "start"
      payload:
        nodeId: string
        correlationId: string
        timestamp: integer
        error:
          sqlState: string
          message: string

  executionConfigSchema:
    type: "object"
    required: ["sql", "database", "connectionId"]
    properties:
      sql:
        type: "string"
      database:
        type: "string"
      connectionId:
        type: "string"
```

---

### 4. Approval（审批）

**用途**：人工审批流程。

**特点**：

- 不产生 node.succeeded 或 node.failed 事件
- 产生特定事件：node.approved、node.rejected、node.timeout

**TaskSchema 定义**：

```yaml
TaskSchema:
  type: "approval"
  description: "人工审批任务"
  category: "approval"

  actions:
    - action: "start"
      description: "发起审批请求"
      triggeredBy: "node.start_requested"
      payload:
        nodeId: string
        correlationId: string
        config:
          approvers: string[]               # 审批人列表
          timeoutMinutes: integer           # 超时时间（分钟）
          notificationUrl: string?          # 通知回调 URL
        inputs:                             # 审批上下文（自由结构）
          # 例如: { reason: "数据质量检查", score: 0.85 }

  events:
    - eventType: "node.started"
      description: "审批请求已发出"
      producedBy: "start"
      payload:
        nodeId: string
        correlationId: string
        timestamp: integer
        approvalId: string                  # 审批 ID

    - eventType: "node.approved"
      description: "审批通过"
      producedBy: "start"
      payload:
        nodeId: string
        correlationId: string
        timestamp: integer
        approvalId: string
        outputs:
          approver: string                  # 审批人
          comment: string                   # 审批意见

    - eventType: "node.rejected"
      description: "审批拒绝"
      producedBy: "start"
      payload:
        nodeId: string
        correlationId: string
        timestamp: integer
        approvalId: string
        outputs:
          approver: string
          reason: string                    # 拒绝原因

    - eventType: "node.timeout"
      description: "审批超时"
      producedBy: "start"
      payload:
        nodeId: string
        correlationId: string
        timestamp: integer
        approvalId: string

  executionConfigSchema:
    type: "object"
    required: ["approvers", "timeoutMinutes"]
    properties:
      approvers:
        type: "array"
        items:
          type: "string"
      timeoutMinutes:
        type: "integer"
        minimum: 1
      notificationUrl:
        type: "string"
        format: "uri"
```

**使用示例**：

```yaml
nodes:
  - id: quality_check
    taskConfig:
      taskType: "pyspark"
      config:
        mainFile: "s3://scripts/quality_check.py"
    startPayload:
      inputs:
        data_path: "{{ event:extract.succeeded.payload.outputs.output_path }}"
    startWhen: "event:extract.succeeded"

  - id: manual_review
    taskConfig:
      taskType: "approval"
      config:
        approvers: ["alice@company.com", "bob@company.com"]
        timeoutMinutes: 1440  # 24小时
    startPayload:
      inputs:
        reason: "数据质量检查"
        score: "{{ event:quality_check.succeeded.payload.outputs.quality_score }}"
    # 质量分数低于 0.9 时需要审批
    startWhen: "event:quality_check.succeeded && {{ event:quality_check.succeeded.payload.outputs.quality_score < 0.9 }}"

  - id: load
    taskConfig:
      taskType: "sql"
      config:
        sql: "INSERT INTO target_table SELECT * FROM temp_table"
        database: "prod"
        connectionId: "prod_db"
    # 质量达标或审批通过后继续
    startWhen: "(event:quality_check.succeeded && {{ event:quality_check.succeeded.payload.outputs.quality_score >= 0.9 }}) || event:manual_review.approved"
```

---

### 5. Streaming（流处理）

**用途**：长期运行的流处理任务。

**特点**：

- 支持 stop 和 restart 行为
- 产生额外事件：node.stopped、node.restarted

**TaskSchema 定义**：

```yaml
TaskSchema:
  type: "streaming"
  description: "流处理任务"
  category: "computation"

  actions:
    - action: "start"
      description: "启动流处理任务"
      triggeredBy: "node.start_requested"
      payload:
        nodeId: string
        correlationId: string
        config:
          source: object                    # 数据源配置
          sink: object                      # 数据汇配置
          checkpointPath: string            # 检查点路径
        inputs:                             # 处理配置（自由结构）

    - action: "stop"
      description: "停止流处理任务"
      triggeredBy: "node.stop_requested"
      payload:
        nodeId: string
        correlationId: string

    - action: "restart"
      description: "重启流处理任务"
      triggeredBy: "node.restart_requested"
      payload:
        nodeId: string
        correlationId: string

  events:
    - eventType: "node.started"
      description: "流处理任务已启动"
      producedBy: "start"
      payload:
        nodeId: string
        correlationId: string
        timestamp: integer
        executorJobId: string

    - eventType: "node.stopped"
      description: "流处理任务已停止"
      producedBy: "stop"
      payload:
        nodeId: string
        correlationId: string
        timestamp: integer
        executorJobId: string

    - eventType: "node.restarted"
      description: "流处理任务已重启"
      producedBy: "restart"
      payload:
        nodeId: string
        correlationId: string
        timestamp: integer
        executorJobId: string

    - eventType: "node.failed"
      description: "流处理任务失败"
      producedBy: "start"
      payload:
        nodeId: string
        correlationId: string
        timestamp: integer
        executorJobId: string
        error:
          message: string
          stackTrace: string

    - eventType: "node.metrics_updated"
      description: "流处理指标更新"
      producedBy: "start"
      payload:
        nodeId: string
        correlationId: string
        timestamp: integer
        metrics:
          processedRecords: integer
          throughput: number                # 每秒处理记录数
          lag: integer                      # 延迟（毫秒）

  executionConfigSchema:
    type: "object"
    required: ["source", "sink", "checkpointPath"]
    properties:
      source:
        type: "object"
      sink:
        type: "object"
      checkpointPath:
        type: "string"
```

---

## 核心概念详解

### Actions（行为）

行为定义了任务可以被触发的操作。每个行为通过事件触发，并声明输入 payload 结构。

#### 标准行为

- **start**：启动任务执行（所有任务类型）
- **stop**：停止任务（流处理任务）
- **restart**：重启任务（流处理任务）
- **retry**：重试失败任务（可选）

#### 行为触发流程

```
ControlFlowEngine                ExecutorRegistry                  Executor
      │                                  │                             │
      │ 1. 评估 startWhen                │                             │
      │──> 条件满足                      │                             │
      │                                  │                             │
      │ 2. 发布 node.start_requested     │                             │
      │─────────────────────────────────→│                             │
      │                                  │                             │
      │                                  │ 3. 查找 taskType 对应的执行器│
      │                                  │──> PySparkExecutor         │
      │                                  │                             │
      │                                  │ 4. 调用 Executor.execute()  │
      │                                  │────────────────────────────→│
      │                                  │                             │
      │                                  │                             │ 5. 执行任务
      │                                  │                             │──> 启动 Spark Job
      │                                  │                             │
      │ 6. 发布 node.started             │                             │
      │←────────────────────────────────────────────────────────────────│
      │                                  │                             │
      │                                  │                             │ 7. 任务完成
      │                                  │                             │──> Spark Job 成功
      │                                  │                             │
      │ 8. 发布 node.succeeded           │                             │
      │←────────────────────────────────────────────────────────────────│
```

---

### Events（事件）

事件是任务在执行过程中产生的状态通知，用于驱动流水线编排。

#### 标准事件

- **node.started**：任务已开始执行（所有任务类型）
- **node.succeeded**：任务执行成功（批处理任务）
- **node.failed**：任务执行失败（所有任务类型）

#### 任务特定事件

不同任务类型可以产生特定事件：

- **流处理**：node.stopped、node.restarted、node.metrics_updated
- **审批**：node.approved、node.rejected、node.timeout
- **自定义**：根据业务需求定义（如 node.checkpoint_created）

#### 事件命名规范

- **前缀**：统一使用 `node.` 前缀
- **格式**：`node.<event_name>`，使用下划线分隔（如 node.metrics_updated）
- **语义**：事件名称应清晰表达状态或动作（succeeded 而非 completed，表达成功语义）

#### 事件发布机制

执行器通过 Event Bus 发布事件：

```python
# 执行器代码示例
event_bus.publish({
    'eventType': 'node.succeeded',
    'payload': {
        'nodeId': node_id,
        'correlationId': correlation_id,
        'timestamp': int(time.time() * 1000),
        'executorJobId': spark_job_id,
        'duration': execution_duration,
        'outputs': {
            'output_path': 's3://bucket/output',
            'record_count': 1000
        }
    }
})
```

---

### Payload 结构设计

#### 输入 Payload（action.payload）

定义触发事件的结构：

```yaml
payload:
  # 标准字段（系统填充）
  nodeId: string                            # 由 ControlFlowEngine 填充
  correlationId: string                     # 由 ControlFlowEngine 填充

  # 配置字段（由 Node.taskConfig.config 提供）
  config: object                            # 结构由 executionConfigSchema 约束

  # 数据字段（由 Node.startPayload 提供）
  inputs?: object                           # 业务输入（自由结构）
  params?: object                           # 参数（用于简单任务，如 trigger）
```

#### 输出 Payload（event.payload）

定义事件的结构：

```yaml
payload:
  # 标准字段（系统填充）
  nodeId: string                            # 由执行器填充
  correlationId: string                     # 由执行器填充
  timestamp: integer                        # 由执行器填充

  # 执行器字段（由执行器填充）
  executorJobId?: string                    # 执行器任务 ID
  duration?: integer                        # 执行时长（毫秒）

  # 数据字段（由用户脚本或执行器填充）
  outputs?: object                          # 业务输出（自由结构）
  error?: object                            # 错误信息（失败事件）
  metrics?: object                          # 监控指标
```

#### 自由度说明

- **inputs/outputs 结构自由**：TaskSchema 只声明字段存在性，不约束具体结构
- **用户脚本定义内容**：
  - PySpark 脚本通过 `sys.stdout` 输出 JSON 格式的 outputs
  - SQL 执行器根据查询结果填充 outputs
- **TaskDefinition 可选文档化**：可在 TaskDefinition 中文档化预期结构，帮助用户理解

**PySpark 脚本示例**：

```python
# extract.py
import json
import sys

# 执行业务逻辑
output_path = "s3://bucket/extracted/2025-01-15"
record_count = 1000

# 输出 outputs（JSON 格式）
outputs = {
    'output_path': output_path,
    'record_count': record_count
}
print(json.dumps({'outputs': outputs}))
```

---

## 能力推断

### 推断规则

通过检查 payload 结构推断任务能力，无需显式 capabilities 字段：

1. **支持业务输入**：action.payload 包含 `inputs` 字段
2. **支持业务输出**：event.payload（node.succeeded）包含 `outputs` 字段
3. **支持参数传递**：action.payload 包含 `params` 字段
4. **支持停止操作**：存在 `stop` action
5. **支持重启操作**：存在 `restart` action

### 推断示例

**PySpark Operator**：

```yaml
# action.payload 包含 inputs → 支持业务输入
# node.succeeded.payload 包含 outputs → 支持业务输出
能力: 支持输入和输出
```

**Trigger**：

```yaml
# action.payload 包含 params → 支持参数传递
# node.started.payload 包含 params → 透传参数
# 无 node.succeeded → 不产生业务输出
能力: 只支持参数传递
```

**Approval**：

```yaml
# action.payload 包含 inputs → 支持输入（审批上下文）
# node.approved.payload 包含 outputs → 支持输出（审批结果）
# 无 node.succeeded → 特殊事件模型
能力: 支持输入和输出，使用特定事件
```

### 工具函数

平台可提供辅助函数：

```python
def supports_business_inputs(task_schema: TaskSchema) -> bool:
    """检查任务类型是否支持业务输入"""
    for action in task_schema.actions:
        if 'inputs' in action.payload:
            return True
    return False

def supports_business_outputs(task_schema: TaskSchema) -> bool:
    """检查任务类型是否支持业务输出"""
    for event in task_schema.events:
        if event.eventType in ['node.succeeded', 'node.approved'] and 'outputs' in event.payload:
            return True
    return False

def is_long_running(task_schema: TaskSchema) -> bool:
    """检查任务类型是否为长期运行"""
    return any(action.action == 'stop' for action in task_schema.actions)
```

---

## 执行器架构

### ExecutorRegistry

系统级注册表，映射任务类型到执行器实现：

```python
class ExecutorRegistry:
    def __init__(self):
        self._executors: Dict[str, Type[Executor]] = {}

    def register(self, task_type: str, executor_class: Type[Executor]):
        """注册执行器"""
        self._executors[task_type] = executor_class

    def get_executor(self, task_type: str) -> Type[Executor]:
        """获取执行器"""
        return self._executors.get(task_type)

# 注册内置执行器
executor_registry = ExecutorRegistry()
executor_registry.register('pyspark', PySparkExecutor)
executor_registry.register('sql', SqlExecutor)
executor_registry.register('approval', ApprovalExecutor)
executor_registry.register('streaming', StreamingExecutor)
executor_registry.register('trigger', TriggerExecutor)
```

### Executor 接口

```python
from abc import ABC, abstractmethod

class Executor(ABC):
    def __init__(self, task_schema: TaskSchema):
        self.task_schema = task_schema

    @abstractmethod
    async def execute(self, action: str, payload: dict):
        """
        执行任务行为

        Args:
            action: 行为名称（如 start, stop）
            payload: 输入 payload
        """
        pass

    @abstractmethod
    async def publish_event(self, event_type: str, payload: dict):
        """
        发布事件

        Args:
            event_type: 事件类型（如 node.started）
            payload: 事件 payload
        """
        pass
```

### PySparkExecutor 示例

```python
class PySparkExecutor(Executor):
    async def execute(self, action: str, payload: dict):
        if action == 'start':
            await self._start(payload)
        else:
            raise ValueError(f"Unsupported action: {action}")

    async def _start(self, payload: dict):
        node_id = payload['nodeId']
        correlation_id = payload['correlationId']
        config = payload['config']
        inputs = payload.get('inputs', {})

        # 1. 发布 node.started
        spark_job_id = await self._submit_spark_job(config, inputs)
        await self.publish_event('node.started', {
            'nodeId': node_id,
            'correlationId': correlation_id,
            'timestamp': int(time.time() * 1000),
            'executorJobId': spark_job_id
        })

        # 2. 等待任务完成
        result = await self._wait_for_completion(spark_job_id)

        # 3. 发布 node.succeeded 或 node.failed
        if result.success:
            await self.publish_event('node.succeeded', {
                'nodeId': node_id,
                'correlationId': correlation_id,
                'timestamp': int(time.time() * 1000),
                'executorJobId': spark_job_id,
                'duration': result.duration,
                'outputs': result.outputs
            })
        else:
            await self.publish_event('node.failed', {
                'nodeId': node_id,
                'correlationId': correlation_id,
                'timestamp': int(time.time() * 1000),
                'executorJobId': spark_job_id,
                'duration': result.duration,
                'error': {
                    'message': result.error_message,
                    'stackTrace': result.stack_trace
                }
            })
```

---

## 扩展机制

### 注册自定义 TaskSchema

用户可以注册自定义任务类型：

```yaml
# 注册自定义任务类型
POST /api/v1/task-schemas
Content-Type: application/json

{
  "type": "custom_ml_training",
  "description": "自定义机器学习训练任务",
  "category": "computation",
  "actions": [
    {
      "action": "start",
      "description": "启动训练任务",
      "triggeredBy": "node.start_requested",
      "payload": {
        "nodeId": "string",
        "correlationId": "string",
        "config": {
          "modelType": "string",
          "hyperparameters": "object"
        },
        "inputs": {
          "trainingDataPath": "string",
          "validationDataPath": "string"
        }
      }
    }
  ],
  "events": [
    {
      "eventType": "node.started",
      "producedBy": "start",
      "payload": {
        "nodeId": "string",
        "correlationId": "string",
        "timestamp": "integer",
        "executorJobId": "string"
      }
    },
    {
      "eventType": "node.training_progress",
      "producedBy": "start",
      "payload": {
        "nodeId": "string",
        "correlationId": "string",
        "timestamp": "integer",
        "metrics": {
          "epoch": "integer",
          "loss": "number",
          "accuracy": "number"
        }
      }
    },
    {
      "eventType": "node.succeeded",
      "producedBy": "start",
      "payload": {
        "nodeId": "string",
        "correlationId": "string",
        "timestamp": "integer",
        "duration": "integer",
        "outputs": {
          "modelPath": "string",
          "finalAccuracy": "number"
        }
      }
    }
  ],
  "executionConfigSchema": {
    "type": "object",
    "required": ["modelType"],
    "properties": {
      "modelType": {
        "type": "string",
        "enum": ["cnn", "lstm", "transformer"]
      },
      "hyperparameters": {
        "type": "object"
      }
    }
  }
}
```

### 注册自定义 Executor

```python
# 实现自定义执行器
class CustomMLTrainingExecutor(Executor):
    async def execute(self, action: str, payload: dict):
        if action == 'start':
            await self._start_training(payload)

    async def _start_training(self, payload: dict):
        # 实现训练逻辑
        ...

        # 定期发布进度事件
        await self.publish_event('node.training_progress', {
            'nodeId': node_id,
            'correlationId': correlation_id,
            'timestamp': int(time.time() * 1000),
            'metrics': {
                'epoch': current_epoch,
                'loss': current_loss,
                'accuracy': current_accuracy
            }
        })

        # 训练完成后发布成功事件
        await self.publish_event('node.succeeded', {
            'nodeId': node_id,
            'correlationId': correlation_id,
            'timestamp': int(time.time() * 1000),
            'duration': duration,
            'outputs': {
                'modelPath': model_path,
                'finalAccuracy': final_accuracy
            }
        })

# 注册执行器
executor_registry.register('custom_ml_training', CustomMLTrainingExecutor)
```

---

## 设计模式总结

### 1. 契约式设计

- TaskSchema 是接口契约，定义行为和事件的 payload 结构
- 执行器实现契约，负责执行逻辑和事件发布
- Node 使用契约，提供配置和输入数据

### 2. 事件驱动架构

- 所有数据流通过事件传递
- 松耦合：节点通过事件名称订阅，无需直接依赖
- 可扩展：新增事件类型不影响现有节点

### 3. 分层解耦

- **TaskSchema 层**：定义能力契约（what can be done）
- **Executor 层**：实现执行逻辑（how to do）
- **Node 层**：提供配置和数据（what to do）

### 4. 配置即代码

- TaskSchema 通过 YAML/JSON 定义，易于理解和版本管理
- TaskDefinition 可选复用配置，减少重复
- Node 内联或引用配置，灵活性高

### 5. 渐进式增强

- 最简模型：只需 actions 和 events
- 可选增强：添加自定义事件、指标、状态查询
- 扩展友好：注册自定义 TaskSchema 和 Executor

---

## 与其他领域模型的关系

### TaskSchema → TaskDefinition

- **关系**：TaskDefinition 引用一个 TaskSchema
- **作用**：TaskDefinition 提供可复用的配置模板，减少 Node 中的重复配置
- **约束**：TaskDefinition.taskConfig.config 必须符合 TaskSchema.executionConfigSchema

### TaskSchema → Node

- **关系**：Node 通过 taskConfig.taskType 间接引用 TaskSchema
- **作用**：Node 提供实际的配置和输入数据
- **约束**：
  - Node.taskConfig.config 必须符合 TaskSchema.executionConfigSchema
  - Node.startPayload.inputs 结构由用户自由定义（TaskSchema 只声明字段存在性）

### TaskSchema → Event

- **关系**：TaskSchema.events 定义任务会产生的事件类型
- **作用**：事件是节点间协作的媒介
- **流程**：
  1. 执行器发布事件（根据 TaskSchema.events）
  2. Event Bus 分发事件
  3. ControlFlowEngine 评估订阅该事件的节点的 startWhen
  4. 满足条件的节点被触发

### TaskSchema → ExecutorRegistry

- **关系**：ExecutorRegistry 映射 taskType → ExecutorClass
- **作用**：根据 Node.taskConfig.taskType 查找对应的执行器
- **解耦**：TaskSchema 不包含执行器信息，执行器是系统配置

---

## FAQ

### Q1: TaskSchema 和 TaskDefinition 有什么区别？

**TaskSchema**：

- 任务类型的元定义（类定义）
- 定义"这类任务能做什么"（能力契约）
- 全局唯一，由平台或用户注册
- 例如：`pyspark`、`sql`、`approval`

**TaskDefinition**：

- 可复用的配置模板（实例配置）
- 引用一个 TaskSchema 并提供具体配置
- 可选：用于减少 Node 中的重复配置
- 例如：`pyspark_etl_template`（配置了 mainFile、driverMemory 等）

### Q2: 为什么 TaskSchema 不包含 executor 字段？

**设计原则**：分离关注点

- **TaskSchema** = 能力契约（what can be done）
- **Executor** = 实现细节（how to do）

**实际架构**：

- **ExecutorRegistry**：系统级注册表，映射 taskType → ExecutorClass
- **运行时**：根据 Node.taskConfig.taskType 查找执行器

**好处**：

- TaskSchema 可以独立定义和版本管理
- 执行器可以独立实现和部署
- 支持多种执行器实现同一 TaskSchema（如本地执行器、远程执行器）

### Q3: 如何知道一个任务类型支持哪些能力？

通过检查 TaskSchema 的 actions 和 events 结构推断：

1. **支持业务输入**：action.payload 包含 `inputs` 字段
2. **支持业务输出**：event.payload 包含 `outputs` 字段
3. **支持停止**：存在 `stop` action
4. **支持重启**：存在 `restart` action
5. **特殊事件模型**：产生非标准事件（如 node.approved）

平台可提供工具函数辅助推断（见 "能力推断" 章节）。

### Q4: inputs 和 outputs 的结构如何定义？

**设计理念**：自由结构，按需定义

- **TaskSchema**：只声明 inputs/outputs 字段的存在性，不约束具体结构
- **用户脚本**：定义实际结构和内容
  - PySpark 脚本通过 `sys.stdout` 输出 JSON 格式的 outputs
  - SQL 执行器根据查询结果填充 outputs
- **TaskDefinition（可选）**：可以文档化预期结构，帮助用户理解

**示例**：

```yaml
# TaskSchema: 只声明字段存在
events:
  - eventType: "node.succeeded"
    payload:
      outputs: object  # 自由结构

# TaskDefinition: 可选文档化
taskDefinition:
  expectedOutputs:
    output_path: string
    record_count: integer

# 用户脚本: 实际填充
# extract.py
outputs = {
    'output_path': 's3://bucket/output',
    'record_count': 1000
}
print(json.dumps({'outputs': outputs}))
```

### Q5: Trigger 任务类型有什么特殊之处？

**Trigger 是简单的内置任务类型**：

1. **用途**：流水线入口节点，接收外部参数并透传
2. **特点**：
   - 无需执行器（或有极简执行器）
   - 头节点（无 startWhen）
   - 接收 params，立即发布 node.started 事件
   - params 透传到 node.started.payload
3. **触发方式**：通过外部 API 调用流水线时提供 params
4. **设计理念**：将外部触发统一为事件模型，下游节点通过事件订阅获取参数

### Q6: 为什么审批任务不产生 node.succeeded 事件？

**特定事件模型**：

审批任务有三种可能的结果：

- **approved**：审批通过
- **rejected**：审批拒绝
- **timeout**：审批超时

使用 `node.approved`、`node.rejected`、`node.timeout` 比强制使用 `node.succeeded` 更语义化。

**下游节点可以精确订阅**：

```yaml
# 只在审批通过时继续
startWhen: "event:manual_review.approved"

# 审批拒绝时发送通知
startWhen: "event:manual_review.rejected"

# 审批超时时升级处理
startWhen: "event:manual_review.timeout"
```

### Q7: 如何定义自定义任务类型？

1. **定义 TaskSchema**：

   - 定义 actions 和 events
   - 定义 executionConfigSchema
   - 通过 API 注册
2. **实现 Executor**：

   - 继承 Executor 接口
   - 实现 execute() 方法
   - 调用 publish_event() 发布事件
3. **注册 Executor**：

   - 在 ExecutorRegistry 中注册

详见 "扩展机制" 章节的示例。

---

## 参考资料

- [Pipeline 领域模型](./Pipeline.md)
- [Event 领域模型](./Event.md)
- [TaskDefinition 领域模型](./TaskDefinition.md)
- [事件驱动架构设计](../架构设计/001-event-driven-execution.md)
