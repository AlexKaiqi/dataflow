# 任务类型：Approval（审批节点）

## 1. 概述

Approval 是一种特殊的任务类型，用于在数据流中插入人工审批环节。审批节点不执行任何数据处理逻辑，而是等待外部审批决策，根据审批结果决定流程的走向。

**核心特点**：

- **无计算逻辑**：不处理数据，只等待审批
- **事件驱动**：通过外部 API 触发审批事件
- **分支控制**：下游节点根据审批结果执行不同分支
- **平台集成**：平台提供审批 UI 和通知机制

**典型场景**：

- 数据质量审核：数据工程师审核数据清洗结果
- 模型发布审批：模型训练完成后，需要审批才能部署
- 敏感数据访问：访问敏感数据前需要权限审批
- 实验开关：A/B 测试开关需要人工确认

---

## 2. 任务定义结构

### 2.1 基本结构

```yaml
TaskDefinition:
  namespace: "com.company.tasks"
  name: "quality_check_approval"
  version: "1.0.0"
  type: "approval"
  
  # 审批配置
  executionDefinition:
    approvers: string[]           # 审批人列表
    description: string           # 审批说明
    timeout: integer              # 超时时间（秒）
    approvalUI: object            # 审批界面配置（可选）
  
  # 输入变量（审批时可查看的数据）
  inputVariables:
    - name: data_summary
      type: object
      required: true
    - name: quality_metrics
      type: object
      required: true
  
  # 输出变量（审批结果）
  outputVariables:
    - name: status
      type: string              # approved / rejected / timeout
    - name: approver
      type: string
    - name: comment
      type: string
    - name: timestamp
      type: datetime
```

### 2.2 executionDefinition 详解

```yaml
executionDefinition:
  # 审批人配置
  approvers:
    - "user1@example.com"
    - "user2@example.com"
  
  # 审批说明（显示在审批界面）
  description: |
    请审核数据清洗结果：
    - 检查数据质量指标
    - 验证空值率是否符合要求
    - 确认数据分布是否正常
  
  # 超时时间（秒）
  timeout: 86400  # 24小时
  
  # 审批界面配置（可选）
  approvalUI:
    title: "数据质量审核"
    fields:
      - name: "data_summary"
        label: "数据摘要"
        type: "json"
      - name: "quality_metrics"
        label: "质量指标"
        type: "table"
```

---

## 3. 生命周期事件

审批节点发布以下事件：

### 3.1 标准事件

| 事件名 | 时机 | Payload | 说明 |
|--------|------|---------|------|
| `started` | 开始等待审批 | `{ executionId, startedAt }` | 审批请求已创建 |
| `approved` | 审批通过 | `{ executionId, approver, comment, timestamp }` | 审批人批准 |
| `rejected` | 审批拒绝 | `{ executionId, approver, reason, timestamp }` | 审批人拒绝 |
| `timeout` | 审批超时 | `{ executionId, timeoutAt }` | 超过timeout未审批 |

### 3.2 事件引用示例

```yaml
# 下游节点根据审批结果执行不同分支
- alias: "process_approved_data"
  startWhen: "event:quality_check.approved"

- alias: "handle_rejection"
  startWhen: "event:quality_check.rejected"

- alias: "handle_timeout"
  startWhen: "event:quality_check.timeout"
```

---

## 4. Node 配置示例

### 4.1 基本审批节点

```yaml
nodes:
  - alias: "quality_check"
    taskRef:
      namespace: "com.company.tasks"
      name: "quality_check_approval"
      version: "1.0.0"
    startWhen: "event:data_cleaning.succeeded"
    inputBindings:
      data_summary:
        type: "reference"
        source: "data_cleaning.output.summary"
      quality_metrics:
        type: "reference"
        source: "data_cleaning.output.metrics"
```

### 4.2 带超时告警的审批节点

```yaml
nodes:
  - alias: "quality_check"
    taskRef:
      namespace: "com.company.tasks"
      name: "quality_check_approval"
      version: "1.0.0"
    startWhen: "event:data_cleaning.succeeded"
    inputBindings:
      data_summary:
        type: "reference"
        source: "data_cleaning.output.summary"
      quality_metrics:
        type: "reference"
        source: "data_cleaning.output.metrics"
    alertWhen: "{{ quality_check.status == 'timeout' }}"
    alertConfig:
      channels: ["email", "slack"]
      message: "数据质量审核超时，请尽快处理"
```

### 4.3 完整的审批工作流

```yaml
nodes:
  # 数据清洗
  - alias: "data_cleaning"
    taskRef:
      namespace: "com.company.tasks"
      name: "clean_user_data"
      version: "1.0.0"
    startWhen: "cron:0 2 * * *"
  
  # 质量审核
  - alias: "quality_check"
    taskRef:
      namespace: "com.company.tasks"
      name: "quality_check_approval"
      version: "1.0.0"
    startWhen: "event:data_cleaning.succeeded"
    inputBindings:
      data_summary:
        type: "reference"
        source: "data_cleaning.output.summary"
      quality_metrics:
        type: "reference"
        source: "data_cleaning.output.metrics"
  
  # 审批通过分支
  - alias: "load_to_warehouse"
    taskRef:
      namespace: "com.company.tasks"
      name: "load_data"
      version: "1.0.0"
    startWhen: "event:quality_check.approved"
    inputBindings:
      data_path:
        type: "reference"
        source: "data_cleaning.output.path"
  
  # 审批拒绝分支
  - alias: "notify_failure"
    taskRef:
      namespace: "com.company.tasks"
      name: "send_notification"
      version: "1.0.0"
    startWhen: "event:quality_check.rejected"
    inputBindings:
      message:
        type: "literal"
        value: "数据质量审核未通过，请检查数据"
```

