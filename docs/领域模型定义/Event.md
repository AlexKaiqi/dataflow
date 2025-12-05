# 事件系统（Event System）

## 1. 概述

事件系统是数据流平台的核心驱动机制，所有节点的执行都通过事件触发，不进行轮询。

### 核心原则

1. **事件驱动**：所有执行由事件触发，引擎只响应事件
2. **状态订阅**：引擎根据节点状态订阅不同的事件
3. **一次性触发**：大多数事件只触发一次，触发后移除订阅
4. **类型安全**：事件携带类型化的 payload

---

## 2. 事件分类

### 2.1 任务生命周期事件

#### 批处理任务（Batch Task）

| 事件名 | 时机 | Payload | 说明 |
|--------|------|---------|------|
| `started` | 任务开始执行 | `{ executionId, startedAt }` | 进入 RUNNING 状态 |
| `succeeded` | 任务执行成功 | `{ executionId, outputs, completedAt }` | 输出数据已就绪 |
| `failed` | 任务执行失败 | `{ executionId, error, completedAt }` | 可能触发重试 |
| `skipped` | 任务被跳过 | `{ executionId, reason }` | 上游失败或条件不满足 |
| `completed` | 任务完成（成功或失败） | `{ executionId, status, completedAt }` | 终态事件 |

#### 流式服务（Streaming Service）

| 事件名 | 时机 | Payload | 说明 |
|--------|------|---------|------|
| `started` | 服务启动完成 | `{ executionId, startedAt }` | 开始提供服务 |
| `stopped` | 服务已停止 | `{ executionId, stoppedAt, reason }` | 主动停止或异常退出 |
| `statusChanged` | 状态变化 | `{ executionId, status, health }` | 健康检查或降级 |

**状态字段**：

- `status`: `starting` \| `running` \| `degraded` \| `stopped`
- `health`: `healthy` \| `unhealthy`

### 2.2 流程生命周期事件

| 事件名 | 时机 | Payload |
|--------|------|---------|
| `PipelineExecutionStarted` | 流程开始执行 | `{ executionId, pipelineId, version }` |
| `PipelineExecutionRoundStarted` | 新轮次开始 | `{ executionId, roundNumber }` |
| `PipelineExecutionSucceeded` | 流程执行成功 | `{ executionId, completedAt }` |
| `PipelineExecutionFailed` | 流程执行失败 | `{ executionId, failedNodes[] }` |
| `PipelineExecutionStopped` | 流程被终止 | `{ executionId, stoppedAt }` |

### 2.3 时钟事件

| 事件名 | 时机 | Payload |
|--------|------|---------|
| `cron:<pattern>` | 定时触发 | `{ executionDate, triggeredAt }` |
| `cron:@startup` | 流程启动时 | `{ startedAt }` |

### 2.4 外部事件

由外部系统通过 API 触发的事件，需在 TaskDefinition 中声明。

**示例**：

```yaml
TaskDefinition:
  type: "approval"
  externalEvents:
    - name: "approved"
      schema:
        approver: string
        comment: string
        timestamp: datetime
    - name: "rejected"
      schema:
        approver: string
        reason: string
        timestamp: datetime
```

**事件引用**：

```yaml
Node:
  alias: "process_approved_data"
  startWhen: "event:quality_check.approved"
```

---

## 3. 事件总线（Event Bus）

### 3.1 架构

```text
┌─────────────────────────────────────────┐
│          事件发布者（Publishers）        │
│  - TaskExecution                        │
│  - PipelineExecution                    │
│  - CronScheduler                        │
│  - ExternalEventAPI                     │
└─────────────────────────────────────────┘
              ↓ publish(event)
┌─────────────────────────────────────────┐
│         事件总线（Event Bus）            │
│  - 事件路由                             │
│  - 订阅管理                             │
│  - 事件持久化（可选）                   │
└─────────────────────────────────────────┘
              ↓ notify(subscribers)
┌─────────────────────────────────────────┐
│        执行引擎（Execution Engine）      │
│  - 监听事件                             │
│  - 计算条件                             │
│  - 触发节点执行                         │
└─────────────────────────────────────────┘
```

### 3.2 订阅管理

**订阅生命周期**：

```text
节点状态: pending
  订阅: startWhen 中的事件
  触发: 收到事件 → 计算条件 → 状态转换
  清理: 转换为 running/waiting → 移除订阅

节点状态: running (批处理)
  订阅: 无
  触发: 任务自然完成
  清理: 转换为终态 → 无需清理

节点状态: running (流式)
  订阅: stopWhen 中的事件
  触发: 收到事件 → 计算条件 → 停止服务
  清理: 转换为 stopped → 移除订阅

节点状态: waiting (审批/外部事件)
  订阅: 审批完成事件或外部事件
  触发: 收到事件 → 决定成功/失败
  清理: 转换为终态 → 移除订阅
```

