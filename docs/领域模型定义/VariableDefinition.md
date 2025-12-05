# VariableDefinition 设计

## 1. 概念

`VariableDefinition` 是一个 **Value Object**，用于定义变量的元数据和来源。在 DDD 架构中被使用于：

- `TaskDefinition` 的 `inputVariables` 和 `outputVariables`
- `PipelineDefinition` 的 `inputVariables` 和 `outputVariables`

**职责**：定义协议（接口契约），声明"有哪些参数/输出"、它们的类型、默认值

**核心设计**：VariableDefinition 只定义"这个变量是什么"，不涉及"如何获取"或"如何转换"

**与 VariableReference 的区别**：

- **VariableDefinition**：设计时使用，定义接口契约
  - inputVariables：声明"Task 需要哪些参数"（名称、类型、是否必需、默认值）
  - outputVariables：声明"Task 产生哪些输出"（名称、类型、描述）
  - **只定义协议，不涉及运行时如何获取值**

- **VariableReference**：运行时使用，Node 中表示"如何获取和使用变量"（参见 `VariableReference.md`）
  - 在 Node 的 inputs 中使用
  - 通过 Jinja2 表达式指定"从哪里获取值"
  - 表达式中引用的数据被用来填充 Task 的 inputVariables

---

## 2. 结构

```yaml
VariableDefinition:
  # === 基础属性（定义协议） ===
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

  # === 值验证（定义协议） ===
  validation: object
    description: "对变量值的验证规则"
    required: false
    properties:
      pattern: string
        description: "正则表达式，用于字符串验证"
        required: false

      minLength: number
        description: "最小长度"
        required: false

      maxLength: number
        description: "最大长度"
        required: false

      enum: array
        description: "允许的值列表"
        required: false

      minValue: number
        description: "最小值（用于数字）"
        required: false

      maxValue: number
        description: "最大值（用于数字）"
        required: false

  # === 默认值（定义协议） ===
  defaultValue: any
    description: "变量的默认值"
    required: false
    note: |
      - 当 required=true 时不应有 defaultValue（必需变量必须由外部提供）
      - 当 required=false 时，若无 defaultValue，默认为 null
```

**说明**：

- VariableDefinition **只定义协议**（名称、类型、验证）
- 值的**转换**由 **VariableReference** 处理（Node 中的 Jinja2 表达式）
- "从哪里获取值"也由 **VariableReference** 负责（参见 `2.2 值对象-变量引用.md`）

---

## 3. 简单示例

### 3.1 来自 Pipeline 输入的变量

## 3. 简单示例

### 3.1 必需的字符串参数

```yaml
- name: "modelVersion"
  type: "string"
  required: true
  description: "模型版本号"
  validation:
    pattern: "^v\\d+\\.\\d+\\.\\d+$"
```

### 3.2 可选的数字参数（带默认值）

```yaml
- name: "partitionNum"
  type: "number"
  required: false
  description: "分区数"
  defaultValue: 10
```

### 3.3 必需的数组输出

```yaml
- name: "cephPathsList"
  type: "array"
  required: true
  description: "Ceph 路径列表"
  validation:
    minLength: 1
```

---

## 4. 不变式

1. **唯一性**：同一上下文中，`name` 必须唯一

2. **类型一致性**
   - `type` 必须是预定义的有效类型
   - 若指定了 `validation`，应与 `type` 相容

3. **默认值**
   - `required=true` 时不应有 `defaultValue`（必需变量必须由外部提供）
   - `required=false` 时若无 `defaultValue`，默认为 `null`
   - `defaultValue` 的类型应与 `type` 匹配

---

## 5. 完整示例：Task 中的变量定义

```yaml
TaskDefinition:
  name: "task_assemble_parameters"
  version: "v1.0"
  description: "组装参数和收集 Ceph 路径"

  inputVariables:
    # 1. 必需参数（来自 Pipeline 输入）
    - name: "modelVersion"
      type: "string"
      required: true
      description: "模型版本号"
      validation:
        pattern: "^v\\d+\\.\\d+\\.\\d+$"

    # 3. 必需参数（带转换）
    - name: "auditInfo"
      type: "object"
      required: true
      description: "审计信息"
      transformation:
        type: "jinja2"
        template: |
          {
            "timestamp": "{{ value.timestamp }}",
            "user": "{{ value.user | upper }}"
          }

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

**说明**：

- VariableDefinition 只定义**协议**（参数的名称、类型、验证规则）
- **不涉及运行时如何获取或转换值**（那是 VariableReference 的职责）
- 实际参数绑定和转换在 Pipeline 的 Node 中通过 VariableReference 完成
- 参见 `2.2 值对象-变量引用.md` 了解如何在 Node 中使用变量

---
