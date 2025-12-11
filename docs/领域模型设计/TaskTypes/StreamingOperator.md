# 任务类型：StreamingOperator (流处理)

## 1. 概述

**StreamingOperator** 代表无限流处理任务（如 Flink Job）。与批处理不同，流处理任务通常长期运行，需要支持复杂的生命周期管理（停止、重启、扩缩容）。

**核心特性**:
- **长期运行**: 除非显式停止或出错，否则一直运行。
- **状态管理**: 支持 Savepoint/Checkpoint 机制。
- **动态控制**: 支持运行时调整并行度 (Scale)。

---

## 2. TaskSchema 能力定义

```yaml
TaskSchema:
  type: "flink_streaming"
  description: "Apache Flink 流处理任务"

  # ==== 1. 支持的行为 (Actions) ====
  actions:
    start:
      description: "提交并启动 Flink 作业"
      params:
        savepointPath: string?     # 从指定 Savepoint 启动
    
    stop:
      description: "停止作业并触发 Savepoint"
      params:
        drain: boolean             # 是否等待数据处理完
        savepointDir: string?      # Savepoint 保存路径
    
    restart:
      description: "重启作业 (Stop + Start)"
      params:
        fromLatestSavepoint: boolean
    
    scale:
      description: "调整并行度 (Rescale)"
      params:
        parallelism: integer       # 目标并行度

  # ==== 2. 产生的事件 (Events) ====
  events:
    - name: "started"
      payload: { jobId: string, webUrl: string }
    
    - name: "stopped"
      payload: { savepointPath: string }
    
    - name: "failed"
      payload: { error: string, restartable: boolean }
    
    - name: "metrics"
      payload: { lag: long, throughput: double }
      description: "定期上报监控指标"

  # ==== 3. 状态定义 (States) ====
  states:
    CREATED: "作业已创建但未提交"
    RUNNING: "作业正在运行"
    STOPPED: "作业已停止 (Clean Stop)"
    FAILED: "作业异常退出"
    SCALING: "正在调整并行度"
```

---

## 3. TaskDefinition 配置结构

```yaml
TaskDefinition:
  type: "flink_streaming"
  
  # 执行配置
  executionConfig:
    # 代码位置
    jarUrl: string                 # Flink Jar 包地址
    entryClass: string             # 主类名
    
    # Flink 参数
    flinkVersion: string           # e.g., "1.17"
    parallelism: integer           # 默认并行度
    
    # Checkpoint 配置
    checkpointInterval: integer    # 毫秒
    stateBackend: "rocksdb" | "filesystem"
    
    # 运行时参数 (传递给 main 方法)
    programArgs:
      - "--topic"
      - "{input_topic}"
      - "--group.id"
      - "{consumer_group}"

  # 资源配置
  resources:
    jobManagerMemory: "2g"
    taskManagerMemory: "4g"
    slotsPerTaskManager: 2
```

## 4. ControlPolicy 示例

流处理任务通常配合 **ControlPolicy** 实现自动化运维：

```yaml
Node:
  id: "realtime_etl"
  taskConfig:
    taskDefinitionRef: "com.ops:flink_etl:1.0"
  
  controlPolicy:
    # 自动扩容: 当消费积压超过 10000 时，扩容到 4 并行度
    customRules:
      - condition: "event.type == 'metrics' && event.payload.lag > 10000"
        action: "scale"
        params: { parallelism: "4" }
    
    # 维护窗口自动停止
    stopWhen: "event.type == 'maintenance.window.start'"
    
    # 维护结束自动恢复 (从最近 Savepoint)
    restartWhen: "event.type == 'maintenance.window.end'"
```
