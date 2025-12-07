# 领域模型文档规范

## 1. 文档目标

领域模型文档是项目的**核心资产**，用于：

- 定义业务概念和规则
- 指导代码实现
- 作为测试用例的需求来源
- 团队沟通的共同语言

## 2. 文档组织原则

### 2.1 概念完整性

每个文档应覆盖一个完整的领域概念，包括：

- **用户视角**：如何配置、使用
- **系统视角**：如何实现、执行

❌ **反例**：拆分成 VariableDefinition.md 和 VariableReference.md
✅ **正例**：统一为 Variable.md，包含定义、引用、解析三个方面

### 2.2 聚合根优先

Definition 和 Execution 是同一聚合根的两个状态：

- **Definition**：设计态（用户配置）
- **Execution**：运行态（系统执行）

应合并到同一文档，用章节区分。

### 2.3 扁平结构

避免过度嵌套，保持目录扁平：

```text
领域模型定义/
├── PipelineDefinition.md  # 包含 Pipeline 和 Node 的定义
├── TaskDefinition.md
├── Expression.md
├── Event.md
├── VariableDefinition.md
├── VariableReference.md
└── TaskTypes/             # 唯一的子目录
    ├── Approval.md
    ├── SQL.md
    └── ...
```

## 3. 文档结构模板

### 3.1 聚合根文档（如 Pipeline.md）

```markdown
# Pipeline

## 概述
- 定义：什么是 Pipeline
- 职责：Pipeline 在系统中的作用
- 生命周期：从定义到执行的完整流程

## Pipeline Definition（设计态）

### 数据结构
\`\`\`yaml
# 完整的 YAML 配置示例
\`\`\`

### 字段说明
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|

### 配置示例
\`\`\`yaml
# 典型场景示例
\`\`\`

### 验证规则
- 必填字段校验
- 业务规则约束

## Pipeline Execution（运行态）

### 状态机
\`\`\`mermaid
stateDiagram-v2
    [*] --> pending
    pending --> running
    running --> completed
    running --> failed
\`\`\`

### 执行流程
1. 初始化
2. 任务调度
3. 状态更新
4. 结果收集

### 运行时数据
\`\`\`python
# 执行时的内部数据结构
\`\`\`

## 领域事件
- pipeline.created
- pipeline.started
- pipeline.completed
- pipeline.failed

## 实现指导

### 代码位置
- 聚合根：`src/domain/models/pipeline.py`
- 仓储接口：`src/domain/repositories/pipeline_repository.py`
- 领域事件：`src/domain/events/pipeline_events.py`

### 不变量（Invariants）
- Pipeline 必须至少包含一个 Task
- Task 的依赖关系不能形成环

### 测试要点
- 测试文件：`tests/unit/domain/models/test_pipeline.py`
- 关键场景：创建、验证、执行、失败处理

## FAQ
常见问题和解答

## 参考
- 相关文档链接
```

### 3.2 值对象文档（如 Expression.md）

```markdown
# Expression

## 概述
- 定义：表达式是什么
- 分类：EventExpression vs StateExpression

## 语法规范

### Event Expression
\`\`\`yaml
# 语法示例
\`\`\`

### State Expression
\`\`\`yaml
# 语法示例
\`\`\`

## 使用场景
在哪些地方使用表达式

## 执行机制
系统如何解析和执行表达式

## 实现指导
- 代码位置：`src/domain/models/expression.py`
- 解析器：`src/domain/services/expression_parser.py`

## 示例
典型用法示例

## FAQ
```

### 3.3 任务类型文档（TaskTypes/Approval.md）

```markdown
# Approval Task

## 概述
人工审批任务类型

## Task Definition
\`\`\`yaml
# 配置示例
\`\`\`

## 生命周期事件
- approval.started
- approval.approved
- approval.rejected
- approval.timeout

## Node 配置
\`\`\`yaml
# 节点配置示例
\`\`\`

## API 规范
### 批准 API
\`\`\`http
POST /api/v1/executions/{id}/approve
\`\`\`

## 实现要求
- UI 交互
- 通知机制
- 权限控制

## 使用场景
- 数据质量审批
- 模型部署审批

## FAQ
```

## 4. 文档编写规范

### 4.1 命名约定

- 文件名使用 PascalCase：`Pipeline.md`、`Expression.md`
- 章节标题使用中英文：`## Pipeline Definition（设计态）`

