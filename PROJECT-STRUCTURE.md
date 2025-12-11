# ControlFlow - 反应式控制流编排引擎

> **注意**: 本项目正处于从 `DataFlow` 到 `ControlFlow` 的品牌重塑过程中。代码模块目前仍保留 `dataflow-` 前缀，将按照[迁移计划](./MIGRATION-PLAN.md)逐步更新。

## 1. 项目概述

**ControlFlow** 是一个基于 **COLA (Clean Object-Oriented and Layered Architecture)** 架构的下一代编排引擎。
与传统工作流引擎不同，ControlFlow 强调 **"Reactive Control" (反应式控制)** —— 不仅负责启动任务，更关注任务运行时的状态管理、干预和交互。

## 2. 技术栈

- **Language**: Java 21 (LTS)
- **Framework**: Spring Boot 3.5.8
- **Build**: Gradle 8.x
- **Architecture**: COLA 4.0
- **Storage**: MySQL 8.0 / H2
- **ORM**: MyBatis Flex / JPA

## 3. 模块结构 (Module Structure)

```text
controlflow-root/
├── build.gradle
├── settings.gradle
│
├── dataflow-client/           -> [Planned: controlflow-api]
│   ├── api/                   # 核心接口 (TaskService, PipelineService)
│   └── dto/                   # 数据传输对象 (Command, Event, Query)
│
├── dataflow-adapter/          -> [Planned: controlflow-adapter]
│   ├── web/                   # REST Controller (管理控制台 API)
│   ├── rpc/                   # gRPC Server (Agent 通信)
│   └── mq/                    # Message Consumer (事件订阅)
│
├── dataflow-app/              -> [Planned: controlflow-core]
│   ├── executor/              # 命令处理 (CmdExe)
│   ├── scheduler/             # 调度器 (Quartz/TimeWheel)
│   └── service/               # 编排逻辑 (Orchestrator)
│
├── dataflow-domain/           -> [Planned: controlflow-domain]
│   ├── model/
│   │   ├── task/              # 任务聚合根 (Task, TaskState)
│   │   ├── pipeline/          # 流程聚合根 (Pipeline, Node)
│   │   └── taskschema/        # 任务能力定义 (Actions, Events)
│   ├── ability/               # 领域能力 (ResourceCheck, RateLimit)
│   └── gateway/               # 基础设施接口 (TaskExecutorGateway)
│
├── dataflow-infrastructure/   -> [Planned: controlflow-infra]
│   ├── persistence/           # DB 实现 (MySQL, Redis)
│   ├── engine/                # 执行引擎适配器 (Plugins)
│   │   ├── flink/             # Flink Rest Client
│   │   ├── spark/             # Livy / K8s Client
│   │   └── ray/               # Ray Job Submission Client
│   └── eventbus/              # 事件总线实现 (Kafka/RocketMQ)
│
└── start/                     # 启动入口
```

## 4. 关键架构设计

### 4.1 领域层 (Domain)

核心业务逻辑，不依赖任何技术实现。

- **TaskSchema**: 定义任务的"元数据"（支持哪些 Action，产生哪些 Event）。
- **Reactive State Machine**: 负责管理任务的生命周期状态流转。

### 4.2 基础设施层 (Infrastructure)

实现了与外部系统的交互。为了支持多种计算引擎，采用了**插件化设计**：

- `EngineAdapter`: 统一接口，屏蔽 Flink/Spark/Ray 的差异。
- `EventChannel`: 统一事件通道，接收来自 Agent 或 外部系统的回调。

### 4.3 应用层 (App)

负责用例编排。

- **Command Handlers**: 处理 `StartTask`, `StopTask`, `ScaleTask` 等指令。
- **Event Handlers**: 处理 `TaskStarted`, `TaskFailed`, `ApprovalCompleted` 等事件。

## 5. 开发规范

1.  **依赖原则**: Domain 层不依赖 Infrastructure 层，通过 Gateway 接口倒置依赖。
2.  **包命名**: 新增代码请使用 `com.tencent.controlflow` 包名。
3. **扩展性**: 新增 Task Type 时，优先在 Infrastructure 层增加对应的 Adapter 实现，而非修改核心逻辑。

## 6. 快速开始

```bash
# 编译项目
./gradlew clean build

# 运行单元测试
./gradlew test

# 启动服务 (Dev Profile)
./gradlew :start:bootRun --args='--spring.profiles.active=dev'
```
