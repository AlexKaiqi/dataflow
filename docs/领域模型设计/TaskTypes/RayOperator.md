# Ray 算子任务

## 概述

Ray 算子任务是一种支持声明式多算子组合的数据处理任务类型。它基于 Ray 框架，允许用户通过配置多个算子（Operators）来构建复杂的数据处理流水线，每个算子负责特定的数据转换或处理逻辑。

支持多种算子框架，如：
- **DataJuicer**: 数据清洗和预处理算子
- **自定义算子**: 用户自定义的 Ray 算子
- **第三方算子**: 其他基于 Ray 的算子库

## 任务类型

**类型标识**：`RAY_OPERATOR`

## 支持的行为

- **start**: 启动任务执行
- **retry**: 重试失败的任务

## 产生的事件

- **started**: 任务开始执行
- **completed**: 任务成功完成
- **failed**: 任务执行失败

## 输入变量

### 必需变量

```yaml
inputVariables:
  - name: input_path
    type: string
    required: true
    description: "输入数据路径，支持多种数据源（HDFS、S3、本地文件等）"
    
  - name: output_path
    type: string
    required: true
    description: "输出数据路径"
```

### 可选变量

```yaml
  - name: operators
    type: array
    required: false
    description: "算子配置列表，定义数据处理流水线"
    
  - name: sample_rate
    type: number
    required: false
    default: 1.0
    description: "数据采样率（0.0-1.0）"
    
  - name: num_workers
    type: number
    required: false
    default: 1
    description: "并行处理的 worker 数量"
```

## 输出变量

```yaml
outputVariables:
  - name: rows_processed
    type: number
    description: "处理的数据行数"
    
  - name: rows_filtered
    type: number
    description: "被过滤掉的数据行数"
    
  - name: operators_executed
    type: number
    description: "实际执行的算子数量"
    
  - name: execution_time
    type: number
    description: "任务执行时间（秒）"
    
  - name: output_path
    type: string
    description: "实际输出路径"
```

## 执行配置

### executionConfig 结构

```yaml
executionConfig:
  # 算子框架（可选，默认为 datajuicer）
  framework: "datajuicer"         # datajuicer | custom | other
  
  # 算子配置列表
  operators:
    - type: "filter"              # 算子类型
      name: "remove_nulls"        # 算子名称
      config:                     # 算子特定配置
        columns: ["text", "label"]
        
    - type: "map"
      name: "normalize_text"
      config:
        column: "text"
        lowercase: true
        remove_punctuation: true
        
    - type: "dedup"
      name: "deduplicate"
      config:
        columns: ["text"]
        method: "exact"
  
  # 资源配置
  resources:
    cpu: 4
    memory: "8G"
    
  # 执行参数
  batch_size: 1000
  cache_dir: "/tmp/datajuicer"
```

## 支持的算子类型

以下以 DataJuicer 算子为例，自定义算子可根据实际需求定义。

### 1. Filter 算子

过滤数据，移除不符合条件的记录。

```yaml
type: "filter"
config:
  # 基于列值过滤
  columns: ["text", "label"]           # 必须非空的列
  
  # 基于条件表达式
  condition: "length(text) > 10"       # 条件表达式
  
  # 基于质量分数
  min_quality_score: 0.8
```

### 2. Map 算子

对数据进行映射转换。

```yaml
type: "map"
config:
  column: "text"                       # 要转换的列
  
  # 文本处理
  lowercase: true                      # 转小写
  remove_punctuation: true             # 移除标点
  remove_stopwords: true               # 移除停用词
  
  # 自定义函数
  function: "lambda x: x.strip()"      # Python lambda 函数
```

### 3. Dedup 算子

数据去重。

```yaml
type: "dedup"
config:
  columns: ["text"]                    # 去重依据的列
  method: "exact"                      # 去重方法：exact | fuzzy | minhash
  threshold: 0.95                      # 相似度阈值（fuzzy/minhash）
```

### 4. Tokenize 算子

文本分词。

```yaml
type: "tokenize"
config:
  column: "text"
  tokenizer: "bert"                    # 分词器类型：bert | gpt2 | jieba
  max_length: 512
```

### 5. Sample 算子

数据采样。

```yaml
type: "sample"
config:
  rate: 0.1                            # 采样率
  method: "random"                     # 采样方法：random | stratified
  seed: 42                             # 随机种子
```

### 6. Transform 算子

数据格式转换。

