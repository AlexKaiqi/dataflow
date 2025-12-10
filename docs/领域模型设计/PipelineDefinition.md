# **《PipelineDefinition 流水线定义》**

## 概述

**PipelineDefinition（流水线定义）** 是数据编排系统的核心聚合根，负责定义和管理数据处理流水线的完整结构。它通过组合多个**节点（Node）**——流水线的编排单元，代表工作流中的一个执行点——来编排复杂的数据处理流程，基于事件驱动机制实现灵活的依赖关系表达：节点可以订阅任务完成事件、外部系统事件、定时事件、状态变化事件等，从而支持顺序依赖、条件触发、多路汇聚、超时控制、异常恢复、外部协同等多样化的编排语义。

### 核心职责

1. **控制流编排**：通过 `xxxWhen` 表达式（`startWhen`、`stopWhen`、`retryWhen`、`alertWhen`）控制节点的行为触发时机，实现松耦合的事件驱动执行
2. **数据流管理**：通过 `inputBindings` 中的变量引用显式声明节点间的数据依赖和流向
3. **版本管理**：维护草稿版本（DRAFT）和已发布版本（PUBLISHED），确保生产环境的稳定性和可追溯性
4. **子流水线复用**：提供流水线级别的 `inputVariables` 和 `outputVariables` 接口，使 Pipeline 可以作为 Node 被其他 Pipeline 引用，支持流水线的嵌套组合

## 领域模型结构

```yaml
PipelineDefinition:
  # 唯一标识
  id: string                           # Pipeline ID，全局唯一
  namespace: string                    # 命名空间，如 "com.company.pipelines"
  name: string                         # Pipeline 名称

  # 版本管理
  versions: PipelineVersion[]
    - version: string                  # 版本号："draft" | "1.0.0" | "1.1.0"
      status: enum                     # "DRAFT" | "PUBLISHED"

      # Pipeline 输入/输出接口
      inputVariables: VariableDefinition[]   # Pipeline 的输入参数
      outputVariables: VariableDefinition[]  # Pipeline 的输出结果

      # 节点列表（唯一的编排结构）
      nodes: Node[]                    # 节点（Node）：流水线的编排单元，代表工作流中的一个执行点
        # 节点标识
        - id: string                   # 节点 ID，在当前 Pipeline 版本内唯一，用于引用和定位
                                       # 建议使用有意义的名称，如 "extract_user_data"

          # 节点类型与引用
          type: enum                   # "task" | "pipeline"
          taskDefinition?: TaskDefinitionRef      # type=task 时使用
            ref: string                # 引用已发布的 TaskDefinition："namespace:name:version"
            inline?: TaskDefinition    # 或内联定义（仅用于当前 Pipeline）
          pipelineDefinition?: PipelineDefinitionRef  # type=pipeline 时使用（子流水线）
            ref: string                # 引用已发布的 PipelineDefinition："namespace:name:version"

          # 输入绑定：将定义的 inputVariables 绑定到具体值
          inputBindings: Map[string, Expression]
            # key: 输入变量名（来自 TaskDefinition 或 PipelineDefinition）
            # value: 表达式
            #   - 上游节点输出：{{ node_id.variable_name }}
            #   - Pipeline 输入：{{ pipeline.input.param_name }}
            #   - 常量值：直接值（字符串、数字、布尔等）
            #   - 计算表达式：{{ node_a.count + node_b.count }}

          # 执行控制表达式：定义节点行为触发时机
          startWhen: Expression        # 何时启动（必填）
            # 示例：
            #   - "event:upstream.completed"  # 等待上游完成
            #   - "event:a.completed && event:b.completed"  # 多路汇聚（AND）
            #   - "event:a.completed || event:b.completed"  # 多路选择（OR）
            #   - "event:check.completed && {{ check.score > 0.9 }}"  # 条件分支
            #   - "cron:0 2 * * *"  # 定时触发
          startMode: enum?             # 触发模式："once" | "repeat"（默认 once）
            # once: 触发一次后取消订阅（批处理任务默认）
            # repeat: 保持订阅持续触发（周期性任务、流处理）

          stopWhen: Expression?        # 何时停止（仅 streaming 类型任务）
            # 示例："event:manual.stop || {{ error_rate > 0.1 }}"

          restartWhen: Expression?     # 何时重启（仅 streaming 类型任务）
            # 示例："event:config_updated.triggered"

          retryWhen: Expression?       # 何时重试（批处理任务，不适用于 approval）
            # 示例："event:task.failed && {{ attempts < 3 }}"

          alertWhen: Expression?       # 何时告警（不影响任务执行）
            # 示例："cron:*/5 * * * * && {{ execution_time > 3600 }}"      # 版本元数据
      releaseNotes: string             # 发布说明
      createdAt: Timestamp
      createdBy: string                # 仅 PUBLISHED 版本有值

  # Pipeline 元数据
  metadata:
    owners: string[]                   # 所有者列表
    tags: string[]                     # 标签
    createdAt: Timestamp
```

