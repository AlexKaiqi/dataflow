# Event（事件）

## 1. 概述

**事件（Event）** 是数据流平台中节点状态变化的通知机制，用于驱动流水线的编排执行。任务在执行过程中会自动产生标准事件，其他节点通过订阅这些事件来触发自己的行为。

### 核心职责

1. **状态通知**：任务执行时自动产生状态变化事件（started、completed、failed 等）
2. **流程触发**：作为节点 `xxxWhen` 表达式的触发源，驱动流程编排
3. **松耦合协作**：发布者和订阅者通过事件名称解耦，无需直接依赖

### 使用场景

- **节点编排**：在 `startWhen`、`stopWhen`、`restartWhen`、`retryWhen`、`alertWhen` 中订阅事件
- **跨流水线协作**：订阅其他流水线的事件实现协同
- **外部集成**：订阅外部系统事件（如 Kafka 消息、Webhook 触发）
- **定时触发**：订阅定时器事件（cron 表达式）

---

## 2. 事件分类

### 2.1 节点生命周期事件

节点在执行过程中根据任务类型自动产生不同的事件。

**事件命名规则**：`{node_id}.{event_name}`

- `node_id`：节点在流水线中的唯一标识
- `event_name`：事件名称（小写，使用下划线）

#### 批处理任务（PySpark、SQL、Ray 等）

| 事件名称       | 触发时机       | 说明                     |
| -------------- | -------------- | ------------------------ |
| `started`    | 任务开始执行   | 任务启动时立即发布       |
| `completed`  | 任务执行成功   | 任务成功完成时发布       |
| `failed`     | 任务执行失败   | 任务失败时发布           |

**示例**：

```yaml
nodes:
  - id: extract_data
    type: task
    taskDefinition:
      ref: "com.company.tasks:pyspark_extractor:1.0.0"  # PySpark 任务
    startWhen: "event:pipeline.started"
  
  - id: transform_data
    type: task
    taskDefinition:
      ref: "com.company.tasks:sql_transform:1.0.0"  # SQL 任务
    # 订阅上游的 completed 事件
    startWhen: "event:extract_data.completed"
```

#### 流处理任务（Streaming）

| 事件名称       | 触发时机       | 说明                     |
| -------------- | -------------- | ------------------------ |
| `started`    | 任务开始执行   | 任务启动时立即发布       |
| `completed`  | 任务执行成功   | 任务成功完成时发布       |
| `failed`     | 任务执行失败   | 任务失败时发布           |
| `stopped`    | 任务被停止     | 流处理任务停止时发布     |
| `restarted`  | 任务被重启     | 流处理任务重启时发布     |

**示例**：

```yaml
nodes:
  - id: kafka_consumer
    type: task
    taskDefinition:
      ref: "com.company.tasks:kafka_streaming:1.0.0"
    startWhen: "event:pipeline.started"
    startMode: repeat
    # 手动停止或上游停止时停止
    stopWhen: "event:manual.stop || event:upstream.stopped"
    # 配置更新时重启
    restartWhen: "event:config.updated"
```

#### 审批任务（Approval）

| 事件名称       | 触发时机       | 说明                     |
| -------------- | -------------- | ------------------------ |
| `started`    | 审批流程开始   | 发起审批请求时发布       |
| `approved`   | 审批通过       | 人工审批通过时发布       |
| `rejected`   | 审批拒绝       | 人工审批拒绝时发布       |
| `timeout`    | 审批超时       | 审批超时时发布           |

**说明**：审批任务没有 `completed` 和 `failed` 事件，而是用 `approved`、`rejected`、`timeout` 表示最终状态。

**示例**：

```yaml
nodes:
  - id: quality_check
    type: task
    startWhen: "event:extract_data.completed"

  - id: manual_review
    type: task
    taskDefinition:
      ref: "com.company.tasks:approval:1.0.0"
    # 数据质量不达标时需要审批
    startWhen: "event:quality_check.completed && {{ quality_check.score < 0.9 }}"

  - id: load_data
    type: task
    # 质量达标或审批通过后继续
    startWhen: "(event:quality_check.completed && {{ quality_check.score >= 0.9 }}) || event:manual_review.approved"
```

### 2.2 流水线事件

流水线级别的生命周期事件：

