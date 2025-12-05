
# 聚合根：PipelineExecution（工作流执行）

## 职责

- **工作流执行记录**：记录 PipelineDefinition 的执行实例
- **灵活重跑**：支持从任意节点重跑，自动级联下游执行
- **执行审计**：通过 Round 机制完整记录每次执行的链路和变量快照

## 结构

```text
PipelineExecution（聚合根）
├── id: string (executionId，全局唯一)
├── pipelineId: string (所执行工作流的 id)
├── pipelineVersion: string (所执行工作流的版本)
├── status: "PENDING" | "RUNNING" | "SUCCESS" | "FAILURE" | "STOPPED"
├── inputVariables: Map<string, any> (工作流级变量的值，整个 Execution 期间保持不变)
├── rounds[]
│   ├── roundNumber: integer (轮次号，从 1 开始)
│   ├── status: "PENDING" | "RUNNING" | "SUCCESS" | "FAILURE" | "STOPPED"
│   ├── nodeExecutions: Map<nodeAlias, executionId> (该轮次中各节点的执行 id 引用)
│   │   # key: 节点别名（对应 PipelineDefinition.nodes[].alias）
│   │   # value: TaskExecution.id（任务节点）或其他执行记录 ID
│   ├── variableOverrides: Map<string, any> (该轮次的变量覆盖值，用于重跑时修改参数)
│   ├── startedAt: Timestamp
│   ├── completedAt: Timestamp
│   └── triggeredBy: string (该轮次由谁触发："initial" | nodeAlias)
└── metadata
    ├── createdAt: Timestamp
    ├── createdBy: string
    └── tags: string[]
```

## 不变式

1. **唯一性**
   - executionId 全局唯一
   - rounds[] 中的 roundNumber 在同一 Execution 内唯一且递增

2. **版本绑定**
   - pipelineId + pipelineVersion 必须指向存在且已发布的 PipelineDefinition

3. **变量一致性**
   - inputVariables 整个 Execution 期间不变
   - 不同 round 间通过 variableOverrides 记录参数变化

4. **Round 完成条件**（简化后的规则）
   - Round 完成 = nodeExecutions 中所有节点的执行状态都不在 [PENDING, RUNNING]
   - 节点执行状态包括：SUCCESS, FAILURE, STOPPED, SKIPPED
   - Round 状态计算：
     - 所有节点 SUCCESS → Round SUCCESS
     - 任何节点 FAILURE/STOPPED → Round FAILURE
     - 部分 SKIPPED + 其余 SUCCESS → Round SUCCESS

5. **节点执行状态**
   - 上游节点失败时，下游节点标记为 SKIPPED（不再是 PENDING）
   - 只有在上游都 SUCCESS 的情况下，下游节点才能执行
   - 条件节点（Condition）为 false 时，其下游节点标记为 SKIPPED

6. **重跑约束**
   - 只能在最后一个 Round 完成后才能重跑
   - 重跑时必须检查上游节点都已完成
   - 重跑新建 Round，roundNumber 自动递增
   - 重跑可以指定多个节点同时重跑

7. **状态转移**
   - Execution 的状态由最后一个 Round 的状态决定（动态计算）
   - STOPPED 是最终状态，Execution 无法继续执行

## 事件

- `PipelineExecutionStarted` - 工作流执行已开始
- `PipelineExecutionRoundStarted` - 新的执行轮次已开始
- `PipelineExecutionSucceeded` - 工作流执行成功
- `PipelineExecutionFailed` - 工作流执行失败
- `PipelineExecutionStopped` - 工作流执行被终止

## 命令

### StartPipeline 命令

**参数**：

- `pipelineId` (string，必需)：工作流 ID
- `version` (string，必需)：工作流版本
- `inputVariables` (Map<string, any>，必需)：工作流级输入变量及其值

**说明**：
启动工作流执行，生成新的 executionId 和第一个 Round。系统会验证输入变量，然后根据工作流定义中的依赖关系，逐步执行任务。

**返回**：
返回新创建的 PipelineExecution 对象，包含自动生成的 executionId 和初始 Round。

**业务规则**：

- PipelineDefinition(pipelineId, version) 必须存在且已发布
- `inputVariables` 必须包含该 PipelineDefinition 的所有必需输入变量
- 初始状态必须为 PENDING
- 创建第一个 Round，roundNumber = 1，triggeredBy = "initial"

---

### ReplayExecution 命令

**参数**：

- `executionId` (string，必需)：执行 ID
- `targetNodes` (string[]，必需)：要重新执行的节点别名列表
- `mode` (string，可选)：重跑模式
  - `"from_nodes"`（默认）：从指定节点开始，级联执行所有下游
  - `"only_nodes"`：仅重跑指定节点，不影响下游
  - `"downstream_only"`：仅重跑指定节点的下游，不包含节点本身
