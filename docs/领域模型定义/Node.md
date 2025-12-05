# 值对象：Node（节点）

## 1. 概念

`Node` 是 PipelineDefinition 中的**值对象**，代表工作流中的一个执行单元。Node 是"实例化的任务"，包含：

- **任务引用**：指向具体的 TaskDefinition
- **变量绑定**：定义该节点的输入参数如何获取（通过 VariableReference）
- **执行策略**：节点级别的执行配置

**核心设计理念**：

- **TaskDefinition**：定义"是什么"（可复用的任务模板）
- **Node**：定义"怎么用"（任务在 Pipeline 中的实例化）

## 2. Node vs TaskDefinition

| 维度 | TaskDefinition | Node |
|------|---------------|------|
| **定义** | 可复用的任务模板 | 任务在 Pipeline 中的实例 |
| **位置** | 独立存在，跨 Pipeline 复用 | 存在于 PipelineDefinition.nodes[] 中 |
| **职责** | 声明"需要什么输入"（协议） | 指定"输入从哪里来"（绑定） |
| **变量** | VariableDefinition（接口定义） | VariableReference（实际绑定） |
| **复用性** | 可被多个 Pipeline 引用 | 仅属于特定 Pipeline |
| **修改影响** | 影响所有引用该任务的 Pipeline | 仅影响当前 Pipeline |

**类比**：

```
TaskDefinition = 函数定义
  def process_data(input_path: str, batch_size: int) -> str:
      ...

Node = 函数调用
  result = process_data(
      input_path=upstream_node.output,  # 绑定参数来源
      batch_size=1000
  )
```

## 3. 结构

