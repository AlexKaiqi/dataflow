# ADR 003: 任务模式与任务定义的重新设计

## 状态

提议中 (Proposed)

## 背景

当前设计存在概念混淆：

### 问题 1：TaskType vs TaskDefinition 职责不清

```
当前设计（有问题）:
TaskType (枚举)          TaskDefinition (实例)
- PYSPARK_OPERATOR  →   - namespace: "com.tasks"
- SQL_OPERATOR           - name: "my_etl_task"
- RAY_OPERATOR           - type: PYSPARK_OPERATOR
                         - supportedActions: [start, retry]  ❌ 这应该由 type 决定
                         - producedEvents: [started, completed, failed]  ❌ 这也应该由 type 决定
```

**问题**：
- `supportedActions` 和 `producedEvents` 不应该在 TaskDefinition 中定义
- 这些应该由 TaskType 决定，TaskDefinition 只是 TaskType 的一个具体实例

### 问题 2：缺少元定义层

```
缺失的概念层次:

┌──────────────────────┐
│  TaskSchema (元定义) │  ← 缺失！定义"这类任务能做什么"
│  - type: "pyspark"   │    - 支持哪些 actions
│  - actions: [...]    │    - 产生哪些 events
│  - events: [...]     │    - executor 配置
└──────────────────────┘    - 配置 schema
          │
          │ instance_of
          ▼
┌──────────────────────┐
│  TaskDefinition      │  ← 当前实现，应该是 Schema 的实例
│  - taskType: "pyspark"│   - 具体的执行配置
│  - config: {...}     │    - 输入输出变量
└──────────────────────┘
          │
          │ instance_of
          ▼
┌──────────────────────┐
│  TaskExecution       │  ← 运行时实例
│  - status: running   │
└──────────────────────┘
```

### 问题 3：节点状态扩展性

用户需求：希望节点状态也可以自定义，能够通过 RPC 查询执行器的自定义状态。

```yaml
# 例如：自定义 ML 训练任务
taskType: "ml_training"
customStates:
  - name: "training"
    description: "模型训练中"
    queryEndpoint: "/status"
    queryInterval: 10000

  - name: "validating"
    description: "模型验证中"
    queryEndpoint: "/validation-status"
```

## 决策

重新设计概念层次，引入 **TaskSchema（任务元模式）**，明确三层结构。

## 新的概念模型

### 三层架构

```
┌─────────────────────────────────────────────────────────────────┐
│  Layer 1: Schema Layer (元定义层 - 定义类别)                      │
│                                                                   │
│  TaskSchema                                                       │
│  - 定义任务类型的能力边界                                          │
│  - 定义支持的 actions                                             │
│  - 定义产生的 events                                              │
│  - 定义可能的 states（含自定义状态）                               │
│  - 定义 executor 配置                                             │
│  - 定义配置 schema                                                │
│                                                                   │
│  例如: "pyspark_operator", "custom_ml_training"                   │
└─────────────────────────────────────────────────────────────────┘
                         │
                         │ instance_of (一个 Schema 可以有多个 Definition)
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│  Layer 2: Definition Layer (定义层 - 具体任务模板)                │
│                                                                   │
│  TaskDefinition                                                   │
│  - 引用一个 TaskSchema (taskType)                                 │
│  - 提供具体的执行配置 (executionConfig)                            │
│  - 定义输入输出变量                                                │
│  - 可被多个 Pipeline Node 复用                                     │
│                                                                   │
│  例如: "com.company:etl_processor:1.0.0"                          │
└─────────────────────────────────────────────────────────────────┘
                         │
                         │ instance_of (一个 Definition 可以有多次 Execution)
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│  Layer 3: Execution Layer (执行层 - 运行时实例)                   │
│                                                                   │
│  TaskExecution                                                    │
│  - 引用一个 TaskDefinition                                        │
│  - 记录运行时状态和输出                                            │
│  - 每次执行都是新的实例                                            │
│                                                                   │
│  例如: "exec_20250115_001"                                        │
└─────────────────────────────────────────────────────────────────┘
```