- `forceRerun` (boolean，可选)：是否强制重跑已成功的节点，默认 false
- `variableOverrides` (Map<string, any>，可选)：该轮次的变量覆盖值

**说明**：
从指定节点重新执行。前置条件：目标节点的上游必须都已完成。系统会创建新的 Round 并根据 mode 参数决定重跑范围。

**返回**：
返回新创建的 Round 记录，包含 roundNumber 和新的节点执行列表。

**业务规则**：

- 只能在最后一个 Round 完成后才能重跑
- 重跑时必须检查上游节点都已完成（状态不在 [PENDING, RUNNING]）
- 重跑新建 Round，roundNumber 自动递增
- variableOverrides 仅对本轮次生效
- `mode="from_nodes"`：
  - 重新执行 targetNodes 及其所有可达下游
  - 如果 forceRerun=false，跳过已经 SUCCESS 的节点
- `mode="only_nodes"`：
  - 仅重新执行 targetNodes，下游使用上一轮的结果
  - 适用于调试单个节点
- `mode="downstream_only"`：
  - 不重跑 targetNodes，仅重跑其下游
  - 适用于 targetNodes 已修复，需要传播结果的场景

---

### StopExecution 命令

**参数**：

- `executionId` (string，必需)：执行 ID

**说明**：
强制停止执行。当前 Round 中所有未完成的任务标记为 STOPPED，整个执行标记为 STOPPED。

**返回**：
返回更新后的 PipelineExecution 对象，状态为 STOPPED。

**业务规则**：

- STOPPED 是最终状态，Execution 无法继续执行
- 已运行的任务保持原状态，未运行的任务标记为 STOPPED

---

## 查询

### GetPipelineExecution

**参数**：

- `executionId` (string，必需)：执行 ID

**说明**：
获取工作流执行的完整记录，包括所有 Round 的执行详情和变量快照。

**返回**：

```json
{
  "id": "string",
  "pipelineId": "string",
  "pipelineVersion": "string",
  "status": "PENDING | RUNNING | SUCCESS | FAILURE | STOPPED",
  "inputVariables": "Map<string, any>",
  "rounds": [
    {
      "roundNumber": "int",
      "status": "PENDING | RUNNING | SUCCESS | FAILURE | STOPPED",
      "taskExecutions": "Map<taskAlias, executionId>",
      "variableOverrides": "Map<string, any>",
      "startedAt": "Timestamp",
      "completedAt": "Timestamp",
      "triggeredBy": "string"
    }
  ],
  "metadata": {
    "createdAt": "Timestamp",
    "createdBy": "string",
    "tags": "string[]"
  }
}
```

**业务规则**：

- 返回所有 Round 的完整信息
- 提供完整的执行链路追踪

**使用场景**：

- 监控工作流执行进度
- 查看完整的执行历史
- 审计执行链路

### GetPipelineExecutionRound

**参数**：

- `executionId` (string，必需)：执行 ID
- `roundNumber` (int，可选)：轮次号，不指定时返回最后一个 Round

**说明**：
获取指定轮次的执行详情。

**返回**：
指定 Round 的详细信息，包括该轮次中所有任务的执行记录。

**业务规则**：

- roundNumber 必须存在于该执行中
- 若不指定 roundNumber，返回最后一个 Round

**使用场景**：

- 查看特定轮次的执行细节
- 分析重跑过程

### ListPipelineExecutions

**参数**：

- `pipelineId` (string，必需)：工作流 ID
- `version` (string，可选)：版本号，可选
- `status` (string，可选)：执行状态筛选
- `pageNumber` (int)：页码，默认 1
- `pageSize` (int)：每页数量，默认 20

**说明**：
列出工作流的执行历史，支持分页和筛选。

**返回**：
分页的 PipelineExecution 列表。

**业务规则**：

- 默认按创建时间倒序排列
- 支持按状态筛选

**使用场景**：

- 查看工作流的执行历史
- 统计执行情况

### GetNodeExecutionInRound

**参数**：

- `executionId` (string，必需)：执行 ID
- `roundNumber` (int，必需)：轮次号
- `nodeAlias` (string，必需)：节点别名

**说明**：
获取指定 Round 中某个节点的执行记录。

**返回**：
节点的完整执行记录（TaskExecution 对象或其他执行记录）。

**业务规则**：

- 节点必须在该 Round 中存在

**使用场景**：

- 查看特定节点在特定 Round 中的执行结果
- 排查节点执行失败
- 分析条件节点的判断结果

---