```yaml
Node:
  # === 基础标识 ===
  alias: string
    description: "节点别名，在 Pipeline 内唯一"
    required: true
    example: "collect_data, process_batch_1, validate_results"

  type: enum
    description: "节点类型"
    required: true
    enum: ["task", "condition", "parallel"]
    default: "task"

  # === 触发与条件控制 ===
  startWhen: string
    description: "启动条件，定义何时检查该节点的启动（事件驱动）"
    required: true
    note: |
      - 事件表达式，支持逻辑运算符（&&、||、!、()）
      - 语法：event:<nodeAlias>.<eventName> 或 cron:<pattern>
      - 可混合事件和状态："event:... && {{ state_condition }}"
      - 详见 Expression.md 文档
    examples:
      - "event:upstream.succeeded"  # 等待上游任务成功
      - "event:node_a.succeeded && event:node_b.succeeded"  # 等待多个任务
      - "cron:0 2 * * *"  # 定时触发（每天 2:00）
      - "cron:@startup"  # 启动时触发
      - "event:upstream.succeeded && {{ upstream.output.row_count > 0 }}"  # 混合表达式

  failWhen: string
    description: "失败条件，满足时标记节点为 failed"
    required: false
    note: |
      - 状态表达式，引用节点的输出变量或状态
      - 优先级最高，优先于其他 *When 条件
      - 详见 Expression.md 文档
    examples:
      - "upstream.output.row_count == 0"  # 数据为空时失败
      - "extract.output.quality_score < 0.5"  # 质量不达标时失败
      - "retry_count >= 3"  # 重试次数过多时失败

  skipWhen: string
    description: "跳过条件，满足时标记节点为 skipped"
    required: false
    note: |
      - 状态表达式，用于条件路由
      - 跳过的节点不会执行，下游节点可能继续执行（取决于依赖配置）
      - 详见 Expression.md 文档
    examples:
      - "decision.output.route != 'A'"  # 路由不匹配时跳过
      - "upstream.output.is_empty == true"  # 上游无数据时跳过
      - "quality_check.approval_done.status != 'approved'"  # 审核未通过时跳过

  retryWhen: string
    description: "重试条件，任务失败时检查是否重试（仅批处理）"
    required: false
    note: |
      - 状态表达式（Jinja2），在任务失败后自动检查
      - 仅对 executionMode=batch 的节点有效
      - 需配合 retryPolicy 定义重试次数和间隔
      - 详见 Expression.md 文档
    examples:
      - "{{ not partition_exists(input_table, execution_date) }}"  # 分区不存在时重试
      - "{{ retry_count < 3 }}"  # 重试次数未达上限

  stopWhen: string
    description: "停止条件，定义何时检查停止条件（仅流式）"
    required: false
    note: |
      - 事件表达式，用于流式节点的停止条件
      - 仅对 executionMode=streaming 的节点有效
      - 可混合事件和状态："event:... && {{ state_condition }}"
      - 详见 Expression.md 文档
    examples:
      - "event:kafka_source.stopped"  # 上游停止时停止
      - "event:monitor.statusChanged && {{ monitor.health == 'critical' }}"  # 混合表达式

  alertWhen: string
    description: "告警条件，满足时发送告警（不影响执行）"
    required: false
    note: |
      - 状态表达式，用于数据质量监控、性能告警等扩展能力
      - 告警不影响节点的执行状态
      - 需配合 alertConfig 定义告警渠道和内容
      - 详见 Expression.md 文档
    examples:
      - "transform.output.null_ratio > 0.1"  # 空值率过高时告警
      - "stream_processor.metrics.lag > 10000"  # 延迟过高时告警
      - "merge.output.row_count < 1000"  # 数据量过低时告警

  retryPolicy: object
    description: "重试策略（仅当 retryWhen 存在时有效）"
    required: false
    properties:
      maxAttempts: integer
        description: "最大重试次数"
        default: 3
      interval: integer
        description: "重试间隔（秒）"
        default: 60
      backoff: enum
        description: "退避策略"
        enum: ["fixed", "exponential"]
        default: "fixed"

  alertConfig: object
    description: "告警配置（仅当 alertWhen 存在时有效）"
    required: false
    properties:
      channel: string
        description: "告警渠道"
        enum: ["email", "slack", "webhook"]
      severity: string
        description: "告警级别"
        enum: ["info", "warning", "critical"]
        default: "warning"
      message: string
        description: "告警消息模板（支持变量插值）"

  description: string
    description: "节点描述"
    required: false

  # === 任务节点配置 (type="task") ===
  taskRef: object
    description: "引用的 TaskDefinition"
    required: true (当 type="task" 时)
    properties:
      namespace: string
        description: "任务的命名空间"
        required: true
        example: "com.company.tasks"

      name: string
        description: "任务名称"
        required: true
        example: "data_processor"

      version: string
        description: "任务版本（必须是 PUBLISHED）"
        required: true
        example: "1.2.0"

  inputs: Map<string, VariableReference>
    description: "输入变量绑定"
    required: true (当 type="task" 时)
    note: |
      - key: 变量名，必须对应 TaskDefinition.inputVariables 中声明的变量
      - value: VariableReference 对象，定义该变量的来源和转换
      - TaskDefinition 中 required=true 的变量必须在此提供绑定
    examples:
      model_version:
        expression: "{{pipe.model_version}}"
        type: "string"
      
      input_data:
        expression: "{{upstream_node.output_path}}"
        type: "string"
      
      batch_size:
        expression: "{{collect_paths.path_count * 10}}"
        type: "number"

  # === 条件节点配置 (type="condition") ===
  condition: object
    description: "条件判断配置"
    required: true (当 type="condition" 时)
    properties:
      expression: string
        description: "条件表达式（Jinja2 语法），返回布尔值"
        required: true
        examples:
          - "{{check_quality.pass_rate >= 0.95}}"
          - "{{upstream.status == 'success' && upstream.count > 100}}"
          - "{{pipe.env == 'prod'}}"

      variableReferences: VariableReference[]
        description: "条件表达式中引用的变量"
        required: true
        note: "必须包含 expression 中所有变量的解析"

  # === 并行节点配置 (type="parallel") ===
  parallelBranches: object
    description: "并行执行配置"
    required: true (当 type="parallel" 时)
    properties:
      branches: string[]
        description: "并行分支的节点别名列表"
        required: true
        example: ["branch_a", "branch_b", "branch_c"]

      joinStrategy: enum
        description: "并行分支的汇合策略"
        required: true
        enum: ["all", "any", "majority"]
        default: "all"
        note: |
          - all: 所有分支都成功才算成功
          - any: 任意一个分支成功即算成功
          - majority: 超过半数分支成功才算成功

  # === 执行策略 ===
  executionPolicy: object
    description: "节点级别的执行策略（覆盖 TaskDefinition 的默认配置）"
    required: false
    properties:
      maxRetries: integer
        description: "最大重试次数"
        required: false
        default: "继承自 TaskDefinition"

      timeout: integer
        description: "超时时间（秒）"
        required: false

      resourceId: string
        description: "执行资源标识（如 Ray 集群 ID、Spark 集群 ID）"
        required: false

      alarmConfig: object
        description: "告警配置"
        required: false
        properties:
          enabled: boolean
          channels: string[]  # ["email", "slack", "webhook"]
          recipients: string[]
```

