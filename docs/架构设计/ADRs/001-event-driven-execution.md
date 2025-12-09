# ADR-001: 事件驱动执行模型

> **状态**: 已采纳  
> **决策日期**: 2025-12-08  
> **影响范围**: 执行引擎、编排逻辑、依赖管理

---

## 决策背景

在数据处理编排系统中,任务之间的依赖关系和执行控制至关重要。传统的依赖声明式编排(如 Airflow)采用显式的 `dependencies` 字段来定义执行顺序,但这种方式在面对复杂业务场景时存在局限性。

我们需要决定采用何种执行模型来支持:
- 复杂的条件分支和汇聚
- 流处理任务的动态控制(停止/重启)
- 异步任务的灵活协调
- 人工审批的超时和回退
- 跨流水线的事件传播

---

## 核心问题

### 传统依赖声明式编排的局限性

以 Airflow 为代表的传统编排系统使用显式的依赖声明:

```python
# Airflow 示例
task_a >> task_b  # task_b 依赖 task_a
task_a >> [task_c, task_d]  # 并行分支
```

**存在的问题**:

1. **静态依赖图**: 必须在定义时确定全部依赖关系,无法根据运行时状态动态调整
2. **条件分支复杂**: 需要引入 `BranchOperator` 等特殊节点,增加配置复杂度
3. **循环依赖难处理**: 流处理场景下的"停止-重启"循环逻辑难以表达
4. **汇聚逻辑割裂**: 需要显式的 `JoinNode` 来合并多个分支,增加编排复杂度
5. **跨流水线协作困难**: 不同流水线之间的依赖需要外部协调机制

---

## 决策内容

采用 **事件驱动执行模型(Event-Driven Execution)**,节点通过订阅和发布事件来实现依赖控制。

### 核心机制

```yaml
# 事件驱动方式
nodes:
  - id: upstream
    taskRef: process_data
    startWhen: "event:pipeline.started"
  
  - id: downstream
    taskRef: analyze_result
    startWhen: "event:upstream.completed && {{ upstream.row_count > 0 }}"
```

**关键特性**:

1. **事件订阅替代依赖声明**: 通过 `startWhen` 表达式订阅上游事件
2. **状态条件组合**: 支持事件表达式 + 状态表达式的复合条件
3. **隐式依赖关系**: 依赖关系从事件订阅模式推导,无需显式声明
4. **动态执行控制**: 根据运行时状态决定是否触发执行

---

## 事件驱动编排能解决的场景

### 场景 1: 复杂条件分支(Quality Gate)

**业务需求**: 数据质量检查后,根据分数决定走"人工审批"还是"直接入库"

#### 传统依赖声明式实现(Airflow)

```python
# ❌ 需要引入 BranchOperator
def quality_gate(**context):
    score = context['task_instance'].xcom_pull(task_ids='quality_check')
    if score > 0.9:
        return 'high_quality_path'
    else:
        return 'manual_review_path'

quality_check = SparkOperator(task_id='quality_check')
branching = BranchPythonOperator(
    task_id='quality_gate',
    python_callable=quality_gate
)
high_quality = DummyOperator(task_id='high_quality_path')
manual_review = DummyOperator(task_id='manual_review_path')

quality_check >> branching
branching >> [high_quality, manual_review]
```

**问题**:
- 需要引入 `BranchOperator` 这种特殊节点
- 分支逻辑和业务逻辑耦合在 Python 代码中
- 难以在配置文件中声明分支规则

#### 事件驱动实现

```yaml
# ✅ 通过事件表达式自然表达分支
nodes:
  - id: quality_check
    taskRef: check_data_quality
    startWhen: "event:load_data.completed"
  
  # 高质量路径 - 直接入库
  - id: direct_ingest
    taskRef: ingest_to_warehouse
    startWhen: "event:quality_check.completed && {{ quality_check.score > 0.9 }}"
  
  # 低质量路径 - 人工审批
  - id: manual_review
    taskRef: approval_task
    startWhen: "event:quality_check.completed && {{ quality_check.score <= 0.9 }}"
  
  # 审批通过后入库
  - id: approved_ingest
    taskRef: ingest_to_warehouse
    startWhen: "event:manual_review.approved"
```

