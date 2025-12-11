# **《Pipeline 流水线》**

## 概述

**Pipeline（流水线）** 是 Node 的集合容器。它只负责组织和管理节点，不参与执行控制。节点通过事件驱动机制自主协作，Pipeline 提供统一的命名空间和管理接口。

### 核心定位

**Pipeline 是节点的容器**：
- ✅ 提供节点的命名空间（Node ID 在 Pipeline 内唯一）
- ✅ 提供节点的增删改操作接口
- ✅ 提供节点的统一管理和查询
- ❌ 不维护执行状态（状态在 Node 自己）
- ❌ 不产生事件（只有 Node 产生事件）
- ❌ 没有生命周期（创建后就一直存在）

### 设计理念

1. **极简容器**：Pipeline 只是节点的组织单元
2. **节点自治**：每个节点独立决定何时执行（通过 startWhen）
3. **事件驱动**：节点通过事件协作，不依赖 Pipeline
4. **动态管理**：随时可以增删改节点

---

## 领域模型结构

```yaml
Pipeline:
  # ==== 1. 唯一标识与元数据 ====
  id: string                           # Pipeline ID，全局唯一
  name: string                         # Pipeline 名称
  description: string?                 # Pipeline 描述

  # 元数据
  metadata:
    owners: string[]                   # 所有者列表
    tags: string[]                     # 标签
    createdAt: Timestamp
    createdBy: string

  # ==== 2. 节点集合 ====
  nodes: Node[]                        # 节点列表
    Node:                              # 详细结构见 Node.md
      id: string                       # 节点 ID
      taskConfig: TaskConfig           # 任务配置
      startWhen: string?               # 触发条件 (SpEL)
      startPayload: Map<String,String> # 启动输入映射
      controlPolicy: ControlPolicy?    # 控制策略
```

---

## Pipeline 操作示例

### 1. 创建 Pipeline

```bash
POST /api/v1/pipelines
Content-Type: application/json

{
  "id": "user_etl_pipeline",
  "name": "用户数据 ETL 流水线",
  "description": "提取、转换、加载用户数据",
  "metadata": {
    "owners": ["data-team@company.com"],
    "tags": ["etl", "user-data"]
  },
  "nodes": []
}
```

### 2. 添加节点 (Trigger)

```bash
POST /api/v1/pipelines/user_etl_pipeline/nodes
Content-Type: application/json

{
  "id": "trigger",
  "taskConfig": {
    "taskType": "trigger",
    "config": { "cron": "0 0 * * *" }
  },
  "startWhen": "event.type == 'scheduler.tick'"
}
```

### 3. 添加节点 (Extract - 依赖 Trigger)

```bash
POST /api/v1/pipelines/user_etl_pipeline/nodes
{
  "id": "extract",
  "taskConfig": {
    "taskType": "pyspark",
    "config": {
      "mainFile": "s3://bucket/scripts/extract.py"
    }
  },
  "startWhen": "event.type == 'task.succeeded' && event.source.endsWith('trigger')",
  "startPayload": {
    "date": "{{ event.payload.date }}"
  },
  "controlPolicy": {
    "retryWhen": "retryCount < 3"
  }
}
```

### 4. 修改节点

```bash
PUT /api/v1/pipelines/user_etl_pipeline/nodes/extract
Content-Type: application/json

{
  "taskConfig": {
    "taskType": "pyspark",
    "config": {
      "mainFile": "s3://bucket/scripts/extract_v2.py",
      "driverMemory": "4g"
    }
  },
  "startWhen": "event.type == 'task.succeeded' && event.source.endsWith('trigger')",
  "startPayload": {
    "date": "{{ event.payload.date }}",
    "region": "us-east-1"
  },
  "controlPolicy": {
    "retryWhen": "retryCount < 5"
  }
}
```

### 5. 删除节点

```bash
DELETE /api/v1/pipelines/user_etl_pipeline/nodes/extract
```
