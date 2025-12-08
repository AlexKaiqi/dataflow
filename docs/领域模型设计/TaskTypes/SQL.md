# 任务类型：sql

## 概述

`sql` 任务类型用于执行离线 SQL 查询，支持多种 SQL 引擎（Hive、Spark SQL、Presto、Trino、Iceberg）。适用于数据仓库查询、报表生成、数据聚合等场景。

## 变量传递机制

SQL 任务采用**模板替换**方式传递变量：

- 在 SQL 语句中使用 `{variable_name}` 占位符
- 执行前，执行引擎将占位符替换为 `Node.inputBindings` 中的实际值
- 替换发生在 SQL 执行前，运行时无法动态访问变量

示例：

```sql
SELECT * FROM {input_table} 
WHERE date BETWEEN '{start_date}' AND '{end_date}'
  AND user_id IN ({user_ids})
```

## executionDefinition 结构

```yaml
executionDefinition:
  engine: "hive" | "spark_sql" | "presto" | "trino" | "iceberg"
  
  query: string                    # SQL 查询语句（支持模板变量）
  
  database: string?                # 默认数据库（可选）
  
  # 输出配置（可选，仅当 SQL 需要写入结果时）
  outputFormat: "parquet" | "orc" | "csv" | "json"?  
    # 输出文件格式
    # - parquet: 列式存储，压缩率高，适合分析查询
    # - orc: 列式存储，优化的 Hive 格式
    # - csv: 文本格式，易读但性能较低
    # - json: JSON 格式，适合半结构化数据
  
  outputMode: "overwrite" | "append" | "error_if_exists"?
    # 输出模式
    # - overwrite: 覆盖已存在的数据
    # - append: 追加到已存在的数据
    # - error_if_exists: 如果目标已存在则报错
```

## resources 结构

资源配置取决于底层执行引擎（如 Spark、Presto 集群），通常由平台统一管理。TaskDefinition 中暂不定义资源结构，由 Node 在执行时通过 `resourcePool` 指定集群资源。

## 完整示例

### 示例 1：数据聚合查询（INSERT）

```yaml
TaskDefinition:
  namespace: "com.company.analytics"
  name: "user_stats"
  version: "1.0.0"
  status: "PUBLISHED"
  type: "sql"
  
  inputVariables:
    - name: "start_date"
      type: "string"
      description: "统计开始日期（YYYY-MM-DD）"
    - name: "end_date"
      type: "string"
      description: "统计结束日期（YYYY-MM-DD）"
    - name: "output_table"
      type: "string"
      description: "输出表名"
  
  outputVariables:
    - name: "output_table"
      type: "string"
      description: "输出表名"
    - name: "record_count"
      type: "integer"
      description: "记录数"
  
  executionDefinition:
    engine: "spark_sql"
    database: "analytics"
    
    query: |
      INSERT INTO {output_table}
      SELECT 
        user_id,
        COUNT(*) as event_count,
        COUNT(DISTINCT event_type) as event_type_count,
        MIN(event_time) as first_event,
        MAX(event_time) as last_event
      FROM events
      WHERE event_date BETWEEN '{start_date}' AND '{end_date}'
      GROUP BY user_id
    
    outputFormat: "parquet"
    outputMode: "overwrite"
  
  metadata:
    description: "用户统计报表"
```

### 示例 2：Iceberg 表查询

```yaml
TaskDefinition:
  namespace: "com.company.lakehouse"
  name: "iceberg_analytics"
  version: "1.0.0"
  status: "PUBLISHED"
  type: "sql"
  
  inputVariables:
    - name: "snapshot_date"
      type: "string"
      description: "数据快照日期"
    - name: "min_amount"
      type: "integer"
      description: "最小金额阈值"
  
  outputVariables:
    - name: "result_count"
      type: "integer"
      description: "结果记录数"
  
  executionDefinition:
    engine: "iceberg"
    database: "analytics_db"
    
    query: |
      SELECT 
        category,
        SUM(amount) as total_amount,
        COUNT(*) as transaction_count
      FROM transactions
      WHERE date = '{snapshot_date}'
        AND amount >= {min_amount}
      GROUP BY category
      ORDER BY total_amount DESC
  
  metadata:
    description: "Iceberg 表分析查询"
```

## 参数替换

SQL 查询支持参数化，使用 `{variable_name}` 语法引用 `inputVariables`：

```sql
SELECT * 
FROM orders 
WHERE order_date BETWEEN '{start_date}' AND '{end_date}'
  AND status = '{status}'
```

在 Pipeline 的 Node 中通过 `inputBindings` 提供具体值：

```yaml
nodes:
  - alias: "generate_report"
    taskDefinitionId: "user_stats_v1"
    inputBindings:
      start_date: "2025-01-01"
      end_date: "2025-01-31"
      output_table: "user_stats_202501"
```

## 执行流程

**定义时**（TaskDefinition）：

- 编写 SQL 查询逻辑
- 声明输入/输出变量
- 指定 SQL 引擎和输出格式

**执行时**（Node in Pipeline）：

- 替换 SQL 查询中的参数
- 提交查询到 SQL 引擎执行
- 等待查询完成
- 返回结果统计信息