**优势**:
- ✅ 无需特殊控制节点,分支逻辑声明式表达
- ✅ 支持多条件分支(可以扩展到 3+ 条路径)
- ✅ 配置即文档,易于理解和维护

---

### 场景 2: 动态汇聚(Dynamic Join)

**业务需求**: 多个数据源并行处理后,只要有一个成功就继续执行(OR 汇聚)

#### 传统依赖声明式实现

```python
# ❌ 需要自定义 TriggerRule
source_a = SparkOperator(task_id='source_a')
source_b = SparkOperator(task_id='source_b')
source_c = SparkOperator(task_id='source_c')

# 只有一个成功就触发
merge = SparkOperator(
    task_id='merge',
    trigger_rule='one_success'  # 内置的触发规则有限
)

[source_a, source_b, source_c] >> merge
```

**问题**:
- 内置的 `trigger_rule` 只支持有限的模式(all_success, one_success, none_failed 等)
- 无法表达更复杂的条件(如"至少 2 个成功")
- 无法根据运行时状态动态调整汇聚逻辑

#### 事件驱动实现

```yaml
# ✅ 灵活表达任意汇聚逻辑
nodes:
  - id: source_a
    taskRef: load_from_hdfs
    startWhen: "event:pipeline.started"
  
  - id: source_b
    taskRef: load_from_iceberg
    startWhen: "event:pipeline.started"
  
  - id: source_c
    taskRef: load_from_kafka
    startWhen: "event:pipeline.started"
  
  # OR 汇聚 - 任意一个完成即可
  - id: merge
    taskRef: merge_data
    startWhen: "event:source_a.completed || event:source_b.completed || event:source_c.completed"
  
  # 更复杂的汇聚 - 至少 2 个成功
  - id: quality_merge
    taskRef: merge_data
    startWhen: |
      (event:source_a.completed && event:source_b.completed) ||
      (event:source_a.completed && event:source_c.completed) ||
      (event:source_b.completed && event:source_c.completed)
```

**优势**:
- ✅ 支持任意复杂的汇聚条件(AND、OR、XOR、至少 N 个等)
- ✅ 无需预定义触发规则,声明式表达
- ✅ 可以结合运行时状态(如数据量、质量分数)决定汇聚

---

### 场景 3: 流处理的动态控制(Stop-Restart Loop)

**业务需求**: 流处理任务需要根据外部事件动态停止和重启(如配置更新、资源维护)

#### 传统依赖声明式实现

```python
# ❌ 无法表达循环依赖和动态控制
streaming_task = StreamingOperator(task_id='streaming')

# 问题:
# 1. 如何表达"配置更新时停止并重启"?
# 2. 如何避免循环依赖?
# 3. 如何集成外部事件(如 Kafka 配置变更通知)?
```

**问题**:
- DAG 本质上是无环的,无法表达"停止-重启"的循环逻辑
- 外部事件驱动(如 Kafka 配置变更)难以集成
- 需要额外的调度器来管理流处理任务的生命周期

#### 事件驱动实现

```yaml
# ✅ 自然表达流处理的动态控制
nodes:
  - id: streaming_processor
    taskRef: kafka_streaming
    startWhen: "event:pipeline.started"
    stopWhen: "event:config_updated || event:resource_maintenance"
    restartWhen: "event:streaming_processor.stopped && {{ config_ready }}"
```

**优势**:
- ✅ 支持 `stopWhen` 和 `restartWhen` 表达式
- ✅ 可以订阅外部事件(如 Kafka 配置变更)
- ✅ 无需循环依赖,通过事件自然表达"停止-重启"逻辑

**执行流程**:

