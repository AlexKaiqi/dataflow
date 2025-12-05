# Dataflow - 事件驱动的数据处理编排平台

## 项目概述

Dataflow 是一个**声明式、事件驱动**的数据处理编排平台，旨在简化 AI/ML 训练前的数据预处理工作流。通过 YAML 配置定义数据处理管线，自动编排任务执行，支持批处理、流处理和人工审批等多种任务类型。

### 核心价值

- **声明式配置**：用 YAML 描述数据处理逻辑，无需编写复杂的分布式代码
- **事件驱动**：基于事件的自动化任务触发和状态管理，无需轮询
- **多模态支持**：统一处理文本、图像、视频等不同类型数据
- **异构数据源**：抽象化访问 HDFS、Iceberg、MQ、Ceph 等多种存储
- **灵活执行**：支持 PySpark、Ray 等执行引擎，自动选择最优方案

## 解决的问题

### 目标用户

**数据工程师** - 需要为 AI/ML 团队准备训练数据，但不想深入学习分布式计算框架的复杂性。

### 核心痛点

1. **复杂性高**：编写 Spark/Ray 代码需要深厚的分布式系统知识
2. **重复劳动**：相似的数据处理逻辑需要反复编写
3. **维护困难**：硬编码的数据源连接和执行参数难以管理
4. **缺乏编排**：任务依赖、重试、监控需要大量胶水代码
5. **多模态割裂**：文本和视频数据处理使用完全不同的工具链

### 业务场景

#### 场景 1：文本数据批处理（NLP 训练数据准备）

```yaml
# 典型流程：数据采集 → 清洗 → 分词 → 特征提取 → 数据集划分
pipeline:
  name: nlp_data_preprocessing
  tasks:
    - name: load_raw_text
      type: sql
      source: iceberg://raw_corpus
      
    - name: clean_and_filter
      type: pyspark
      dependencies: [load_raw_text]
      startWhen: "event:load_raw_text.completed"
      script: |
        # 去除 HTML、去重、质量过滤
        
    - name: tokenize
      type: pyspark
      dependencies: [clean_and_filter]
      startWhen: "event:clean_and_filter.completed"


**特点**：

- 批处理模式（TB 级历史数据一次性处理）
- 高并行度（充分利用集群资源）
- 数据量大但不需要实时性

#### 场景 2：视频数据处理（计算机视觉训练数据）

```yaml
# 流程：视频解码 → 抽帧 → 质量过滤 → 目标检测 → 数据增强
pipeline:
  name: video_frame_extraction
  tasks:
    - name: decode_videos
      type: ray  # 利用 Ray 的分布式 GPU 支持
      source: ceph://raw_videos
      
    - name: quality_filter
      dependencies: [decode_videos]
      startWhen: "event:decode_videos.completed"
      
    - name: object_detection
      dependencies: [quality_filter]
      startWhen: "event:quality_filter.completed"


**特点**：

- IO + 计算双密集（视频解码极耗资源）
- 需要 GPU 加速（模型推理）
- PB 级存储管理

#### 场景 3：实时流处理 + 人工审批

```yaml
# 内容审核流：MQ 消息 → 实时分类 → 质量检测 → 人工审批 → 训练集合并
pipeline:
  name: realtime_content_review
  tasks:
    - name: consume_stream
      type: streaming
      source: kafka://user_content
      startWhen: "cron:* * * * *"  # 持续运行
      
    - name: auto_review
      dependencies: [consume_stream]
      startWhen: "event:consume_stream.data_available"
      
    - name: human_approval
      type: approval  # 人工审批任务
      dependencies: [auto_review]
      startWhen: "event:auto_review.completed && {{ quality_score < 0.8 }}"
      timeout: 24h
      
    - name: merge_to_dataset
      dependencies: [human_approval]
      startWhen: "event:human_approval.approved"


**特点**：

- 实时响应（秒级延迟）
- 人机结合（自动化 + 人工决策）
- 增量更新训练集

## 如何解决

### 核心设计理念

#### 1. 事件驱动执行

**传统轮询模式**（低效）：
```python
while True:
    if upstream_task.status == "completed":
        start_current_task()
    time.sleep(10)  # 浪费资源