## 核心字段说明

### nodes（节点列表）

#### 概念

**节点（Node）** 是流水线中的编排单元，代表工作流中的一个执行点。节点通过引用任务定义并配置执行控制逻辑，将可复用的任务模板实例化到具体的业务流程中。

**节点标识（id）**：

- 在 Pipeline 版本内唯一
- 用于节点间的引用（如 `{{ extract_data.output_path }}`）
- 建议使用有意义的名称（如 `extract_user_data`、`validate_quality`），而非抽象编号（如 `node_001`）

**节点是任务定义的实例化**：

- 任务定义（TaskDefinition）：定义"能做什么"（可复用的任务模板）
- 节点（Node）：定义"何时做、如何做"（任务在流水线中的实例化配置）

**类比**：

```text
TaskDefinition = 函数定义
  def process_data(input_path: str, quality_threshold: float):
      # 处理逻辑
      return output_path, quality_score

Node = 函数调用 + 调用条件（id 用于标识这次调用）
  # id: transform_user_data
  if event:upstream.completed and context.data_ready:
      result = process_data(
          input_path = extract_data.output_path,  # 通过 id 引用其他节点
          quality_threshold = 0.9
      )
```

**节点与任务定义的关系**：

| 维度               | 任务定义                         | 节点                              |
| ------------------ | -------------------------------- | --------------------------------- |
| **定义**     | 可复用的任务模板                 | 任务在流水线中的实例              |
| **位置**     | 独立存在，可跨流水线复用         | 存在于流水线定义的 nodes 中       |
| **职责**     | "能做什么"：定义行为、输入输出   | "何时做、如何做"：控制执行时机    |
| **变量**     | 声明需要什么输入（变量定义）     | 绑定输入从哪里来（inputBindings） |
| **事件**     | 定义产生什么事件（outputEvents） | 订阅哪些事件（startWhen 中引用）  |
| **复用性**   | 可被多个流水线的多个节点引用     | 仅属于特定流水线                  |
| **修改影响** | 影响所有引用该定义的节点         | 仅影响当前流水线                  |

#### type（节点类型）

Node 通过 `type` 字段区分引用的是任务还是子流水线：

- `type: task`：引用 TaskDefinition，执行具体的任务逻辑
- `type: pipeline`：引用 PipelineDefinition，将子流水线作为节点嵌套使用

#### 引用定义的方式

##### 方式 1：引用 TaskDefinition（type=task）

```yaml
nodes:
  - id: data_transform
    type: task
    taskDefinition:
      ref: "com.company:spark_etl:1.0.0"
    inputBindings:
      input_path: "{{ pipeline.input.data_source }}"
      batch_size: 1000
    startWhen: "event:extract_source_data.completed"
```

##### 方式 2：引用 PipelineDefinition（type=pipeline，子流水线）

```yaml
nodes:
  - id: sub_etl_pipeline
    type: pipeline
    pipelineDefinition:
      ref: "com.company:common_etl:1.0.0"
    inputBindings:
      source_table: "{{ pipeline.input.source }}"
      target_date: "{{ pipeline.input.date }}"
    startWhen: "event:pipeline.started"
```

