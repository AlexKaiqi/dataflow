# **《TaskDefinition 任务定义》**

## 概述

TaskDefinition 是可复用的任务模板，定义了一类任务"能做什么"、"需要什么输入"、"产生什么输出"。它是独立存在的领域概念，可以被多个流水线的多个节点引用。

### 核心职责

- **定义任务行为**：描述任务类型及其特有的行为能力（如批处理的 start/retry，流处理的 start/stop/restart）
- **声明接口契约**：定义输入变量和输出变量，明确任务与外部的数据交互
- **定义事件规约**：说明任务产生的事件（如 started、completed、failed）
- **支持版本管理**：允许同一任务有多个版本，支持灰度发布和回滚
- **支持跨流水线复用**：一个 TaskDefinition 可以被多个流水线引用

### 设计原则

**TaskDefinition 只关心"是什么"，不关心"何时执行"**：

- ✅ 定义：任务类型、输入输出、执行逻辑
- ✅ 定义：任务支持哪些行为（actions）
- ✅ 定义：任务产生哪些事件（events）
- ❌ 不定义：何时启动（startWhen）
- ❌ 不定义：何时重试（retryWhen）
- ❌ 不定义：依赖关系

**编排逻辑由节点控制**：具体的执行时机、依赖关系、重试策略等编排逻辑在流水线的节点中通过表达式（startWhen、stopWhen 等）定义。

## 领域模型结构

```yaml
TaskDefinition:
  # 唯一标识
  namespace: string              # 命名空间，如 "com.company.tasks"
  name: string                   # 任务名称，如 "data_transform"
  version: string                # 版本号（见版本管理说明）
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
  lastModifiedAt: timestamp       # 最后修改时间
  lastModifiedBy: string          # 最后修改者
  status: "DRAFT" | "PUBLISHED"   # 版本状态
```

### 版本管理

TaskDefinition 支持多版本管理：

**草稿版本（Draft Versions）**：

- 格式：`draft-YYYYMMDDHHmmss`（如 `draft-20250115140000`）
- 状态：`DRAFT`
- 特点：
  - 可修改
  - 不可被流水线引用
  - 每次修改创建新的草稿版本（追加式）
  - 保留完整的修改历史

**已发布版本（Published Versions）**：

- 格式：语义化版本 `major.minor.patch`（如 `1.0.0`）
- 状态：`PUBLISHED`
- 特点：
  - 不可修改（immutable）
  - 可被流水线引用
  - 遵循语义化版本规范

**版本演进示例**：

```yaml
versions:
  - version: "draft-20250115140000"  # 最新草稿
    status: "DRAFT"
  - version: "draft-20250115130000"  # 历史草稿
    status: "DRAFT"
  - version: "1.0.0"                 # 已发布版本
    status: "PUBLISHED"
```

## **《TaskType 任务类型》**

不同的任务类型定义了不同的行为能力、事件集合和变量特征：

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

#### Approval 审批任务

- **行为**：start（启动审批流程）
- **事件**：started, approved, rejected, timeout
- **输出变量示例**：approver, approval_time, comments

#### Wait 等待任务

- **行为**：start
- **事件**：started, timeout, completed
- **输出变量示例**：wait_duration

详细说明请参考 [TaskTypes 目录](./TaskTypes/) 下的各任务类型文档。

## 核心概念详解

### **《InputVariable 输入变量》** / **《OutputVariable 输出变量》**

TaskDefinition 通过 `inputVariables` 和 `outputVariables` 定义任务与外部的数据接口契约。

#### 输入变量

定义任务执行需要的输入参数：

```yaml
inputVariables:
  - name: data_path                    # 变量名
    type: string                       # 数据类型
    required: true                     # 是否必填
    description: "输入数据路径"
  
  - name: quality_threshold
    type: number
    required: false
    default: 0.9                       # 默认值
    description: "数据质量阈值"
```

#### 输出变量

定义任务执行后产生的输出变量：

```yaml
outputVariables:
  - name: rows_processed
    type: integer                      # 数据类型
    description: "处理的数据行数"
  
  - name: quality_score
    type: number
    description: "数据质量分数"
  
  - name: output_path
    type: string
    description: "输出数据路径"
```

**重要说明**：

- 输入变量在节点中通过表达式绑定具体值
- 输出变量在任务执行完成后自动写入执行上下文，供下游节点使用
- 输出变量可以在其他节点的 `startWhen`、`stopWhen` 等表达式中引用

## **《Action 行为》**

行为（Action）是任务可以被触发执行的操作。不同任务类型支持不同的行为。

**所有任务通用**：

- `start`: 启动任务执行

**特定任务类型**：

- `retry`: 重试失败的任务（批处理任务）
- `stop`: 停止运行中的任务（流处理任务）
- `restart`: 重启任务（流处理任务）

## **《Event 事件》**

