# 任务类型：model_inference

## 概述

`model_inference` 是专门用于批量模型推理的任务类型，支持特征工程和模型推理的一体化流程。适用于大规模批量推理场景。

## executionDefinition 结构

```yaml
executionDefinition:
  modelLocation:
    type: "mlflow" | "s3" | "local"
    url: string                    # 模型存储位置
    version: string?               # 模型版本（type=mlflow 时）
  
  featureEngineering:              # 特征工程（可选）
    codeLocation:
      type: "git" | "s3" | "local"
      url: string
      ref: string?
      path: string?
    
    operators:
      - name: string               # 特征算子名称
        class: string              # 算子类全限定名
        config: object             # 算子配置
  
  inferenceConfig:
    batchSize: integer             # 批处理大小
    framework: "pytorch" | "tensorflow" | "onnx" | "sklearn"
    deviceType: "cpu" | "gpu"
    preprocessor: string?          # 预处理器类名（可选）
    postprocessor: string?         # 后处理器类名（可选）
```

## resources 结构

```yaml
resources:
  cpu: string                      # CPU 核心数
  memory: string                   # 内存大小
  gpu: integer?                    # GPU 数量
  inferenceResources:              # 推理特定资源
    modelReplicas: integer?        # 模型副本数（并行推理）
    maxConcurrency: integer?       # 最大并发请求数
```

## 特征算子协议

特征算子遵循与 `ray_operator` 相同的参数注入和返回值绑定机制：

```python
from abc import ABC, abstractmethod
from typing import Dict, Any, Tuple
import pandas as pd

class FeatureOperator(ABC):
    """特征工程算子基类"""
    
    def __init__(self, config: Dict[str, Any]):
        """
        初始化算子
        
        Args:
            config: 算子配置参数（来自 executionDefinition.featureEngineering.operators[].config）
        """
        self.config = config
    
    @abstractmethod
    def process(self, **kwargs) -> Any:
        """
        处理特征数据
        
        参数注入机制：
            - 参数名必须与 TaskDefinition.inputVariables[].name 匹配
            - 执行引擎自动注入参数值
            - 支持类型标注（如 pd.DataFrame, str, int 等）
        
        返回值绑定机制：
            - 单个返回值：自动绑定到第一个 outputVariable
            - 多个返回值（Tuple）：按位置顺序绑定
            - Dict 返回值：按 key 匹配 outputVariables[].name
        
        示例：
            def process(self, data: pd.DataFrame) -> pd.DataFrame:
                # 特征工程处理
                return processed_data
        """
        pass
```

## 完整示例

```yaml
TaskDefinition:
  id: "user_scoring_v1"
  namespace: "com.company.models"
  name: "用户评分模型推理"
  type: "model_inference"
  
  versions:
    - version: "1.0.0"
      status: "PUBLISHED"
      
      inputVariables:
        - name: "user_data_path"
          type: "string"
          description: "用户数据路径"
        - name: "output_path"
          type: "string"
          description: "评分结果输出路径"
      
      outputVariables:
        - name: "inference_count"
          type: "integer"
          description: "推理样本数量"
        - name: "output_path"
          type: "string"
          description: "结果输出路径"
      
      executionDefinition:
        modelLocation:
          type: "mlflow"
          url: "mlflow://models/user_scoring"
          version: "production"
        
        featureEngineering:
          codeLocation:
            type: "git"
            url: "https://github.com/company/feature-ops.git"
            ref: "v2.1.0"
            path: "features/user_features.py"
          
          operators:
            - name: "age_binning"
              class: "user_features.AgeBinning"
              config:
                bins: [0, 18, 30, 45, 60, 100]
                labels: ["teen", "young", "middle", "senior", "elder"]
            
            - name: "behavior_aggregation"
              class: "user_features.BehaviorAggregation"
              config:
                window_days: 30
                metrics: ["click_count", "purchase_count", "avg_session_time"]
        
        inferenceConfig:
          batchSize: 1000
          framework: "pytorch"
          deviceType: "gpu"
          preprocessor: "user_features.Normalizer"
          postprocessor: "user_features.ScoreCalibration"
      
      resources:
        cpu: "8"
        memory: "32Gi"
        gpu: 2
        inferenceResources:
          modelReplicas: 2
          maxConcurrency: 100
```

## 执行流程

**定义时**（TaskDefinition）：
- 指定模型位置和版本
- 配置特征工程算子链
- 设置推理参数（batch size、framework）
- 声明资源需求

**执行时**（Node in Pipeline）：
- 加载模型和特征工程代码
- 读取输入数据
- 执行特征工程算子链
- 批量模型推理
- 写入输出结果