```
1. streaming_processor 启动 → 发布 streaming_processor.started 事件
2. 监听到 config_updated 事件 → 评估 stopWhen 条件 → 停止任务
3. 任务停止 → 发布 streaming_processor.stopped 事件
4. 检测到 config_ready 状态变化 → 评估 restartWhen 条件 → 重启任务
5. 回到步骤 1
```

---

### 场景 4: 人工审批的超时和回退

**业务需求**: 数据质量审批任务,24 小时内未审批则自动拒绝并触发降级流程

#### 传统依赖声明式实现

```python
# ❌ 需要额外的定时器和状态管理
approval = PythonOperator(task_id='approval')
timeout_sensor = TimeSensor(
    task_id='timeout_check',
    timeout=86400,  # 24 hours
    mode='reschedule'
)

# 问题:
# 1. 如何同时监听审批事件和超时事件?
# 2. 如何在超时后自动触发降级流程?
# 3. 如何避免重复执行?
```

**问题**:
- 需要引入 `Sensor` 来轮询超时状态,效率低下
- 审批事件和超时事件的协调逻辑复杂
- 难以表达"优先等待审批,超时则降级"的语义

#### 事件驱动实现

```yaml
# ✅ 通过事件表达式自然处理超时
nodes:
  - id: manual_approval
    taskRef: approval_task
    startWhen: "event:quality_check.completed && {{ quality_check.score < 0.8 }}"
    timeout: 24h  # 任务定义中声明超时时间
  
  # 审批通过路径
  - id: approved_ingest
    taskRef: ingest_to_warehouse
    startWhen: "event:manual_approval.approved"
  
  # 审批拒绝或超时路径
  - id: rejected_handling
    taskRef: downgrade_process
    startWhen: "event:manual_approval.rejected || event:manual_approval.timeout"
```

**优势**:
- ✅ 审批任务自动发布 `timeout` 事件(由任务定义保证)
- ✅ 下游节点通过订阅事件自然处理超时场景
- ✅ 无需额外的定时器或轮询机制

**执行流程**:

```
1. manual_approval 启动 → 发布 manual_approval.started 事件
2. 等待外部 API 调用(批准/拒绝)
3a. 24 小时内批准 → 发布 manual_approval.approved 事件 → 触发 approved_ingest
3b. 24 小时内拒绝 → 发布 manual_approval.rejected 事件 → 触发 rejected_handling
3c. 24 小时超时 → 发布 manual_approval.timeout 事件 → 触发 rejected_handling
```

---

### 场景 5: 跨流水线事件传播(Pipeline Composition)

**业务需求**: 流水线 A 完成后,自动触发流水线 B 执行(类似微服务的事件驱动)

#### 传统依赖声明式实现

```python
# ❌ 需要外部协调器(如 Airflow 的 TriggerDagRunOperator)
pipeline_a = DAG(dag_id='pipeline_a')
trigger_b = TriggerDagRunOperator(
    task_id='trigger_pipeline_b',
    trigger_dag_id='pipeline_b',
    dag=pipeline_a
)

# 问题:
# 1. 流水线之间的依赖关系不清晰(需要查看 TriggerDagRunOperator)
# 2. 无法传递复杂的状态信息(只能通过 XCom)
# 3. 无法处理流水线 A 的多个分支触发不同的流水线
```

**问题**:
- 跨流水线依赖需要特殊的 Operator
- 状态传递依赖 XCom,受大小限制
- 无法表达"流水线 A 的某个分支完成后触发流水线 B"

#### 事件驱动实现

```yaml
# Pipeline A
pipelineId: data_processing_pipeline
nodes:
  - id: final_step
    taskRef: export_results
    startWhen: "event:merge.completed"
# 完成后自动发布 data_processing_pipeline.completed 事件

---
# Pipeline B - 订阅 Pipeline A 的完成事件
pipelineId: downstream_pipeline
nodes:
  - id: consume_results
    taskRef: load_processed_data
    startWhen: "event:data_processing_pipeline.completed && {{ data_processing_pipeline.status == 'success' }}"
```

