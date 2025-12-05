# VariableReference 设计

## 1. 概念

`VariableReference` 是一个 **Value Object**，用于表示**节点中对变量的引用和转换**。

在 DDD 架构中被使用于：

- `PipelineNode` 的 `inputs`（节点的输入变量绑定）
- `ConditionNode` 的 `condition.variableReferences`（条件表达式中的变量引用）
- 任何需要消费变量的节点

**核心职责**：

- **关联**：将上游的变量（流程变量、系统变量、环境变量）与当前节点的输入参数进行关联
- **转换**：在使用变量前对其进行数据转换和格式化
- **验证**：确保引用的变量存在且类型匹配

**与 VariableDefinition 的区别**：

| 维度 | VariableDefinition | VariableReference |
|------|------------------|-----------------|
| **位置** | Task/Pipeline 的定义中 | Node 中使用 |
| **职责** | 声明"有哪些输入/输出" | 表示"如何使用变量" |
| **来源** | 定义变量来自哪里 | 选择使用哪个上游的哪个字段 |
| **用途** | 编程时定义接口契约 | 运行时关联和转换数据 |
| **例子** | Task 说"我需要输入 model_version" | Node 说"用 pipe.model_version 来填充 model_version" |

---

## 2. 结构

```yaml
VariableReference:
  # === 基础标识 ===
  name: string
    description: "变量在当前节点中的名称（或目标参数名）"
    required: true
    example: "var1, model_version, dataset_paths"

  # === 原始表达式（用户输入） ===
  expression: string
    description: "Jinja2 表达式，用户直接输入，完整表示变量来源和转换"
    required: true
    examples:
      - "{{pipe.model_version}}"                    # 来自 Pipeline 输入
      - "{{upstream_node.output_field}}"            # 来自上游节点
      - "{{sys.execution_id}}"                      # 系统变量
      - "{{env.API_KEY}}"                           # 环境变量
      - "{{collect_paths.ceph_paths | length}}"    # 带 Jinja2 过滤器
      - "{{node_a.output ?? default_value}}"        # 降级处理

  # === 类型信息 ===
  type: string
    description: "变量的推断类型"
    required: false
    enum: ["string", "number", "boolean", "array", "object", "unknown"]
    default: "unknown"
    note: |
      - 如果引用来自 VariableDefinition，可从源定义推断
      - 如果无法推断，则为 "unknown"，运行时由 Jinja2 决定

  # === 可空性 ===
  nullable: boolean
    description: "是否允许结果为 null/undefined"
    required: false
    default: false

  # === 默认值 ===
  defaultValue: any
    description: "当表达式结果为 null 时的默认值"
    required: false
    note: "等价于 Jinja2 中的 {{ expression | default(defaultValue) }}"

  # === 运行时执行信息 ===
  parsedExpression: object
    description: "表达式解析后的结构化表示（可选，用于验证和分析）"
    required: false
    properties:
      sourceType: enum
        description: "主要的来源类型"
        enum: ["system_variable", "upstream_node", "pipeline_variable", "environment_variable", "constant", "expression"]
        example: "upstream_node"

      sourceIdentifier: string
        description: "来源的标识符"
        example: "collect_paths, sys, pipe, env"

      fieldPath: array
        description: "访问路径（支持嵌套）"
        items: string | integer
        example: ["ceph_paths_list"]           # {{collect_paths.ceph_paths_list}}
                 ["output", "data", 0]         # {{collect_paths.output.data[0]}}

      dependencies: array
        description: "该表达式依赖的所有上游节点"
        items: string
        example: ["collect_paths", "mount_ceph"]

  # === 元数据 ===
  description: string
    description: "对该变量引用的说明"
    required: false
```

---

## 3. 使用场景

### 场景 1：简单字段引用

```yaml
nodes:
  - alias: "process_data"
    task: "SomeTask"
    inputs:
      model_version: "{{pipe.model_version}}"
      dataset_id: "{{upstream_node.dataset_id}}"
```

对应的 VariableReference：

