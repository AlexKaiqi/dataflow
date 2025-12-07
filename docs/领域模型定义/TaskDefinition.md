# **《TaskDefinition 任务定义》**

## 概述

**《TaskDefinition 任务定义》**是可复用的任务模板，定义了一类任务"能做什么"、"需要什么输入"、"产生什么输出"。它是独立于 **《Pipeline 流水线》**的领域概念，可以被多个 **《Pipeline 流水线》**的多个 **《Node 节点》**引用。

### 核心职责

- **定义任务行为**：描述任务类型及其特有的行为能力（如批处理的 start/retry，流处理的 start/stop/restart）
- **声明接口契约**：定义输入变量和输出变量，明确任务与外部的数据交互
- **定义事件规约**：说明任务在不同状态下产生的事件（如 started、completed、failed）
- **支持版本管理**：允许同一任务有多个版本，支持灰度发布和回滚
- **支持跨 Pipeline 复用**：一个 TaskDefinition 可以被多个 Pipeline 引用

### 设计原则

**《TaskDefinition 任务定义》只关心"是什么"，不关心"何时执行"**：

- ✅ 定义：任务类型、输入输出、执行逻辑
- ✅ 定义：任务支持哪些 **《Action 行为》**
- ✅ 定义：任务产生哪些 **《Event 事件》**
- ❌ 不定义：何时启动（startWhen）
- ❌ 不定义：何时重试（retryWhen）
- ❌ 不定义：依赖关系

**编排逻辑由 《Node 节点》控制**：具体的执行时机、依赖关系、重试策略等编排逻辑在 **《Pipeline 流水线》**的 **《Node 节点》**中通过 **《Expression 表达式》**（startWhen、stopWhen 等）定义。

## 领域模型结构

```yaml
TaskDefinition:
  # 唯一标识
  namespace: string              # 命名空间，如 "com.company.tasks"
  name: string                   # 任务名称，如 "data_transform"
  version: string                # 版本号，如 "1.0.0"
  # 复合键 namespace:name:version 全局唯一
  
  # 基本信息
  type: TaskType                 # 任务类型（见下文）
  description: string            # 任务描述
  
  # 接口契约
  inputVariables: List[VariableDefinition]   # 输入变量定义
  outputVariables: List[VariableDefinition]  # 输出变量定义
  
  # 行为定义
  supportedActions: List[Action]  # 支持的行为（由 type 决定）
  outputEvents: List[Event]       # 产生的事件（由 type 决定）
  
  # 执行定义（类型特定）
  executionConfig: object         # 根据 type 不同而不同
  
  # 元数据
  createdAt: timestamp
  createdBy: string
  status: "DRAFT" | "PUBLISHED"
```

## 任务类型（TaskType）

不同的任务类型定义了不同的**行为能力**、**事件集合**和**变量特征**：

### 批处理任务

#### PySpark 任务

- **行为**：start, retry
- **事件**：started, completed, failed
- **输出变量示例**：rows_processed, execution_time

#### SQL 任务

- **行为**：start, retry
- **事件**：started, completed, failed
- **输出变量示例**：rows_affected, query_time

#### Ray 任务

- **行为**：start, retry
- **事件**：started, completed, failed
- **输出变量示例**：tasks_completed, processing_time

### 流处理任务

#### Streaming 任务

- **行为**：start, stop, restart, retry
- **事件**：started, stopped, restarted, completed, failed
- **输出变量示例**：processed_records, current_offset, lag

### 控制流任务

#### Approval 任务

- **行为**：start（启动审批流程）
- **事件**：started, approved, rejected, timeout
- **输出变量示例**：approver, approval_time, comments

#### Wait 任务

- **行为**：start
- **事件**：started, timeout, completed
- **输出变量示例**：wait_duration

详细说明请参考 [TaskTypes 目录](./TaskTypes/) 下的各任务类型文档。

## 核心概念详解

### 输入/输出变量

TaskDefinition 通过 `inputVariables` 和 `outputVariables` 定义任务与外部的数据接口契约。

#### 输入变量（inputVariables）

定义任务执行需要的输入参数：

```yaml
inputVariables:
  - name: data_path
    type: string
    required: true
    description: "输入数据路径"
  
  - name: quality_threshold
    type: number
    required: false
    default: 0.9
    description: "数据质量阈值"
```

#### 输出变量（outputVariables）

定义任务执行后产生的输出变量：

```yaml
outputVariables:
  - name: rows_processed
    type: integer
    description: "处理的数据行数"
  
  - name: quality_score
    type: number
    description: "数据质量分数"
  
  - name: output_path
    type: string
    description: "输出数据路径"
```

**重要说明**：

- 输入变量在 Node 中通过表达式绑定具体值
- 输出变量在任务执行完成后自动写入上下文，供下游 Node 使用
- 输出变量可以在其他 Node 的 `startWhen`、`stopWhen` 等表达式中引用

### 行为与事件

#### 行为（Actions）