### 4.2 代码示例

- 配置示例使用 YAML
- 实现示例使用 Python
- 包含完整的可运行示例
- 添加注释说明关键点

### 4.3 图表使用

- 状态机使用 Mermaid stateDiagram
- 流程图使用 Mermaid flowchart
- 架构图使用 Mermaid graph

### 4.4 名词首次引入规范

当在文档中首次引入一个新的领域概念时，必须遵循以下规范：

**格式要求**：

```markdown
**概念名称（英文名称）** 定义说明。
```

或在表格/列表中：

```yaml
fieldName: Type  # 概念名称（英文名称）：定义说明
```

**示例**：

✅ **正确**：
```markdown
# Pipeline 的唯一编排结构
nodes: Node[]  # 节点（Node）：流水线的编排单元，代表工作流中的一个执行点
```

或在正文中：
```markdown
**节点（Node）** 是流水线中的编排单元，代表工作流中的一个执行点。
```

❌ **错误**：
```markdown
nodes: Node[]  # 节点列表
```
（缺少英文名称和完整定义）

**关键原则**：

1. **中英文对照**：同时提供中文术语和英文概念名
2. **定义明确**：一句话说清楚"是什么"
3. **位置恰当**：在首次使用时立即说明，不要让读者疑惑
4. **统一术语**：使用术语表（docs/术语表.md）中定义的标准术语

**新概念引入检查清单**：

- [ ] 提供中文术语
- [ ] 提供英文概念名（首字母大写）
- [ ] 给出清晰的定义（一句话）
- [ ] 如果是复杂概念，说明其在系统中的作用
- [ ] 在术语表中添加该术语

### 4.5 交叉引用

- 使用相对路径链接：`参见 [Event.md](./Event.md)`
- 在 "参考" 章节列出相关文档
- 引用其他文档的概念时，首次出现时添加链接

### 4.6 版本控制

文档变更需要：

1. 更新文档内容
2. 运行相关测试确保一致性
3. 在 Git commit message 中说明变更原因

## 5. 文档驱动开发流程

```text
1. 编写/更新领域模型文档
   ↓
2. 基于文档编写测试用例（TDD Red）
   ↓
3. 实现代码使测试通过（TDD Green）
   ↓
4. 重构代码（TDD Refactor）
   ↓
5. 验证代码符合文档定义
   ↓
6. 更新文档（如有新发现）
```

## 6. 质量检查清单

每份领域模型文档应满足：

- [ ] 概述清晰，定义了"是什么"
- [ ] 包含完整的配置示例
- [ ] 字段说明完整（类型、必填、默认值）
- [ ] 状态机或流程图可视化
- [ ] 指明代码实现位置
- [ ] 列出核心业务规则（不变量）
- [ ] 包含典型使用场景
- [ ] 有 FAQ 章节
- [ ] 交叉引用相关文档
- [ ] Markdown 格式规范（通过 linter）

## 7. 文档评审

### 评审维度

1. **完整性**：是否覆盖概念的所有方面
2. **准确性**：是否与实际实现一致
3. **可读性**：技术人员和业务人员都能理解
4. **可测试性**：能否基于文档编写测试用例

### 评审流程

- 领域专家评审业务规则
- 架构师评审技术设计
- 开发人员评审可实现性
- 测试人员评审可测试性

## 8. 反模式警示

❌ **拆分过细**：VariableDefinition.md + VariableReference.md  
✅ **概念完整**：Variable.md 包含定义、引用、解析

❌ **只写配置**：缺少执行机制说明  
✅ **用户+系统**：既有配置示例，又有实现指导

❌ **缺少示例**：只有字段定义，没有使用场景  
✅ **示例驱动**：多个典型场景示例

❌ **孤立文档**：没有交叉引用  
✅ **文档网络**：通过链接形成知识网络

## 9. 工具支持

### Markdown Linter

```bash
# 使用 markdownlint 检查格式
markdownlint docs/领域模型定义/
```

### 文档生成

```bash
# 从文档生成 OpenAPI spec
# 从文档生成测试骨架
# TBD
```

## 10. 附录

### 参考资源

- [DDD 领域驱动设计](https://www.domainlanguage.com/ddd/)
- [C4 Model 文档化](https://c4model.com/)
- [ADR 架构决策记录](https://adr.github.io/)
