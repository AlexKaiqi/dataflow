
# **《PipelineDefinition 流水线定义》**

## 概述

**《PipelineDefinition 流水线定义》**是编排聚合根，定义了数据处理流水线的完整结构。它通过一系列 **《Node 节点》**来组织执行流程，每个 **《Node 节点》**引用一个 **《TaskDefinition 任务定义》**并通过事件驱动的方式控制执行顺序和依赖关系。

**核心特点**：

- **事件驱动编排**：**《Node 节点》**之间不使用显式的 ConditionNode、JoinNode、ForkNode，而是通过 `startWhen` **《Expression 表达式》**订阅 **《Event 事件》**来实现控制流
- **可复用的任务模板**：**《Node 节点》**引用 **《TaskDefinition 任务定义》**，**《TaskDefinition 任务定义》**可跨 **《Pipeline 流水线》**复用
- **版本管理**：支持 DRAFT 和 PUBLISHED 状态的版本控制
- **输入/输出定义**：**《Pipeline 流水线》**可作为"黑盒"被其他 **《Pipeline 流水线》**引用（类似于 **《TaskDefinition 任务定义》**）

## 核心职责

1. **定义节点列表**：**《Pipeline 流水线》**由一组 **《Node 节点》**组成，每个 **《Node 节点》**引用一个 **《TaskDefinition 任务定义》**
2. **事件驱动编排**：**《Node 节点》**通过 `startWhen` **《Expression 表达式》**订阅上游 **《Event 事件》**，实现依赖控制
3. **输入/输出接口**：定义 **《Pipeline 流水线》**级别的输入和输出变量，支持嵌套复用
4. **版本管理**：维护 DRAFT 和 PUBLISHED 版本，确保已发布版本不可变

## 设计原则

1. **扁平化结构**：**《Pipeline 流水线》**只有 `nodes[]` 数组，没有单独的 `tasks` 数组
2. **事件订阅模式**：依赖关系通过 `startWhen` **《Event 事件》** **《Expression 表达式》**隐式定义，而非显式的 `dependsOn` 字段
3. **无显式控制节点**：不需要 ConditionNode、JoinNode、ForkNode，所有控制流通过 **《Expression 表达式》**实现
4. **任务复用**：**《TaskDefinition 任务定义》**独立于 **《Pipeline 流水线》**存在，可被多个 **《Pipeline 流水线》**引用

## **《Pipeline 流水线》**与 **《Node 节点》**的关系

| 维度 | **《PipelineDefinition 流水线定义》** | **《Node 节点》** |
|------|-------------------|------|
| 定义 | 编排的整体结构 | 编排中的单个执行单元 |
| 职责 | 定义"有哪些节点" | 定义"何时执行、如何执行" |
| 复用性 | 可作为整体被其他 **《Pipeline 流水线》**引用 | 引用可复用的 **《TaskDefinition 任务定义》** |
| 控制流 | 通过 nodes 数组组织 | 通过 startWhen 订阅上游 **《Event 事件》** |

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
      nodes: Node[]                    # 详见 Node.md
        - id: string                   # 节点 ID
          name: string                 # 节点名称
          taskDefinition: TaskDefinitionRef   # 引用的任务定义
          inputBindings: Map[string, Expression]  # 输入绑定
          startWhen: Expression        # 何时启动（必填）
          stopWhen: Expression?        # 何时停止（仅流处理）
          retryWhen: Expression?       # 何时重试
          alertWhen: Expression?       # 何时告警
      
      # 版本元数据
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

Pipeline 的**唯一编排结构**，所有执行逻辑都通过 Node 定义。

**关键点**：

