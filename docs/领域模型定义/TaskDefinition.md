# 聚合根：TaskDefinition（任务定义）

## 职责

- **任务配置**：定义任务的入参、出参、执行逻辑和执行类型
- **版本控制**：版本控制
- **任务复用**：支持跨流水线复用
- **任务执行**：支持指定输入参数运行任务
- **执行类型管理**：支持多种执行类型（ray_operator、model_inference、sql、pyspark_operator、flink 等）

## 核心概念

TaskDefinition 是**定义时配置**，描述任务本身的特征和逻辑，包括：

- 任务需要什么输入/输出（inputVariables/outputVariables）
- 任务的代码在哪里、如何执行（executionDefinition）
- 任务的**默认资源需求**（resources，可在 Node 中覆盖）
- 任务的健康检查规则（healthCheck，仅流式任务）
- 任务可发布的外部事件（externalEvents，仅外部任务）

TaskDefinition 不包含：

- 具体的输入值（在 Node 中通过 inputBindings 指定）
- 资源池/集群选择（在 Node 中通过 resourcePool 指定）
- 执行策略（超时、重试、优先级在 Node 中通过 executionPolicy 指定）
- 告警配置（在 Node 中通过 alertConfig 指定）

**资源配置原则**：

- TaskDefinition 中的 `resources` 是**默认值**，基于典型数据量估算
- Node 中可通过 `resources` 字段覆盖默认值，适应实际数据规模
- 建议 TaskDefinition 作者提供 `resourceGuidance`，说明不同数据量的资源建议

## 结构

```text
TaskDefinition（聚合根）
├── namespace: string (命名空间，如 "com.company.tasks")
├── name: string (任务名称，如 "data_cleaner")
├── version: string (版本号，如 "draft" | "1.0.0" | "1.1.0" | "2.0.0")
│   # 复合键：namespace + name + version 全局唯一
│
├── status: "DRAFT" | "PUBLISHED"
├── type: string (任务类型: "ray_operator" | "model_inference" | "sql" | "pyspark_operator" | "flink_sql" | "flink_jar")
├── inputVariables: VariableDefinition[]  # 输入参数定义
├── outputVariables: VariableDefinition[] # 输出结果定义
├── executionDefinition: object           # 执行定义（根据 type 不同而不同，见各任务类型文档）
├── resources: ResourceRequirements       # 资源需求定义
│   ├── cpu: string                       # CPU 需求（如 "4"、"8"）
│   ├── memory: string                    # 内存需求（如 "8Gi"、"16Gi"）
│   ├── gpu: integer?                     # GPU 数量（可选）
│   └── <engine-specific>: object?        # 引擎特定资源（如 sparkResources、flinkResources）
├── resourceGuidance: ResourceGuidance?   # 资源配置指导（可选）
├── externalEvents: ExternalEvent[]?      # 外部事件定义（仅外部任务）
├── healthCheck: HealthCheckConfig?       # 健康检查配置（仅流式任务）
├── releaseNotes: string?                 # 版本发布说明
├── createdAt: Timestamp
├── createdBy: string
├── publishedAt: Timestamp?               # 发布时间(仅 PUBLISHED 版本)
├── publishedBy: string?                  # 发布者(仅 PUBLISHED 版本)
└── metadata
    └── description: string
```

**唯一标识**：`namespace:name:version` 复合键全局唯一

**版本管理**：

- 同一个 `namespace:name` 可以有多个版本
- 每个版本是独立的 TaskDefinition 实例
- DRAFT 版本：`namespace:name:draft`（每个任务最多一个 DRAFT 版本）
- PUBLISHED 版本：`namespace:name:1.0.0`、`namespace:name:1.1.0` 等

## 支持的任务类型

- **[ray_operator](./TaskTypes/RayOperator.md)**: 使用 Ray 的灵活数据处理任务（仅批处理）
- **[model_inference](./TaskTypes/ModelInference.md)**: 批量模型推理任务，支持特征处理
- **[sql](./TaskTypes/SQL.md)**: 离线 SQL 查询（Hive、Spark SQL、Presto、Trino）
- **[pyspark_operator](./TaskTypes/PySparkOperator.md)**: PySpark 大规模数据处理任务
- **[flink_sql / flink_jar](./TaskTypes/FlinkSQL-FlinkJar.md)**: 实时流处理任务

## 详细字段说明

### resources（资源需求）

定义任务执行所需的**默认资源**，基于典型数据量估算。在 Pipeline 的 Node 中可以覆盖这些默认值。

