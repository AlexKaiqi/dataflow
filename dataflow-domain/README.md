# TaskDefinition 领域模型实现

## 概述

TaskDefinition（任务定义）是可复用的任务模板，定义了一类任务"能做什么"、"需要什么输入"、"产生什么输出"。

## 核心类结构

### 1. 聚合根

- **TaskDefinition**: 任务定义聚合根
  - 管理任务的所有版本
  - 提供版本创建、发布等操作
  - 确保业务不变式

### 2. 实体

- **TaskVersion**: 任务版本
  - 表示任务定义的某个特定版本
  - 分为草稿版本（DRAFT）和已发布版本（PUBLISHED）
  - 包含输入输出变量、执行配置等

### 3. 值对象

- **VariableDefinition**: 变量定义
  - 定义输入/输出变量的元数据
  - 包含类型、验证规则、默认值等

- **ValidationRule**: 验证规则
  - 定义变量值的约束条件
  - 支持正则、长度、枚举、范围等验证

### 4. 枚举

- **TaskType**: 任务类型
  - PYSPARK_OPERATOR: PySpark批处理任务
  - SQL_OPERATOR: SQL批处理任务
  - RAY_OPERATOR: Ray分布式计算任务
  - STREAMING_OPERATOR: 流处理任务
  - APPROVAL: 审批任务
  - MODEL_INFERENCE: 模型推理任务

- **VersionStatus**: 版本状态
  - DRAFT: 草稿状态（可修改）
  - PUBLISHED: 已发布状态（不可修改）

- **VariableType**: 变量类型
  - STRING, NUMBER, BOOLEAN, ARRAY, OBJECT, FILE

### 5. 领域事件

- **TaskDefinitionCreated**: 任务定义已创建
- **TaskVersionCreated**: 任务版本已创建
- **TaskVersionPublished**: 任务版本已发布

### 6. 仓储接口

- **TaskDefinitionRepository**: 任务定义仓储接口

### 7. 领域服务

- **TaskDefinitionService**: 任务定义领域服务
  - 提供创建、发布、删除等业务操作
  - 处理领域事件发布

## 使用示例

### 创建任务定义

```java
TaskDefinitionService service = new TaskDefinitionService(repository);

// 创建一个 PySpark 任务定义
TaskDefinition taskDef = service.createTaskDefinition(
    "com.company.tasks",      // namespace
    "data_transform",         // name
    TaskType.PYSPARK_OPERATOR, // type
    "数据转换任务",            // description
    "alice"                   // createdBy
);
// 自动创建初始草稿版本: draft-20250109xxxxxx
```

### 添加输入输出变量

```java
// 添加输入变量
VariableDefinition inputVar = VariableDefinition.builder()
    .name("data_path")
    .type(VariableType.STRING)
    .required(true)
    .description("输入数据路径")
    .validation(ValidationRule.builder()
        .pattern("^s3://.*")
        .build())
    .build();

service.addInputVariable(
    "com.company.tasks",
    "data_transform",
    "draft-20250109xxxxxx",
    inputVar
);

// 添加输出变量
VariableDefinition outputVar = VariableDefinition.builder()
    .name("rows_processed")
    .type(VariableType.NUMBER)
    .required(false)
    .description("处理的数据行数")
    .build();

service.addOutputVariable(
    "com.company.tasks",
    "data_transform",
    "draft-20250109xxxxxx",
    outputVar
);
```

### 创建新的草稿版本

```java
// 基于最新草稿创建新版本
TaskVersion newDraft = service.createDraftVersion(
    "com.company.tasks",
    "data_transform",
    "bob"
);
// 新草稿版本: draft-20250109yyyyyy
```

### 发布版本

```java
// 发布草稿版本为正式版本
service.publishVersion(
    "com.company.tasks",
    "data_transform",
    "draft-20250109yyyyyy",  // 草稿版本号
    "1.0.0",                 // 语义化版本号
    "初始版本发布",          // 发布说明
    "alice"                  // 发布者
);
```

### 查询任务定义

```java
// 查找任务定义
Optional<TaskDefinition> taskDef = repository.findByNamespaceAndName(
    "com.company.tasks",
    "data_transform"
);

// 获取最新已发布版本
TaskVersion published = taskDef.get().getLatestPublishedVersion();

// 获取最新草稿版本
TaskVersion draft = taskDef.get().getLatestDraftVersion();

// 获取特定版本
TaskVersion version = taskDef.get().getVersion("1.0.0");
```

## 设计原则

### 1. 单一职责

TaskDefinition 只关心"是什么"，不关心"何时执行"：
- ✅ 定义任务类型、输入输出、执行逻辑
- ✅ 定义任务支持哪些行为（actions）
- ✅ 定义任务产生哪些事件（events）
- ❌ 不定义何时启动（startWhen）
- ❌ 不定义何时重试（retryWhen）
- ❌ 不定义依赖关系

### 2. 版本管理

- **草稿版本**: 追加式创建，每次修改创建新的 draft-{timestamp}
- **已发布版本**: 完全不可变（immutable）
- **版本递增**: 新版本号必须大于所有已发布版本
- **引用约束**: 被引用的版本不允许删除

### 3. 不变式保护

TaskDefinition 通过以下机制保护不变式：
- 复合键全局唯一：`namespace:name:version`
- 已发布版本不可修改
- 删除前检查引用约束
- 语义化版本号格式验证

## 领域事件

所有领域事件遵循统一格式：

```json
{
  "eventId": "uuid",
  "eventType": "TaskDefinitionCreated",
  "timestamp": "2025-01-09T10:00:00Z",
  "aggregateId": "namespace:name",
  "version": 1,
  "payload": {
    // 事件特定数据
  }
}
```

## 下一步

- [ ] 实现 TaskDefinitionRepository 的基础设施层实现
- [ ] 实现事件发布机制
- [ ] 添加单元测试
- [ ] 实现不同 TaskType 的特定配置类
- [ ] 实现 PipelineDefinition 领域模型