## 详细设计

### 1. TaskSchema (元定义)

```yaml
TaskSchema:
  # 类型标识
  type: string                              # "pyspark_operator", "custom_ml_training"
  description: string
  category: enum                            # BATCH, STREAMING, CONTROL_FLOW, CUSTOM

  # 能力定义（Schema 决定）
  actions:                                  # 支持的行为
    start:
      method: POST
      endpoint: "/execute"
      description: "启动任务"

    stop:
      method: POST
      endpoint: "/stop"
      description: "停止任务"

    retry:
      method: POST
      endpoint: "/retry"
      description: "重试任务"

    # 可以有更多自定义 actions
    pause:
      method: POST
      endpoint: "/pause"

  events:                                   # 产生的事件
    - name: "started"
      description: "任务已启动"
    - name: "completed"
      description: "任务完成"
    - name: "failed"
      description: "任务失败"
    # 可以有更多自定义事件
    - name: "paused"
      description: "任务已暂停"

  # 🆕 状态定义（支持自定义状态）
  states:
    # 标准状态（所有任务通用）
    - name: "pending"
      description: "等待执行"
      terminal: false

    - name: "running"
      description: "执行中"
      terminal: false

    - name: "completed"
      description: "已完成"
      terminal: true

    - name: "failed"
      description: "失败"
      terminal: true

    # 🆕 自定义状态（可选）
    - name: "training"
      description: "模型训练中"
      terminal: false
      queryConfig:
        endpoint: "/training-status"
        interval: 10000                     # 轮询间隔（毫秒）
        parser: "json"                      # 解析器类型
        path: "$.status"                    # JSON Path

    - name: "validating"
      description: "模型验证中"
      terminal: false
      queryConfig:
        endpoint: "/validation-status"
        interval: 5000

  # Executor 配置
  executor:
    type: "HTTP"                            # HTTP, GRPC, WEBHOOK
    baseUrl: "http://executor-service:8080"
    timeout: 30000
    auth:
      type: "bearer"
      token: "${EXECUTOR_TOKEN}"

  # 配置 Schema（JSON Schema）
  executionConfigSchema:
    type: "object"
    properties:
      sparkConf:
        type: "object"
        additionalProperties:
          type: "string"
      mainClass:
        type: "string"
    required: ["mainClass"]

  # 元数据
  builtin: boolean                          # 是否内置类型
  createdBy: string
  createdAt: timestamp
```

### 2. TaskDefinition (任务定义 - Schema 的实例)

```yaml
TaskDefinition:
  # 唯一标识
  namespace: string                         # "com.company.tasks"
  name: string                              # "etl_processor"

  # 引用 TaskSchema
  taskType: string                          # "pyspark_operator"（必须是已注册的 Schema）

  # 任务描述
  description: string

  # 具体的执行配置（符合 TaskSchema.executionConfigSchema）
  executionConfig: object
    # 例如（对于 pyspark_operator）:
    mainClass: "com.company.ETLJob"
    sparkConf:
      spark.executor.memory: "4g"
      spark.executor.cores: "2"

  # 输入输出变量定义
  inputVariables: List[VariableDefinition]
  outputVariables: List[VariableDefinition]

  # 版本管理
  versions: List[TaskVersion]

  # 元数据
  createdAt: timestamp
  createdBy: string

  # ❌ 不再包含这些（由 TaskSchema 决定）:
  # supportedActions  - 由 taskType 的 Schema 决定
  # producedEvents    - 由 taskType 的 Schema 决定
  # states           - 由 taskType 的 Schema 决定
```

### 3. TaskExecution (运行时实例)

```yaml
TaskExecution:
  # 唯一标识
  id: string                                # "exec_20250115_001"

  # 引用 TaskDefinition
  taskDefinition:
    namespace: string
    name: string
    version: string

  # 运行时信息
  status: string                            # 当前状态（来自 TaskSchema.states）

  # 🆕 自定义状态信息（如果有）
  customStateData: object
    # 例如（ML 训练任务）:
    currentEpoch: 10
    totalEpochs: 100
    trainingLoss: 0.234
    validationAccuracy: 0.876

  # 输入输出
  inputs: Map[string, any]
  outputs: Map[string, any]

  # 执行追踪
  externalExecutionId: string               # 执行器返回的 ID
  startedAt: timestamp
  completedAt: timestamp
  error: string

  # 事件历史
  eventHistory: List[Event]
```

