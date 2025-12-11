# 任务类型：Approval (人工审批)

## 1. 概述

**Approval** 任务类型用于在工作流中引入人工干预环节。当流程执行到此节点时，会暂停并等待外部系统的信号（批准或拒绝），常用于上线发布、高危操作确认等场景。

**核心特性**:
- **异步等待**: 任务启动后进入挂起状态，不占用计算资源。
- **超时控制**: 支持配置审批超时时间。
- **多渠道通知**: 集成邮件、IM 等通知渠道。

---

## 2. TaskSchema 能力定义

```yaml
TaskSchema:
  type: "approval"
  description: "人工审批任务"

  # ==== 1. 支持的行为 (Actions) ====
  actions:
    request_approval:
      description: "发起审批请求"
      params:
        approvers: List<String>
        context: Map<String, Any>

    approve:
      description: "批准"
      params:
        approver: string
        comment: string?

    reject:
      description: "拒绝"
      params:
        approver: string
        comment: string?

    remind:
      description: "发送催办通知"
      params: {}

  # ==== 2. 产生的事件 (Events) ====
  events:
    - name: "approval_requested"
      payload: { ticketId: string, approvers: List<String> }

    - name: "approved"
      payload: { approver: string, comment: string, timestamp: long }

    - name: "rejected"
      payload: { approver: string, comment: string, timestamp: long }

    - name: "timeout"
      payload: { duration: long }

  # ==== 3. 状态定义 (States) ====
  states:
    PENDING: "等待审批"
    APPROVED: "已批准"
    REJECTED: "已拒绝"
    EXPIRED: "审批超时"
```

---

## 3. TaskDefinition 配置结构

```yaml
TaskDefinition:
  type: "approval"

  # 审批配置
  approvalConfig:
    approvers: List<String>          # 审批人列表 (用户ID或角色)
    minApprovals: int                # 最少通过人数 (默认 1)
    timeoutSeconds: long             # 超时时间 (秒)

    # 通知模板
    notification:
      title: string
      content: string                # 支持变量替换
      channels: ["email", "slack", "webhook"]?

  # 回调配置 (可选)
  callback:
    url: string                      # 审批状态变更回调地址
```

## 4. 交互流程

1. **发起**: 任务启动，状态变为 `PENDING`，发送 `approval_requested` 事件。
2. **等待**: 系统通过配置的渠道通知审批人。
3. **决策**:
   - 审批人调用 `approve` -> 状态变为 `APPROVED`，任务成功。
   - 审批人调用 `reject` -> 状态变为 `REJECTED`，任务失败。
   - 超过 `timeoutSeconds` -> 状态变为 `EXPIRED`，任务失败。