**使用建议**：

- **引用 Task**：适用于可复用的任务，支持版本管理和跨 Pipeline 共享
- **引用 Pipeline**：将复杂的流程封装为子流水线，实现流水线的组合和复用

#### inputBindings（输入绑定）

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

#### xxxWhen（行为控制表达式）

节点通过 `xxxWhen` 表达式定义各种行为的触发时机。**所有行为控制表达式都必须订阅至少一个事件**，以实现事件驱动的执行模型。

**表达式类型**：

| 表达式          | 必填 | 适用场景       | 触发机制                | 说明                       |
| --------------- | ---- | -------------- | ----------------------- | -------------------------- |
| `startWhen`   | ✓   | 所有节点       | `event:` 或 `cron:` | 订阅事件或定时触发启动     |
| `stopWhen`    | -    | streaming 任务 | `event:` 或 `cron:` | 订阅事件或定时触发停止     |
| `restartWhen` | -    | streaming 任务 | `event:`              | 订阅事件触发重启           |
| `retryWhen`   | -    | 批处理任务     | `event:task.failed`   | 任务失败时评估重试条件     |
| `alertWhen`   | -    | 所有节点       | `event:` 或 `cron:` | 订阅事件或定时检查告警条件 |

**典型示例**:

```yaml
# startWhen:默认 once 模式(触发一次后取消订阅)
- id: transform_data
  startWhen: "event:extract_source_data.completed"  # 顺序执行,extract 完成后触发一次
  # startMode 省略,默认为 once

# startWhen：显式指定 repeat 模式（保持订阅持续触发）
- id: daily_report
  startWhen: "cron:0 2 * * *"  # 每天凌晨 2 点触发
  startMode: repeat  # 持续订阅，每天都触发

# 多路汇聚(AND)
startWhen: "event:branch_a.completed && event:branch_b.completed"

# 条件分支
startWhen: "event:check_data_quality.completed && {{ check_data_quality.score > 0.9 }}"

# stopWhen：必须订阅事件（流处理）
stopWhen: "event:manual.stop"  # 手动停止
stopWhen: "event:upstream.stopped"  # 上游停止时级联停止
stopWhen: "cron:0 0 * * *"  # 定时停止：每天午夜

# restartWhen：必须订阅事件（流处理）
restartWhen: "event:config_updated.triggered"  # 配置更新时重启
restartWhen: "event:dependency.restarted"  # 依赖重启时同步重启

# retryWhen：必须包含 event:task.failed
retryWhen: "event:task.failed && {{ attempts < 3 }}"  # 最多重试 3 次
retryWhen: "event:task.failed && {{ attempts < 3 && error_type == 'transient' }}"  # 仅临时错误重试

# alertWhen：订阅事件触发告警检查
alertWhen: "cron:*/5 * * * * && {{ execution_time > 3600 }}"  # 每 5 分钟检查执行时间
alertWhen: "cron:*/1 * * * * && {{ memory_usage > 0.9 }}"  # 每分钟检查内存使用率
alertWhen: "event:task.failed && {{ attempts >= 3 }}"  # 重试耗尽后立即告警
alertWhen: "event:metrics.updated && {{ lag > 10000 }}"  # 指标更新时检查延迟
```

**触发机制说明**：

所有 `xxxWhen` 表达式都基于事件驱动模型：

- **event: 表达式**：订阅节点事件、外部系统事件或流水线事件

  - `event:node_id.event_name` - 节点产生的事件
  - `event:pipeline.started` - 流水线级别事件
  - `event:task.failed` - 任务失败事件
- **cron: 表达式**：定时触发，本质上是订阅时钟事件

  - `cron:0 2 * * *` - 每天凌晨 2 点
  - `cron:*/5 * * * *` - 每 5 分钟
  - 系统将 cron 表达式转换为时钟事件，在表达式求值时触发