```yaml
resources:
  cpu: string                      # CPU 核心数（如 "4"、"8"）
  memory: string                   # 内存大小（如 "8Gi"、"16Gi"）
  gpu: integer?                    # GPU 数量（可选）
  
  # 引擎特定资源（根据任务类型选择）
  rayResources: object?            # Ray 特定资源（ray_operator）
  sparkResources: object?          # Spark 特定资源（pyspark_operator、sql）
  flinkResources: object?          # Flink 特定资源（flink_sql、flink_jar）
  inferenceResources: object?      # 推理特定资源（model_inference）

# 可选：资源配置指导
resourceGuidance:
  description: string              # 默认配置的适用场景说明
  recommendations:                 # 不同数据量的资源建议
    - dataSize: string             # 数据量范围（如 "< 10GB"、"10-50GB"）
      resources: object            # 推荐的资源配置
```

**使用说明**：

- TaskDefinition 中的 `resources` 是**默认值**，通常基于中等数据量（如 10-50GB）估算
- 在 Pipeline 的 Node 中可以通过 `resources` 字段**完全覆盖**默认值
- 建议提供 `resourceGuidance` 帮助用户根据数据量选择合适的资源配置

详见各任务类型文档。

### externalEvents（外部事件声明）

用于声明任务可以发布的外部事件（如人工审核完成、外部系统回调等）。仅适用于需要外部系统交互的任务。

```yaml
externalEvents:
  - name: string              # 事件名称（如 approval_done、labeling_completed）
    description: string       # 事件描述
    schema: object            # 事件数据的 Schema
```

系统会自动生成回调端点：

```http
POST /api/v1/task-executions/{task_execution_id}/events/{event_name}
```

**参数说明**：

- `task_execution_id`: TaskExecution 的全局唯一 ID（任务执行实例）
- `event_name`: 事件名称（在 TaskDefinition 的 externalEvents 中定义）

**说明**：

- TaskExecution 可以是**独立执行**（用于测试、调试），也可以是 **Pipeline 中的执行**
- `task_execution_id` 全局唯一，无论是否在 Pipeline 中
- 回调 API 统一，外部系统只需要记住 `task_execution_id`

**示例 1：独立执行**

```bash
# 1. 创建独立的 Task 执行（测试用）
POST /api/v1/task-executions
{
  "taskDefinitionId": "manual_audit_v1",
  "taskDefinitionVersion": "1.0.0",
  "inputs": {
    "data_path": "s3://test/sample-data.parquet"
  }
}

# 响应
{
  "taskExecutionId": "task_exec_99999",
  "status": "waiting_event",
  "callbackUrl": "/api/v1/task-executions/task_exec_99999/events/approval_done"
}

# 2. 外部系统回调
POST /api/v1/task-executions/task_exec_99999/events/approval_done
{
  "status": "approved",
  "reviewer": "alice",
  "comment": "测试数据审核通过"
}
```

**示例 2：Pipeline 中执行**

```bash
# 1. 启动 Pipeline 执行
POST /api/v1/pipeline-executions
{
  "pipelineDefinitionId": "data_processing_v1",
  "inputs": {...}
}

# 响应（包含各个 Node 的 TaskExecution ID）
{
  "pipelineExecutionId": "pipe_exec_789",
  "nodes": [
    {
      "nodeAlias": "manual_audit",
      "taskExecutionId": "task_exec_002",
      "status": "waiting_event",
      "callbackUrl": "/api/v1/task-executions/task_exec_002/events/approval_done"
    }
  ]
}

# 2. 外部系统回调（使用 TaskExecution ID）
POST /api/v1/task-executions/task_exec_002/events/approval_done
{
  "status": "approved",
  "reviewer": "bob",
  "comment": "数据质量良好，批准处理"
}

# 系统自动关联到 Pipeline，继续执行下游节点
```

### healthCheck（健康检查配置）

用于流式任务的健康检查，系统会周期性检查并发布 `statusChanged` 事件。仅适用于 `flink_sql` 和 `flink_jar` 任务。

```yaml
healthCheck:
  enabled: boolean            # 是否启用健康检查
  interval: integer           # 检查间隔（秒）
  endpoint: string?           # 健康检查端点（可选）
  timeout: integer?           # 检查超时（秒，默认 10）
  healthyThreshold: integer?  # 连续成功次数判定为健康（默认 1）
  unhealthyThreshold: integer? # 连续失败次数判定为不健康（默认 3）
```

详见 [FlinkSQL-FlinkJar](./TaskTypes/FlinkSQL-FlinkJar.md) 文档。

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