**优势**:
- ✅ 流水线之间通过事件松耦合
- ✅ 支持复杂的触发条件(如"只有成功时触发")
- ✅ 事件携带完整的执行上下文(通过 VariablePool)
- ✅ 可以订阅流水线内部的特定节点事件(细粒度控制)

**高级场景**:

```yaml
# Pipeline C - 订阅 Pipeline A 的特定节点
pipelineId: monitoring_pipeline
nodes:
  - id: check_quality
    taskRef: validate_quality
    # 订阅 Pipeline A 的 quality_check 节点完成事件
    startWhen: "event:data_processing_pipeline.quality_check.completed && {{ quality_score < 0.8 }}"
```

---

### 场景 6: 增量数据处理的状态驱动(Incremental Processing)

**业务需求**: 只有当新数据到达时才触发处理,避免空跑

#### 传统依赖声明式实现

```python
# ❌ 需要 Sensor 轮询数据源
data_sensor = S3KeySensor(
    task_id='wait_for_data',
    bucket_name='my-bucket',
    bucket_key='data/{{ ds }}/part-*.parquet',
    poke_interval=60,  # 每分钟轮询一次
    timeout=3600
)

process_data = SparkOperator(task_id='process')
data_sensor >> process_data
```

**问题**:
- Sensor 轮询效率低下,浪费资源
- 无法集成外部消息队列(如 Kafka 数据就绪通知)
- 难以表达"累积到一定数量后再处理"的逻辑

#### 事件驱动实现

```yaml
# ✅ 订阅数据就绪事件
nodes:
  # 外部系统(如 Kafka)发布 data_available 事件
  - id: process_incremental
    taskRef: spark_incremental_job
    startWhen: "event:data_source.data_available && {{ data_source.record_count > 1000 }}"
```

**优势**:
- ✅ 无需轮询,事件驱动的即时响应
- ✅ 可以集成外部消息队列(Kafka、RabbitMQ)
- ✅ 支持批量触发条件(累积到一定量再处理)

**集成示例**:

```yaml
# 基础设施层配置
eventSources:
  - type: kafka
    topic: data_ready_events
    mapping:
      event_type: data_source.data_available
      payload:
        record_count: $.metadata.count
```

---

### 场景 7: 失败重试的细粒度控制(Smart Retry)

**业务需求**: 根据失败原因决定重试策略(网络错误立即重试,数据错误不重试)

#### 传统依赖声明式实现

```python
# ❌ 重试策略在任务定义中硬编码
process_task = SparkOperator(
    task_id='process',
    retries=3,
    retry_delay=timedelta(minutes=5),
    retry_exponential_backoff=True
)

# 问题:
# 1. 所有失败都用相同的重试策略
# 2. 无法根据失败原因动态调整
# 3. 无法在重试前执行补救措施(如清理资源)
```

**问题**:
- 重试策略固定,无法动态调整
- 无法区分可重试错误和不可重试错误
- 无法在重试前执行补救措施

#### 事件驱动实现

```yaml
# ✅ 根据失败原因决定重试策略
nodes:
  - id: process_data
    taskRef: spark_batch_job
    startWhen: "event:load_data.completed"
    retryWhen: |
      event:process_data.failed && 
      {{ process_data.error_type in ['NetworkError', 'TimeoutError'] }} &&
      {{ process_data.retry_count < 3 }}
  
  # 数据错误时触发人工介入
  - id: manual_fix
    taskRef: approval_task
    startWhen: |
      event:process_data.failed && 
      {{ process_data.error_type == 'DataValidationError' }}
```

**优势**:
- ✅ 支持基于失败原因的条件重试
- ✅ 可以在重试前执行补救措施
- ✅ 失败路径可以触发不同的处理流程(如人工介入)

---

## 事件驱动 vs 依赖声明对比总结