事件（Event）是任务在执行过程中产生的状态通知。其他节点可以通过订阅这些事件来触发自己的行为。

**所有任务通用事件**：

- `{node_id}.started`: 任务开始执行
- `{node_id}.completed`: 任务成功完成（或 `approved` 对于审批任务）
- `{node_id}.failed`: 任务执行失败（或 `rejected` 对于审批任务）

**特定任务类型事件**：

- 审批任务：`{node_id}.timeout` - 审批超时
- 流处理任务：`{node_id}.stopped` - 任务被停止
- 流处理任务：`{node_id}.restarted` - 任务被重启

**使用示例**：

```yaml
pipeline:
  nodes:
    - id: data_processing
      taskDefinition: spark_etl_v1
      startWhen: "event:pipeline.started"        # 订阅 Pipeline 启动事件
  
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
      # 两条路径：质量达标或审批通过（事件汇聚）
      startWhen: "event:quality_check.completed || event:manual_review.approved"
```

## 不变式

- **复合键全局唯一**：`namespace:name:version` 三元组在系统中全局唯一。
- **草稿版本追加式创建**：每次修改创建新的 `draft-{timestamp}` 版本，不覆盖历史草稿。
- **已发布版本不可变**：状态为 `PUBLISHED` 的任务定义完全不可修改。
- **版本号递增**：已发布版本号必须大于该任务的所有已发布版本。
- **引用约束**：已被任何流水线引用的已发布版本不允许删除。

## 领域事件

### TaskDefinitionCreated

任务定义已创建（聚合根创建，自动创建初始草稿版本）。

**消息体结构**：

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "TaskCreated",
  "timestamp": "2025-01-15T10:00:00Z",
  "aggregateId": "com.company.tasks:data_cleaner",
  "version": 1,
  "payload": {
    "namespace": "com.company.tasks",
    "name": "data_cleaner",
    "initialVersion": "draft-20250115100000",
    "type": "ray_operator",
    "createdBy": "alice"
  }
}
```

**Payload 字段说明**：

- `namespace`, `name`: 任务定义标识符
- `initialVersion`: 自动创建的初始草稿版本号
- `type`: 任务类型
- `createdBy`: 创建者

### TaskVersionCreated

草稿版本已创建（创建新的草稿版本）。

**消息体结构**：

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440001",
  "eventType": "TaskDraftVersionCreated",
  "timestamp": "2025-01-15T14:00:00Z",
  "aggregateId": "com.company.tasks:data_cleaner",
  "version": 2,
  "payload": {
    "namespace": "com.company.tasks",
    "name": "data_cleaner",
    "version": "draft-20250115140000",
    "basedOn": "draft-20250115130000",
    "createdBy": "bob"
  }
}
```

**Payload 字段说明**：

- `version`: 新创建的草稿版本号
- `basedOn`: 基于哪个版本创建（可以是草稿或已发布版本）
- `createdBy`: 创建者

**查询完整内容**：

```bash
# 查询新版本的完整定义
GET /api/v1/task-definitions/com.company.tasks:data_cleaner:draft-20250115140000

# 查询基础版本进行对比
GET /api/v1/task-definitions/com.company.tasks:data_cleaner:draft-20250115130000
```

### TaskVersionPublished

版本已发布（从草稿版本发布为正式版本）。

**消息体结构**：

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440002",
  "eventType": "TaskVersionPublished",
  "timestamp": "2025-01-15T15:00:00Z",
  "aggregateId": "com.company.tasks:data_cleaner",
  "version": 3,
  "payload": {
    "namespace": "com.company.tasks",
    "name": "data_cleaner",
    "fromVersion": "draft-20250115140000",
    "toVersion": "1.0.0",
    "publishedBy": "alice",
    "releaseNotes": "Initial release with data cleaning operators"
  }
}
```

**Payload 字段说明**：

- `fromVersion`: 发布的源草稿版本号
- `toVersion`: 新的已发布版本号（语义化版本）
- `publishedBy`: 发布者
- `releaseNotes`: 发布说明

---

### 查询完整数据

事件订阅者如需获取完整的任务定义数据，应通过版本号查询：

```bash
# 查询特定草稿版本
GET /api/v1/task-definitions/com.company.tasks:data_cleaner:draft-20250115140000

# 查询最新草稿（简写）
GET /api/v1/task-definitions/com.company.tasks:data_cleaner:draft

