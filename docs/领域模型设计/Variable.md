# Variable（变量）

## 1. 概述

**变量（Variable）** 是数据流平台中用于参数传递和数据交换的核心机制。变量系统包含三个关键方面：

- **变量定义（Variable Definition）**：设计时使用，定义接口契约
- **变量引用（Variable Reference）**：运行时使用，指定如何获取值
- **变量解析（Variable Resolution）**：运行时使用，执行值的计算和转换

### 职责

1. **定义协议**：声明"有哪些参数/输出"、它们的类型、默认值
2. **值绑定**：指定运行时如何获取变量的值
3. **值转换**：支持通过表达式进行数据转换和组合

### 使用场景

- `TaskDefinition` 的 `inputVariables` 和 `outputVariables`：声明任务的接口
- `PipelineDefinition` 的 `inputVariables` 和 `outputVariables`：声明流水线的接口
- `Node` 的 `inputs`：指定如何为任务提供输入值

---

## 2. Variable Definition（变量定义）

### 2.1 概念

**VariableDefinition** 是一个 **值对象（Value Object）**，用于定义变量的元数据。

**核心设计原则**：

- VariableDefinition 只定义"这个变量是什么"
- 不涉及"如何获取"或"如何转换"（那是 Variable Reference 的职责）
- 只定义协议，不涉及运行时逻辑

### 2.2 数据结构

```yaml
VariableDefinition:
  # === 基础属性 ===
  name: string
    description: "变量名称，在同一上下文中唯一"
    required: true

  type: string
    description: "变量类型"
    required: true
    enum: ["string", "number", "boolean", "array", "object", "file"]

  required: boolean
    description: "该变量是否必需"
    required: false
    default: true

  description: string
    description: "变量的描述"
    required: true

  # === 值验证 ===
  validation: object  # 验证规则（Validation）：对变量值的约束条件
    description: "对变量值的验证规则"
    required: false
    properties:
      pattern: string  # 正则模式（Pattern）：用于字符串验证的正则表达式
        description: "正则表达式，用于字符串验证"
        required: false

      minLength: number  # 最小长度（MinLength）：字符串或数组的最小长度
        description: "最小长度"
        required: false

      maxLength: number  # 最大长度（MaxLength）：字符串或数组的最大长度
        description: "最大长度"
        required: false

      enum: array  # 枚举值（Enum）：允许的值列表
        description: "允许的值列表"
        required: false

      minValue: number  # 最小值（MinValue）：数字类型的最小值
        description: "最小值（用于数字）"
        required: false

      maxValue: number  # 最大值（MaxValue）：数字类型的最大值
        description: "最大值（用于数字）"
        required: false

  # === 默认值 ===
  defaultValue: any  # 默认值（DefaultValue）：变量未提供时使用的默认值
    description: "变量的默认值"
    required: false
    note: |
      - 当 required=true 时不应有 defaultValue（必需变量必须由外部提供）
      - 当 required=false 时，若无 defaultValue，默认为 null
```

### 2.3 配置示例

#### 必需的字符串参数

```yaml
- name: "modelVersion"
  type: "string"
  required: true
  description: "模型版本号"
  validation:
    pattern: "^v\\d+\\.\\d+\\.\\d+$"
```

#### 可选的数字参数（带默认值）

```yaml
- name: "partitionNum"
  type: "number"
  required: false
  description: "分区数"
  defaultValue: 10
```

#### 必需的数组输出

```yaml
- name: "cephPathsList"
  type: "array"
  required: true
  description: "Ceph 路径列表"
  validation:
    minLength: 1
```

### 2.4 验证规则（不变式）

1. **唯一性**：同一上下文中，`name` 必须唯一
2. **类型一致性**

   - `type` 必须是预定义的有效类型
   - 若指定了 `validation`，应与 `type` 相容
3. **默认值规则**

   - `required=true` 时不应有 `defaultValue`（必需变量必须由外部提供）
   - `required=false` 时若无 `defaultValue`，默认为 `null`
   - `defaultValue` 的类型应与 `type` 匹配

### 2.5 完整示例

