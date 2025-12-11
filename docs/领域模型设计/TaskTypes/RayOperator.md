# 任务类型：RayOperator (分布式计算)

## 1. 概述

**RayOperator** 任务类型用于在 Ray 集群上提交和管理分布式计算任务。适用于机器学习训练、超参数调优、强化学习等高性能计算场景。

**核心特性**:
- **分布式执行**: 原生支持 Ray 的 Actor/Task 模型。
- **资源隔离**: 支持指定 CPU/GPU 资源配额。
- **动态扩缩**: 结合 Ray Autoscaler 实现资源弹性。

---

## 2. TaskSchema 能力定义

```yaml
TaskSchema:
  type: "ray_job"
  description: "Ray 分布式计算任务"

  # ==== 1. 支持的行为 (Actions) ====
  actions:
    submit:
      description: "提交 Ray Job"
      params:
        entrypoint: string
        runtimeEnv: Map<String, Any>

    stop:
      description: "停止 Ray Job"
      params: {}

    get_logs:
      description: "获取任务日志"
      params:
        lines: int?

  # ==== 2. 产生的事件 (Events) ====
  events:
    - name: "job_submitted"
      payload: { rayJobId: string, submissionId: string }

    - name: "status_changed"
      payload: { oldStatus: string, newStatus: string }

    - name: "job_finished"
      payload: { exitCode: int, result: Any }

  # ==== 3. 状态定义 (States) ====
  states:
    PENDING: "等待调度"
    RUNNING: "正在运行"
    SUCCEEDED: "执行成功"
    FAILED: "执行失败"
    STOPPED: "已停止"
```

---

## 3. TaskDefinition 配置结构

```yaml
TaskDefinition:
  type: "ray_job"

  # 连接配置
  connection:
    address: string                  # Ray Head 节点地址 (ray://...)
    dashboardUrl: string?            # Ray Dashboard 地址

  # 执行配置
  jobConfig:
    entrypoint: string               # 启动命令 (e.g., "python train.py")
    workingDir: string?              # 工作目录

    # 运行时环境 (pip packages, env vars, etc.)
    runtimeEnv:
      pip: List<String>?
      env_vars: Map<String, String>?
      working_dir: string?

  # 资源需求
  resources:
    numCpus: float?
    numGpus: float?
    memory: string?                  # e.g., "4GB"
```

## 4. 运行时环境

RayOperator 强依赖 `runtime_env` 来隔离任务依赖。建议在 `TaskDefinition` 中明确指定所需的 Python 包版本，以避免环境冲突。

示例：

```yaml
runtimeEnv:
  pip:
    - "numpy==1.21.0"
    - "torch==1.9.0"
  env_vars:
    TF_ENABLE_ONEDNN_OPTS: "0"
```
