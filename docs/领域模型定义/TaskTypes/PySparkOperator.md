# 任务类型：pyspark_operator

## 概述

`pyspark_operator` 是基于 PySpark 的大规模数据处理任务，支持自定义算子进行复杂的 ETL 和数据转换。适用于 TB 级以上数据处理场景。

## executionDefinition 结构

```yaml
executionDefinition:
  codeLocation:
    type: "git" | "s3" | "local"
    url: string                    # 代码仓库 URL 或存储路径
    ref: string?                   # Git 分支/标签
    path: string?                  # 代码文件路径
  
  operators:
    - name: string                 # 算子名称
      class: string                # 算子类全限定名
      config: object               # 算子配置参数
  
  sparkConfig: object?             # Spark 配置（可选）
    spark.sql.shuffle.partitions: string
    spark.default.parallelism: string
    # 其他 Spark 配置项
```

## resources 结构

```yaml
resources:
  cpu: string                      # Driver CPU
  memory: string                   # Driver 内存
  sparkResources:                  # Spark 特定资源
    executorCount: integer         # Executor 数量
    executorCpu: string            # 每个 Executor 的 CPU
    executorMemory: string         # 每个 Executor 的内存
    executorCores: integer?        # 每个 Executor 的核心数
```

## 算子协议

所有 PySpark 算子必须实现以下接口：

```python
from abc import ABC, abstractmethod
from typing import Dict, Any, Tuple
from pyspark.sql import DataFrame, SparkSession

class PySparkOperator(ABC):
    """PySpark 算子基类"""
    
    def __init__(self, spark: SparkSession, config: Dict[str, Any]):
        """
        初始化算子
        
        Args:
            spark: SparkSession 实例（由执行引擎注入）
            config: 算子配置参数（来自 executionDefinition.operators[].config）
                   这是算子的静态配置，在任务定义时指定
        """
        self.spark = spark
        self.config = config
    
    @abstractmethod
    def process(self, **kwargs) -> Any:
        """
        处理输入数据
        
        参数注入机制：
            - 执行引擎通过反射获取 process 方法的参数签名
            - 参数名必须与 TaskDefinition.inputVariables[].name 匹配
            - 执行引擎自动从 Node.inputBindings 中获取值并注入
            - 对于 DataFrame 类型，执行引擎自动从数据源加载
        
        返回值绑定机制：
            - 单个返回值：自动绑定到第一个 outputVariable
            - 多个返回值（Tuple）：按位置顺序绑定到 outputVariables
            - Dict 返回值：按 key 匹配 outputVariables[].name 进行绑定
        
        示例：
            # 单输入单输出
            def process(self, input_df: DataFrame) -> DataFrame:
                return input_df.filter(...)
            
            # 多输入多输出（Tuple）
            def process(self, users_df: DataFrame, events_df: DataFrame) -> Tuple[DataFrame, int]:
                result_df = users_df.join(events_df, ...)
                count = result_df.count()
                return result_df, count
            
            # 多输出（Dict）
            def process(self, input_df: DataFrame) -> Dict[str, Any]:
                return {
                    "output_df": result_df,
                    "record_count": result_df.count()
                }
        """
        pass
```

## 完整示例

```yaml
TaskDefinition:
  id: "etl_user_profile_v1"
  namespace: "com.company.etl"
  name: "用户画像 ETL"
  type: "pyspark_operator"
  
  versions:
    - version: "1.0.0"
      status: "PUBLISHED"
      
      inputVariables:
        - name: "user_base_path"
          type: "string"
          description: "用户基础信息路径"
        - name: "behavior_path"
          type: "string"
          description: "用户行为数据路径"
        - name: "output_path"
          type: "string"
          description: "输出路径"
      
      outputVariables:
        - name: "output_path"
          type: "string"
          description: "用户画像输出路径"
        - name: "processed_count"
          type: "integer"
          description: "处理的用户数"
      
      executionDefinition:
        codeLocation:
          type: "git"
          url: "https://github.com/company/spark-operators.git"
          ref: "v1.2.0"
          path: "operators/user_profile.py"
        
        operators:
          - name: "join_user_behavior"
            class: "user_profile.JoinUserBehavior"
            config:
              join_type: "left"
              behavior_window_days: 90
          
          - name: "compute_rfm"
            class: "user_profile.ComputeRFM"
            config:
              recency_bins: [7, 30, 90, 180]
              frequency_bins: [1, 5, 20, 50]
              monetary_bins: [100, 500, 2000, 10000]
          
          - name: "feature_engineering"
            class: "user_profile.FeatureEngineering"
            config:
              features:
                - "avg_order_value"
                - "purchase_frequency"
                - "favorite_category"
                - "churn_probability"
        
        sparkConfig:
          spark.sql.shuffle.partitions: "400"
          spark.default.parallelism: "400"
          spark.sql.adaptive.enabled: "true"
      
      resources:
        cpu: "4"
        memory: "8Gi"
        sparkResources:
          executorCount: 10
          executorCpu: "4"
          executorMemory: "16Gi"
          executorCores: 4
```

## 执行流程

**定义时**（TaskDefinition）：
- 声明输入/输出变量
- 指定代码位置和算子列表
- 配置 Spark 参数
- 声明资源需求

**执行时**（Node in Pipeline）：
- 初始化 SparkSession
- 加载算子代码
- 按顺序执行算子链
- 输出结果供下游使用