```yaml
TaskDefinition:
  name: "task_assemble_parameters"
  version: "v1.0"
  description: "组装参数和收集 Ceph 路径"

  inputVariables:
    # 1. 必需的字符串参数
    - name: "modelVersion"
      type: "string"
      required: true
      description: "模型版本号"
      validation:
        pattern: "^v\\d+\\.\\d+\\.\\d+$"

    # 2. 必需的对象参数
    - name: "auditInfo"
      type: "object"
      required: true
      description: "审计信息，包含 timestamp 和 user 字段"

  outputVariables:
    # 1. 任务的输出变量
    - name: "sampleYmlConfig"
      type: "object"
      required: true
      description: "采样配置对象"

    # 2. 任务的输出变量（数组）
    - name: "cephPathsList"
      type: "array"
      required: true
      description: "收集到的 Ceph 路径列表"
      validation:
        minLength: 1

---

PipelineDefinition:
  name: "fusion_integration"
  version: "v1.0"

  inputVariables:
    - name: "modelVersion"
      type: "string"
      required: true
      description: "模型版本号"
      validation:
        pattern: "^v\\d+\\.\\d+\\.\\d+$"

    - name: "location"
      type: "string"
      required: true
      description: "地域"
      validation:
        enum: ["beijing", "shanghai", "hongkong"]

  outputVariables:
    # Pipeline 的输出变量（来自内部任务执行）
    - name: "dedupResult"
      type: "object"
      required: true
      description: "最终去重融合结果"
```

---

## 3. Variable Reference（变量引用）

### 3.1 概念

**VariableReference** 用于在 Node 的 `inputs` 中指定"如何获取变量的值"。

**核心设计原则**：

- 使用 Jinja2 表达式引用其他节点的输出或 Pipeline 的输入
- 支持数据转换和组合
- 在运行时解析，填充到 Task 的 inputVariables

### 3.2 数据结构

```yaml
Node:
  alias: string
    description: "节点别名（Alias）：节点在 Pipeline 中的唯一标识"

  taskRef: string
    description: "任务引用（TaskRef）：引用的 TaskDefinition 名称"

  inputs: object
    description: "输入绑定（Inputs）：将 Node 的输入绑定到 Task 的 inputVariables"
    note: "键是 TaskDefinition.inputVariables 中声明的变量名"
    patternProperties:
      ".*":
        type: string
        description: "Jinja2 表达式（Jinja2 Expression）：用于计算变量值的模板表达式"
```

### 3.3 引用语法

#### 引用 Pipeline 输入

```yaml
Node:
  alias: "extract"
  taskRef: "sql_query"
  inputs:
    modelVersion: "{{ pipeline.input.modelVersion }}"  # 直接引用
    location: "{{ pipeline.input.location }}"
```

#### 引用上游节点输出

```yaml
Node:
  alias: "transform"
  taskRef: "data_transform"
  inputs:
    inputPath: "{{ extract.output.filePath }}"  # 引用 extract 节点的输出
    rowCount: "{{ extract.output.rowCount }}"
```

#### 数据转换

```yaml
Node:
  alias: "merge"
  taskRef: "data_merge"
  inputs:
    # 字符串拼接
    outputPath: "{{ pipeline.input.basePath }}/merged/{{ pipeline.input.date }}"

    # 数值计算
    totalPartitions: "{{ extract.output.partitionNum * 2 }}"

    # 条件表达式
    processingMode: "{{ 'batch' if extract.output.rowCount > 10000 else 'streaming' }}"

    # 对象构造
    config: |
      {
        "version": "{{ pipeline.input.modelVersion }}",
        "location": "{{ pipeline.input.location }}",
        "timestamp": "{{ extract.output.completedAt }}"
      }

    # 数组操作
    paths: "{{ extract.output.paths + transform.output.paths }}"
```

### 3.4 完整示例