- **约束**：

  - `startWhen`、`stopWhen`、`alertWhen` 可以使用 `event:` 或 `cron:`
  - `restartWhen` 只能使用 `event:`（流处理通常由事件驱动）
  - `retryWhen` 必须包含 `event:task.failed`

**订阅模式（startMode）**：

控制 `startWhen` 触发后是否继续订阅：

- **once**（默认）：触发一次后自动取消订阅

  - 适用场景：批处理任务、一次性执行的节点
  - 行为：事件触发后启动任务，完成后不再响应相同事件
- **repeat**：保持订阅，持续响应事件

  - 适用场景：周期性任务（cron）、需要多次执行的节点
  - 行为：每次事件触发都启动任务（如果上次执行已完成）

**示例对比**：

```yaml
# 场景1：批处理 ETL（默认 once）
- id: daily_etl
  startWhen: "event:upstream.completed"
  # 第一次 upstream.completed 触发后执行，后续 upstream 再次完成不会触发

# 场景2：周期性任务（显式 repeat）
- id: hourly_aggregation
  startWhen: "cron:0 * * * *"
  startMode: repeat
  # 每小时触发一次，持续执行

# 场景3：手动触发（显式 repeat）
- id: manual_task
  startWhen: "event:manual.trigger"
  startMode: repeat
  # 每次手动触发都执行
```

> **设计原则**：默认一次性触发避免意外的重复执行，需要重复触发的场景显式声明。

**事件源**：

- 节点事件：`event:node_id.event_name`
- 流水线事件：`event:pipeline.started`
- 定时触发：`cron:0 2 * * *`

详见 [Expression.md](./Expression.md) 和 [Event.md](./Event.md)

#### 节点编排关键点

Pipeline 的**唯一编排结构**，所有执行逻辑都通过 Node 定义。

**关键点**：

- Pipeline 没有单独的 `tasks` 字段，Node 通过 `taskDefinition.ref` 引用 TaskDefinition
- Node 之间的依赖关系通过 `xxxWhen` 表达式隐式定义，无需显式的 `dependsOn` 字段
- 行为控制（启动、停止、重试、告警）和控制流（条件分支、并行、汇聚）都通过 `xxxWhen` 表达式实现
- **无显式控制节点**：不需要 ConditionNode、JoinNode、ForkNode，所有行为控制通过表达式实现

**示例**：

```yaml
nodes:
  # 起始节点：订阅 pipeline.started 事件
  - id: extract
    taskDefinition:
      ref: "com.company:data_extractor:1.0.0"
    inputBindings:
      source: "{{ pipeline.input.source_table }}"
    startWhen: "event:pipeline.started"

  # 依赖节点:订阅 extract.completed 事件
  - id: transform_data
    taskDefinition:
      ref: "com.company:data_transformer:1.0.0"
    inputBindings:
      input_path: "{{ extract_source_data.output_path }}"
    startWhen: "event:extract_source_data.completed"

  # Join 节点:订阅多个上游事件
  - id: merge_results
    taskDefinition:
      ref: "com.company:data_merger:1.0.0"
    inputBindings:
      path_a: "{{ branch_a.output_path }}"
      path_b: "{{ branch_b.output_path }}"
    startWhen: "event:branch_a.completed && event:branch_b.completed"

  # 条件分支:高质量路径
  - id: publish_high_quality_data
    taskDefinition:
      ref: "com.company:publisher:1.0.0"
    inputBindings:
      data_path: "{{ check_data_quality.output_path }}"
    startWhen: "event:check_data_quality.completed && {{ check_data_quality.score > 0.9 }}"

  # 条件分支:低质量需要审批
  - id: approve_low_quality_data
    taskDefinition:
      ref: "com.company:approval:1.0.0"
    inputBindings:
      reason: "质量评分: {{ check_data_quality.score }}"
    startWhen: "event:check_data_quality.completed && {{ check_data_quality.score <= 0.9 }}"
```

### inputVariables / outputVariables

Pipeline 作为整体的输入/输出接口，使得 Pipeline 可以像 TaskDefinition 一样被其他 Pipeline 引用。