## 4. 节点类型详解

### 4.1 Task Node（任务节点）

最常用的节点类型，执行具体的计算任务。

**批处理任务示例**：

```yaml
- alias: "merge"
  type: "task"
  startWhen: "event:user_features.succeeded && event:item_features.succeeded"
  failWhen: "user_features.output.row_count == 0 || item_features.output.row_count == 0"
  alertWhen: "merge.output.row_count < 1000"
  taskRef:
    namespace: "com.example.tasks"
    name: "merge_features"
    version: "2.1.0"
  inputs:
    user_path:
      expression: "{{user_features.output.path}}"
      type: "string"
    item_path:
      expression: "{{item_features.output.path}}"
      type: "string"
  alertConfig:
    channel: "slack"
    severity: "warning"
    message: "合并后数据量过低: {{ merge.output.row_count }}"
  description: "合并用户和商品特征"
```

**流式任务示例**：

```yaml
- alias: "deduplicate"
  type: "task"
  executionMode: "streaming"
  startWhen: "event:kafka_source.healthy"
  stopWhen: "event:kafka_source.unhealthy"
  alertWhen: "deduplicate.metrics.lag > 10000"
  taskRef:
    namespace: "com.example.tasks"
    name: "stream_deduplicate"
    version: "1.0.0"
  inputs:
    source_topic:
      expression: "{{pipe.kafka_topic}}"
      type: "string"
  description: "实时数据去重"
```

**定时任务示例**：

```yaml
- alias: "extract"
  type: "task"
  startWhen: "cron:0 2 * * *"
  retryWhen: "!partition_exists(input_table, execution_date)"
  failWhen: "retry_count >= 3"
  taskRef:
    namespace: "com.example.tasks"
    name: "extract_data"
    version: "1.5.0"
  inputs:
    partition_date:
      expression: "{{execution_date}}"
      type: "string"
  retryPolicy:
    maxAttempts: 3
    interval: 300
  description: "每日数据抽取"
```

### 4.2 Condition Node（条件节点）

用于流程分支控制，根据上游节点的输出决定执行路径。

```yaml
- alias: "route_approved"
  type: "task"
  startWhen: "event:quality_check.approval_done"
  skipWhen: "quality_check.approval_done.status != 'approved'"
  taskRef:
    namespace: "com.example.tasks"
    name: "process_approved"
    version: "1.0.0"
  description: "处理审核通过的数据"

- alias: "route_rejected"
  type: "task"
  startWhen: "event:quality_check.approval_done"
  skipWhen: "quality_check.approval_done.status != 'rejected'"
  taskRef:
    namespace: "com.example.tasks"
    name: "process_rejected"
    version: "1.0.0"
  description: "处理审核拒绝的数据"
```

**执行逻辑**：

- `skipWhen` 条件满足时，节点标记为 `skipped`，不执行
- 通过不同的 `skipWhen` 条件实现多路由分支
- 可以配合 `failWhen` 处理异常情况

### 4.3 Parallel Node（并行节点）

用于并行执行多个独立的任务分支。

```yaml
- alias: "parallel_features"
  type: "parallel"
  startWhen: "event:extract.succeeded"
  parallelBranches:
    branches: ["user_features", "item_features", "context_features"]
    joinStrategy: "all"
  description: "并行计算多种特征"

- alias: "user_features"
  type: "task"
  startWhen: "event:parallel_features.started"
  taskRef:
    namespace: "com.example.tasks"
    name: "compute_user_features"
    version: "1.0.0"

- alias: "item_features"
  type: "task"
  startWhen: "event:parallel_features.started"
  taskRef:
    namespace: "com.example.tasks"
    name: "compute_item_features"
    version: "1.0.0"

- alias: "merge"
  type: "task"
  startWhen: "event:user_features.succeeded && event:item_features.succeeded"
  description: "合并并行计算的特征"
```