# 查询已发布版本
GET /api/v1/task-definitions/com.company.tasks:data_cleaner:1.0.0
```

**优点**：

- **避免数据冗余**：事件中不重复存储完整结构
- **完整可追溯**：任何版本的完整状态都可查询
- **降低耦合度**：事件结构与聚合根模型独立演进
- **简化事件处理**：订阅者只需处理轻量级通知

## 命令

### CreateDefinition

创建任务定义（聚合根），系统自动创建初始草稿版本。

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

**返回**：

```json
{
  "namespace": "com.company.tasks",
  "name": "data_cleaner",
  "initialVersion": "draft-20250115100000",
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
- 系统自动创建初始草稿版本：`draft-{YYYYMMDDHHmmss}`
- 任务类型决定了执行定义需要哪些配置
- 初始版本为空模板，需要通过 `CreateDraftVersion` 填充内容

**触发事件**：`TaskCreated`

---

### CreateVersion

创建新的草稿版本。

**请求**：

```http
POST /api/v1/task-definitions/{namespace:name}/drafts
Content-Type: application/json

{
  "basedOn": "draft-20250115100000",
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

创建新的草稿版本。可以基于：

- 最新的草稿版本（默认）
- 指定的草稿版本
- 已发布版本

**返回**：

```json
{
  "namespace": "com.company.tasks",
  "name": "data_cleaner",
  "version": "draft-20250115140000",
  "basedOn": "draft-20250115100000",
  "status": "DRAFT",
  "type": "ray_operator",
  "inputVariables": [...],
  "outputVariables": [...],
  "executionDefinition": {...},
  "resources": {...},
  "metadata": {
    "description": "更新描述"
  },
  "createdAt": "2025-01-15T14:00:00Z",
  "createdBy": "bob"
}
```

**业务规则**：

- `basedOn` 可选，默认为最新草稿版本
- `basedOn` 可以是草稿版本或已发布版本
- 系统自动生成新版本号：`draft-{YYYYMMDDHHmmss}`
- 保留所有历史草稿版本（追加式，不覆盖）
- 可以修改任何字段（type, inputVariables, outputVariables 等）

**触发事件**：`TaskDraftVersionCreated`

---

### PublishVersion

发布草稿版本为正式版本。

**请求**：

```http
POST /api/v1/task-definitions/{namespace:name}/drafts/{draft-version}/publish
Content-Type: application/json

{
  "version": "1.0.0",
  "releaseNotes": "Initial release with data cleaning operators"
}
```

**说明**：

将指定的草稿版本发布为正式版本。发布后该版本变为不可变，可以被 Pipeline 引用。

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
  "publishedFrom": "draft-20250115140000",
  "publishedAt": "2025-01-15T15:00:00Z",
  "publishedBy": "alice"
}
```

**业务规则**：

- 草稿版本必须存在且状态为 DRAFT
- `version` 必须遵循语义化版本规范（major.minor.patch）
- `version` 必须大于该任务的所有已发布版本（递增原则）
- 发布后已发布版本不可修改（immutable）
- 原草稿版本保留，可用于历史追溯

**触发事件**：`TaskVersionPublished`

## 任务查询

### GetTaskDefinition

**请求**：

```http
GET /api/v1/task-definitions/{namespace:name:version}
```

**示例**：

```bash
# 获取已发布版本
GET /api/v1/task-definitions/com.company.tasks:data_cleaner:1.0.0

# 获取特定草稿版本
GET /api/v1/task-definitions/com.company.tasks:data_cleaner:draft-20250115140000

# 获取最新草稿版本（简写）
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
GET /api/v1/task-definitions/{namespace:name}/versions?status=DRAFT&limit=100&offset=0
```

**示例**：

```bash
# 列出所有版本
GET /api/v1/task-definitions/com.company.tasks:data_cleaner/versions

# 只列出草稿版本
GET /api/v1/task-definitions/com.company.tasks:data_cleaner/versions?status=DRAFT

# 只列出已发布版本
GET /api/v1/task-definitions/com.company.tasks:data_cleaner/versions?status=PUBLISHED
```

**说明**：

列出某个任务的所有版本，包括所有草稿版本和已发布版本。该查询返回版本的完整列表，帮助用户了解任务的演变历史。

**返回**：

```json
{
  "namespace": "com.company.tasks",
  "name": "data_cleaner",
  "versions": [
    {
      "version": "draft-20250115140000",
      "status": "DRAFT",
      "createdAt": "2025-01-15T14:00:00Z",
      "createdBy": "bob"
    },
    {
      "version": "draft-20250115130000",
      "status": "DRAFT",
      "createdAt": "2025-01-15T13:00:00Z",
      "createdBy": "alice"
    },
    {
      "version": "1.0.0",
      "status": "PUBLISHED",
      "publishedAt": "2025-01-15T12:00:00Z",
      "publishedBy": "alice",
      "releaseNotes": "Initial release"
    }
  ],
  "total": 3
}
```

**查询参数**：

- `status`: 可选，过滤版本状态（`DRAFT` 或 `PUBLISHED`）
- `limit`: 每页返回数量
- `offset`: 分页偏移量

**业务规则**：

- 返回的版本按时间降序排列（最新的在前）
- 草稿版本和已发布版本混合展示
- 可通过 `status` 参数过滤

---