| 场景 | 传统依赖声明式 | 事件驱动 | 核心优势 |
|------|---------------|---------|---------|
| **条件分支** | 需要 `BranchOperator` | 通过 `startWhen` 表达式 | ✅ 声明式配置,无需控制节点 |
| **动态汇聚** | 有限的 `trigger_rule` | 任意复杂的布尔表达式 | ✅ 支持 OR/AND/至少 N 个等任意逻辑 |
| **流处理控制** | 无法表达循环依赖 | `stopWhen` / `restartWhen` | ✅ 自然表达停止-重启循环 |
| **人工审批超时** | 需要 `Sensor` 轮询 | 任务自动发布 `timeout` 事件 | ✅ 无需轮询,事件驱动 |
| **跨流水线协作** | 需要 `TriggerDagRunOperator` | 订阅其他流水线的事件 | ✅ 松耦合,支持细粒度订阅 |
| **增量数据处理** | `Sensor` 轮询数据源 | 订阅外部 `data_available` 事件 | ✅ 无轮询,集成消息队列 |
| **智能重试** | 固定重试策略 | 基于失败原因的条件重试 | ✅ 动态调整重试策略 |

---

## 实现约束

### 必须实现的核心机制

1. **事件发布与订阅**
   - 节点执行完成后自动发布事件
   - 支持 `startWhen` / `stopWhen` / `restartWhen` / `retryWhen` 表达式
   - 表达式评估引擎(事件模式匹配 + 状态条件评估)

2. **订阅生命周期管理**
   - 节点启动后自动取消 `startWhen` 订阅(避免重复触发)
   - 流处理节点保持 `stopWhen` / `restartWhen` 订阅
   - 支持外部事件源集成(Kafka、RabbitMQ)

3. **状态可见性**
   - 通过 VariablePool 暴露节点执行状态
   - 支持表达式访问上游节点的输出变量
   - 事件携带必要的上下文信息

### 需要权衡的设计决策

| 设计点 | 选项 A | 选项 B | 推荐 |
|-------|-------|-------|------|
| 事件存储 | 内存(高性能) | 持久化(可追溯) | 混合: 内存 + 异步持久化 |
| 表达式语言 | 自定义 DSL | CEL(通用表达式语言) | CEL(可移植性) |
| 跨流水线事件 | 全局事件总线 | 直接订阅 | 全局事件总线(解耦) |
| 外部事件源 | 适配器模式 | 原生集成 | 适配器(扩展性) |

---

## 风险与缓解措施

### 风险 1: 事件风暴(Event Storm)

**描述**: 大量节点同时完成,产生海量事件,导致系统负载过高

**缓解措施**:
- 实现事件批处理(合并同类事件)
- 限制单个流水线的节点数量(建议 < 100)
- 异步事件发布(不阻塞节点执行)

### 风险 2: 表达式复杂度失控

**描述**: 过于复杂的 `startWhen` 表达式难以理解和维护

**缓解措施**:
- 提供表达式复杂度检查(如嵌套层级 < 3)
- 鼓励使用命名中间变量(提高可读性)
- 提供常见模式的模板(如 OR 汇聚、超时处理)

### 风险 3: 调试困难

**描述**: 事件驱动的隐式依赖关系难以追踪和调试

**缓解措施**:
- 实现事件链追踪(记录完整的事件触发路径)
- 提供可视化工具(展示节点间的事件订阅关系)
- 保存完整的事件历史(支持事后分析)

---

## 传统调度框架的局限性分析

### 为什么 Airflow 等框架不采用事件驱动？

#### 历史原因

**1. 诞生背景决定架构**

| 框架 | 诞生年份 | 初始场景 | 核心设计 |
|------|---------|---------|---------|
| Oozie | 2011 | Hadoop 批处理调度 | XML 定义的 DAG,重点是资源调度 |
| Azkaban | 2012 | LinkedIn 批处理作业 | 基于文件的依赖声明 |
| Airflow | 2015 | Airbnb 数据管道 | Python 代码定义 DAG |
| Prefect | 2018 | 现代化 Airflow 替代 | 依然基于 DAG,改进了 UI |