- Pipeline 没有单独的 `tasks` 字段，Node 通过 `taskDefinition.ref` 引用 TaskDefinition
- Node 之间的依赖关系通过 `startWhen` 表达式隐式定义，无需显式的 `dependsOn` 字段
- 控制流（条件分支、并行、汇聚）都通过 `startWhen` 表达式实现

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
  
  # 依赖节点：订阅 extract.completed 事件
  - id: transform
    taskDefinition:
      ref: "com.company:data_transformer:1.0.0"
    inputBindings:
      input_path: "{{ extract.output_path }}"
    startWhen: "event:extract.completed"
  
  # Join 节点：订阅多个上游事件
  - id: merge
    taskDefinition:
      ref: "com.company:data_merger:1.0.0"
    inputBindings:
      path_a: "{{ branch_a.output_path }}"
      path_b: "{{ branch_b.output_path }}"
    startWhen: "event:branch_a.completed && event:branch_b.completed"
  
  # 条件分支：高质量路径
  - id: publish_high_quality
    taskDefinition:
      ref: "com.company:publisher:1.0.0"
    inputBindings:
      data_path: "{{ quality_check.output_path }}"
    startWhen: "event:quality_check.completed && {{ quality_check.score > 0.9 }}"
  
  # 条件分支：低质量需要审批
  - id: approval
    taskDefinition:
      ref: "com.company:approval:1.0.0"
    inputBindings:
      reason: "质量评分: {{ quality_check.score }}"
    startWhen: "event:quality_check.completed && {{ quality_check.score <= 0.9 }}"
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

### versions（版本管理）

Pipeline 支持版本管理，每个版本有独立的 `nodes`、`inputVariables`、`outputVariables`。

**版本状态**：

- **DRAFT**：草稿版本，可以修改，最多只有一个
- **PUBLISHED**：已发布版本，不可变，可以有多个

**版本示例**：

```yaml
versions:
  - version: "draft"
    status: "DRAFT"
    nodes: [...]
    inputVariables: [...]
  
  - version: "1.0.0"
    status: "PUBLISHED"
    nodes: [...]
    releaseNotes: "Initial release"
    createdAt: "2024-01-01T00:00:00Z"
    createdBy: "user@company.com"
  
  - version: "1.1.0"
    status: "PUBLISHED"
    nodes: [...]
    releaseNotes: "Added quality check"
    createdAt: "2024-02-01T00:00:00Z"
    createdBy: "user@company.com"
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
        - id: extract
          name: "提取源数据"
          taskDefinition:
            ref: "com.company:spark_extractor:1.0.0"
          inputBindings:
            table_name: "{{ pipeline.input.source_table }}"
            partition_date: "{{ pipeline.input.target_date }}"
          startWhen: "event:pipeline.started"
        
        # 2. 数据转换
        - id: transform
          name: "数据转换"
          taskDefinition:
            ref: "com.company:spark_transformer:1.0.0"
          inputBindings:
            input_path: "{{ extract.output_path }}"
          startWhen: "event:extract.completed"
          retryWhen: "{{ attempts < 3 }}"
        
        # 3. 质量检查
        - id: quality_check
          name: "质量检查"
          taskDefinition:
            ref: "com.company:quality_checker:1.0.0"
          inputBindings:
            data_path: "{{ transform.output_path }}"
            threshold: "{{ pipeline.input.quality_threshold }}"
          startWhen: "event:transform.completed"
        
        # 4a. 高质量路径：直接发布
        - id: publish_high_quality
          name: "发布高质量数据"
          taskDefinition:
            ref: "com.company:publisher:1.0.0"
          inputBindings:
            data_path: "{{ transform.output_path }}"
          startWhen: "event:quality_check.completed && {{ quality_check.score > 0.9 }}"
        
        # 4b. 低质量路径：需要审批
        - id: approval
          name: "数据审批"
          taskDefinition:
            ref: "com.company:approval:1.0.0"
          inputBindings:
            title: "低质量数据发布审批"
            description: "质量评分: {{ quality_check.score }}"
            approvers: ["admin@company.com"]
          startWhen: "event:quality_check.completed && {{ quality_check.score <= 0.9 }}"
        
        # 5. 汇聚：两条路径都完成后发送通知
        - id: notify
          name: "发送通知"
          taskDefinition:
            ref: "com.company:notifier:1.0.0"
          inputBindings:
            message: "Pipeline 执行完成"
          startWhen: "event:publish_high_quality.completed || event:approval.approved"
      
      releaseNotes: "Initial release with quality check and approval"
      createdAt: "2024-01-01T00:00:00Z"
      createdBy: "user@company.com"
```

**控制流说明**：