---

## 5. 审批 API

### 5.1 提交审批决策

```http
POST /api/v1/executions/{executionId}/approve
Content-Type: application/json

{
  "decision": "approved",  # approved | rejected
  "comment": "数据质量符合要求",
  "approver": "reviewer@example.com"
}
```

**响应**：

```json
{
  "executionId": "exec-123",
  "status": "approved",
  "approver": "reviewer@example.com",
  "comment": "数据质量符合要求",
  "timestamp": "2025-12-05T10:30:00Z"
}
```

### 5.2 查询审批状态

```http
GET /api/v1/executions/{executionId}
```

**响应**：

```json
{
  "executionId": "exec-123",
  "taskId": "com.company.tasks:quality_check_approval:1.0.0",
  "status": "waiting",  # waiting | approved | rejected | timeout
  "inputVariables": {
    "data_summary": { ... },
    "quality_metrics": { ... }
  },
  "approvers": ["reviewer@example.com"],
  "timeout": 86400,
  "startedAt": "2025-12-05T10:00:00Z",
  "expiresAt": "2025-12-06T10:00:00Z"
}
```

---

## 6. 平台集成

### 6.1 审批界面

平台应该提供审批界面，包括：

- **数据展示**：显示 inputVariables 中的数据
- **审批操作**：批准/拒绝按钮
- **评论输入**：审批人填写意见
- **历史记录**：显示审批历史

### 6.2 通知机制

平台应该在以下时机发送通知：

- 审批请求创建时 → 通知审批人
- 审批即将超时时 → 提醒审批人
- 审批超时时 → 通知流程创建者

### 6.3 权限控制

- 只有指定的审批人可以提交审批决策
- 审批历史应该可追溯
- 支持审批人委托（可选）

---

## 7. 状态机

```text
┌─────────┐
│ PENDING │  # 节点创建，等待 startWhen
└────┬────┘
     │ startWhen 触发
     ↓
┌─────────┐
│ WAITING │  # 等待审批
└────┬────┘
     │
     ├─→ 审批通过 → SUCCEEDED (发布 approved 事件)
     ├─→ 审批拒绝 → FAILED (发布 rejected 事件)
     └─→ 审批超时 → FAILED (发布 timeout 事件)
```

---

## 8. 输出变量

审批节点自动生成以下输出变量：

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `status` | string | `approved` \| `rejected` \| `timeout` |
| `approver` | string | 审批人邮箱（timeout 时为空） |
| `comment` | string | 审批意见 |
| `timestamp` | datetime | 审批时间 |

**下游使用**：

```yaml
- alias: "send_notification"
  startWhen: "event:quality_check.approved"
  inputBindings:
    message:
      type: "literal"
      value: "数据审核通过，审批人：{{ quality_check.output.approver }}"
```

---

## 9. 与其他任务类型的对比

| 维度 | Approval | SQL/Operator | Streaming |
|------|----------|-------------|-----------|
| **执行逻辑** | 无（等待外部输入） | 有（数据处理） | 有（持续运行） |
| **输入** | 展示数据 | 处理数据 | 消费数据流 |
| **输出** | 审批结果 | 处理结果 | 数据流 |
| **终止条件** | 审批/拒绝/超时 | 成功/失败 | 停止事件 |
| **重试** | 不支持 | 支持 | 不支持 |

---

## 10. 常见问题

### Q1: 如果审批人不在线怎么办？

**答**：使用 timeout 机制。

```yaml
executionDefinition:
  approvers: ["reviewer@example.com"]
  timeout: 86400  # 24小时超时
  
# 超时后触发告警或默认操作
- alias: "handle_timeout"
  startWhen: "event:quality_check.timeout"
```

### Q2: 可以有多个审批人吗？

**答**：当前设计支持多个审批人，任意一人审批即可。未来可以扩展为：

- **任意一人审批**：approvalMode: "any"
- **所有人审批**：approvalMode: "all"
- **多数人审批**：approvalMode: "majority"

### Q3: 审批决策可以撤回吗？

**答**：当前设计不支持撤回。审批一旦提交，节点进入终态（SUCCEEDED/FAILED）。

### Q4: 审批节点可以重跑吗？

**答**：可以通过 Pipeline 重跑机制重新创建审批请求。

### Q5: 审批界面如何定制？

**答**：通过 approvalUI 配置：

```yaml
executionDefinition:
  approvalUI:
    title: "自定义标题"
    fields:
      - name: "data_summary"
        label: "数据摘要"
        type: "json"  # json | table | text | image
      - name: "quality_metrics"
        label: "质量指标"
        type: "table"
```

---

## 11. 总结

Approval 任务类型的核心价值：

1. **人机协作**：在自动化流程中插入人工决策点
2. **质量把关**：关键环节需要人工审核
3. **合规要求**：某些操作需要审批记录
4. **灵活控制**：根据审批结果动态调整流程走向

**关键设计**：

- 无计算逻辑，只等待审批
- 事件驱动，通过 API 提交决策
- 平台集成，提供审批 UI 和通知
- 支持超时，避免流程阻塞

**参考文档**：

- [Event.md](../Event.md) - 了解审批事件的发布和订阅
- [Expression.md](../Expression.md) - 了解如何使用审批结果
- [PipelineDefinition.md](../PipelineDefinition.md) - 了解如何配置审批节点