**共同特点**: 都是为**批处理调度**而生,重点解决"什么时候运行"而非"如何动态协调"

**2. DAG 是一等公民**

```python
# Airflow 的核心抽象
with DAG('my_dag', schedule_interval='@daily') as dag:
    task_a >> task_b >> task_c  # 依赖关系是图的边
```

- DAG 必须是**静态无环图**,在调度前完全确定
- 这种设计简化了调度器实现,但限制了动态性
- 修改依赖关系 = 修改 DAG 定义 = 重新部署

**3. 调度器架构的约束**

传统调度器的核心循环:

```python
while True:
    # 1. 扫描所有 DAG
    for dag in all_dags:
        # 2. 检查是否到执行时间
        if should_run(dag):
            # 3. 检查任务依赖是否满足
            for task in dag.tasks:
                if dependencies_met(task):
                    # 4. 提交任务执行
                    submit(task)
    time.sleep(heartbeat_interval)  # 通常 5-30 秒
```

**问题**:
- 基于轮询,无法实时响应外部事件
- 调度器是集中式瓶颈(单点)
- 状态检查开销随 DAG 数量线性增长

---

### Airflow 对复杂场景的"变通"支持

#### 1. 条件分支 - BranchOperator (勉强支持)

```python
def branch_func(**context):
    # ❌ 业务逻辑藏在 Python 代码中
    if context['ti'].xcom_pull(task_ids='check') > 0.9:
        return 'high_quality_path'
    else:
        return 'low_quality_path'

branching = BranchPythonOperator(
    task_id='branching',
    python_callable=branch_func
)
```

**缺点**:
- 分支逻辑与 DAG 定义分离,难以理解
- 只支持简单的 if-else,无法声明式表达
- 无法在 UI 中直观看到分支条件

#### 2. 动态汇聚 - TriggerRule (有限支持)

```python
merge = PythonOperator(
    task_id='merge',
    trigger_rule='one_success'  # 内置的有限选项
)
```

**内置的 TriggerRule**:
- `all_success`: 所有上游成功(默认)
- `all_failed`: 所有上游失败
- `all_done`: 所有上游完成(无论成败)
- `one_success`: 至少一个成功
- `one_failed`: 至少一个失败
- `none_failed`: 没有失败
- `none_skipped`: 没有跳过

**无法支持**:
- "至少 N 个成功"(N > 1)
- "至少 50% 成功"
- "上游 A 成功 OR (上游 B 和 C 都成功)"
- 结合运行时状态的条件(如数据量、质量分数)

#### 3. 外部事件 - Sensor (低效)

```python
# ❌ 轮询效率低下
wait_file = FileSensor(
    task_id='wait_file',
    filepath='/data/input.csv',
    poke_interval=60,  # 每分钟轮询
    timeout=3600,
    mode='poke'  # 占用 worker slot
)
```

**问题**:
- **轮询浪费资源**: 即使没有事件也在检查
- **占用 worker slot**: `poke` 模式阻塞 worker
- **reschedule 模式开销**: 每次重新调度有延迟
- **无法集成消息队列**: 无法监听 Kafka/RabbitMQ

**Airflow 2.2+ 的 Deferrable Operators**:
```python
# 改进版: 异步等待,不占用 worker
wait_file = FileSensorAsync(
    task_id='wait_file',
    filepath='/data/input.csv'
)
```

✅ 不占用 worker
❌ 依然基于轮询(triggerer 进程)
❌ 无法订阅外部事件总线

#### 4. 流处理控制 - 几乎不支持

```python
# ❌ Airflow 本质上是批处理调度器
streaming_task = BashOperator(
    task_id='streaming',
    bash_command='spark-submit streaming_job.py'
)
# 问题:
# 1. 如何动态停止流处理任务?
# 2. 如何根据外部事件重启?
# 3. DAG Run 何时标记为完成?
```