| 事件名称       | 触发时机         | 说明                 |
| -------------- | ---------------- | -------------------- |
| `pipeline.started`   | 流水线开始执行   | 流水线启动时发布     |
| `pipeline.completed` | 流水线执行成功   | 所有节点完成时发布   |
| `pipeline.failed`    | 流水线执行失败   | 任一节点失败时发布   |
| `pipeline.paused`    | 流水线被暂停     | 手动暂停时发布       |
| `pipeline.resumed`   | 流水线被恢复     | 从暂停恢复时发布     |
| `pipeline.cancelled` | 流水线被取消     | 手动取消时发布       |

**使用示例**：

```yaml
nodes:
  - id: init_resources
    type: task
    # 流水线启动时执行初始化
    startWhen: "event:pipeline.started"
  
  - id: cleanup
    type: task
    # 流水线完成或失败时清理资源
    startWhen: "event:pipeline.completed || event:pipeline.failed"
```

### 2.3 外部事件

外部事件由外部系统或用户发布到平台，供流水线节点订阅。

**特点**：

- **事件源标识**：使用外部系统名称作为事件源（如 `kafka.message_received`、`webhook.data_ready`）
- **执行绑定**：外部事件需要指定关联的流水线执行实例
- **安全性**：外部系统需要经过身份验证才能发布事件

#### 典型场景：外部审批系统

```yaml
nodes:
  - id: data_quality_check
    type: task
    startWhen: "event:extract_data.completed"

  - id: wait_for_approval
    type: task
    taskDefinition:
      ref: "com.company.tasks:external_approval:1.0.0"
    # 数据质量不达标时需要外部审批
    startWhen: "event:data_quality_check.completed && {{ data_quality_check.score < 0.9 }}"

  - id: load_data
    type: task
    # 等待外部审批系统发布 approved 事件
    startWhen: "event:external_approval.approved"
```

**执行流程**：

1. 流水线执行到需要外部审批的节点
2. 系统发送审批请求到外部审批系统
3. 用户在外部系统完成审批
4. 外部系统发布审批结果事件（`external_approval.approved` 或 `external_approval.rejected`）
5. 流水线根据接收到的事件继续执行

**事件负载示例**：

审批通过事件：

```json
{
  "eventType": "external_approval.approved",
  "payload": {
    "approver": "alice@company.com",
    "comment": "数据质量符合要求",
    "approvedAt": "2025-01-15T10:30:00Z"
  }
}
```

审批拒绝事件：

```json
{
  "eventType": "external_approval.rejected",
  "payload": {
    "approver": "bob@company.com",
    "reason": "数据质量不达标",
    "rejectedAt": "2025-01-15T10:25:00Z"
  }
}
```

### 2.4 定时事件

由调度器产生的时钟事件：

**Cron 表达式**：

```yaml
nodes:
  - id: daily_report
    type: task
    # 每天凌晨 2 点触发
    startWhen: "cron:0 2 * * *"
    startMode: repeat  # 持续触发

  - id: health_check
    type: task
    # 每 5 分钟检查一次
    startWhen: "cron:*/5 * * * *"
    startMode: repeat
```

**等价事件表示**（系统内部转换）：

```yaml
# cron 表达式会被系统转换为定时事件
startWhen: "event:timer.cron_0_2_*_*_*"
```

---

## 3. 事件引用（Event Reference）

### 3.1 引用语法

在节点的 `xxxWhen` 表达式中通过以下语法引用事件：

```text
event:<source>.<event_name>
```

**语法说明**：

- `event:` - 固定前缀，标识这是一个事件引用
- `<source>` - 事件源：
  - **节点 ID**：引用同一流水线内节点的事件（如 `extract_data`）
  - **pipeline**：引用流水线级别事件（如 `pipeline.started`）
  - **外部系统名称**：引用外部事件（如 `kafka`、`webhook`）
- `<event_name>` - 事件名称：
  - 节点事件：`started`、`completed`、`failed`、`stopped`、`approved` 等
  - 流水线事件：`started`、`completed`、`failed` 等

### 3.2 基础引用示例

#### 顺序执行

```yaml
nodes:
  - id: extract_data
    type: task
    startWhen: "event:pipeline.started"

  - id: transform_data
    type: task
    # 等待上游完成
    startWhen: "event:extract_data.completed"

  - id: load_data
    type: task
    # 等待上游完成
    startWhen: "event:transform_data.completed"
```

#### 并行执行

```yaml
nodes:
  - id: extract_source_a
    type: task
    startWhen: "event:pipeline.started"

  - id: extract_source_b
    type: task
    startWhen: "event:pipeline.started"

  # 两个节点并行执行，都订阅 pipeline.started
```