```yaml
PipelineDefinition:
  name: "data_processing"
  inputVariables:
    - name: "modelVersion"
      type: "string"
      required: true
    - name: "basePath"
      type: "string"
      required: true

  nodes:
    - alias: "extract"
      taskRef: "sql_query"
      inputs:
        # 直接引用 Pipeline 输入
        version: "{{ pipeline.input.modelVersion }}"
        outputPath: "{{ pipeline.input.basePath }}/extract"

    - alias: "transform"
      taskRef: "data_transform"
      startWhen: "event:extract.succeeded"
      inputs:
        # 引用上游输出
        inputPath: "{{ extract.output.filePath }}"

        # 数据转换
        config: |
          {
            "version": "{{ pipeline.input.modelVersion }}",
            "rowCount": {{ extract.output.rowCount }},
            "normalized": true
          }

    - alias: "load"
      taskRef: "data_load"
      startWhen: "event:transform.succeeded"
      inputs:
        # 组合多个来源
        inputPaths: "{{ [extract.output.filePath, transform.output.filePath] }}"
        targetPath: "{{ pipeline.input.basePath }}/final"
```

---

## 4. Variable Resolution（变量解析）

### 4.1 解析时机

变量在以下时机进行解析：

| 阶段                    | 时机                        | 解析内容                                             |
| ----------------------- | --------------------------- | ---------------------------------------------------- |
| **Pipeline 创建** | 用户提交 Pipeline Execution | 解析 `pipeline.input.*`，验证必需参数              |
| **Node 启动**     | 事件触发 Node 执行          | 解析 Node 的 `inputs`，填充 Task 的 inputVariables |
| **Task 执行**     | Task 运行时                 | 读取已解析的 inputVariables，执行任务逻辑            |
| **Task 完成**     | Task 成功完成               | 收集 Task 的 outputVariables，供下游引用             |

### 4.2 解析流程

```text
1. Node 收到启动事件
   ↓
2. 读取 Node.inputs 中的所有表达式
   ↓
3. 从变量池获取当前快照，构建变量上下文（Variable Context）
   - pipeline.input.*: Pipeline 的输入变量
   - <nodeAlias>.output.*: 其他节点的输出变量（如已写入）
   - <nodeAlias>.status: 节点状态
   - <nodeAlias>.health: 节点健康状态（流式任务）
   ↓
4. 使用 Jinja2 引擎（Jinja2 Engine）渲染每个表达式
   ↓
5. 验证结果是否符合 TaskDefinition.inputVariables 的要求
   - 类型匹配（Type Matching）
   - 必填检查（Required Check）
   - 验证规则（Validation Rule）
   ↓
6. 将解析后的值传递给 Task 执行
```

### 4.3 变量作用域与可见性

#### 变量池结构

**变量池（Variable Pool）** 是属于特定 PipelineExecution 的变量存储空间：

```yaml
# 变量池结构（按 PipelineExecution 隔离）
VariablePool[execution_id]:
  pipeline:
    input: {}     # Pipeline 的输入变量（PipelineExecution 创建时设置）
    output: {}    # Pipeline 的输出变量（PipelineExecution 完成后设置）

  nodes:
    <nodeAlias>:  # 每个节点（无论是否执行）
      output: {}    # 节点的输出变量（Task 成功完成后写入）
      status: ""    # 节点状态（succeeded/failed/skipped/running）
      health: ""    # 健康状态（仅流式任务）
      metrics: {}   # 运行时指标（仅流式任务）
```

#### 可见性规则

变量可见性通过 **命名空间隔离（Namespace Isolation）** 控制：

1. **PipelineExecution 级别隔离**

   - 每个 PipelineExecution 有独立的变量池
   - 无法跨 PipelineExecution 访问变量
   - 即使是同一 Pipeline 的不同执行实例，变量也完全隔离
2. **变量获取时机**

   - 变量在 Node 启动时从变量池中获取
   - 此时获取的是变量池的**当前快照**
   - 不存在显式的"上游节点"概念
3. **变量访问规则**

   - 可以引用变量池中任何已写入的变量
   - 引用不存在的变量会导致解析失败
   - 并行执行的节点可能获取到不同时刻的变量快照

#### 示例

