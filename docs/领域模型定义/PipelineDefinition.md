
# 聚合根：PipelineDefinition（工作流定义）

## 职责

- **任务编排**：通过 Node（节点）组织执行流程，每个 Node 可以是任务节点、条件节点或并行节点
- **输出定义**：定义工作流作为整体的输出变量（可选，用于工作流被作为任务复用时）
- **版本控制**：独立于任务的版本管理
- **变量绑定**：通过 Node 中的 VariableReference 定义运行时参数绑定

## 核心概念

### Node（节点）

Node 是 Pipeline 中的执行单元，代表工作流中的一个"实例化的任务"。区别于 TaskDefinition（任务定义），Node 包含：

- **任务引用**：指向具体的 TaskDefinition
- **变量绑定**：通过 VariableReference 定义该节点的输入参数如何从上游获取
- **执行策略**：节点级别的执行配置（覆盖 TaskDefinition 的默认策略）

**Node vs TaskDefinition**：

| 维度 | TaskDefinition | Node |
|------|---------------|------|
| 定义 | 可复用的任务模板 | 任务在 Pipeline 中的实例 |
| 位置 | 独立存在，跨 Pipeline 复用 | 存在于 PipelineDefinition 中 |
| 职责 | 声明"需要什么输入" | 指定"输入从哪里来" |
| 变量 | VariableDefinition（协议） | VariableReference（绑定） |

## 结构

```text
PipelineDefinition（聚合根）
├── id: string (全局唯一标识)
├── namespace: string (如 "com.company.pipelines")
├── name: string (工作流名称)
├── versions[]
│   ├── version: string ("draft" | "1.0.0" | "1.1.0" 等)
│   ├── status: "DRAFT" | "PUBLISHED"
│   ├── inputVariables: VariableDefinition[]  # 工作流级输入变量定义
│   ├── outputVariables: VariableDefinition[]  # 工作流级输出变量定义
│   │
│   ├── nodes[]  # 节点列表（取代原 taskReferences）
│   │   ├── alias: string (节点别名，在 Pipeline 内唯一)
│   │   ├── type: "task" | "condition" | "parallel"  # 节点类型
│   │   │
│   │   ├── dependsOn: string[]  # 该节点依赖的上游节点别名列表
│   │   │   # 空数组表示入度为 0 的起始节点
│   │   │   # 执行引擎会验证依赖的节点都存在且无循环依赖
│   │   │   # 系统可选地从 inputs 中的 VariableReference 自动推断依赖
│   │   │
│   │   ├── taskRef: object (当 type="task" 时必需)
│   │   │   ├── namespace: string (所引用任务的 namespace)
│   │   │   ├── name: string (所引用任务的 name)
│   │   │   └── version: string (所引用任务的版本，必须是 PUBLISHED 版本)
│   │   │
│   │   ├── inputs: Map<string, VariableReference>  # 输入变量绑定
│   │   │   # key: 变量名（对应 TaskDefinition.inputVariables 中的 name）
│   │   │   # value: VariableReference 对象，定义该变量的来源和转换
│   │   │   # 示例: {"model_version": VariableReference(expression="{{pipe.model_version}}")}
│   │   │
│   │   ├── condition: object (当 type="condition" 时必需)
│   │   │   ├── expression: string (条件表达式，Jinja2 语法)
│   │   │   │   # 示例: "{{node_a.status == 'success' && node_a.count > 100}}"
│   │   │   └── variableReferences: VariableReference[]  # 条件中引用的变量
│   │   │
│   │   ├── parallelBranches: object (当 type="parallel" 时必需)
│   │   │   ├── branches: string[]  # 并行分支的节点别名列表
│   │   │   └── joinStrategy: "all" | "any" | "majority"  # 汇合策略
│   │   │
│   │   ├── executionPolicy: object (节点级执行策略，覆盖 Task 默认值)
│   │   │   ├── maxRetries: integer (最大重试次数)
│   │   │   ├── timeout: integer (超时时间，秒)
│   │   │   ├── resourceId: string (执行资源标识)
│   │   │   └── alarmConfig: object (告警配置)
│   │   │
│   │   └── description: string (节点描述)
│   │
│   ├── releaseNotes: string
│   ├── createdAt: Timestamp
│   └── createdBy: string (仅 PUBLISHED 版本有值)
│
└── metadata
    ├── owners: string[]
    ├── tags: string[]
    └── createdAt: Timestamp
```

## 不变式

1. **唯一性**
   - pipelineId 全局唯一
   - namespace + name 的组合全局唯一
   - 同一 version 内，nodes[].alias 必须唯一

2. **版本控制**
   - pipelineId + version 必须指向唯一的版本
   - DRAFT 版本最多一个

3. **节点类型约束**
   - 当 node.type = "task" 时，必须有 taskRef 和 inputs
   - 当 node.type = "condition" 时，必须有 condition
   - 当 node.type = "parallel" 时，必须有 parallelBranches