**执行逻辑**：

- Parallel Node 触发时，同时启动所有 branches 中的节点
- 根据 joinStrategy 决定何时完成：
  - `all`: 所有分支都成功
  - `any`: 任意一个分支成功
  - `majority`: 超过半数成功
- 下游节点可以通过 `trigger` 等待并行分支完成

## 5. 变量绑定示例

### 示例 1：从 Pipeline 输入获取

```yaml
# PipelineDefinition 的输入
inputVariables:
  - name: "model_version"
    type: "string"
    required: true

# Node 的变量绑定
nodes:
  - alias: "train_model"
    type: "task"
    inputs:
      version:
        expression: "{{pipe.model_version}}"  # 引用 Pipeline 输入
        type: "string"
```

### 示例 2：从上游节点获取

```yaml
nodes:
  - alias: "collect_data"
    type: "task"
    # ... 输出 output_path
  
  - alias: "process_data"
    type: "task"
    inputs:
      input_path:
        expression: "{{collect_data.output_path}}"  # 引用上游输出
        type: "string"
```

### 示例 3：使用转换和计算

```yaml
nodes:
  - alias: "process_batch"
    type: "task"
    inputs:
      # 数组长度
      item_count:
        expression: "{{collect_paths.paths | length}}"
        type: "number"
      
      # 数组过滤
      valid_paths:
        expression: "{{collect_paths.paths | select('match', '*.parquet') | list}}"
        type: "array"
      
      # 字符串拼接
      output_name:
        expression: "result_{{pipe.date}}_{{pipe.region}}"
        type: "string"
      
      # 条件表达式
      priority:
        expression: "{{pipe.env == 'prod' ? 'high' : 'normal'}}"
        type: "string"
```

### 示例 4：降级和默认值

```yaml
nodes:
  - alias: "robust_process"
    type: "task"
    inputs:
      # 多级降级
      config_path:
        expression: "{{custom_config.path ?? default_config.path ?? '/default/config.yaml'}}"
        type: "string"
      
      # 带默认值
      batch_size:
        expression: "{{pipe.batch_size}}"
        type: "number"
        defaultValue: 1000
```

## 6. 不变式

1. **唯一性**
   - alias 在同一 PipelineDefinition.version 内必须唯一

2. **类型约束**
   - type="task" 时，必须有 taskRef 和 inputs
   - type="condition" 时，必须有 condition
   - type="parallel" 时，必须有 parallelBranches

3. **任务引用有效性**
   - taskRef 引用的 TaskDefinition 必须存在且已发布（PUBLISHED）
   - taskRef.version 不能是 "draft"

4. **变量绑定完整性**
   - inputs 中的 key 必须对应 TaskDefinition.inputVariables 中声明的变量
   - TaskDefinition 中 required=true 的变量必须在 inputs 中提供绑定
   - 不允许绑定 TaskDefinition 中未声明的变量

5. **依赖关系有效性**
   - dependsOn 中的节点别名必须存在于 Pipeline.nodes 中
   - 所有节点的 dependsOn 关系必须形成 DAG（无循环依赖）
   - 入度为 0 的起始节点 dependsOn 为空数组

6. **变量来源可达性**
   - VariableReference 中引用的上游节点应该在 dependsOn 中声明
   - 建议：inputs 引用的节点与 dependsOn 保持一致
   - 系统可提供自动推断功能（从 inputs 提取依赖）

7. **条件节点约束**
   - condition.expression 中引用的所有节点必须在 dependsOn 中声明
   - condition.variableReferences 必须完整解析 expression 中的所有变量

8. **并行节点约束**
   - parallelBranches.branches 中的节点必须存在于 nodes 中
   - 并行分支之间不应有 dependsOn 关系（避免部分顺序执行）

## 7. 验证规则

### 创建/修改 Node 时的验证