## 关键改进点

### 改进 1: 职责清晰

| 层次 | 职责 | 示例 |
|------|------|------|
| **TaskSchema** | 定义"这类任务能做什么" | actions, events, states, executor |
| **TaskDefinition** | 定义"这个任务怎么做" | executionConfig, inputVariables |
| **TaskExecution** | 记录"这次执行的情况" | status, outputs, error |

### 改进 2: 支持自定义状态查询

```yaml
# TaskSchema 中定义自定义状态
states:
  - name: "training"
    queryConfig:
      endpoint: "/training-status"
      interval: 10000
      parser: "json"
      path: "$.training.progress"
```

执行器返回：

```json
{
  "status": "training",
  "training": {
    "progress": {
      "currentEpoch": 10,
      "totalEpochs": 100,
      "loss": 0.234
    }
  }
}
```

Dataflow 自动轮询并更新 `TaskExecution.customStateData`。

### 改进 3: 动态扩展能力

用户只需：

1. **注册 TaskSchema** (配置文件或 API)

```yaml
# custom-ml-training-schema.yaml
type: "custom_ml_training"
actions:
  start: {...}
  pause: {...}
  resume: {...}
events:
  - started
  - paused
  - resumed
  - completed
  - failed
states:
  - pending
  - running
  - training      # 自定义状态
  - validating    # 自定义状态
  - completed
executor:
  type: HTTP
  baseUrl: "http://ml-executor:8080"
```

2. **创建 TaskDefinition** (引用 Schema)

```yaml
namespace: "ai.tasks"
name: "sentiment_model_training"
taskType: "custom_ml_training"  # 引用已注册的 Schema
executionConfig:
  modelType: "transformer"
  datasetPath: "s3://data/sentiment"
```

3. **执行器实现** (任意语言)

```python
@app.route('/execute', methods=['POST'])
def execute():
    # 接收执行请求，启动训练
    ...

@app.route('/training-status', methods=['GET'])
def training_status():
    # 返回自定义状态
    return {
        "status": "training",
        "currentEpoch": 10,
        "totalEpochs": 100
    }
```

## 状态查询机制设计

### 主动轮询模式

```
Dataflow                    Executor
   │                           │
   │──execute────────────────→ │
   │                           │
   │                           │ (start training)
   │                           │
   │──GET /training-status───→ │
   │←─{status: "training", ...}│
   │                           │
   │ (wait 10s)                │
   │                           │
   │──GET /training-status───→ │
   │←─{status: "validating",...}│
   │                           │
   │ (wait 10s)                │
   │                           │
   │──GET /training-status───→ │
   │←─{status: "completed",...}│
   │                           │
   │ (stop polling)            │
```

### Webhook 推送模式（可选）

```
Dataflow                    Executor
   │                           │
   │──execute(callback_url)──→ │
   │                           │
   │                           │ (start training)
   │                           │
   │                           │ (epoch 10 completed)
   │←─POST callback({status})──│
   │                           │
   │                           │ (validation started)
   │←─POST callback({status})──│
   │                           │
   │                           │ (training completed)
   │←─POST callback({status})──│
```

## API 设计

### 注册 TaskSchema

```http
POST /api/v1/task-schemas
Content-Type: application/json

{
  "type": "custom_ml_training",
  "description": "自定义机器学习训练任务",
  "category": "CUSTOM",
  "actions": {...},
  "events": [...],
  "states": [...],
  "executor": {...},
  "executionConfigSchema": {...}
}
```

### 查询 TaskSchema