4. **依赖一致性**
   - nodes 中 taskRef 引用的 TaskDefinition 必须存在且已发布
   - node.dependsOn 中的节点别名必须存在于 nodes 中
   - 所有节点的 dependsOn 关系必须形成有向无环图（DAG），不允许循环依赖
   - 入度为 0 的节点（起始节点）其 dependsOn 为空数组

5. **变量绑定一致性**
   - node.inputs 中的 key 必须对应 TaskDefinition.inputVariables 中声明的变量
   - TaskDefinition 中 required=true 的变量，必须在 node.inputs 中提供绑定
   - VariableReference 中引用的上游节点必须在该节点的 dependsOn 中声明（或可被推断）
   - 建议：inputs 中引用的节点应该在 dependsOn 中显式声明，保持一致性

6. **条件节点约束**
   - condition.expression 中引用的所有节点，必须在该节点的 dependsOn 中声明
   - condition.variableReferences 必须包含 expression 中所有变量的解析

7. **状态转移**
   - DRAFT 状态可以修改
   - PUBLISHED 状态不可变，只能基于其创建新的 DRAFT 版本

## 事件

- `PipelineDefinitionCreated` - 工作流定义已创建（DRAFT 版本）
- `PipelineDefinitionModified` - 工作流定义已修改（DRAFT 版本）
- `PipelineDefinitionPublished` - 工作流定义已发布（新增 PUBLISHED 版本）

## 命令

### CreatePipelineDefinition 命令

**参数**：

- `namespace` (string，必需)：工作流的命名空间，如 "com.company.pipelines"
- `name` (string，必需)：工作流的名称，在同一 namespace 内唯一
- `description` (string，可选)：工作流的描述
- `owner` (string，必需)：工作流的所有者

**说明**：
创建一个新的工作流定义。系统会自动生成一个版本号为 "draft" 的 DRAFT 版本，此版本处于草稿状态。

**返回**：
返回新创建的 PipelineDefinition 对象，包含自动生成的 pipelineId 和 DRAFT 版本。

**业务规则**：

- namespace + name 的组合必须全局唯一
- 初始化时 DRAFT 版本的 inputVariables、outputVariables、nodes、edges 都为空

---

### ModifyPipelineDefinition 命令

**参数**：

- `pipelineId` (string，必需)：要修改的工作流 ID
- `inputVariables` (VariableDefinition[]，可选)：修改工作流输入变量定义
- `outputVariables` (VariableDefinition[]，可选)：修改工作流输出变量定义
- `nodes` (Node[]，可选)：修改节点列表（包含每个节点的 dependsOn）
- `description` (string，可选)：修改描述信息

**说明**：
修改工作流定义的 DRAFT 版本。只有 DRAFT 版本允许被修改，已发布的版本是不可变的。

**返回**：
返回修改后的 PipelineDefinition 对象。

**业务规则**：

- 只能修改 DRAFT 版本，已发布的版本不允许修改
- nodes 中 taskRef 引用的 TaskDefinition 必须存在且已发布
- nodes 的 dependsOn 关系必须形成有向无环图（DAG），不允许循环依赖
- 节点的 inputs 绑定必须满足 TaskDefinition 的 inputVariables 要求
- VariableReference 中引用的上游节点必须在该节点的 dependsOn 中声明

---

### PublishPipelineDefinition 命令

**参数**：

- `pipelineId` (string，必需)：要发布的工作流 ID
- `version` (string，必需)：发布后的版本号，必须遵循语义化版本规范（如 "1.0.0"）
- `releaseNotes` (string，可选)：发布说明

**说明**：
将工作流定义的 DRAFT 版本发布为正式版本。发布后该版本变为不可变。

**返回**：
返回新发布的 PipelineDefinition 版本对象。

**业务规则**：

- 必须存在 DRAFT 版本才能发布
- 版本号必须大于所有已发布的版本号（递增原则）
- 发布后自动创建一个新的 DRAFT 版本供后续修改

---

## 查询

### GetPipelineDefinition

**参数**：

- `namespace` (string，必需)：工作流的命名空间
- `name` (string，必需)：工作流的名称
- `version` (string，可选)：工作流的版本号。如不指定，默认返回 DRAFT 版本；如指定具体版本号，则返回对应的已发布版本

**说明**：
按工作流地址（namespace + name + version）获取单个工作流定义。

**返回**：
返回匹配的 PipelineDefinition 对象，包含完整的版本信息、任务引用、依赖关系等。

**业务规则**：

- 若指定 version 参数，必须是已发布的版本号或 "draft"
- 若不指定 version 参数，返回该工作流族最新的 DRAFT 版本；如果没有 DRAFT 版本，返回最新的 PUBLISHED 版本

**使用场景**：

- 获取工作流定义用于执行
- 前端页面展示工作流详情

### ListPipelineVersions

**参数**：

- `pipelineId` (string，必需)：工作流的全局唯一标识符
- `limit` (integer)：返回结果的最大数量，默认 100
- `offset` (integer)：分页偏移量，默认 0

**说明**：
列出某个工作流的所有版本，包括 DRAFT 版本和所有 PUBLISHED 版本。

**返回**：
返回版本列表，每个版本包含版本号、状态、创建时间等元数据。