```yaml
# 示例：并行节点可以引用相同的变量
PipelineDefinition:
  nodes:
    - alias: "extract"
      startWhen: "cron:0 2 * * *"
      # extract 完成后写入 output 到变量池

    - alias: "transform_a"
      startWhen: "event:extract.succeeded"
      inputs:
        inputPath: "{{ extract.output.filePath }}"  # 从变量池获取

    - alias: "transform_b"
      startWhen: "event:extract.succeeded"  # 与 transform_a 并行
      inputs:
        inputPath: "{{ extract.output.filePath }}"  # 从变量池获取相同变量
```

**关键点**：

- `transform_a` 和 `transform_b` 并行执行
- 两者都从变量池中读取 `extract.output.filePath`
- 没有"上游"概念，只有变量池和访问时机
- 事件依赖（`startWhen`）决定何时启动，不决定变量可见性

### 4.4 错误处理

**验证失败场景**：

```yaml
# 场景 1：缺少必需变量
TaskDefinition.inputVariables:
  - name: "modelVersion"
    required: true

Node.inputs:
  # 缺少 modelVersion

# 错误：Required variable 'modelVersion' is missing

---

# 场景 2：类型不匹配
TaskDefinition.inputVariables:
  - name: "partitionNum"
    type: "number"

Node.inputs:
  partitionNum: "{{ pipeline.input.location }}"  # location 是 string

# 错误：Type mismatch for 'partitionNum': expected number, got string

---

# 场景 3：引用不存在的变量
Node.inputs:
  inputPath: "{{ nonexistent.output.path }}"

# 错误：Variable 'nonexistent.output.path' not found in context

---

# 场景 4：验证规则不满足
TaskDefinition.inputVariables:
  - name: "modelVersion"
    type: "string"
    validation:
      pattern: "^v\\d+\\.\\d+\\.\\d+$"

Node.inputs:
  modelVersion: "{{ pipeline.input.version }}"  # version = "1.0"

# 错误：Validation failed for 'modelVersion': does not match pattern
```

---

## 5. 与其他概念的关系

### 5.1 与 Expression 的关系

- **Variable**：定义节点的**输入参数**如何获取（如 `{{ upstream.output.path }}`）
- **Expression**：定义节点的**触发条件和行为**（如 `event:upstream.succeeded`）

**使用场景对比**：

```yaml
Node:
  # Expression：定义何时执行
  startWhen: "event:extract.succeeded"
  skipWhen: "{{ extract.output.rowCount == 0 }}"

  # Variable：定义如何获取输入
  inputs:
    inputPath: "{{ extract.output.filePath }}"
    rowCount: "{{ extract.output.rowCount }}"
```

两者使用相同的 Jinja2 语法，但上下文和用途不同。

### 5.2 与 TaskDefinition 的关系

- **TaskDefinition**：声明任务需要哪些输入、产生哪些输出（接口契约）
- **Node.inputs**：实现接口契约，指定如何为这些输入提供值

**示例**：

```yaml
# TaskDefinition 声明接口
TaskDefinition:
  name: "sql_query"
  inputVariables:
    - name: "query"
      type: "string"
      required: true
    - name: "outputPath"
      type: "string"
      required: true

# Node 实现接口
Node:
  alias: "extract"
  taskRef: "sql_query"
  inputs:
    query: "SELECT * FROM users WHERE date = '{{ pipeline.input.date }}'"
    outputPath: "{{ pipeline.input.basePath }}/extract"
```

### 5.3 与 Event Payload 的关系

事件可以携带 payload，payload 数据可以作为变量引用：

```yaml
# 事件定义
TaskDefinition:
  type: "approval"
  externalEvents:
    - name: "approved"
      schema:
        approver: string
        comment: string

# 变量引用事件 payload
Node:
  alias: "process_approved"
  startWhen: "event:quality_check.approved"
  inputs:
    approver: "{{ quality_check.approved.approver }}"
    comment: "{{ quality_check.approved.comment }}"
```

## 参考

- [Expression.md](./Expression.md) - 表达式语法规范
- [Event.md](../架构设计/Event.md) - 事件系统架构
- [TaskDefinition.md](./TaskDefinition.md) - 任务定义
- [PipelineDefinition.md](./PipelineDefinition.md) - 流水线定义
- [Jinja2 官方文档](https://jinja.palletsprojects.com/)
