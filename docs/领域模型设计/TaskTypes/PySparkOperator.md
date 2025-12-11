# 任务类型：PySparkOperator (批处理)

## 1. 概述

**PySparkOperator** 是基于 PySpark 的大规模数据处理任务，支持自定义算子进行复杂的 ETL 和数据转换。适用于 TB 级以上数据处理场景。

**核心特性**:
- **有限流**: 任务有明确的开始和结束。
- **高吞吐**: 利用 Spark 分布式计算能力。
- **重试机制**: 支持失败自动重试。

---

## 2. TaskSchema 能力定义

```yaml
TaskSchema:
  type: "pyspark_batch"
  description: "PySpark 批处理任务"

  # ==== 1. 支持的行为 (Actions) ====
  actions:
    start:
      description: "提交 Spark 作业"
      params:
        args: Map<String, String>  # 运行时参数覆盖

    cancel:
      description: "取消正在运行的作业"
      params: {}

  # ==== 2. 产生的事件 (Events) ====
  events:
    - name: "started"
      payload: { applicationId: string, trackingUrl: string }

    - name: "succeeded"
      payload: { outputPaths: List<String>, metrics: Map<String, Number> }

    - name: "failed"
      payload: { error: string, logUrl: string }

  # ==== 3. 状态定义 (States) ====
  states:
    SUBMITTED: "作业已提交"
    RUNNING: "作业正在运行"
    SUCCEEDED: "作业成功完成"
    FAILED: "作业失败"
    CANCELLED: "作业被取消"
```

---

## 3. TaskDefinition 配置结构

```yaml
TaskDefinition:
  type: "pyspark_batch"

  # 执行配置
  executionConfig:
    # 代码位置
    codeLocation:
      type: "git" | "s3" | "local"
      url: string                    # 代码仓库 URL 或存储路径
      ref: string?                   # Git 分支/标签
      path: string?                  # 代码文件路径

    # 算子链配置
    operators:
      - name: string                 # 算子名称
        class: string                # 算子类全限定名
        config: object               # 算子配置参数

    # Spark 配置
    sparkConfig:
      spark.sql.shuffle.partitions: "200"
      spark.default.parallelism: "100"

  # 资源配置
  resources:
    driverMemory: "2g"
    executorMemory: "4g"
    executorCores: 2
    executorInstances: 10
```

## 4. 算子协议

所有 PySpark 算子必须实现以下接口：

```python
from abc import ABC, abstractmethod
from typing import Dict, Any
from pyspark.sql import SparkSession

class PySparkOperator(ABC):
    """PySpark 算子基类"""

    def __init__(self, spark: SparkSession, config: Dict[str, Any]):
        self.spark = spark
        self.config = config

    @abstractmethod
    def process(self, **kwargs) -> Any:
        """
        处理输入数据
        参数由执行引擎根据 TaskDefinition.inputVariables 自动注入
        """
        pass
```