**Airflow 的假设**: 所有任务最终会结束
**流处理的现实**: 任务可能永久运行

**变通方案**: 
- 使用 `ShortCircuitOperator` 提前终止 DAG
- 使用外部调度器(如 Kubernetes CronJob)管理流任务

#### 5. 跨 DAG 依赖 - TriggerDagRunOperator (割裂)

```python
# DAG A
trigger_b = TriggerDagRunOperator(
    task_id='trigger_dag_b',
    trigger_dag_id='dag_b',
    execution_date='{{ ds }}'
)

# ❌ 问题:
# 1. DAG 之间依赖关系不可见(需要查看代码)
# 2. 无法传递复杂状态(XCom 有大小限制)
# 3. 无法订阅 DAG A 的内部节点事件
```

---

### 为什么不改造成事件驱动？

#### 1. 架构惯性 (Legacy Burden)

**Airflow 的核心假设**:
```python
# 调度器的核心数据结构
class DagRun:
    dag_id: str
    execution_date: datetime  # 核心: 每个 Run 对应一个时间点
    state: str
    
class TaskInstance:
    dag_id: str
    task_id: str
    execution_date: datetime  # 任务通过 (dag_id, task_id, execution_date) 唯一标识
    state: str
```

**改造成本**:
- 数据库 schema 需要重构(影响数亿行历史数据)
- 调度器核心逻辑需要重写(10 万行代码)
- 破坏向后兼容性(数十万用户的 DAG 需要迁移)

#### 2. 社区惯性 (Community Inertia)

**Airflow 用户的典型画像**:
- 数据工程师,熟悉 Python
- 主要场景: 定时批处理(ETL)
- 对复杂事件驱动的需求不强烈

**改造动力不足**:
- 批处理场景下,现有方案"够用"
- 引入事件驱动增加学习曲线
- 社区更关注可观测性、稳定性等问题

#### 3. 竞争压力不足

**市场现状**:
- Airflow 已是事实标准(GitHub 30k+ stars)
- 新框架(Prefect、Dagster)也主要改进 UX,未改变 DAG 范式
- 云服务商提供托管版本(AWS MWAA、Google Cloud Composer)

**没有颠覆性竞争者**:
- Temporal(工作流引擎)定位不同
- AWS Step Functions(有事件驱动)但云锁定
- Dify(AI 应用编排)规模较小

---

### 新一代框架的尝试

#### 1. Prefect 2.0 (改进但未根本改变)

```python
from prefect import flow, task

@task
def extract():
    return data

@task
def transform(data):
    return processed

@flow
def my_flow():
    data = extract()
    result = transform(data)
```

**改进**:
- ✅ 动态 DAG 生成(运行时构建依赖)
- ✅ 更好的状态管理
- ✅ 原生支持 Kubernetes

**依然局限**:
- ❌ 依然基于任务依赖,非事件驱动
- ❌ 外部事件依然需要轮询
- ❌ 流处理场景支持有限

#### 2. Temporal (工作流引擎,非调度器)

```go
func MyWorkflow(ctx workflow.Context) error {
    // ✅ 支持长时间等待
    workflow.Sleep(ctx, 24 * time.Hour)
    
    // ✅ 支持外部信号
    var signal string
    workflow.GetSignalChannel(ctx, "approval").Receive(ctx, &signal)
}
```

**优势**:
- ✅ 原生支持长时间等待(持久化状态)
- ✅ 支持外部信号
- ✅ 强大的容错能力

**定位不同**:
- 更像"分布式事务引擎",非数据处理调度
- 需要编写代码(Go/Java/Python),非配置驱动
- 学习曲线陡峭

#### 3. AWS Step Functions (云原生,有事件驱动)

```json
{
  "States": {
    "WaitForApproval": {
      "Type": "Task",
      "Resource": "arn:aws:states:::lambda:invoke.waitForTaskToken"
    }
  }
}
```