#### 多路汇聚（AND）

```yaml
nodes:
  - id: extract_source_a
    type: task
    startWhen: "event:pipeline.started"

  - id: extract_source_b
    type: task
    startWhen: "event:pipeline.started"

  - id: merge_data
    type: task
    # 等待两个上游都完成（逻辑与）
    startWhen: "event:extract_source_a.completed && event:extract_source_b.completed"
```

#### 多路选择（OR）

```yaml
nodes:
  - id: process_primary
    type: task
    startWhen: "event:check_primary.completed"

  - id: process_backup
    type: task
    startWhen: "event:check_backup.completed"

  - id: aggregate
    type: task
    # 任一上游完成即可启动（逻辑或）
    startWhen: "event:process_primary.completed || event:process_backup.completed"
```

---

## 4. 订阅模式（Subscription Mode）

### 4.1 Once 模式（默认）

**行为**：事件触发一次后自动取消订阅

**适用场景**：批处理任务、一次性执行的节点

```yaml
- id: batch_etl
  type: task
  startWhen: "event:upstream.completed"
  # startMode 默认为 once
```

**执行流程**：

1. 节点创建时订阅 `upstream.completed` 事件
2. 事件触发后启动任务
3. 任务启动后自动取消订阅
4. 后续的 `upstream.completed` 事件不再触发该节点

### 4.2 Repeat 模式

**行为**：保持订阅，持续响应事件

**适用场景**：周期性任务、流处理任务、需要多次触发的节点

```yaml
- id: hourly_aggregation
  type: task
  startWhen: "cron:0 * * * *"
  startMode: repeat  # 显式指定 repeat 模式
```

**执行流程**：

1. 节点创建时订阅定时事件
2. 每次事件触发时启动任务（如果上次执行已完成）
3. 订阅关系持续保持
4. 直到节点被删除或流水线停止

### 4.3 模式选择指南

| 场景                 | 推荐模式 | 示例                                  |
| -------------------- | -------- | ------------------------------------- |
| 顺序执行的批处理任务 | once     | ETL 流水线中的各个节点                |
| 定时任务             | repeat   | 每日报表、每小时聚合                  |
| 流处理任务           | repeat   | Kafka 消费者、实时数据处理            |
| 外部事件监听         | repeat   | Webhook 监听、文件监听                |
| 人工审批             | once     | 审批通过后继续执行                    |

---

## 5. 事件消息体结构

### 5.1 通用消息结构

所有事件都遵循统一的消息格式：

```json
{
  "eventId": "uuid",              // 事件唯一标识符
  "eventType": "string",          // 事件类型（如 "TaskCompleted"）
  "timestamp": "ISO8601",         // 事件发生时间
  "source": "string",             // 事件源（节点 ID 或系统名称）
  "payload": {                    // 事件负载
    // 事件特定的数据
  }
}
```

### 5.2 节点事件 Payload

#### started 事件

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "TaskStarted",
  "timestamp": "2025-01-15T10:00:00Z",
  "source": "extract_data",
  "payload": {
    "nodeId": "extract_data",
    "pipelineId": "com.company.pipelines:user_data_etl",
    "executionId": "exec_123",
    "taskDefinition": "com.company.tasks:spark_extractor:1.0.0"
  }
}
```

#### completed 事件

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440001",
  "eventType": "TaskCompleted",
  "timestamp": "2025-01-15T10:05:30Z",
  "source": "extract_data",
  "payload": {
    "nodeId": "extract_data",
    "pipelineId": "com.company.pipelines:user_data_etl",
    "executionId": "exec_123",
    "duration": 330,  // 执行时长（秒）
    "outputs": {      // 输出变量
      "output_path": "s3://bucket/data/2025-01-15",
      "row_count": 1000000,
      "quality_score": 0.95
    }
  }
}
```

#### failed 事件

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440002",
  "eventType": "TaskFailed",
  "timestamp": "2025-01-15T10:03:15Z",
  "source": "transform_data",
  "payload": {
    "nodeId": "transform_data",
    "pipelineId": "com.company.pipelines:user_data_etl",
    "executionId": "exec_123",
    "error": {
      "type": "DataQualityError",
      "message": "Invalid data format in column 'age'",
      "code": "DQ_001"
    },
    "attempts": 2  // 已重试次数
  }
}
```

### 5.3 审批事件 Payload

#### approved 事件

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440003",
  "eventType": "ApprovalApproved",
  "timestamp": "2025-01-15T10:30:00Z",
  "source": "manual_review",
  "payload": {
    "nodeId": "manual_review",
    "pipelineId": "com.company.pipelines:user_data_etl",
    "executionId": "exec_123",
    "approver": "alice@company.com",
    "comment": "数据质量符合要求，批准继续处理",
    "approvedAt": "2025-01-15T10:30:00Z"
  }
}
```

