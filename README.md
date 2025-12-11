# ControlFlow Orchestration Engine

> **注意**: 本项目原名为 DataFlow，现更名为 **ControlFlow**，以更准确地反映其核心职责——**控制流编排**而非数据流传输。

## 1. 项目简介

**ControlFlow** 是一个基于 **事件驱动 (Event-Driven)** 和 **反应式控制 (Reactive Control)** 的下一代工作流编排引擎。

与传统的工作流引擎（如 Airflow, DolphinScheduler）不同，ControlFlow 不仅仅关注任务的静态依赖（DAG），更关注任务在运行时的**动态控制**。它通过统一的**控制平面 (Control Plane)**，以标准化的方式管理批处理 (Batch)、流处理 (Streaming) 以及混合编排 (Hybrid) 场景。

### 核心理念

*   **Control Flow over Data Flow**: 引擎只负责"什么时候启动/停止任务"（控制流），而不负责"数据怎么传输"（数据流）。数据通过外部存储（S3, HDFS, Kafka）流转，引擎只传递数据的引用（Payload）。
*   **Reactive Control**: 任务不再是"发射后不管" (Fire-and-Forget)。引擎持续监听任务状态和外部事件，根据 **ControlPolicy** 动态做出决策（如 Stop, Restart, Scale, Skip）。
*   **Event-Driven Architecture**: 所有状态变更皆事件。节点之间通过事件松耦合协作，支持复杂的触发逻辑（SpEL 表达式）。

## 2. 核心特性

*   **混合编排 (Hybrid Orchestration)**: 在同一个 Pipeline 中无缝编排 Batch（有限流）和 Streaming（无限流）任务。
*   **标准化抽象 (TaskSchema)**: 通过 TaskSchema 定义任务的"能力契约"（Actions, Events, States），实现对任意异构系统的统一纳管。
*   **声明式控制 (ControlPolicy)**: 通过 SpEL 表达式声明任务的运行时行为（如 `stopWhen`, `restartWhen`, `retryWhen`）。
*   **逻辑关联 (Correlation)**: 通过 `correlationId` 串联跨越多个物理执行周期（Execution）的业务逻辑。

## 3. 典型应用场景

### 场景 1：混合流批处理 (Hybrid Pipeline)

上游批处理任务完成后，触发下游流处理任务启动；流处理任务在维护窗口期间自动暂停。

```yaml
nodes:
  # 1. 批处理任务
  - id: "batch_loader"
    taskConfig: { taskType: "spark_batch" }
    controlPolicy:
      retryWhen: "retryCount < 3"

  # 2. 流处理任务 (依赖批处理)
  - id: "stream_processor"
    taskConfig: { taskType: "flink_streaming" }
    startWhen: "event.type == 'task.succeeded' && event.source.endsWith('batch_loader')"
    controlPolicy:
      stopWhen: "event.type == 'maintenance.start'"
      restartWhen: "event.type == 'maintenance.end'"
```

### 场景 2：实时人机协作 (Human-in-the-loop)

流处理发现低置信度数据 -> 触发人工审批 -> 审批通过 -> 合并数据。

```yaml
nodes:
  - id: "human_approval"
    taskConfig: { taskType: "approval" }
    startWhen: "event.type == 'data.quality.low'"
    controlPolicy:
      alertWhen: "duration > 24h"
```

## 4. 文档导航

### 快速入门
*   [快速开始](docs/快速开始.md): 了解如何定义和运行第一个 Pipeline。
*   [术语表](docs/术语表.md): 核心概念定义的权威参考。

### 领域模型设计
*   [Node (节点)](docs/领域模型设计/Node.md): 编排的基本单元，包含 TaskConfig 和 ControlPolicy。
*   [Event (事件)](docs/领域模型设计/Event.md): 系统神经系统的信号载体。
*   [Pipeline (流水线)](docs/领域模型设计/Pipeline.md): 节点的容器和命名空间。
*   [TaskSchema (任务模式)](docs/领域模型设计/TaskSchema.md): 任务能力的元定义。

### 架构设计
*   [架构总览](docs/架构设计/architecture-overview.md): 系统的分层架构和核心组件。
*   [事件设计规范](docs/设计规范/event-design-specification.md): CloudEvents 规范在系统中的应用。

### 开发规范
*   [代码开发规范](docs/代码开发规范/coding-standards.md)
*   [Git 工作流](docs/代码开发规范/git-workflow.md)

## 5. 为什么叫 ControlFlow?

在现代数据平台中，"数据流"通常由专门的计算引擎（如 Spark, Flink）或传输工具（如 Kafka）处理。编排引擎的真正价值在于**控制**——即在正确的时间、以正确的配置、对正确的资源执行操作。

ControlFlow 强调这一职责：**我们不搬运数据，我们指挥数据的加工过程。**