行为是任务可以被触发执行的操作，不同任务类型支持不同的行为：

**所有任务通用**：

- `start`: 启动任务执行

**特定任务类型**：

- `retry`: 重试失败的任务（批处理任务）
- `stop`: 停止运行中的任务（流处理任务）
- `restart`: 重启任务（流处理任务）

#### 事件（Events）

事件是任务在不同状态下自动产生的通知，其他 Node 可以通过订阅这些事件来触发自己的行为。

**所有任务通用事件**：

- `{node_id}.started`: 任务开始执行
- `{node_id}.completed`: 任务成功完成（或 `approved` 对于审批任务）
- `{node_id}.failed`: 任务执行失败（或 `rejected` 对于审批任务）

**特定任务类型事件**：

- 审批任务：`{node_id}.timeout` - 审批超时
- 流处理任务：`{node_id}.stopped` - 任务被停止
- 流处理任务：`{node_id}.restarted` - 任务被重启

**事件使用示例**：

```yaml
pipeline:
  nodes:
    - id: data_processing
      taskDefinition: spark_etl_v1
      startWhen: "event:pipeline.started"
  
    - id: quality_check
      taskDefinition: quality_validator_v1
      # 等待上游完成事件 + 检查输出变量
      startWhen: "event:data_processing.completed && {{ data_processing.quality_score > 0.9 }}"
  
    - id: manual_review
      taskDefinition: approval_task_v1
      # 质量不达标时需要人工审批
      startWhen: "event:data_processing.completed && {{ data_processing.quality_score <= 0.9 }}"
  
    - id: deploy
      taskDefinition: deploy_task_v1
      # 两条路径：质量达标或审批通过
      startWhen: "event:quality_check.completed || event:manual_review.approved"
```

### TaskDefinition 的复用

TaskDefinition 可以被多个 Pipeline 的多个 Node 引用：

```yaml
# TaskDefinition（全局定义）
taskDefinition:
  namespace: com.company
  name: quality_approval
  version: "1.0.0"
  type: approval
  inputVariables:
    - name: data_summary
      type: object
  outputVariables:
    - name: approver
      type: string
    - name: approval_time
      type: timestamp

# Pipeline 1
pipeline:
  name: pipeline_a
  nodes:
    - id: review_step_1
      taskDefinition: com.company:quality_approval:1.0.0
      startWhen: "event:transform.completed"

# Pipeline 2
pipeline:
  name: pipeline_b
  nodes:
    - id: final_review
      taskDefinition: com.company:quality_approval:1.0.0  # 复用同一个定义
      startWhen: "event:last_step.completed"
```

## 不变式

- **复合键全局唯一**：`namespace:name:version` 三元组在系统中全局唯一。
- **DRAFT 版本唯一性**：每个 `namespace:name` 组合最多只有一个 DRAFT 版本（`version=draft`）。
- **PUBLISHED 版本不可变**：状态为 PUBLISHED 的任务定义完全不可修改。
- **引用约束**：已被任何 Pipeline 引用的 PUBLISHED 版本不允许删除。

## 事件

- `TaskDefinitionCreated` - 任务定义已创建（DRAFT 版本）
- `TaskDefinitionModified` - 任务定义已修改（DRAFT 版本）
- `TaskDefinitionPublished` - 任务定义已发布（新增 PUBLISHED 版本）

**TODO**：补充事件的消息体结构（EventPayload）定义

## 命令

### CreateTaskDefinition

**请求**：

```http
POST /api/v1/task-definitions
Content-Type: application/json

{
  "namespace": "com.company.tasks",
  "name": "data_cleaner",
  "type": "ray_operator",
  "metadata": {
    "description": "数据清洗任务"
  }
}
```

**说明**：
创建一个新的任务定义。系统会自动创建一个 `version=draft` 的 DRAFT 版本，此版本处于草稿状态，允许修改但不允许被 Pipeline 引用。

**返回**：

```json
{
  "namespace": "com.company.tasks",
  "name": "data_cleaner",
  "version": "draft",
  "status": "DRAFT",
  "type": "ray_operator",
  "metadata": {
    "description": "数据清洗任务"
  },
  "createdAt": "2025-01-15T10:00:00Z",
  "createdBy": "alice"
}
```

**业务规则**：

- `namespace:name` 的组合必须全局唯一（如果已存在，返回 409 Conflict）
- type 决定了 executionDefinition 需要哪些配置
- 初始化时 DRAFT 版本的 inputVariables、outputVariables、executionDefinition 都为空，需要通过 ModifyTaskDefinition 填充

---

### ModifyTaskDefinition

**请求**：

```http
PATCH /api/v1/task-definitions/{namespace:name}/draft
Content-Type: application/json

{
  "inputVariables": [...],
  "outputVariables": [...],
  "executionDefinition": {...},
  "resources": {...},
  "metadata": {
    "description": "更新描述"
  }
}
```

**说明**：
修改任务定义的 DRAFT 版本。只有 `version=draft` 的 DRAFT 版本允许被修改，PUBLISHED 版本是不可变的。

