# Node（节点）

## 概述

Node 是 Pipeline 中的**编排单元**，代表工作流中的一个执行点。Node 通过引用 TaskDefinition 并配置执行控制逻辑，将可复用的任务模板实例化到具体的业务流程中。

### 核心职责

- **引用任务定义**：通过 TaskDefinition 引用声明"要做什么"
- **绑定输入变量**：指定任务输入从哪里来（上游输出、Pipeline 输入、常量）
- **控制执行时机**：通过表达式（startWhen、stopWhen 等）定义"何时执行"
- **定义依赖关系**：通过事件订阅隐式定义与其他 Node 的依赖关系

### 设计原则

**Node 是 TaskDefinition 的实例化**：

- **TaskDefinition**：定义"能做什么"（可复用的任务模板）
- **Node**：定义"何时做、如何做"（任务在 Pipeline 中的实例化配置）

**类比**：

```text
TaskDefinition = 函数定义
  def process_data(input_path: str, quality_threshold: float):
      # 处理逻辑
      return output_path, quality_score

Node = 函数调用 + 调用条件
  if event:upstream.completed and context.data_ready:
      result = process_data(
          input_path = upstream.output_path,      # 输入绑定
          quality_threshold = 0.9                 # 常量
      )
```

## Node 与 TaskDefinition 的关系

| 维度 | TaskDefinition | Node |
|------|---------------|------|
| **定义** | 可复用的任务模板 | 任务在 Pipeline 中的实例 |
| **位置** | 独立存在，可跨 Pipeline 复用 | 存在于 PipelineDefinition.nodes 中 |
| **职责** | "能做什么"：定义行为、输入输出 | "何时做、如何做"：控制执行时机 |
| **变量** | 声明需要什么输入（inputVariables） | 绑定输入从哪里来（inputBindings） |
| **事件** | 定义产生什么事件（outputEvents） | 订阅哪些事件（startWhen 中引用） |
| **复用性** | 可被多个 Pipeline 的多个 Node 引用 | 仅属于特定 Pipeline |
| **修改影响** | 影响所有引用该定义的 Node | 仅影响当前 Pipeline |

## 领域模型结构

```yaml
Node:
  # 唯一标识
  id: string                           # 节点 ID，在 Pipeline 内唯一
  name: string                         # 节点名称（可读）
  
  # 任务引用
  taskDefinition: TaskDefinitionRef    # 引用的任务定义
    # 方式1：引用已存在的 TaskDefinition
    ref: string                        # "namespace:name:version"
    
    # 方式2：内联定义（仅用于该 Pipeline）
    inline: TaskDefinition
  
  # 输入绑定
  inputBindings: Map[string, Expression]
    # 将 TaskDefinition 的 inputVariables 绑定到具体值
    # key: 输入变量名（来自 TaskDefinition.inputVariables）
    # value: 表达式（常量、上游输出、Pipeline 输入）
  
  # 执行控制表达式
  startWhen: Expression               # 何时启动（必填）
  stopWhen: Expression?               # 何时停止（仅流处理任务）
  restartWhen: Expression?            # 何时重启（仅流处理任务）
  retryWhen: Expression?              # 何时重试（批处理任务）
  alertWhen: Expression?              # 何时告警（可选）
```

## 核心字段说明

### taskDefinition（任务定义引用）

Node 通过 `taskDefinition` 字段引用一个 TaskDefinition，有两种方式：

#### 方式 1：引用全局 TaskDefinition

```yaml
nodes:
  - id: data_transform
    taskDefinition:
      ref: "com.company:spark_etl:1.0.0"
    inputBindings:
      input_path: "{{ pipeline.input.data_source }}"
      batch_size: 1000
    startWhen: "event:extract.completed"
```

#### 方式 2：内联定义 TaskDefinition

```yaml
nodes:
  - id: simple_task
    taskDefinition:
      inline:
        type: sql
        query: "SELECT * FROM table"
        inputVariables: []
        outputVariables:
          - name: row_count
            type: integer
    startWhen: "event:pipeline.started"
```

**使用建议**：

- **引用方式**：适用于可复用的任务，支持版本管理和跨 Pipeline 共享
- **内联方式**：适用于简单的一次性任务，无需复用

### inputBindings（输入绑定）

`inputBindings` 将 TaskDefinition 声明的输入变量绑定到具体值：

```yaml
# TaskDefinition 声明
inputVariables:
  - name: data_path
    type: string
  - name: quality_threshold
    type: number

# Node 绑定
inputBindings:
  data_path: "{{ upstream_node.output_path }}"     # 引用上游输出
  quality_threshold: 0.9                            # 常量值
```

