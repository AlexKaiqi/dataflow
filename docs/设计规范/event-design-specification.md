# 事件设计规范

## 概述

本规范定义了系统中所有领域事件的设计原则、消息结构和使用场景。本规范用于**领域建模阶段**，指导事件的设计和文档编写，确保事件的一致性、可追溯性和可维护性。

## 设计原则

### 轻量化设计

领域事件遵循**轻量化设计**原则：

- **最小必要信息**：只包含标识变化的关键信息
- **引用标识符**：包含足够的标识符让订阅者能查询完整数据
- **避免数据冗余**：不在事件中复制完整的聚合根结构
- **可追溯性**：通过版本号可以查询到任何时刻的完整状态

### Command vs Event

**Command（命令）** 表达"修改意图"：

- 用户通过 API 发送的请求体
- 包含要修改的字段和值
- 在执行前发送

**Event（事件）** 记录"已发生的事实"：

- Command 执行后生成
- 包含执行上下文（如新版本号、时间戳、事件ID）
- 不包含完整状态快照（通过版本号查询）

**关系**：

```text
Event = Command Input + Execution Context
```

```text
用户发送 Command    →    系统执行    →    生成 Event
   (意图)                (处理)           (事实记录)
```

**示例对比**：

| 来源                        | 字段                               | 说明                 |
| --------------------------- | ---------------------------------- | -------------------- |
| **Command Input**     | `modifiedBy`, `inputVariables` | 用户提供的修改内容   |
| **Execution Context** | `version`, `previousVersion`   | 执行时确定的版本信息 |
| **Execution Context** | `timestamp`, `eventId`         | 系统生成的时间和ID   |

## 事件消息体通用结构

所有领域事件都遵循统一的消息结构：

```json
{
  "eventId": "uuid",           // 事件唯一标识符
  "eventType": "string",       // 事件类型名称
  "timestamp": "ISO8601",      // 事件发生时间
  "aggregateId": "string",     // 聚合根标识符
  "version": "integer",        // 事件序列号（单调递增）
  "payload": {                 // 事件负载（轻量化设计）
    // 最小必要信息
  }
}
```

### 字段说明

#### eventId

- **类型**：UUID 字符串
- **格式**：`xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`
- **用途**：事件的全局唯一标识符
- **生成**：系统自动生成
- **示例**：`"550e8400-e29b-41d4-a716-446655440000"`

#### eventType

- **类型**：字符串
- **格式**：PascalCase，描述性事件名称
- **用途**：事件类型，用于路由和处理
- **命名规范**：
  - 使用过去时态（如 `Created`, `Modified`, `Published`）
  - 包含聚合根名称（如 `TaskDefinitionCreated`）
- **示例**：`"TaskDefinitionModified"`, `"PipelineExecutionStarted"`

#### timestamp

- **类型**：ISO 8601 日期时间字符串
- **格式**：`YYYY-MM-DDTHH:mm:ss.sssZ`
- **用途**：事件发生的时间戳
- **时区**：UTC（以 Z 结尾）
- **示例**：`"2025-01-15T14:00:00.123Z"`

#### aggregateId

- **类型**：字符串
- **格式**：取决于聚合根类型
- **用途**：聚合根标识符，用于关联同一聚合根的所有事件
- **格式示例**：
  - TaskDefinition: `namespace:name`（不包含版本号）
  - PipelineDefinition: `namespace:name`
  - PipelineExecution: `execution_id`
- **示例**：`"com.company.tasks:data_cleaner"`

#### version

- **类型**：正整数
- **范围**：从 1 开始递增
- **用途**：该聚合根的事件序列号，用于事件溯源和顺序保证
- **保证**：单调递增，无间隙
- **示例**：`1`, `2`, `3`, ...

**注意**：此 `version` 字段是事件的序列号，不是聚合根的版本号（如 `draft-20250115140000`）。

#### payload

- **类型**：JSON 对象
- **内容**：轻量化的事件负载
- **包含**：
  - 聚合根的关键标识符（namespace, name, version 等）
  - 变更相关的元数据（如 modifiedBy, previousVersion）
  - 业务相关的关键信息（如 releaseNotes）
- **不包含**：
  - 完整的聚合根状态快照
  - 详细的变更内容（通过版本查询获取）

## 事件发布原则

### 发布时机

事件应在业务操作**成功完成后**立即发布：

1. 执行业务逻辑
2. 持久化状态变更
3. 发布领域事件

### 发布保证

- **至少一次（At-least-once）**：事件可能被重复发送，订阅者需要实现幂等性
- **顺序保证**：同一聚合根的事件按 `version` 顺序发布
- **异步发布**：不阻塞业务操作

## 事件命名规范

### 事件类型命名

格式：`{AggregateName}{Action}[{Detail}]`

- **AggregateName**：聚合根名称（PascalCase）
- **Action**：动作（过去时态，如 Created, Modified, Published）
- **Detail**：可选的详细说明

**示例**：

- `TaskCreated` - 任务已创建
- `TaskDraftVersionCreated` - 任务草稿版本已创建
- `TaskVersionPublished` - 任务版本已发布
- `PipelineCreated` - 流水线已创建
- `PipelineDraftVersionCreated` - 流水线草稿版本已创建
- `PipelineVersionPublished` - 流水线版本已发布
- `PipelineExecutionStarted` - 流水线执行已启动
- `PipelineExecutionCompleted` - 流水线执行已完成
- `PipelineExecutionFailed` - 流水线执行已失败

### Payload 字段命名

- 使用 **camelCase**
- 使用描述性名称
- 遵循领域模型的术语

**示例**：

```json
{
  "namespace": "com.company.tasks",
  "name": "data_cleaner",
  "version": "draft-20250115140000",
  "basedOn": "draft-20250115130000",
  "createdBy": "bob",
  "releaseNotes": "Fix bug in data validation"
}
```

## 版本兼容性

### 事件演进策略

事件结构需要演进时，遵循以下原则：

1. **只增加字段，不删除字段**：保持向后兼容
2. **新字段设为可选**：旧版本订阅者可以忽略
3. **使用版本化事件类型**：如需重大变更，创建新的事件类型（如 `DraftVersionCreatedV2`）

**示例**：

```json
// V1
{
  "eventType": "TaskDraftVersionCreated",
  "payload": {
    "version": "draft-20250115140000",
    "createdBy": "bob"
  }
}

// V2 - 添加新字段
{
  "eventType": "DraftVersionCreated",
  "payload": {
    "version": "draft-20250115140000",
    "basedOn": "draft-20250115130000",  // ← 新增
    "createdBy": "bob",
    "reason": "Bug fix"  // ← 新增（可选）
  }
}
```

### 事件消费者兼容性

订阅者需要处理：

- **未知字段**：忽略不认识的字段
- **缺失字段**：为缺失的可选字段提供默认值
- **类型检查**：验证字段类型，优雅处理类型错误

## 参考文档

- [TaskDefinition 领域模型](../领域模型定义/TaskDefinition.md)
- [领域驱动设计文档规范](./domain-model-documentation.md)
- [架构概览](../架构设计/architecture-overview.md)