**关键规则**：

1. **状态驱动订阅**：不同状态订阅不同事件
2. **转换后移除**：状态转换后立即移除旧订阅
3. **一次性触发**：同一事件不会触发同一节点两次

---

## 4. 事件表达式

事件表达式用于定义**何时检查**某个条件，详见 [Expression.md](./Expression.md)。

### 4.1 基本语法

```yaml
# 单个事件
event:<nodeAlias>.<eventName>

# 组合事件
event:a.succeeded && event:b.succeeded
event:a.completed || event:b.completed
!event:upstream.failed

# 时钟事件
cron:0 2 * * *
cron:@startup
```

### 4.2 使用场景

| 字段 | 事件表达式 | 检查时机 |
|------|-----------|---------|
| `startWhen` | ✅ 必需 | 何时检查启动条件 |
| `stopWhen` | ✅ 可选 | 何时检查停止条件（仅流式） |
| `retryWhen` | ❌ | 失败后自动检查（不是事件） |
| `alertWhen` | ❌ | 完成后自动检查（不是事件） |

**示例**：

```yaml
- alias: "processor"
  executionMode: "streaming"
  startWhen: "event:kafka_source.started"    # 监听启动事件
  stopWhen: "event:kafka_source.stopped"     # 监听停止事件
```

---

## 5. 事件与状态的融合

### 5.1 混合表达式

支持在一个表达式中组合事件和状态：

```yaml
# 语法
startWhen: "<EventExpression> && <StateExpression>"

# 示例
startWhen: "event:upstream.succeeded && {{ upstream.output.row_count > 0 }}"
stopWhen: "event:monitor.statusChanged && {{ monitor.health == 'critical' }}"
```

**语义**：

- **EventExpression**: 定义检查时机（引擎订阅事件）
- **StateExpression**: 定义检查条件（事件发生时计算）

### 5.2 执行流程

```text
1. 引擎根据 EventExpression 订阅事件
2. 事件发生时：
   a. 读取变量（从变量管理器）
   b. 计算 StateExpression
   c. 决定是否触发行为（启动/停止/告警）
3. 状态转换后移除订阅
```

---

## 6. 任务类型与事件

### 6.1 批处理任务

**发布的事件**：

- `started`, `succeeded`, `failed`, `skipped`, `completed`

**监听的事件**：

- `startWhen`: 何时启动

**示例**：

```yaml
- alias: "extract"
  taskType: "sql"
  startWhen: "cron:0 2 * * *"
  retryWhen: "{{ retry_count < 3 }}"
```

### 6.2 流式任务

**发布的事件**：

- `started`, `stopped`, `statusChanged`

**监听的事件**：

- `startWhen`: 何时启动
- `stopWhen`: 何时停止

**示例**：

```yaml
- alias: "kafka_consumer"
  taskType: "flink_sql"
  executionMode: "streaming"
  startWhen: "event:kafka_source.started"
  stopWhen: "event:kafka_source.stopped"
```

### 6.3 审批节点

**发布的事件**：

- `started`, `approved`, `rejected`, `timeout`

**监听的事件**：

- `startWhen`: 何时开始等待审批
- 审批事件由平台管理

**示例**：

```yaml
- alias: "quality_check"
  taskType: "approval"
  startWhen: "event:upstream.succeeded"
  approvalConfig:
    approvers: ["user@example.com"]
    timeout: 86400
```

**下游使用**：

```yaml
- alias: "process_approved"
  startWhen: "event:quality_check.approved"

- alias: "process_rejected"
  startWhen: "event:quality_check.rejected"
```

---

## 7. 实现指导

### 7.1 事件总线实现

**必需功能**：

1. 发布事件：`publish(eventName, payload)`
2. 订阅事件：`subscribe(eventPattern, callback)`
3. 取消订阅：`unsubscribe(subscriptionId)`
4. 事件过滤：支持通配符（如 `event:*.succeeded`）

**可选功能**：

1. 事件持久化（用于重放和审计）
2. 事件顺序保证（同一节点的事件按顺序处理）
3. 事件超时处理（长时间未收到事件时告警）

### 7.2 执行引擎集成