```http
GET /api/v1/task-schemas/custom_ml_training

# 响应
{
  "type": "custom_ml_training",
  "actions": {
    "start": {...},
    "pause": {...},
    "resume": {...}
  },
  "events": ["started", "paused", "resumed", "completed", "failed"],
  "states": [
    {"name": "pending", "terminal": false},
    {"name": "training", "terminal": false, "queryConfig": {...}},
    {"name": "completed", "terminal": true}
  ]
}
```

### 创建 TaskDefinition

```http
POST /api/v1/task-definitions
{
  "namespace": "ai.tasks",
  "name": "sentiment_training",
  "taskType": "custom_ml_training",  # 引用 Schema
  "executionConfig": {
    "modelType": "transformer",
    "epochs": 100
  },
  "inputVariables": [...],
  "outputVariables": [...]
}
```

### 查询执行状态（含自定义状态）

```http
GET /api/v1/task-executions/exec_001

# 响应
{
  "id": "exec_001",
  "taskDefinition": "ai.tasks:sentiment_training:1.0.0",
  "status": "training",
  "customStateData": {
    "currentEpoch": 10,
    "totalEpochs": 100,
    "trainingLoss": 0.234,
    "validationAccuracy": 0.876
  },
  "startedAt": "2025-01-15T10:00:00Z"
}
```

## 实现要点

### 1. TaskSchema 注册与验证

```java
@Component
public class TaskSchemaRegistry {

    private final Map<String, TaskSchema> schemas = new ConcurrentHashMap<>();

    public void register(TaskSchema schema) {
        // 验证 Schema
        validateSchema(schema);

        // 保存
        schemas.put(schema.getType(), schema);
    }

    public boolean supportsAction(String taskType, String action) {
        TaskSchema schema = getSchema(taskType);
        return schema.getActions().containsKey(action);
    }

    public boolean supportsState(String taskType, String state) {
        TaskSchema schema = getSchema(taskType);
        return schema.getStates().stream()
            .anyMatch(s -> s.getName().equals(state));
    }
}
```

### 2. 自定义状态轮询器

```java
@Component
public class CustomStatePoller {

    private final RestTemplate restTemplate;
    private final ScheduledExecutorService executor;

    public void startPolling(TaskExecution execution, StateDefinition stateConfig) {
        long interval = stateConfig.getQueryConfig().getInterval();

        executor.scheduleAtFixedRate(() -> {
            try {
                // 查询状态
                String url = execution.getExecutorBaseUrl() + stateConfig.getQueryConfig().getEndpoint();
                ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

                // 解析状态数据
                Object stateData = parseStateData(response.getBody(), stateConfig.getQueryConfig());

                // 更新执行记录
                execution.updateCustomStateData(stateData);

                // 如果状态变为终止态，停止轮询
                if (isTerminalState(execution.getStatus())) {
                    stopPolling(execution.getId());
                }

            } catch (Exception e) {
                log.error("Failed to poll custom state", e);
            }
        }, 0, interval, TimeUnit.MILLISECONDS);
    }
}
```

## 迁移路径

### 阶段 1: 引入 TaskSchema

1. 创建 `TaskSchema` 实体和注册表
2. 将现有 `TaskType` 枚举转为内置 `TaskSchema`
3. `TaskDefinition` 保持兼容，但内部引用 Schema

### 阶段 2: 分离能力定义

1. 从 `TaskDefinition` 中移除 `supportedActions`、`producedEvents`
2. 改为从 `TaskSchema` 查询

### 阶段 3: 支持自定义状态

1. 实现状态轮询器
2. 开放 TaskSchema 注册 API
3. 提供用户文档和示例

## 优势

1. **概念清晰**：Schema → Definition → Execution 三层明确
2. **完全可扩展**：用户可以注册任意 Schema，无需改代码
3. **自定义状态**：支持执行器自定义状态和轮询查询
4. **向后兼容**：现有设计可以平滑迁移

## 参考

- [Kubernetes Custom Resources](https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/)
- [Airflow Custom Operators](https://airflow.apache.org/docs/apache-airflow/stable/howto/custom-operator.html)
- [Temporal Workflows](https://docs.temporal.io/workflows)