**返回**：

```json
{
  "namespace": "com.company.tasks",
  "name": "data_cleaner",
  "version": "draft",
  "status": "DRAFT",
  "type": "ray_operator",
  "inputVariables": [...],
  "outputVariables": [...],
  "executionDefinition": {...},
  "resources": {...},
  "metadata": {
    "description": "更新描述"
  },
  "updatedAt": "2025-01-15T11:00:00Z",
  "updatedBy": "alice"
}
```

**业务规则**：

- 只能修改 DRAFT 版本（`version=draft`），PUBLISHED 版本不允许修改
- 可以增加、删除或修改 inputVariables 和 outputVariables
- 可以修改 type，但需要同步更新 executionDefinition
- 修改后自动更新 updatedAt 和 updatedBy 字段

---

### PublishTaskDefinition

**请求**：

```http
POST /api/v1/task-definitions/{namespace:name}/publish
Content-Type: application/json

{
  "version": "1.0.0",
  "releaseNotes": "Initial release with data cleaning operators"
}
```

**说明**：

将任务定义的 DRAFT 版本发布为正式版本。发布后该版本变为不可变，可以被 Pipeline 引用。系统会：

1. 将当前 DRAFT 版本复制为新的 PUBLISHED 版本
2. 设置 status = "PUBLISHED"，记录 publishedAt 和 publishedBy

**返回**：

```json
{
  "namespace": "com.company.tasks",
  "name": "data_cleaner",
  "version": "1.0.0",
  "status": "PUBLISHED",
  "type": "ray_operator",
  "inputVariables": [...],
  "outputVariables": [...],
  "executionDefinition": {...},
  "resources": {...},
  "metadata": {
    "description": "数据清洗任务"
  },
  "releaseNotes": "Initial release with data cleaning operators",
  "createdAt": "2025-01-15T10:00:00Z",
  "createdBy": "alice",
  "publishedAt": "2025-01-15T12:00:00Z",
  "publishedBy": "alice"
}
```

**业务规则**：

- 必须存在 DRAFT 版本才能发布
- 版本号必须遵循语义化版本规范（major.minor.patch）
- 版本号必须大于该任务的所有已发布版本（递增原则）
- 发布后 PUBLISHED 版本不可修改

## 任务查询

### GetTaskDefinition

**请求**：

```http
GET /api/v1/task-definitions/{namespace:name:version}
```

**示例**：

```bash
# 获取特定版本
GET /api/v1/task-definitions/com.company.tasks:data_cleaner:1.0.0

# 获取 DRAFT 版本
GET /api/v1/task-definitions/com.company.tasks:data_cleaner:draft
```

**说明**：
按 `namespace:name:version` 三元组获取单个任务定义。version 可以是具体的语义化版本号（如 1.0.0）或 draft。

**返回**：

```json
{
  "namespace": "com.company.tasks",
  "name": "data_cleaner",
  "version": "1.0.0",
  "status": "PUBLISHED",
  "type": "ray_operator",
  "inputVariables": [...],
  "outputVariables": [...],
  "executionDefinition": {...},
  "resources": {...},
  "resourceGuidance": {...},
  "externalEvents": [...],
  "healthCheck": {...},
  "metadata": {
    "description": "数据清洗任务"
  },
  "releaseNotes": "Initial release with data cleaning operators",
  "createdAt": "2025-01-15T10:00:00Z",
  "createdBy": "alice",
  "publishedAt": "2025-01-15T12:00:00Z",
  "publishedBy": "alice"
}
```

**业务规则**：

- 如果指定版本不存在，返回 404 Not Found

---

### ListTaskVersions

**请求**：

```http
GET /api/v1/task-definitions/{namespace:name}/versions?limit=100&offset=0
```

**示例**：

```bash
GET /api/v1/task-definitions/com.company.tasks:data_cleaner/versions?limit=100&offset=0
```

**说明**：
列出某个任务的所有版本，包括 DRAFT 版本和所有 PUBLISHED 版本。该查询返回版本的完整列表，帮助用户了解任务的演变历史。

**返回**：

```json
{
  "namespace": "com.company.tasks",
  "name": "data_cleaner",
  "versions": [
    {
      "version": "draft",
      "status": "DRAFT",
      "createdAt": "2025-02-20T10:35:00Z",
      "updatedAt": "2025-02-21T09:15:00Z"
    },
    {
      "version": "1.1.0",
      "status": "PUBLISHED",
      "publishedAt": "2025-02-20T10:35:00Z",
      "publishedBy": "alice",
      "releaseNotes": "Added support for batch processing"
    },
    {
      "version": "1.0.0",
      "status": "PUBLISHED",
      "publishedAt": "2025-02-15T14:25:00Z",
      "publishedBy": "alice",
      "releaseNotes": "Initial release"
    }
  ],
  "total": 3
}
```

**业务规则**：

- 返回的版本按时间降序排列（最新的在前）

---