**事件驱动模式**（高效）：
```yaml
startWhen: "event:upstream_task.completed && {{ data_quality > 0.9 }}"


- 任务通过事件自动触发，无需轮询
- 支持复杂条件：事件表达式 + 状态表达式
- 订阅-取消订阅模式：任务启动后自动取消订阅，避免重复触发

#### 2. 声明式配置

用户只需声明"做什么"，系统自动决定"怎么做"：

```yaml
pipeline:
  name: data_pipeline
  tasks:
    - name: task1
      type: pyspark  # 声明：用 Spark 执行
      source: iceberg://table  # 声明：从 Iceberg 读取
      startWhen: "event:upstream.completed"  # 声明：何时启动
      retryWhen: "{{ attempts < 3 }}"  # 声明：何时重试


系统自动处理：
- 数据源连接管理
- 执行引擎调度
- 依赖关系解析
- 重试和容错

#### 3. 统一抽象层

**数据源抽象**：
```python
# 用户视角：统一接口
source = DataSource.from_uri("iceberg://db.table")
data = source.read()

# 系统内部：适配不同实现
class IcebergAdapter:
    def read(self): ...
class HDFSAdapter:
    def read(self): ...
class KafkaAdapter:
    def read_stream(self): ...


**执行引擎抽象**：
```python
# 系统根据任务类型自动选择引擎
if task.type == "pyspark":
    executor = SparkExecutor()
elif task.type == "ray":
    executor = RayExecutor()


#### 4. DDD 分层架构


领域层 (Domain)
├── 领域模型：Pipeline, Task, Node, Expression, Event, Variable
├── 领域服务：ExpressionEvaluator, EventMatcher, DependencyResolver
└── 领域事件：task.started, task.completed, pipeline.finished

应用层 (Application)
├── 用例：CreatePipeline, ExecutePipeline, ApproveTask
└── 编排：PipelineOrchestrator

基础设施层 (Infrastructure)
├── 持久化：PipelineRepository (SQLAlchemy)
├── 消息总线：EventBus (订阅-发布)
├── 执行引擎：SparkExecutor, RayExecutor
└── 数据源：IcebergAdapter, KafkaAdapter, HDFSAdapter

接口层 (Interfaces)
├── REST API：/pipelines, /executions, /tasks
└── CLI：dataflow run, dataflow status


**依赖规则**：

- Domain 层零外部依赖（纯业务逻辑）
- Application 层依赖 Domain 接口
- Infrastructure 层实现 Domain 定义的仓储接口
- Interfaces 层依赖 Application 用例

### 关键技术特性

#### 表达式系统

**Event Expression**（定义"何时检查"）：
```yaml
startWhen: "event:upstream.completed && event:approval.granted"
stopWhen: "event:timeout || event:error_threshold_exceeded"


**State Expression**（定义"检查什么"）：
```yaml
retryWhen: "{{ attempts < 3 && error_type == 'transient' }}"
alertWhen: "{{ processing_time > 3600 }}"


**Mixed Expression**（组合使用）：
```yaml
startWhen: "event:upstream.completed && {{ data_size > 1000000 }}"


#### 任务类型

| 任务类型 | 用途 | 执行引擎 | 示例 |
|---------|------|---------|------|
| `sql` | 数据查询和转换 | Spark SQL | Iceberg 表 ETL |
| `pyspark` | 批处理 | PySpark | 文本清洗、特征提取 |
| `ray` | GPU 加速批处理 | Ray | 视频解码、模型推理 |
| `streaming` | 实时流处理 | Spark Streaming | Kafka 消息处理 |
| `approval` | 人工审批 | Platform API | 数据质量审核 |

#### 订阅管理

```python
# 任务状态机 + 动态订阅
pending -> running -> completed
  ↓         ↓          ↓
订阅 A    订阅 B     取消所有订阅

# 示例：批处理任务
pending: 订阅 upstream.completed 事件
running: 取消 upstream 订阅，订阅 error 事件
completed: 取消所有订阅


## 技术栈

### 核心技术

- **Python 3.11+**：主要开发语言
- **DDD + TDD**：领域驱动设计 + 测试驱动开发
- **PySpark**：批处理执行引擎
- **Ray**：分布式 GPU 加速引擎
- **Jinja2**：状态表达式模板引擎

### 数据技术