**绑定来源**：

- 上游 Node 的输出变量：`{{ node_id.variable_name }}`
- Pipeline 的输入参数：`{{ pipeline.input.param_name }}`
- 常量值：直接写值（字符串、数字、布尔等）
- 表达式计算：`{{ node_a.count + node_b.count }}`

### startWhen（启动控制）

`startWhen` 定义 Node 何时开始执行，是**唯一必填**的控制表达式：

```yaml
# 等待单个上游完成
startWhen: "event:extract.completed"

# 等待多个上游完成（Join）
startWhen: "event:branch_a.completed && event:branch_b.completed"

# 条件分支：高质量数据直接处理
startWhen: "event:quality_check.completed && {{ quality_check.score > 0.9 }}"

# 条件分支：低质量数据需要审批
startWhen: "event:quality_check.completed && {{ quality_check.score <= 0.9 }}"

# 两条路径汇聚
startWhen: "event:high_quality_path.completed || event:approval.approved"
```

**事件来源**：

- 其他 Node 产生的事件：`event:node_id.event_name`
- Pipeline 级别事件：`event:pipeline.started`
- 定时触发：`cron:0 2 * * *`

详见 [Expression.md](./Expression.md) 和 [Event.md](./Event.md)

### stopWhen（停止控制）

`stopWhen` 定义流处理任务何时停止，**仅适用于 streaming 类型的 TaskDefinition**：

```yaml
stopWhen: "event:upstream_source.stopped || {{ error_rate > 0.1 }}"
```

### restartWhen（重启控制）

`restartWhen` 定义流处理任务何时重启，**仅适用于 streaming 类型的 TaskDefinition**：

```yaml
restartWhen: "event:config_updated.triggered"
```

### retryWhen（重试控制）

`retryWhen` 定义任务失败后何时重试，**不适用于 approval 类型的 TaskDefinition**：

```yaml
retryWhen: "{{ attempts < 3 && error_type == 'transient' }}"
```

### alertWhen（告警控制）

`alertWhen` 定义何时发送告警，不影响任务执行：



## 完整示例

### 示例 1：批处理 ETL 流水线（事件驱动）

```yaml
nodes:
  # 起始节点：通过 pipeline.started 事件触发
  - id: extract
    name: "数据提取"
    taskDefinition:
      ref: "com.company:spark_extract:1.0.0"
    inputBindings:
      source_table: "{{ pipeline.input.source_table }}"
      partition_date: "{{ pipeline.input.date }}"
    startWhen: "event:pipeline.started"
  
  # 等待上游 extract 完成
  - id: transform
    name: "数据转换"
    taskDefinition:
      ref: "com.company:spark_transform:1.0.0"
    inputBindings:
      input_path: "{{ extract.output_path }}"
    startWhen: "event:extract.completed"
    retryWhen: "{{ attempts < 3 }}"
  
  # Join：等待两个上游都完成
  - id: quality_check
    name: "质量检查"
    taskDefinition:
      ref: "com.company:quality_checker:1.0.0"
    inputBindings:
      data_path: "{{ transform.output_path }}"
    startWhen: "event:transform.completed"
  
  # 条件分支：高质量路径
  - id: publish_high_quality
    name: "发布高质量数据"
    taskDefinition:
      ref: "com.company:data_publisher:1.0.0"
    inputBindings:
      data_path: "{{ transform.output_path }}"
    startWhen: "event:quality_check.completed && {{ quality_check.score > 0.9 }}"
  
  # 条件分支：低质量需要审批
  - id: approval
    name: "低质量数据审批"
    taskDefinition:
      ref: "com.company:approval_task:1.0.0"
    inputBindings:
      reason: "质量评分: {{ quality_check.score }}"
    startWhen: "event:quality_check.completed && {{ quality_check.score <= 0.9 }}"
  
  # 汇聚：两条路径汇聚后通知
  - id: notify
    name: "发送通知"
    taskDefinition:
      ref: "com.company:notifier:1.0.0"
    inputBindings:
      status: "完成"
    startWhen: "event:publish_high_quality.completed || event:approval.approved"
```

**控制流说明**：

- **无显式 Condition/Join/Fork 节点**：通过 `startWhen` 表达式控制
- **Join**：`startWhen: "event:a.completed && event:b.completed"`
- **条件分支**：通过不同的 `startWhen` 条件实现
- **汇聚**：`startWhen: "event:path1.completed || event:path2.completed"`

### 示例 2：流处理任务