**优势**:
- ✅ 原生事件驱动(集成 EventBridge)
- ✅ 支持人工审批等待
- ✅ 托管服务,无需运维

**局限**:
- ❌ AWS 云锁定
- ❌ 配置复杂(JSON 定义)
- ❌ 价格昂贵(按状态转换计费)

---

### 我们的差异化定位

| 维度 | Airflow | Prefect | Temporal | AWS Step Functions | **Dataflow (我们)** |
|------|---------|---------|----------|-------------------|-------------------|
| **核心范式** | DAG 依赖声明 | DAG 依赖声明 | 代码工作流 | 状态机 | **事件驱动** |
| **配置方式** | Python 代码 | Python 代码 | Go/Java/Python | JSON | **YAML 声明式** |
| **条件分支** | BranchOperator | 动态分支 | 代码控制 | Choice State | **表达式** |
| **外部事件** | Sensor 轮询 | Sensor 轮询 | Signal | EventBridge | **事件订阅** |
| **流处理** | ❌ | ❌ | ⚠️ | ❌ | **✅ stopWhen/restartWhen** |
| **人工审批** | Sensor 轮询 | Sensor 轮询 | ✅ | ✅ | **✅ timeout 事件** |
| **跨流水线** | TriggerDagRun | TriggerFlow | 子工作流 | ✅ | **✅ 事件传播** |
| **学习曲线** | 中 | 中 | 高 | 中 | **低(声明式)** |
| **云锁定** | ❌ | ❌ | ❌ | ✅ AWS | **❌** |

---

### 总结: 为什么我们要做事件驱动？

#### 1. 传统框架的历史包袱

- 为批处理调度而生,DAG 是核心抽象
- 改造成本巨大,向后兼容性压力
- 社区惯性,改进动力不足

#### 2. 新场景的迫切需求

- **流处理**: 实时数据管道需要动态控制
- **人机协作**: AI 数据标注等需要长时间等待
- **外部集成**: 微服务化趋势,需要事件驱动
- **复杂编排**: 多分支、动态汇聚、条件路由

#### 3. 我们的优势

- ✅ **无历史包袱**: 从零开始,选择最优架构
- ✅ **声明式配置**: YAML > Python 代码,降低门槛
- ✅ **事件驱动原生**: 非后来补丁,架构一致性
- ✅ **数据处理专注**: 针对 AI/ML 数据预处理优化

#### 4. 借鉴而非复制

- 学习 Dify 的事件驱动设计
- 学习 Temporal 的持久化状态
- 学习 Step Functions 的声明式配置
- 但保持独立定位: **AI/ML 数据处理编排**

---

## 决策结果

✅ **采纳事件驱动执行模型**

**理由**:
1. 能够解决传统依赖声明式无法处理的 7 大场景
2. 提供更灵活的执行控制能力(条件分支、动态汇聚、流处理控制)
3. 支持跨流水线协作和外部事件集成
4. 与领域事件(Domain Event)模式天然契合
5. **传统框架受限于历史包袱,无法根本性改造**

**实现优先级**:
1. Phase 1: 基础事件发布订阅 + `startWhen` 表达式
2. Phase 2: 流处理控制 (`stopWhen` / `restartWhen`)
3. Phase 3: 跨流水线事件传播
4. Phase 4: 外部事件源集成(Kafka、RabbitMQ)

---

## 参考资料

- [Dify Workflow Engine - Event-Driven Execution](https://github.com/langgenius/dify)
- [Apache Airflow - Trigger Rules](https://airflow.apache.org/docs/apache-airflow/stable/core-concepts/dags.html#trigger-rules)
- [AWS Step Functions - Event-Driven Workflows](https://aws.amazon.com/step-functions/)
- [Martin Fowler - Event-Driven Architecture](https://martinfowler.com/articles/201701-event-driven.html)
- [Google Common Expression Language (CEL)](https://github.com/google/cel-spec)

---

**版本**: v1.0  
**最后更新**: 2025-12-08  
**作者**: 架构设计组
