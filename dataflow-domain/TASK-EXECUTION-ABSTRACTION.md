# 任务执行抽象层设计

## 概述

引入统一的**任务执行抽象层**，屏蔽本地执行和远程调用的差异，为不同类型的任务提供一致的执行接口。

## 核心设计

### 架构图

```
┌─────────────────────────────────────────────────────────┐
│           TaskExecutionService (统一入口)                │
│  - executeTask(taskType, action, request)               │
│  - queryTaskState(taskType, stateName, executionId)     │
└────────────────────┬────────────────────────────────────┘
                     │
            ┌────────┴────────┐
            │                 │
    ┌───────▼──────┐   ┌─────▼──────────┐
    │ TaskExecutor │   │ TaskExecutor   │
    │  Interface   │   │  Factory       │
    └───────┬──────┘   └────────────────┘
            │
    ┌───────┴───────────────────────┐
    │                               │
┌───▼──────────────┐    ┌──────────▼───────────┐
│ LocalTaskExecutor│    │ RemoteTaskExecutor   │
│   (本地执行)      │    │   (远程调用)          │
└───┬──────────────┘    └──────────┬───────────┘
    │                               │
    ├─ SqlTaskExecutor              ├─ HttpTaskExecutor
    ├─ ShellTaskExecutor            ├─ GrpcTaskExecutor
    └─ ...                          └─ WebhookTaskExecutor
```

### 核心接口

#### TaskExecutor

```java
public interface TaskExecutor {
    // 执行任务行为
    ExecutionResponse execute(String action, ExecutionRequest request);

    // 查询任务状态
    Object queryState(String stateName, String executionId);

    // 批量查询状态
    Map<String, Object> queryStates(String[] stateNames, String executionId);

    // 获取执行器类型
    ExecutorType getType();

    // 能力查询
    boolean supportsAction(String action);
    boolean supportsState(String stateName);
}
```

## 实现类

### 1. LocalTaskExecutor（本地执行器基类）

**适用场景**：
- 简单的内置任务（SQL、Shell 脚本）
- 无需额外部署的轻量级任务
- 对性能要求高的任务（避免网络开销）

**特点**：
- 在 Dataflow 进程内直接执行
- 状态存储在内存或本地数据库
- 无网络调用，性能最优

**实现示例**：

```java
public class SqlTaskExecutor extends LocalTaskExecutor {

    private final Map<String, SqlExecutionState> stateStore;
    private final SqlExecutor sqlExecutor; // JDBC 封装

    @Override
    protected ExecutionResponse doExecute(String action, ExecutionRequest request) {
        // 直接执行 SQL
        int rowsAffected = sqlExecutor.execute(sql, database);

        // 更新本地状态
        stateStore.put(executionId, new State("completed", rowsAffected));

        return ExecutionResponse.success(executionId)
            .addOutput("rowsAffected", rowsAffected);
    }

    @Override
    protected Object getLocalState(String executionId, String stateName) {
        // 从内存获取状态
        return stateStore.get(executionId).getState(stateName);
    }
}
```

### 2. RemoteTaskExecutor（远程执行器基类）

**适用场景**：
- 复杂的计算任务（PySpark、Ray）
- 需要特定运行环境的任务
- 语言无关的任务（Python、Go、Node.js）
- 独立部署的微服务

**特点**：
- 通过网络协议调用远程执行器
- 状态通过 HTTP/gRPC 查询
- 支持多种协议（HTTP、gRPC、Webhook）

**实现示例**：

```java
public class HttpTaskExecutor extends RemoteTaskExecutor {

    private final HttpClient httpClient;

    @Override
    protected ExecutionResponse doRemoteExecute(
            ActionDefinition actionDef,
            ExecutionRequest request) {

        // 构建 HTTP 请求
        String url = config.getBaseUrl() + actionDef.getEndpoint();
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .method(actionDef.getMethod(), bodyPublisher)
            .build();

        // 发送请求
        HttpResponse<String> response = httpClient.send(httpRequest, ...);

        return parseResponse(response);
    }

    @Override
    protected Object doRemoteQuery(StateDefinition stateDef, String executionId) {
        // 发送 HTTP 请求查询状态
        String url = config.getBaseUrl() + stateDef.getQueryConfig().getEndpoint();
        HttpResponse<String> response = httpClient.send(...);

        // 解析响应，提取状态值
        return parseStateValue(response.body(), stateDef);
    }
}
```

## 使用流程

### 1. 注册执行器

```java
// 创建工厂
TaskExecutorFactory factory = new TaskExecutorFactory(schemaRegistry);

// 注册本地执行器
SqlTaskExecutor sqlExecutor = new SqlTaskExecutor(schema, jdbcTemplate);
factory.registerLocalExecutor("sql_operator", sqlExecutor);

// 远程执行器会根据 Schema 自动创建，无需注册
```

### 2. 配置 TaskSchema

**本地执行器配置**：

```yaml
type: "sql_operator"
executor:
  type: "LOCAL"  # 标记为本地执行
  timeout: 60000

states:
  - name: "status"
    type: "string"
    # 不需要 queryConfig，从内存查询
```

**远程执行器配置**：

```yaml
type: "pyspark_operator"
executor:
  type: "HTTP"
  baseUrl: "http://spark-executor:8081"
  timeout: 60000

states:
  - name: "status"
    type: "string"
    queryConfig:
      endpoint: "/status/{executionId}"
      parser: "json"
      path: "$.status"
```

### 3. 统一调用

```java
TaskExecutionService executionService = new TaskExecutionService(factory);

// 执行 SQL（本地）
ExecutionResponse sqlResponse = executionService.executeTask(
    "sql_operator",
    "start",
    sqlRequest
);

// 执行 PySpark（远程）
ExecutionResponse pysparkResponse = executionService.executeTask(
    "pyspark_operator",
    "start",
    pysparkRequest
);

// 上层代码无需关心执行方式的差异！
```