```python
class ExecutionEngine:
    def __init__(self, event_bus, variable_store):
        self.event_bus = event_bus
        self.variable_store = variable_store
    
    def start_node(self, node):
        """启动节点监听"""
        # 解析 startWhen 中的事件
        events = parse_event_expression(node.startWhen)
        
        # 订阅所有相关事件
        for event in events:
            subscription_id = self.event_bus.subscribe(
                event_pattern=event,
                callback=lambda payload: self.handle_event(node, payload)
            )
            # 记录订阅ID，用于后续清理
            self.subscriptions[node.alias] = subscription_id
    
    def handle_event(self, node, payload):
        """处理事件"""
        # 1. 读取变量
        variables = self.variable_store.get_variables(node)
        
        # 2. 计算条件
        if node.has_state_condition():
            condition = evaluate_state_expression(
                node.startWhen, 
                variables
            )
            if not condition:
                return  # 条件不满足，不触发
        
        # 3. 触发行为
        self.trigger_node_execution(node)
        
        # 4. 清理订阅
        self.cleanup_subscriptions(node, 'startWhen')
```

### 7.3 订阅清理策略

```python
def cleanup_subscriptions(self, node, phase):
    """清理指定阶段的订阅"""
    if phase == 'startWhen':
        # 节点启动后，移除 startWhen 订阅
        subscription_id = self.subscriptions.get(node.alias)
        if subscription_id:
            self.event_bus.unsubscribe(subscription_id)
            del self.subscriptions[node.alias]
        
        # 如果是流式任务，订阅 stopWhen
        if node.executionMode == 'streaming' and node.stopWhen:
            events = parse_event_expression(node.stopWhen)
            for event in events:
                subscription_id = self.event_bus.subscribe(
                    event_pattern=event,
                    callback=lambda p: self.handle_stop_event(node, p)
                )
                self.subscriptions[f"{node.alias}_stop"] = subscription_id
```

---

## 8. 与其他文档的关系

| 文档 | 关系 |
|------|------|
| **Expression.md** | 定义事件表达式的语法规范 |
| **Node.md** | 定义节点如何使用事件表达式（startWhen, stopWhen） |
| **TaskExecution.md** | 定义任务执行发布的生命周期事件 |
| **PipelineExecution.md** | 定义流程执行发布的生命周期事件 |
| **TaskDefinition.md** | 定义外部事件的声明方式 |

---

## 9. 常见问题

### Q1: 事件会不会丢失？

**答**：取决于实现。建议：

- 使用可靠的消息队列（如 Kafka、RabbitMQ）
- 事件持久化到数据库
- 提供事件重放机制


### Q2: 如果节点已经在运行，又收到启动事件怎么办？

**答**：不会触发。

- 节点启动后移除 `startWhen` 订阅
- 新的事件不会再触发该节点
- 重复执行由 Pipeline 调度管理，不是单节点职责


### Q3: 流式任务可以反复启动/停止吗？

**答**：不能（单个执行实例）。

- 每个 TaskExecution 只启动一次，停止后进入终态
- 如果需要重启，应该创建新的 TaskExecution


### Q4: 外部事件如何注入？

**答**：通过 API。

```http
POST /api/events
{
  "pipelineExecutionId": "exec-123",
  "nodeAlias": "quality_check",
  "eventName": "approved",
  "payload": {
    "approver": "user@example.com",
    "comment": "LGTM"
  }
}
```

### Q5: 事件表达式中的 `&&`/`||` 如何处理？

**答**：

- `event:a.succeeded && event:b.succeeded`：两个事件都发生时触发
- 引擎维护事件发生状态，当所有条件满足时触发
- 详见 Expression.md 的事件组合逻辑


---

## 10. 扩展性

### 10.1 自定义事件

TaskDefinition 可以声明自定义事件：

```yaml
TaskDefinition:
  type: "custom_processor"
  customEvents:
    - name: "data_quality_check_done"
      schema:
        score: number
        pass: boolean
        details: string
```

### 10.2 事件钩子

未来可以支持全局事件钩子：

```yaml
PipelineDefinition:
  eventHooks:
    - on: "*.failed"
      action: "send_alert"
      config:
        channel: "slack"
        webhook: "https://..."
```

### 10.3 事件过滤器

支持更复杂的事件过滤：

```yaml
startWhen: "event:upstream.succeeded[output.row_count > 1000]"
```

---

## 11. 总结

| 概念 | 核心职责 | 关键特性 |
|------|---------|---------|
| **事件总线** | 事件路由与订阅管理 | 发布/订阅、状态驱动 |
| **事件表达式** | 定义检查时机 | 事件组合、混合状态 |
| **执行引擎** | 事件响应与节点触发 | 订阅清理、一次性触发 |

**核心原则**：

- 事件驱动：所有执行由事件触发
- 状态订阅：不同状态订阅不同事件
- 一次性触发：避免重复执行和资源浪费