**Pipeline 输入示例**：

```yaml
inputVariables:
  - name: source_table
    type: string
    required: true
    description: "源表名称"

  - name: target_date
    type: string
    required: true
    description: "目标日期，格式 YYYY-MM-DD"

  - name: quality_threshold
    type: number
    required: false
    defaultValue: 0.9
    description: "质量阈值"
```

**Pipeline 输出示例**：

```yaml
outputVariables:
  - name: output_path
    type: string
    description: "处理后的数据路径"

  - name: row_count
    type: integer
    description: "处理的数据行数"

  - name: quality_score
    type: number
    description: "数据质量评分"
```

### 版本管理

PipelineDefinition 采用与 [TaskDefinition](./TaskDefinition.md#版本管理) 一致的版本管理策略。

**草稿版本**：

- 格式：`draft-YYYYMMDDHHmmss`（如 `draft-20250115140000`）
- 追加式创建，保留所有历史草稿

**已发布版本**：

- 格式：语义化版本 `major.minor.patch`（如 `1.0.0`）
- 不可变，可被引用

**版本示例**：

```yaml
versions:
  - version: "draft-20250115140000"
    status: "DRAFT"
    nodes: [...]
    inputVariables: [...]

  - version: "1.0.0"
    status: "PUBLISHED"
    nodes: [...]
    releaseNotes: "Initial release"
    publishedAt: "2024-01-01T00:00:00Z"
    publishedBy: "user@company.com"
```

## 完整示例

### 示例：带质量检查和审批的 ETL Pipeline

```yaml
PipelineDefinition:
  id: "pipe_123"
  namespace: "com.company.pipelines"
  name: "user_data_etl"

  versions:
    - version: "1.0.0"
      status: "PUBLISHED"

      # Pipeline 输入
      inputVariables:
        - name: source_table
          type: string
          required: true
        - name: target_date
          type: string
          required: true
        - name: quality_threshold
          type: number
          defaultValue: 0.9

      # Pipeline 输出
      outputVariables:
        - name: output_path
          type: string
        - name: row_count
          type: integer
        - name: final_quality_score
          type: number

      # 节点编排
      nodes:
        # 1. 数据提取
        - id: extract_source_data
          type: task
          taskDefinition:
            ref: "com.company:spark_extractor:1.0.0"
          inputBindings:
            table_name: "{{ pipeline.input.source_table }}"
            partition_date: "{{ pipeline.input.target_date }}"
          startWhen: "event:pipeline.started"

        # 2. 数据转换
        - id: transform_data
          type: task
          taskDefinition:
            ref: "com.company:spark_transformer:1.0.0"
          inputBindings:
            input_path: "{{ extract_source_data.output_path }}"
          startWhen: "event:extract_source_data.completed"
          retryWhen: "{{ attempts < 3 }}"

        # 3. 质量检查
        - id: check_data_quality
          type: task
          taskDefinition:
            ref: "com.company:quality_checker:1.0.0"
          inputBindings:
            data_path: "{{ transform_data.output_path }}"
            threshold: "{{ pipeline.input.quality_threshold }}"
          startWhen: "event:transform_data.completed"

        # 4a. 高质量路径：直接发布
        - id: publish_high_quality_data
          type: task
          taskDefinition:
            ref: "com.company:publisher:1.0.0"
          inputBindings:
            data_path: "{{ transform_data.output_path }}"
          startWhen: "event:check_data_quality.completed && {{ check_data_quality.score > 0.9 }}"

        # 4b. 低质量路径：需要审批
        - id: approve_low_quality_data
          type: task
          taskDefinition:
            ref: "com.company:approval:1.0.0"
          inputBindings:
            title: "低质量数据发布审批"
            description: "质量评分: {{ check_data_quality.score }}"
            approvers: ["admin@company.com"]
          startWhen: "event:check_data_quality.completed && {{ check_data_quality.score <= 0.9 }}"

        # 5. 汇聚：两条路径都完成后发送通知
        - id: send_completion_notification
          type: task
          taskDefinition:
            ref: "com.company:notifier:1.0.0"
          inputBindings:
            message: "Pipeline 执行完成"
          startWhen: "event:publish_high_quality_data.completed || event:approve_low_quality_data.approved"

      releaseNotes: "Initial release with quality check and approval"
      createdAt: "2024-01-01T00:00:00Z"
      createdBy: "user@company.com"
```

**控制流说明**:

- 顺序执行:`extract_source_data` → `transform_data` → `check_data_quality`(通过 startWhen 订阅上游 `completed` 事件)
- 条件分支:质量检查后根据评分分为两条路径
  - 高质量路径:`check_data_quality.score > 0.9` 触发 `publish_high_quality_data`
  - 低质量路径:`check_data_quality.score <= 0.9` 触发 `approve_low_quality_data`
- 汇聚模式:`send_completion_notification` 订阅两条路径的完成事件,任意一条完成即触发(OR 运算)

## 不变式（Invariants）

1. **唯一性约束**

   - `namespace:name:version` 三元组全局唯一
   - 同一 version 内，`nodes[].id` 必须唯一
2. **版本控制约束**

   - 草稿版本追加式创建，不覆盖历史
   - 已发布版本不可变
   - 版本号必须遵循语义化版本规范（如 "1.0.0"）
3. **节点引用有效性**

   - `node.taskDefinition.ref` 引用的任务定义必须存在且已发布
   - 输入绑定中的 key 必须对应任务定义的输入变量
   - 任务定义的必填输入变量必须在输入绑定中绑定
4. **事件可达性**

   - startWhen 中引用的事件必须由某个节点产生（或由流水线产生）
   - 流水线必须有至少一个节点订阅 `event:pipeline.started`（起始节点）
5. **输出变量一致性**

   - 流水线的输出变量必须由某个节点的输出提供
   - 输出变量的绑定通常在流水线执行结束时计算

## 领域事件

Pipeline 的事件设计与 [Task](./TaskDefinition.md#领域事件) 一致：

- `PipelineDefinitionCreated`：流水线创建（聚合根 + 初始草稿版本）
- `PipelineVersionCreated`：草稿版本创建
- `PipelineVersionPublished`：版本发布

详细的事件结构和 payload 字段说明参考 TaskDefinition 文档。

## 命令

### CreateDefinition

创建流水线定义（聚合根），系统自动创建初始草稿版本。

**请求**：

```http
POST /api/v1/pipeline-definitions
Content-Type: application/json

{
  "namespace": "com.company.pipelines",
  "name": "user_data_etl",
  "metadata": {
    "description": "用户数据 ETL 流水线"
  }
}
```

**触发事件**：`PipelineCreated`

---

### CreateVersion

创建新的草稿版本。

**请求**：

```http
POST /api/v1/pipeline-definitions/{namespace:name}/drafts
Content-Type: application/json

{
  "basedOn": "draft-20250115100000",
  "nodes": [...],
  "inputVariables": [...],
  "outputVariables": [...]
}
```

**触发事件**：`DraftVersionCreated`

---

### PublishVersion

发布草稿版本为正式版本。

**请求**：

```http
POST /api/v1/pipeline-definitions/{namespace:name}/drafts/{draft-version}/publish
Content-Type: application/json

{
  "version": "1.0.0",
  "releaseNotes": "Initial release"
}
```

**触发事件**：`PipelineVersionPublished`

## 查询

```http
# 查询特定版本
GET /api/v1/pipeline-definitions/{namespace:name:version}

# 查询最新草稿（简写）
GET /api/v1/pipeline-definitions/{namespace:name:draft}

# 列出所有版本
GET /api/v1/pipeline-definitions/{namespace:name}/versions
```

## 与其他领域模型的关系

### 关键关系说明

#### 1. 聚合根引用关系

| 关系                                               | 类型 | 说明                                                        |
| -------------------------------------------------- | ---- | ----------------------------------------------------------- |
| **PipelineDefinition → TaskDefinition**     | 引用 | 通过 `node.taskDefinition.ref` 引用已发布的任务模板       |
| **PipelineDefinition → PipelineDefinition** | 引用 | 通过 `node.pipelineDefinition.ref` 引用子流水线（自引用） |

#### 2. Value Object 的使用

| Value Object                 | 使用位置                                                            | 说明                                 |
| ---------------------------- | ------------------------------------------------------------------- | ------------------------------------ |
| **VariableDefinition** | TaskDefinition/PipelineDefinition 的 inputVariables/outputVariables | 定义输入/输出接口契约                |
| **Expression**         | Node 的 inputBindings、xxxWhen 字段                                 | 表达条件、事件订阅、变量引用         |
| **Node**               | PipelineDefinition 的 nodes 数组                                    | 流水线的编排单元（实体但不是聚合根） |

#### 3. 运行时交互

```plaintext
设计时                                运行时
┌────────────────┐                   ┌────────────────┐
│ PipelineDef    │  ────create───→   │ PipelineExec   │
│  - nodes[]     │                   │  - nodeExecs[] │
└────────────────┘                   └────────────────┘
        │                                     │
        │ reference                           │ create
        ▼                                     ▼
┌────────────────┐                   ┌────────────────┐
│ TaskDefinition │  ────create───→   │ TaskExecution  │
│  - behaviors   │                   │  - status      │
│  - events      │                   │  - outputs     │
└────────────────┘                   └────────────────┘
```

**说明**:

- **定义(Definition)** 是设计时的聚合根,定义"能做什么"
- **执行(Execution)** 是运行时的聚合根,记录"正在做什么/做了什么"
- PipelineDefinition 通过 Node 引用 TaskDefinition,运行时创建对应的 Execution 实例

#### 4. 事件驱动的依赖关系

```plaintext
Node A                         Node B
  │                              │
  ├─> Publish                    │
  │   event:a.completed          │
  │                              │
  │                              ├─ Subscribe via
  │                              │  startWhen: "event:a.completed"
  │                              │
  │                              ▼ Triggered
  │                           Execute
```

**关键点**:

- Node 之间不直接依赖,通过事件解耦
- TaskDefinition 定义产生哪些事件(outputEvents)
- Node 通过 startWhen 等表达式订阅事件
- Expression 中的事件引用必须对应某个节点的输出事件

## 设计考量

### 为什么选择事件 + 变量的依赖表达方式？

传统的 DAG 编排系统通常使用 `dependsOn: [nodeA, nodeB]` 显式声明依赖，但这种方式存在局限性：

1. **表达能力受限**：难以表达复杂的触发条件（如"任一上游完成"、"超时触发"、"外部事件触发"）
2. **控制流与数据流混淆**：无法区分"等待上游完成"和"需要上游数据"
3. **扩展性差**：无法订阅外部系统事件（如 Kafka 消息、定时调度）

**事件 + 变量的优势**：

- **执行依赖(何时触发)**:通过 `xxxWhen` 表达式订阅事件

  - `startWhen: "event:extract_source_data.completed"` - 上游完成时启动
  - `startWhen: "event:branch_a.completed && event:branch_b.completed"` - 多路汇聚
  - `stopWhen: "event:manual.stop || cron:0 0 * * *"` - 手动停止或定时停止
- **数据依赖(需要什么数据)**:通过 `inputBindings` 引用变量

  - `input_path: "{{ extract_source_data.output_path }}"` - 显式声明数据来源
  - 编排层无需关心数据传递细节,由运行时解析

**示例对比**：

```yaml
# 传统 dependsOn 方式
- id: transform_data
  dependsOn: [extract_source_data]  # 只能表达"等待 extract 完成"

# 事件 + 变量方式
- id: transform_data
  inputBindings:
    input_path: "{{ extract_source_data.output_path }}"    # 数据依赖:需要哪些数据
  startWhen: "event:extract_source_data.completed"         # 执行依赖:何时触发
```

这种设计实现了**关注点分离**：数据流（需要什么）与控制流（何时触发）独立表达，提供更强的灵活性和可扩展性。