#### rejected 事件

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440004",
  "eventType": "ApprovalRejected",
  "timestamp": "2025-01-15T10:25:00Z",
  "source": "manual_review",
  "payload": {
    "nodeId": "manual_review",
    "pipelineId": "com.company.pipelines:user_data_etl",
    "executionId": "exec_123",
    "approver": "bob@company.com",
    "reason": "数据质量不达标，需要重新处理",
    "rejectedAt": "2025-01-15T10:25:00Z"
  }
}
```

---

## 6. 与其他概念的关系

### 6.1 Event vs Variable

| 维度       | Event（事件）              | Variable（变量）       |
| ---------- | -------------------------- | ---------------------- |
| **用途**   | 通知状态变化，触发行为     | 传递数据               |
| **时效性** | 瞬时的，发生后消失         | 持久的，可多次访问     |
| **传播**   | 发布-订阅，一对多          | 直接引用，一对一       |
| **使用**   | 在 `xxxWhen` 中订阅       | 在 `inputBindings` 中引用 |

**配合使用**：

```yaml
- id: check_quality
  type: task
  startWhen: "event:extract_data.completed"
  inputBindings:
    # 引用上游节点的输出变量
    data_path: "{{ extract_data.output_path }}"

- id: load_data
  type: task
  # 订阅事件 + 访问输出变量
  startWhen: "event:check_quality.completed && {{ check_quality.score > 0.9 }}"
  inputBindings:
    # 引用变量
    quality_score: "{{ check_quality.score }}"
```

### 6.2 Event vs Expression

- **Event**：是表达式中的触发源（`event:node.completed`）
- **Expression**：是包含事件引用和条件判断的完整逻辑

```yaml
# Event 引用
"event:node.completed"

# Expression（事件 + 条件）
"event:node.completed && {{ node.score > 0.9 }}"

# Expression（多事件汇聚 + 条件）
"event:a.completed && event:b.completed && {{ a.count + b.count > 1000 }}"
```

### 6.3 Event vs TaskDefinition

- **TaskDefinition**：定义任务的行为能力，声明会产生哪些事件
- **Event**：任务执行时实际产生的状态通知

**关系**：

```yaml
# TaskDefinition 声明
TaskDefinition:
  namespace: "com.company.tasks"
  name: "data_processor"
  type: "pyspark"
  # 隐式支持的事件：started, completed, failed

# PipelineDefinition 中订阅这些事件
PipelineDefinition:
  nodes:
    - id: process_data
      taskDefinition:
        ref: "com.company.tasks:data_processor:1.0.0"
      startWhen: "event:pipeline.started"
    
    - id: next_step
      # 订阅 process_data 产生的 completed 事件
      startWhen: "event:process_data.completed"
```

---

## 7. 术语对照表

| 中文         | 英文                    | 说明                                 |
| ------------ | ----------------------- | ------------------------------------ |
| 事件         | Event                   | 节点状态变化的通知消息               |
| 事件引用     | Event Reference         | 在表达式中引用事件的语法             |
| 事件源       | Event Source            | 产生事件的对象（节点、流水线、系统） |
| 事件订阅     | Event Subscription      | 节点注册对特定事件的监听             |
| 订阅模式     | Subscription Mode       | once（一次）或 repeat（持续）        |
| 发布-订阅    | Publish-Subscribe       | 事件驱动的通信模式                   |
| 节点事件     | Node Event              | 节点执行时产生的事件                 |
| 流水线事件   | Pipeline Event          | 流水线级别的生命周期事件             |
| 外部事件     | External Event          | 来自外部系统的事件                   |
| 定时事件     | Timer Event             | 由调度器产生的时钟事件               |
| 事件负载     | Event Payload           | 事件消息中携带的业务数据             |
| 多路汇聚     | Multi-way Join          | 多个事件都到达时触发（AND）          |
| 多路选择     | Multi-way Choice        | 任一事件到达时触发（OR）             |