```yaml
type: "transform"
config:
  source_format: "jsonl"               # 源格式
  target_format: "parquet"             # 目标格式
  compression: "snappy"                # 压缩方式
```

## 使用示例

### 示例 1：文本清洗流水线

```yaml
namespace: "com.company.tasks"
name: "text_cleaning_pipeline"
type: RAY_OPERATOR
description: "文本数据清洗流水线（使用 DataJuicer 算子）"

inputVariables:
  - name: input_path
    type: string
    required: true
  - name: output_path
    type: string
    required: true

outputVariables:
  - name: rows_processed
    type: number
  - name: rows_filtered
    type: number

executionConfig:
  operators:
    # 1. 过滤空值
    - type: "filter"
      name: "remove_nulls"
      config:
        columns: ["text", "label"]
    
    # 2. 文本规范化
    - type: "map"
      name: "normalize_text"
      config:
        column: "text"
        lowercase: true
        remove_punctuation: true
        remove_stopwords: true
    
    # 3. 去重
    - type: "dedup"
      name: "deduplicate"
      config:
        columns: ["text"]
        method: "fuzzy"
        threshold: 0.95
    
    # 4. 长度过滤
    - type: "filter"
      name: "length_filter"
      config:
        condition: "length(text) >= 10 and length(text) <= 1000"
    
    # 5. 采样（可选）
    - type: "sample"
      name: "downsample"
      config:
        rate: 0.8
        method: "random"
        seed: 42
  
  resources:
    cpu: 8
    memory: "16G"
  
  batch_size: 5000
```

### 示例 2：数据格式转换

```yaml
namespace: "com.company.tasks"
name: "format_converter"
type: RAY_OPERATOR
description: "数据格式转换任务"

executionConfig:
  operators:
    # 1. 转换格式
    - type: "transform"
      name: "jsonl_to_parquet"
      config:
        source_format: "jsonl"
        target_format: "parquet"
        compression: "snappy"
    
    # 2. 数据验证
    - type: "filter"
      name: "validate_schema"
      config:
        required_columns: ["id", "text", "label"]
```

## 执行流程

1. **初始化阶段**
   - 加载输入数据
   - 解析算子配置
   - 初始化执行环境

2. **执行阶段**
   - 按照 `operators` 列表顺序依次执行每个算子
   - 每个算子的输出作为下一个算子的输入
   - 支持算子级别的错误处理和重试

3. **完成阶段**
   - 输出处理结果
   - 生成执行统计信息
   - 清理临时文件

## 错误处理

### 算子级别错误

```yaml
executionConfig:
  operators:
    - type: "filter"
      name: "risky_operation"
      config:
        # 算子配置
      error_handling:
        on_error: "skip"              # skip | fail | retry
        max_retries: 3
        retry_delay: 5
```

### 任务级别错误

- 如果某个算子失败且 `on_error: fail`，整个任务失败
- 如果 `on_error: skip`，跳过该算子继续执行
- 支持任务级别的重试机制

## 性能优化

### 并行处理

```yaml
executionConfig:
  # 启用并行处理
  num_workers: 8
  batch_size: 1000
  
  # 内存优化
  cache_compressed: true
  memory_fraction: 0.8
```

### 增量处理

```yaml
executionConfig:
  # 增量模式
  incremental: true
  checkpoint_path: "/tmp/checkpoints"
  checkpoint_interval: 1000
```

## 监控指标

任务执行时自动收集以下指标：

- `rows_input`: 输入行数
- `rows_output`: 输出行数
- `rows_filtered`: 过滤行数
- `operators_executed`: 执行的算子数
- `execution_time`: 总执行时间
- `memory_peak`: 内存峰值
- `cpu_utilization`: CPU 利用率

## 最佳实践

1. **算子顺序优化**
   - 将过滤算子放在前面，减少后续处理的数据量
   - 将计算密集型算子放在后面

2. **资源配置**
   - 根据数据量合理配置 `cpu` 和 `memory`
   - 大数据集使用多 worker 并行处理

3. **错误处理**
   - 对不稳定的算子配置 `on_error: skip`
   - 启用 checkpoint 支持断点续传

4. **性能调优**
   - 合理设置 `batch_size`
   - 启用 `cache_compressed` 减少内存占用
   - 使用增量模式处理大规模数据

## 参考资源

- [DataJuicer 官方文档](https://github.com/modelscope/data-juicer)
- [算子配置示例](./examples/datajuicer-operators.yaml)
- [性能调优指南](./guides/datajuicer-performance.md)