- **顺序执行**：`extract` → `transform` → `quality_check`（通过 `startWhen` 订阅上游 `completed` 事件）
- **条件分支**：质量检查后根据评分分为两条路径
  - 高质量路径：`quality_check.score > 0.9` 触发 `publish_high_quality`
  - 低质量路径：`quality_check.score <= 0.9` 触发 `approval`
- **汇聚（Join）**：`notify` 订阅两条路径的完成事件，任意一条完成即触发

## 不变式（Invariants）

1. **唯一性约束**
   - `id` 全局唯一
   - `namespace + name` 组合全局唯一
   - 同一 version 内，`nodes[].id` 必须唯一

2. **版本控制约束**
   - DRAFT 版本最多一个
   - PUBLISHED 版本不可变
   - 版本号必须遵循语义化版本规范（如 "1.0.0"）

3. **节点引用有效性**
   - `node.taskDefinition.ref` 引用的 TaskDefinition 必须存在且已发布
   - `inputBindings` 中的 key 必须对应 TaskDefinition 的 `inputVariables`
   - TaskDefinition 的必填输入变量必须在 `inputBindings` 中绑定

4. **事件可达性**
   - `startWhen` 中引用的事件必须由某个 Node 产生（或由 Pipeline 产生）
   - Pipeline 必须有至少一个 Node 订阅 `event:pipeline.started`（起始节点）

5. **输出变量一致性**
   - Pipeline 的 `outputVariables` 必须由某个 Node 的输出提供
   - 输出变量的绑定通常在 Pipeline 执行结束时计算

## 领域事件

- `PipelineDefinitionCreated`：Pipeline 创建（DRAFT 版本）
- `PipelineDefinitionModified`：Pipeline 修改（DRAFT 版本）
- `PipelineDefinitionPublished`：Pipeline 发布（新增 PUBLISHED 版本）

## 命令和查询

### 创建 Pipeline

```
CreatePipelineDefinition(namespace, name, description, owner) -> PipelineDefinition
```

创建新的 Pipeline 定义，自动生成 "draft" 版本。

### 修改 Pipeline

```
ModifyPipelineDefinition(pipelineId, inputVariables?, outputVariables?, nodes?) -> PipelineDefinition
```

修改 DRAFT 版本的 Pipeline，PUBLISHED 版本不可修改。

### 发布 Pipeline

```
PublishPipelineDefinition(pipelineId, version, releaseNotes?) -> PipelineVersion
```

将 DRAFT 版本发布为 PUBLISHED 版本，发布后不可变。

### 查询 Pipeline

```plaintext
GetPipelineDefinition(namespace, name, version?) -> PipelineDefinition
ListPipelineVersions(pipelineId) -> PipelineVersion[]
```

## 与其他领域模型的关系

```plaintext
┌──────────────────────────────────────────┐
│ PipelineDefinition                        │
│                                           │
│  nodes: Node[]                            │
│    │                                      │
│    └──> Node                              │
│          ├─ taskDefinition ───────────┐   │
│          │                            │   │
│          ├─ inputBindings             │   │
│          │   └─> Expression ───────┐  │   │
│          │                         │  │   │
│          ├─ startWhen: Expression   │  │   │
│          ├─ stopWhen?: Expression   │  │   │
│          ├─ retryWhen?: Expression  │  │   │
│          └─ alertWhen?: Expression  │  │   │
│                                     │  │   │
└─────────────────────────────────────┼──┼───┘
                                      │  │
                ┌─────────────────────┘  │
                │                        │
                ▼                        ▼
      ┌──────────────────┐    ┌──────────────────┐
      │ TaskDefinition   │    │ Expression       │
      │                  │    │  - event:...     │
      │ - inputVariables │    │  - cron:...      │
      │ - outputVariables│    │  - {{ state }}   │
      │ - behaviors      │    └──────────────────┘
      │ - events         │
      └──────────────────┘
```

**关键关系**：

- **PipelineDefinition → Node**（组合）：Pipeline 包含多个 Node
- **Node → TaskDefinition**（引用）：Node 通过 `taskDefinition.ref` 引用可复用的任务模板
- **Node → Expression**（组合）：Node 通过多种表达式控制执行时机和条件
- **Expression → Node/TaskDefinition**（引用）：表达式中引用其他 Node 产生的事件或变量