### 4. 查询状态

```java
// 查询 SQL 状态（从内存）
Object sqlStatus = executionService.queryTaskState(
    "sql_operator",
    "status",
    "exec_001"
);

// 查询 PySpark 状态（发 HTTP 请求）
Object pysparkStatus = executionService.queryTaskState(
    "pyspark_operator",
    "status",
    "exec_002"
);

// 统一的查询接口！
```

## 设计优势

### 1. **统一抽象**

上层代码不关心本地还是远程：

```java
// 无论 SQL 还是 PySpark，调用方式完全一致
executionService.executeTask(taskType, action, request);
executionService.queryTaskState(taskType, stateName, executionId);
```

### 2. **灵活扩展**

新增执行器类型只需实现 TaskExecutor 接口：

```java
// 新增 Shell 本地执行器
public class ShellTaskExecutor extends LocalTaskExecutor {
    // 实现 doExecute() 和 getLocalState()
}

// 新增 gRPC 远程执行器
public class GrpcTaskExecutor extends RemoteTaskExecutor {
    // 实现 doRemoteExecute() 和 doRemoteQuery()
}
```

### 3. **性能优化**

简单任务本地执行，避免网络开销：

| 任务类型 | 执行方式 | 延迟 |
|---------|---------|------|
| SQL | 本地（JDBC） | ~10ms |
| Shell | 本地（ProcessBuilder） | ~50ms |
| PySpark | 远程（HTTP） | ~100ms+ |
| ML Training | 远程（HTTP） | ~500ms+ |

### 4. **类型安全**

通过 TaskSchema 约束执行器类型：

```java
// Schema 中定义了支持的 actions 和 states
schema.supportsAction("start");      // true
schema.supportsAction("undefined");  // false

// 执行器自动继承 Schema 的约束
executor.supportsAction("start");    // true
executor.supportsState("status");    // true
```

### 5. **易于测试**

每种执行器可以独立测试：

```java
@Test
public void testSqlExecutor() {
    SqlTaskExecutor executor = new SqlTaskExecutor(schema, mockJdbc);
    ExecutionResponse response = executor.execute("start", request);
    assertEquals("completed", response.getStatus());
}

@Test
public void testHttpExecutor() {
    HttpTaskExecutor executor = new HttpTaskExecutor(config, schema);
    // 使用 WireMock 模拟 HTTP 响应
}
```

## 文件清单

### 核心接口和枚举

- `TaskExecutor.java` - 执行器接口
- `ExecutorType.java` - 执行器类型枚举
- `ExecutionRequest.java` - 执行请求 VO
- `ExecutionResponse.java` - 执行响应 VO

### 本地执行器

- `local/LocalTaskExecutor.java` - 本地执行器抽象基类
- `local/SqlTaskExecutor.java` - SQL 本地执行器实现

### 远程执行器

- `remote/RemoteTaskExecutor.java` - 远程执行器抽象基类
- `remote/HttpTaskExecutor.java` - HTTP 远程执行器实现

### 服务层

- `TaskExecutorFactory.java` - 执行器工厂
- `TaskExecutionService.java` - 统一执行服务

### 示例

- `example/TaskExecutionExample.java` - 完整使用示例

## 对比：旧 TaskStateQuerier

**旧设计**：
```java
// 只能查询状态，不能执行任务
TaskStateQuerier querier = new TaskStateQuerier(registry);
Object status = querier.queryState("pyspark", "status", "exec_001");
```

**新设计**：
```java
// 统一执行和查询
TaskExecutionService service = new TaskExecutionService(factory);

// 执行任务
service.executeTask("pyspark", "start", request);

// 查询状态
service.queryTaskState("pyspark", "status", "exec_001");
```

新设计完全包含旧设计的功能，并提供了更完整的抽象。

## 编译验证

```bash
./gradlew :dataflow-domain:compileJava
# BUILD SUCCESSFUL ✅
```

## 下一步

### 1. 实现更多本地执行器

- `ShellTaskExecutor` - 执行 Shell 脚本
- `JavaTaskExecutor` - 执行 Java 代码
- `PythonTaskExecutor` - 执行 Python 脚本（进程内）

### 2. 实现更多远程执行器

- `GrpcTaskExecutor` - gRPC 协议支持
- `WebhookTaskExecutor` - Webhook 推送支持

### 3. 集成到应用服务层

```java
@Service
public class TaskExecutionApplicationService {

    private final TaskExecutionService executionService;

    @PostMapping("/api/v1/executions")
    public ExecutionResponse execute(@RequestBody ExecutionRequest request) {
        return executionService.executeTask(
            request.getTaskType(),
            "start",
            request
        );
    }

    @GetMapping("/api/v1/executions/{id}/state/{stateName}")
    public Object queryState(@PathVariable String id, @PathVariable String stateName) {
        return executionService.queryTaskState(
            getTaskType(id),
            stateName,
            id
        );
    }
}
```

### 4. 监控和日志

- 执行器调用监控（成功率、延迟）
- 失败重试机制
- 执行日志追踪

## 总结

✅ **完成的工作**：
1. 设计并实现统一的执行抽象层
2. 支持本地执行和远程调用两种模式
3. 提供完整的执行器工厂和服务层
4. 实现 SQL 本地执行器和 HTTP 远程执行器
5. 编译验证通过

🎯 **核心价值**：
- **统一接口**：上层代码无需关心执行方式
- **灵活扩展**：新增执行器类型简单
- **性能优化**：本地执行避免网络开销
- **类型安全**：通过 Schema 约束能力