- **Apache Iceberg**：数据湖表格式（ACID）
- **Kafka/Pulsar**：消息队列
- **HDFS/Ceph/S3**：分布式存储
- **Parquet/Avro**：数据格式

### 基础设施

- **SQLAlchemy**：元数据持久化
- **Redis**：缓存和订阅管理
- **Mermaid**：架构图和状态图
- **Pydantic**：配置验证

## 项目结构


dataflow/
├── docs/                        # 文档（核心资产）
│   ├── 领域模型定义/           # 业务模型设计
│   ├── 架构设计/               # 架构决策 (ADRs)
│   ├── 开发规范/               # 编码和文档规范
│   ├── 测试规范/               # TDD 测试策略
│   └── 业务场景/               # 典型使用场景
├── src/                         # 源代码（实现）
│   ├── domain/                 # 领域层
│   │   ├── models/            # 领域模型
│   │   ├── services/          # 领域服务
│   │   ├── repositories/      # 仓储接口
│   │   └── events/            # 领域事件
│   ├── application/            # 应用层
│   │   ├── use_cases/         # 用例
│   │   └── services/          # 应用服务
│   ├── infrastructure/         # 基础设施层
│   │   ├── persistence/       # 数据持久化
│   │   ├── messaging/         # 事件总线
│   │   ├── ray/               # Ray 集成
│   │   └── external/          # 外部系统
│   └── interfaces/             # 接口层
│       ├── api/               # REST API
│       └── cli/               # 命令行
└── tests/                       # 测试（验证）
    ├── unit/                   # 单元测试
    ├── integration/            # 集成测试
    └── acceptance/             # 验收测试


## 开发原则

### 文档驱动开发


1. 编写/更新领域模型文档
   ↓
2. 基于文档编写测试用例（TDD Red）
   ↓
3. 实现代码使测试通过（TDD Green）
   ↓
4. 重构代码（TDD Refactor）
   ↓
5. 验证代码符合文档定义


**文档 = 核心资产，代码 = 实现资产**

### 测试驱动开发

```python
# tests/unit/domain/models/test_node.py
def test_should_skip_when_startwhen_not_satisfied():
    # Given: Node 定义了 startWhen 条件
    node = Node(
        name="task1",
        startWhen="event:upstream_completed && {{ status == 'ready' }}"
    )
    context = {"status": "pending"}
    
    # When: 事件触发但状态不满足
    should_start = node.evaluate_start_condition(
        event="upstream_completed",
        context=context
    )
    
    # Then: 应该跳过执行
    assert should_start is False


## 路线图

### Phase 1: MVP 核心（当前）

- [x] 领域模型设计（Pipeline, Task, Node, Expression, Event）
- [x] 事件驱动执行模型设计
- [x] DDD 分层架构设计
- [ ] 基础 YAML DSL 解析
- [ ] 1-2 个数据源支持（HDFS + Iceberg）
- [ ] 1 个执行引擎（PySpark）
- [ ] 简单批处理支持

### Phase 2: 功能扩展

- [ ] 更多数据源（Kafka, Ceph）
- [ ] 第二执行引擎（Ray）
- [ ] 流处理支持
- [ ] 任务调度和重试
- [ ] 基础监控

### Phase 3: 生产强化

- [ ] 数据血缘追踪
- [ ] 性能优化
- [ ] 完整监控和告警
- [ ] 用户文档和示例

## 参考资源

### 竞品分析

- **Apache Airflow**：工作流编排标杆，但偏调度而非数据抽象
- **Databricks**：统一批流处理，但成本高且云绑定
- **Prefect/Dagster**：现代化编排，但仍需编写大量代码
- **Ray Data**：分布式数据处理，但缺乏编排能力

### 技术标准

- **数据湖格式**：Apache Iceberg, Delta Lake
- **数据血缘**：OpenLineage 标准
- **编排模式**：DAG（有向无环图）
- **架构模式**：Kappa 架构（统一流处理）

## 贡献指南

欢迎贡献！请遵循以下流程：

1. 阅读 `docs/开发规范/` 了解编码规范
2. 阅读 `docs/测试规范/` 了解测试要求
3. 创建 Feature Branch
4. 编写测试（TDD）
5. 实现功能
6. 提交 Pull Request

## 许可证

待定

## 联系方式

待定