```yaml
nodes:
  - id: kafka_consumer
    name: "Kafka 消费者"
    taskDefinition:
      ref: "com.company:kafka_consumer:1.0.0"
    inputBindings:
      topic: "{{ pipeline.input.topic }}"
      consumer_group: "{{ pipeline.input.group }}"
    startWhen: "event:pipeline.started"
    stopWhen: "event:pipeline.stopped || {{ error_rate > 0.1 }}"
    alertWhen: "{{ lag > 10000 }}"
  
  - id: stream_processor
    name: "流处理"
    taskDefinition:
      ref: "com.company:stream_processor:1.0.0"
    inputBindings:
      stream_source: "{{ kafka_consumer.stream_id }}"
    startWhen: "event:kafka_consumer.started"
    stopWhen: "event:kafka_consumer.stopped"
    restartWhen: "event:config_updated.triggered"
```

### 示例 3：审批节点（Approval 是一种 TaskType）

```yaml
nodes:
  - id: data_approval
    name: "数据发布审批"
    taskDefinition:
      ref: "com.company:approval_task:1.0.0"  # Approval 类型的 TaskDefinition
    inputBindings:
      title: "发布数据到生产环境"
      description: "质量评分: {{ quality_check.score }}"
      approvers: ["user1@company.com", "user2@company.com"]
    startWhen: "event:quality_check.completed && {{ quality_check.score < 0.9 }}"
  
  # 等待审批通过才执行
  - id: publish_to_prod
    name: "发布到生产"
    taskDefinition:
      ref: "com.company:publisher:1.0.0"
    inputBindings:
      data_path: "{{ quality_check.output_path }}"
    startWhen: "event:data_approval.approved"
  
  # 审批拒绝则回滚
  - id: rollback
    name: "回滚"
    taskDefinition:
      ref: "com.company:rollback:1.0.0"
    inputBindings:
      data_path: "{{ quality_check.output_path }}"
    startWhen: "event:data_approval.rejected"
```

**Approval TaskDefinition 的特点**：

- **行为**: 只有 `start` 动作（无 `stop`、`restart`、`retry`）
- **事件**: 产生 `approved`、`rejected`、`timeout` 事件
- **输入变量**: `title`, `description`, `approvers`, `timeout`
- **输出变量**: `decision`, `comment`, `approver`

## 不变式（Invariants）

1. **唯一性**：Node.id 在同一 Pipeline 内必须唯一
2. **引用有效性**：`taskDefinition.ref` 引用的 TaskDefinition 必须存在
3. **输入完整性**：TaskDefinition 的所有必填输入变量必须在 `inputBindings` 中绑定
4. **表达式有效性**：所有 `*When` 表达式必须是合法的事件表达式或状态表达式
5. **事件可达性**：`startWhen` 中引用的事件必须由某个 Node 产生（或 Pipeline 产生）
6. **控制流一致性**：
   - `stopWhen`、`restartWhen` 只能用于 streaming TaskType
   - `retryWhen` 不能用于 approval TaskType

## 与其他领域模型的关系

```
┌─────────────────────────────────────────┐
│ PipelineDefinition                       │
│                                          │
│  nodes: Node[]                           │
│    │                                     │
│    └──> Node                             │
│          ├─ taskDefinition ──────────┐   │
│          │                           │   │
│          ├─ inputBindings            │   │
│          │   └─> Expression ─────┐   │   │
│          │                       │   │   │
│          ├─ startWhen: Expression │   │   │
│          ├─ stopWhen?: Expression │   │   │
│          ├─ retryWhen?: Expression│   │   │
│          └─ alertWhen?: Expression│   │   │
│                                   │   │   │
└───────────────────────────────────┼───┼───┘
                                    │   │
              ┌─────────────────────┘   │
              │                         │
              ▼                         ▼
    ┌──────────────────┐     ┌──────────────────┐
    │ TaskDefinition   │     │ Expression       │
    │                  │     │  - event:...     │
    │ - inputVariables │     │  - cron:...      │
    │ - outputVariables│     │  - {{ state }}   │
    │ - behaviors      │     └──────────────────┘
    │ - events         │
    └──────────────────┘
```

**关键关系**：

- **Node → TaskDefinition**（引用）：Node 通过 `taskDefinition.ref` 引用可复用的任务定义
- **Node → Expression**（组合）：Node 通过多种表达式控制执行时机和条件
- **TaskDefinition → VariableDefinition**（组合）：定义输入/输出接口
- **Expression → Node**（引用）：表达式中引用其他 Node 产生的事件或变量
