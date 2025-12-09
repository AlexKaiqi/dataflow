# Dataflow - 基于 COLA 架构的数据流编排平台

## 项目概述

本项目采用阿里 COLA (Clean Object-Oriented and Layered Architecture) 架构，使用 Spring Boot 3.5.8 和 Gradle 构建工具。

## 技术栈

- **Java**: 21
- **Spring Boot**: 3.5.8
- **构建工具**: Gradle
- **架构**: COLA (阿里巴巴开源的整洁分层架构)
- **持久层**: MyBatis 3.0.3
- **数据库**: MySQL / H2

## 项目结构

```
dataflow/
├── build.gradle                    # 根项目构建配置
├── settings.gradle                 # 多模块配置
├── gradlew                        # Gradle wrapper 脚本
├── gradlew.bat                    # Windows Gradle wrapper
│
├── dataflow-client/               # Client 层 - API 定义
│   └── src/main/java/com/tencent/dataflow/client/
│       ├── api/                   # 对外服务接口定义
│       └── dto/                   # 数据传输对象
│
├── dataflow-adapter/              # Adapter 层 - 适配器
│   └── src/main/java/com/tencent/dataflow/adapter/
│       ├── web/                   # REST API 控制器
│       ├── rpc/                   # RPC 接口实现
│       └── mq/                    # 消息队列监听器
│
├── dataflow-app/                  # Application 层 - 应用服务
│   └── src/main/java/com/tencent/dataflow/app/
│       ├── service/               # 应用服务
│       ├── executor/              # 命令执行器
│       └── query/                 # 查询服务
│
├── dataflow-domain/               # Domain 层 - 领域模型
│   └── src/main/java/com/tencent/dataflow/domain/
│       ├── model/                 # 领域模型/实体
│       ├── gateway/               # 网关接口定义
│       └── service/               # 领域服务
│
├── dataflow-infrastructure/       # Infrastructure 层 - 基础设施
│   └── src/main/java/com/tencent/dataflow/infrastructure/
│       ├── repository/            # 仓储实现
│       ├── mapper/                # MyBatis Mapper
│       ├── config/                # 配置类
│       └── gateway/               # 网关实现
│
└── start/                         # 启动模块
    └── src/main/
        ├── java/com/tencent/dataflow/
        │   └── DataflowApplication.java
        └── resources/
            ├── application.yaml
            ├── application-dev.yaml
            └── application-prod.yaml
```

## COLA 架构分层说明

### 1. Client 层 (dataflow-client)

- **职责**: 对外API定义、DTO定义
- **依赖**: 无业务依赖
- **特点**:
  - 可被外部系统依赖
  - 只包含接口和DTO
  - 使用 COLA 的 DTO 组件

### 2. Adapter 层 (dataflow-adapter)

- **职责**: 处理外部请求，协议转换
- **依赖**: Client、App
- **包含**:
  - REST API 控制器
  - RPC 接口实现
  - 消息队列监听器
  - WebSocket 处理器

### 3. App 层 (dataflow-app)

- **职责**: 应用服务编排，业务流程控制
- **依赖**: Client、Domain
- **包含**:
  - Command 执行器（写操作）
  - Query 服务（读操作）
  - 应用服务编排

### 4. Domain 层 (dataflow-domain)

- **职责**: 核心业务逻辑，领域模型
- **依赖**: 最少依赖，保持纯净
- **包含**:
  - 领域实体 (Entity)
  - 值对象 (Value Object)
  - 领域服务 (Domain Service)
  - 网关接口定义 (Gateway Interface)

### 5. Infrastructure 层 (dataflow-infrastructure)

- **职责**: 技术实现，外部系统集成
- **依赖**: Domain
- **包含**:
  - 数据库访问实现
  - 外部 API 调用
  - 缓存实现
  - 网关接口实现

### 6. Start 层

- **职责**: 应用启动和配置
- **依赖**: Adapter、Infrastructure
- **包含**:
  - 启动类
  - 配置文件
  - 组装所有模块

## 依赖关系图

```
    ┌─────────┐
    │  Start  │ (启动)
    └────┬────┘
         │
    ┌────┴────────┐
    │             │
┌───▼────┐  ┌────▼──────────┐
│Adapter │  │Infrastructure │
└───┬────┘  └────┬──────────┘
    │            │
    │       ┌────▼────┐
    │       │ Domain  │ (核心)
    │       └────▲────┘
    │            │
┌───▼────┐       │
│  App   ├───────┘
└───┬────┘
    │
┌───▼────┐
│ Client │ (API)
└────────┘
```

## 快速开始

### 构建项目

```bash
# 构建所有模块
./gradlew build

# 只编译不测试
./gradlew build -x test

# 清理构建
./gradlew clean build
```

### 运行应用

```bash
# 方式 1: 使用 Gradle
./gradlew :start:bootRun

# 方式 2: 运行 JAR
./gradlew :start:bootJar
java -jar start/build/libs/dataflow-1.0.0-SNAPSHOT.jar

# 指定环境
java -jar start/build/libs/dataflow-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod
```

### 运行测试

```bash
# 运行所有测试
./gradlew test

# 运行特定模块测试
./gradlew :dataflow-app:test

# 查看测试报告
open build/reports/tests/test/index.html
```

## 开发指南

### 模块开发原则

1. **Client 模块**:

   - 只定义接口和DTO
   - 不包含任何实现逻辑
   - 可以被其他系统依赖
2. **Domain 模块**:

   - 保持纯净，最少依赖
   - 包含核心业务逻辑
   - 定义 Gateway 接口，不实现
3. **App 模块**:

   - 编排业务流程
   - 调用 Domain 层服务
   - 通过 Gateway 访问基础设施
4. **Infrastructure 模块**:

   - 实现 Gateway 接口
   - 处理所有技术细节
   - 不包含业务逻辑
5. **Adapter 模块**:

   - 处理协议转换
   - 调用 App 层服务
   - 返回统一响应格式

### 添加新功能

1. 在 `dataflow-client` 定义 API 接口和 DTO
2. 在 `dataflow-domain` 设计领域模型和服务
3. 在 `dataflow-app` 实现应用服务
4. 在 `dataflow-infrastructure` 实现数据访问
5. 在 `dataflow-adapter` 暴露 REST API

## 配置说明

### application.yaml

主配置文件，包含通用配置。

### application-dev.yaml

开发环境配置，包含本地数据库连接等。

### application-prod.yaml

生产环境配置。

## 下一步计划

- [ ] 完善各层的示例代码
- [ ] 添加单元测试和集成测试
- [ ] 配置 CI/CD 流程
- [ ] 添加 API 文档 (Swagger/OpenAPI)
- [ ] 实现具体业务功能

## 参考文档

- [COLA 架构](https://github.com/alibaba/COLA)
- [Spring Boot 官方文档](https://spring.io/projects/spring-boot)
- [Gradle 用户指南](https://docs.gradle.org/)

---

**注意**: 这是一个全新的项目结构，保留了 COLA 的架构优势，同时使用了现代化的技术栈。