```python
def validate_node(node: Node, pipeline: PipelineDefinition) -> List[Error]:
    errors = []
    
    # 1. 别名唯一性
    if node.alias in [n.alias for n in pipeline.nodes if n != node]:
        errors.append(f"Node alias '{node.alias}' already exists")
    
    # 2. dependsOn 有效性
    all_node_aliases = {n.alias for n in pipeline.nodes}
    for dep in node.dependsOn:
        if dep not in all_node_aliases:
            errors.append(f"Dependency '{dep}' not found in pipeline nodes")
        if dep == node.alias:
            errors.append(f"Node cannot depend on itself")
    
    # 3. 任务引用验证（type="task" 时）
    if node.type == "task":
        task = get_task_definition(node.taskRef)
        if not task:
            errors.append(f"TaskDefinition not found: {node.taskRef}")
        elif task.status != "PUBLISHED":
            errors.append(f"Task must be PUBLISHED, got {task.status}")
        
        # 4. 变量绑定验证
        required_inputs = {v.name for v in task.inputVariables if v.required}
        provided_inputs = set(node.inputs.keys())
        
        missing = required_inputs - provided_inputs
        if missing:
            errors.append(f"Missing required inputs: {missing}")
        
        extra = provided_inputs - {v.name for v in task.inputVariables}
        if extra:
            errors.append(f"Unknown inputs (not declared in Task): {extra}")
        
        # 5. 上游依赖一致性验证
        for var_ref in node.inputs.values():
            deps = extract_node_references(var_ref.expression)
            for dep in deps:
                if dep not in node.dependsOn:
                    errors.append(
                        f"Node '{dep}' referenced in inputs but not in dependsOn. "
                        f"Add '{dep}' to dependsOn: {node.dependsOn}"
                    )
    
    # 6. 条件节点验证
    if node.type == "condition":
        deps = extract_node_references(node.condition.expression)
        for dep in deps:
            if dep not in node.dependsOn:
                errors.append(
                    f"Condition references '{dep}' but not in dependsOn"
                )
    
    # 7. 循环依赖检测（在整个 pipeline 级别）
    if has_cycle(pipeline.nodes):
        errors.append(f"Cyclic dependency detected involving node '{node.alias}'")
    
    return errors
```

## 8. 使用场景

### 场景 1：简单的 ETL 流水线

```yaml
nodes:
  - alias: "extract"
    type: "task"
    dependsOn: []  # 起始节点
    taskRef: {namespace: "etl", name: "extractor", version: "1.0.0"}
    inputs:
      source: {expression: "{{pipe.source_db}}"}
  
  - alias: "transform"
    type: "task"
    dependsOn: ["extract"]  # 依赖 extract
    taskRef: {namespace: "etl", name: "transformer", version: "2.0.0"}
    inputs:
      data: {expression: "{{extract.data}}"}
  
  - alias: "load"
    type: "task"
    dependsOn: ["transform"]  # 依赖 transform
    taskRef: {namespace: "etl", name: "loader", version: "1.0.0"}
    inputs:
      data: {expression: "{{transform.data}}"}
      target: {expression: "{{pipe.target_db}}"}
```

### 场景 2：带质量检查的流水线

```yaml
nodes:
  - alias: "process"
    type: "task"
    dependsOn: []
    # ...
  
  - alias: "check_quality"
    type: "task"
    dependsOn: ["process"]
    inputs:
      data: {expression: "{{process.output}}"}
  
  - alias: "quality_gate"
    type: "condition"
    dependsOn: ["check_quality"]
    condition:
      expression: "{{check_quality.pass_rate >= 0.95}}"
  
  - alias: "publish"  # 质量通过才发布
    type: "task"
    dependsOn: ["quality_gate"]  # true 分支
    inputs:
      data: {expression: "{{process.output}}"}
  
  - alias: "alert"  # 质量不通过发告警
    type: "task"
    dependsOn: ["quality_gate"]  # false 分支
    inputs:
      message: {expression: "Quality check failed: {{check_quality.report}}"}
```

### 场景 3：多区域并行处理

