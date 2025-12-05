# 聚合根：TaskExecution（任务执行）

## 职责

- **任务执行记录**：记录 TaskDefinition 的独立执行
- **变量快照**：保存本次执行的输入和输出变量值
- **重跑支持**：支持重新执行，每次执行都是独立的新记录

## 结构

```text
TaskExecution（聚合根）
├── id: string (executionId，全局唯一)
├── taskId: string (所执行任务的 id)
├── taskVersion: string (所执行任务的版本)
├── status: "PENDING" | "RUNNING" | "SUCCESS" | "FAILURE" | "STOPPED" | "SKIPPED"
├── resolvedInputs: Map<string, any> # 实际解析的输入变量及其值
├── outputs: Map<string, any> (输出变量及其值，仅 SUCCESS 时有值)
├── errorMessage: string (错误信息，仅 FAILURE 时有值)
├── skipReason: string (跳过原因，仅 SKIPPED 时有值)
├── startedAt: Timestamp
├── completedAt: Timestamp
└── metadata
    ├── createdAt: Timestamp
    ├── createdBy: string
    ├── sparkApplicationId: string (PySpark 任务才有此字段)
    └── tags: string[]
```

## 不变式

1. **唯一性**
   - executionId 全局唯一

2. **版本绑定**
   - taskId + taskVersion 必须指向存在且已发布的 TaskDefinition

3. **状态转移**
   - 允许的转移：
     - PENDING → RUNNING → SUCCESS/FAILURE
     - PENDING → STOPPED（被终止）
     - PENDING → SKIPPED（上游失败或条件不满足）
     - RUNNING → STOPPED（被终止）
   - STOPPED、SUCCESS、FAILURE、SKIPPED 都是最终状态

4. **SKIPPED 状态**
   - 上游节点失败或被终止时，下游节点标记为 SKIPPED
   - 条件节点判断为 false 时，其下游节点标记为 SKIPPED
   - SKIPPED 节点不消耗执行资源，不产生实际的任务执行
   - skipReason 记录跳过的具体原因（如 "upstream_failed: collect_data"）

## 事件

- `TaskExecutionCreated` - 任务执行已创建
- `TaskExecutionStarted` - 任务执行已开始（状态变为 RUNNING）
- `TaskExecutionSucceeded` - 任务执行成功，输出已保存
- `TaskExecutionFailed` - 任务执行失败，错误信息已记录
- `TaskExecutionStopped` - 任务执行被终止
- `TaskExecutionSkipped` - 任务执行被跳过（上游失败或条件不满足）

## 命令

### ExecuteTask 命令

**参数**：

- `taskId` (string，必需)：要执行的任务ID
- `taskVersion` (string，必需)：任务版本
- `inputs` (Map<string, any>，必需)：输入变量及其值

**说明**：
执行一个指定版本的任务定义，生成新的执行记录。系统会生成全局唯一的 executionId，解析并保存输入变量到 resolvedInputs，创建新的 TaskExecution 聚合根，初始状态为 PENDING。每次执行都是独立的新记录，支持重跑。

**返回**：
返回新创建的 TaskExecution 对象，包含自动生成的 executionId。

**业务规则**：

- TaskDefinition(taskId, taskVersion) 必须存在且已发布
- `inputs` 必须包含该 TaskDefinition 的所有必需输入变量
- 所有输入变量的类型必须符合定义
- executionId 全局唯一
- 每次执行都会记录 createdAt 和 createdBy
- 初始状态必须为 PENDING

### 任务执行查询

#### GetTaskExecution

**参数**：

- `executionId` (string，必需)：任务执行ID

**说明**：
根据 executionId 获取任务执行的详细信息。返回该执行的完整记录，包括输入变量、输出变量、执行状态等。

**返回**：

```json
{
  "id": "string",
  "taskId": "string",
  "taskVersion": "string",
  "status": "PENDING | RUNNING | SUCCESS | FAILURE | STOPPED",
  "resolvedInputs": "Map<string, any>",
  "outputs": "Map<string, any>",
  "errorMessage": "string",
  "startedAt": "Timestamp",
  "completedAt": "Timestamp",
  "metadata": {
    "createdAt": "Timestamp",
    "createdBy": "string",
    "sparkApplicationId": "string",
    "tags": "string[]"
  }
}
```

**业务规则**：

- 返回的信息取决于执行状态
  - PENDING 状态：不包含 startedAt、completedAt、outputs、errorMessage
  - RUNNING 状态：不包含 completedAt、outputs、errorMessage
  - SUCCESS 状态：包含所有信息，errorMessage 为空
  - FAILURE 状态：包含 errorMessage，outputs 为空
  - STOPPED 状态：包含 completedAt

**使用场景**：

- 监控任务执行进度
- 查看任务的最终结果
- 排查任务执行失败原因

#### ListTaskExecutions

**参数**：

- `taskId` (string，可选)：按任务ID筛选
- `status` (string，可选)：按执行状态筛选
- `createdBy` (string，可选)：按创建者筛选
- `pageNumber` (int)：页码（默认1）
- `pageSize` (int)：每页数量（默认20）

**说明**：
查询任务执行列表，支持分页和筛选。可以按任务ID、执行状态、创建者等条件进行过滤，帮助用户了解任务的执行历史。

**返回**：

分页的 TaskExecution 列表，包含总数、当前页码、每页数量等分页信息

**业务规则**：

- 默认按创建时间倒序排列（最新优先）
- 支持组合筛选条件
- 分页参数必须有效（pageSize > 0，pageNumber > 0）

**使用场景**：

- 查看某个任务的所有执行记录
- 查看特定状态的执行记录
- 追踪特定用户执行的任务

#### GetTaskExecutionStats

**参数**：

- `taskId` (string，必需)：任务ID

**说明**：
获取任务执行统计信息，包括总执行次数、成功次数、失败次数等。这有助于了解任务的整体执行情况和稳定性。

**返回**：

```json
{
  "total": "int",
  "success": "int",
  "failure": "int",
  "running": "int",
  "stopped": "int"
}
```

**业务规则**：

- 统计数据实时计算或通过缓存提供
- 各类别统计之和 = total

**使用场景**：

- 任务健康度监控
- 性能趋势分析
- SLA 统计报表

---