```python
VariableReference(
    name="model_version",
    expression="{{pipe.model_version}}",
    type="string",
    nullable=False,
    parsedExpression={
        "sourceType": "pipeline_variable",
        "sourceIdentifier": "pipe",
        "fieldPath": ["model_version"],
        "dependencies": []
    }
)

VariableReference(
    name="dataset_id",
    expression="{{upstream_node.dataset_id}}",
    type="string",
    nullable=False,
    parsedExpression={
        "sourceType": "upstream_node",
        "sourceIdentifier": "upstream_node",
        "fieldPath": ["dataset_id"],
        "dependencies": ["upstream_node"]
    }
)
```

### 场景 2：嵌套对象访问

```yaml
nodes:
  - alias: "validate_user"
    task: "ValidateTask"
    inputs:
      vip_level: "{{fetch_user.user_info.vip_level}}"
```

对应的 VariableReference：

```python
VariableReference(
    name="vip_level",
    expression="{{fetch_user.user_info.vip_level}}",
    type="number",
    nullable=False,
    parsedExpression={
        "sourceType": "upstream_node",
        "sourceIdentifier": "fetch_user",
        "fieldPath": ["user_info", "vip_level"],
        "dependencies": ["fetch_user"]
    }
)
```

### 场景 3：数据转换和过滤

```yaml
nodes:
  - alias: "process_list"
    task: "ListProcessTask"
    inputs:
      filtered_items: "{{upstream_node.items | select('gt', 0) | list}}"
      item_count: "{{upstream_node.items | length}}"
      formatted: "Processing {{pipe.name}} with {{pipe.version}}"
```

对应的 VariableReference：

```python
VariableReference(
    name="filtered_items",
    expression="{{upstream_node.items | select('gt', 0) | list}}",
    type="array",
    nullable=False,
    parsedExpression={
        "sourceType": "expression",  # 由于包含过滤器，标记为 expression
        "dependencies": ["upstream_node"]
    }
)

VariableReference(
    name="item_count",
    expression="{{upstream_node.items | length}}",
    type="number",
    nullable=False,
    parsedExpression={
        "sourceType": "expression",
        "dependencies": ["upstream_node"]
    }
)

VariableReference(
    name="formatted",
    expression="Processing {{pipe.name}} with {{pipe.version}}",
    type="string",
    nullable=False,
    parsedExpression={
        "sourceType": "expression",
        "dependencies": []
    }
)
```

### 场景 4：容错降级

```yaml
nodes:
  - alias: "robust_process"
    task: "RobustTask"
    inputs:
      result: "{{node_a.output ?? node_b.output ?? 'default'}}"
      count: "{{upstream.count}}"
      fallback_count: 10
```

对应的 VariableReference：

```python
VariableReference(
    name="result",
    expression="{{node_a.output ?? node_b.output ?? 'default'}}",
    type="unknown",
    nullable=True,
    defaultValue="default",
    parsedExpression={
        "sourceType": "expression",
        "dependencies": ["node_a", "node_b"]
    }
)

VariableReference(
    name="count",
    expression="{{upstream.count}}",
    type="number",
    nullable=False,
    defaultValue=10,
    parsedExpression={
        "sourceType": "upstream_node",
        "sourceIdentifier": "upstream",
        "fieldPath": ["count"],
        "dependencies": ["upstream"]
    }
)
```

### 场景 5：条件表达式中的多个变量引用

```yaml
nodes:
  - alias: "decision"
    type: "condition"
    condition:
      expression: "{{fetch_user.status == 'active' AND fetch_quota.available > 100}}"
```

对应的 VariableReference：

```python
[
  VariableReference(
      name="user_status",
      expression="{{fetch_user.status}}",
      type="string",
      parsedExpression={
          "sourceType": "upstream_node",
          "sourceIdentifier": "fetch_user",
          "fieldPath": ["status"],
          "dependencies": ["fetch_user"]
      }
  ),
  
  VariableReference(
      name="available_quota",
      expression="{{fetch_quota.available}}",
      type="number",
      parsedExpression={
          "sourceType": "upstream_node",
          "sourceIdentifier": "fetch_quota",
          "fieldPath": ["available"],
          "dependencies": ["fetch_quota"]
      }
  )
]
```

---