**业务规则**：

- 返回的版本按时间倒序排列
- DRAFT 版本最多只有一个
- 版本号遵循语义化版本规范

**使用场景**：

- 查看工作流的版本演变历史
- 选择特定版本的工作流

### GetPipelineWithNodeDetails

**参数**：

- `pipelineId` (string，必需)：工作流 ID
- `version` (string，可选)：版本号，不指定时返回 DRAFT 版本

**说明**：
返回工作流定义并展开所有节点的 taskRef 为完整的 TaskDefinition（用于前端展示和验证）。

**返回**：
完整的工作流定义，包含展开后的所有节点和任务详情。

```json
{
  "id": "pipeline-123",
  "namespace": "com.company.pipelines",
  "name": "data_processing",
  "version": "1.0.0",
  "nodes": [
    {
      "alias": "collect_data",
      "type": "task",
      "taskRef": {
        "namespace": "com.company.tasks",
        "name": "data_collector",
        "version": "1.2.0"
      },
      "taskDetail": {
        // 完整的 TaskDefinition 对象
      },
      "inputs": {
        "source_path": {
          "expression": "{{pipe.input_path}}",
          "type": "string"
        }
      }
    }
  ]
}
```

**业务规则**：

- 自动验证所有节点引用的任务是否存在且已发布
- 如果任务已被删除或版本不再可用，返回错误信息
- 验证变量绑定的完整性和正确性

**使用场景**：

- 前端页面展示工作流和任务的完整信息
- 工作流执行前的验证
- IDE 中的智能提示和错误检查

---

## 完整示例

### 示例 1：简单的线性流水线

```yaml
PipelineDefinition:
  id: "pipeline-001"
  namespace: "com.example.pipelines"
  name: "simple_etl"
  
  versions:
    - version: "1.0.0"
      status: "PUBLISHED"
      
      inputVariables:
        - name: "input_path"
          type: "string"
          required: true
          description: "输入数据路径"
        
        - name: "model_version"
          type: "string"
          required: true
          description: "模型版本"
      
      outputVariables:
        - name: "output_path"
          type: "string"
          required: true
          description: "处理后的输出路径"
      
      nodes:
        # 节点 1: 数据采集
        - alias: "collect_data"
          type: "task"
          dependsOn: []  # 起始节点，无依赖
          taskRef:
            namespace: "com.example.tasks"
            name: "data_collector"
            version: "1.0.0"
          inputs:
            source_path:
              expression: "{{pipe.input_path}}"
              type: "string"
            format:
              expression: "parquet"
              type: "string"
          description: "从源路径采集数据"
        
        # 节点 2: 数据处理
        - alias: "process_data"
          type: "task"
          dependsOn: ["collect_data"]  # 依赖数据采集节点
          taskRef:
            namespace: "com.example.tasks"
            name: "data_processor"
            version: "2.1.0"
          inputs:
            input_data:
              expression: "{{collect_data.output_path}}"
              type: "string"
            model_version:
              expression: "{{pipe.model_version}}"
              type: "string"
            batch_size:
              expression: "1000"
              type: "number"
          executionPolicy:
            maxRetries: 3
            timeout: 3600
          description: "使用指定模型处理数据"
        
        # 节点 3: 数据写入
        - alias: "write_data"
          type: "task"
          dependsOn: ["process_data"]  # 依赖数据处理节点
          taskRef:
            namespace: "com.example.tasks"
            name: "data_writer"
            version: "1.0.0"
          inputs:
            data_path:
              expression: "{{process_data.output_path}}"
              type: "string"
            destination:
              expression: "/output/processed"
              type: "string"

### 示例 2：带条件判断的流水线

```yaml
nodes:
  # 检查数据质量
  - alias: "check_quality"
    type: "task"
    dependsOn: ["collect_data"]
    taskRef:
      namespace: "com.example.tasks"
      name: "quality_checker"
      version: "1.0.0"
    inputs:
      data_path:
        expression: "{{collect_data.output_path}}"
        type: "string"
  
  # 条件节点：判断数据质量
  - alias: "quality_gate"
    type: "condition"
    dependsOn: ["check_quality"]
    condition:
      expression: "{{check_quality.pass_rate >= 0.95}}"
      variableReferences:
        - name: "pass_rate"
          expression: "{{check_quality.pass_rate}}"
          type: "number"
    description: "质量阈值检查"
  
  # 高质量分支：直接处理
  - alias: "fast_process"
    type: "task"
    dependsOn: ["quality_gate"]  # 条件为 true 时执行
    taskRef:
      namespace: "com.example.tasks"
      name: "fast_processor"
      version: "1.0.0"
    inputs:
      data_path:
        expression: "{{collect_data.output_path}}"
  
  # 低质量分支：清洗后处理
  - alias: "clean_data"
    type: "task"
    dependsOn: ["quality_gate"]  # 条件为 false 时执行
    taskRef:
      namespace: "com.example.tasks"
      name: "data_cleaner"
      version: "1.0.0"
    inputs:
      data_path:
        expression: "{{collect_data.output_path}}"
```

---