```yaml
nodes:
  - alias: "prepare"
    type: "task"
    dependsOn: []
    # ... 准备数据
  
  # 定义各区域处理节点（并行执行）
  - alias: "process_beijing"
    type: "task"
    dependsOn: ["prepare"]
    inputs:
      data: {expression: "{{prepare.output}}"}
      region: {expression: "beijing"}
  
  - alias: "process_shanghai"
    type: "task"
    dependsOn: ["prepare"]
    inputs:
      data: {expression: "{{prepare.output}}"}
      region: {expression: "shanghai"}
  
  - alias: "process_guangzhou"
    type: "task"
    dependsOn: ["prepare"]
    inputs:
      data: {expression: "{{prepare.output}}"}
      region: {expression: "guangzhou"}
  
  # 汇总结果（等待所有区域完成）
  - alias: "merge_results"
    type: "task"
    dependsOn: ["process_beijing", "process_shanghai", "process_guangzhou"]
    inputs:
      results:
        expression: "[{{process_beijing.output}}, {{process_shanghai.output}}, {{process_guangzhou.output}}]"
```

---

## 9. 与其他概念的关系

```
┌─────────────────────────────────────────────────────┐
│ PipelineDefinition (聚合根)                         │
│                                                     │
│  ┌──────────────────────────────────────────┐     │
│  │ nodes[] (值对象列表)                     │     │
│  │                                          │     │
│  │  Node 1 ──taskRef──> TaskDefinition     │     │
│  │    │                                     │     │
│  │    └─inputs: Map<string, VariableRef>   │     │
│  │                                          │     │
│  │  Node 2 ──inputs──> VariableReference   │     │
│  │    │                   │                 │     │
│  │    │                   └─references──> Node 1  │
│  │    │                                     │     │
│  │    └─taskRef──> TaskDefinition          │     │
│  │                                          │     │
│  └──────────────────────────────────────────┘     │
│                                                     │
│  edges[] (定义 nodes 间的依赖关系)                │
└─────────────────────────────────────────────────────┘
```

**关键关系**：

1. **Node → TaskDefinition**（引用关系）
   - Node.taskRef 引用具体的 TaskDefinition
   - TaskDefinition 定义"接口"，Node 提供"实现"

2. **Node → VariableReference**（组合关系）
   - Node.inputs 包含多个 VariableReference
   - VariableReference 绑定变量的具体来源

3. **Node → Node**（依赖关系）
   - 通过 PipelineDefinition.edges 定义
   - 通过 VariableReference 中的表达式引用

4. **Node → VariableDefinition**（间接关系）
   - TaskDefinition.inputVariables 声明需要什么
   - Node.inputs 提供如何获取

---

## 10. 最佳实践

### ✅ 推荐做法

1. **节点别名命名清晰**
   ```yaml
   alias: "collect_user_data"  # 好：清晰表达节点功能
   # 避免: alias: "node1", "task_a"
   ```

2. **合理使用执行策略覆盖**
   ```yaml
   # 关键节点增加重试
   executionPolicy:
     maxRetries: 5
     timeout: 7200
   ```

3. **变量绑定使用显式类型**
   ```yaml
   inputs:
     count:
       expression: "{{upstream.count}}"
       type: "number"  # 显式声明类型，便于验证
   ```

4. **复杂表达式使用中间节点**
   ```yaml
   # 不推荐：复杂的内联表达式
   expression: "{{node_a.items | select('gt', node_b.threshold) | map('multiply', 2) | sum}}"
   
   # 推荐：拆分成多个节点
   - alias: "filter_items"
   - alias: "calculate_sum"
   ```

### ❌ 避免做法

1. **循环依赖**
   ```yaml
   # 错误：Node A 依赖 Node B，Node B 又依赖 Node A
   - alias: "node_a"
     dependsOn: ["node_b"]
   
   - alias: "node_b"
     dependsOn: ["node_a"]  # ❌ 循环依赖
   ```

2. **引用未声明的依赖**
   ```yaml
   # 错误：inputs 引用 node_x，但 dependsOn 中没有声明
   - alias: "process"
     dependsOn: []  # ❌ 应该包含 "node_x"
     inputs:
       data: {expression: "{{node_x.output}}"}  # 引用了 node_x
   ```

3. **自依赖**
   ```yaml
   # 错误：节点依赖自己
   - alias: "node_a"
     dependsOn: ["node_a"]  # ❌ 自依赖
   ```

4. **过度嵌套的并行节点**
   ```yaml
   # 避免：parallel 中嵌套 parallel，难以理解和维护
   ```

---
