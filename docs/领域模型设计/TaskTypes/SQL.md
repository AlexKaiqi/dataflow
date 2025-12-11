# 任务类型：SQL (查询任务)

## 1. 概述

**SQL** 任务类型用于执行离线 SQL 查询，支持多种 SQL 引擎（Hive、Spark SQL、Presto、Trino、Iceberg）。适用于数据仓库查询、报表生成、数据聚合等场景。

**核心特性**:
- **声明式**: 仅需编写 SQL，无需关心底层执行。
- **多引擎**: 支持切换不同的 SQL 引擎。
- **模板化**: 支持 SQL 模板变量替换。

---

## 2. TaskSchema 能力定义

```yaml
TaskSchema:
  type: "sql_query"
  description: "通用 SQL 查询任务"

  # ==== 1. 支持的行为 (Actions) ====
  actions:
    execute:
      description: "执行 SQL 查询"
      params:
        variables: Map<String, String>  # SQL 模板变量

    cancel:
      description: "取消查询"
      params: {}

  # ==== 2. 产生的事件 (Events) ====
  events:
    - name: "started"
      payload: { queryId: string, engine: string }

    - name: "succeeded"
      payload: { rowCount: long, outputTable: string }

    - name: "failed"
      payload: { error: string, sqlState: string }

  # ==== 3. 状态定义 (States) ====
  states:
    QUEUED: "排队中"
    RUNNING: "正在执行"
    SUCCEEDED: "执行成功"
    FAILED: "执行失败"
    CANCELLED: "已取消"
```

---

## 3. TaskDefinition 配置结构

```yaml
TaskDefinition:
  type: "sql_query"

  # 执行配置
  executionConfig:
    engine: "hive" | "spark_sql" | "presto" | "trino" | "iceberg"

    database: string?                # 默认数据库

    query: string                    # SQL 查询语句（支持模板变量 {var}）

    # 输出配置（可选）
    outputFormat: "parquet" | "orc" | "csv" | "json"?
    outputMode: "overwrite" | "append" | "error_if_exists"?

  # 资源配置 (通常由引擎托管，此处可选)
  resources:
    queue: string                    # YARN/K8s 队列名
```

## 4. 变量传递机制

SQL 任务采用**模板替换**方式传递变量：

- 在 SQL 语句中使用 `{variable_name}` 占位符
- 执行前，执行引擎将占位符替换为 `Node.startPayload` 中的实际值

示例：

```sql
INSERT INTO {output_table}
SELECT * FROM {input_table}
WHERE date = '{date}'
```
