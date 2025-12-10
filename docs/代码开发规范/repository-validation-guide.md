# Repository 参数校验说明

## 概述

`TaskDefinitionRepository` 接口和实现类使用 JSR-303 Bean Validation 规范进行参数校验，确保所有方法调用的参数合法性。

## 校验注解

### 1. `@NotNull`
用于对象参数，确保参数不为 `null`。

**示例：**
```java
void save(@NotNull TaskDefinition taskDefinition);
```

### 2. `@NotBlank`  
用于字符串参数，确保参数不为 `null`、不为空字符串、不为纯空白字符。

**示例：**
```java
Optional<TaskDefinition> findByNamespaceAndName(
    @NotBlank String namespace, 
    @NotBlank String name
);
```

## 启用校验

在实现类上添加 `@Validated` 注解：

```java
@Repository
@Validated
public class TaskDefinitionRepositoryImpl implements TaskDefinitionRepository {
    // ...
}
```

## 异常处理

当参数校验失败时，会抛出 `jakarta.validation.ConstraintViolationException`：

```java
try {
    repository.save(null);
} catch (ConstraintViolationException e) {
    // 参数校验失败
    Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
    for (ConstraintViolation<?> violation : violations) {
        String message = violation.getMessage();
        String propertyPath = violation.getPropertyPath().toString();
        // 处理校验错误
    }
}
```

## 校验规则

### TaskDefinitionRepository 接口

| 方法 | 参数 | 校验规则 |
|------|------|----------|
| `save` | `taskDefinition` | `@NotNull` |
| `findByNamespaceAndName` | `namespace` | `@NotBlank` |
| | `name` | `@NotBlank` |
| `findByCompositeKey` | `namespace` | `@NotBlank` |
| | `name` | `@NotBlank` |
| | `version` | `@NotBlank` |
| `findByNamespace` | `namespace` | `@NotBlank` |
| `delete` | `namespace` | `@NotBlank` |
| | `name` | `@NotBlank` |
| `exists` | `namespace` | `@NotBlank` |
| | `name` | `@NotBlank` |
| `isVersionReferenced` | `namespace` | `@NotBlank` |
| | `name` | `@NotBlank` |
| | `version` | `@NotBlank` |

## 测试用例

已添加以下参数校验测试：

1. **testValidation_NullTaskDefinition** - 验证 null 任务定义会抛出异常
2. **testValidation_BlankNamespace** - 验证空白命名空间会抛出异常
3. **testValidation_BlankName** - 验证空白名称会抛出异常
4. **testValidation_BlankVersion** - 验证空白版本号会抛出异常
5. **testValidation_NullParameters** - 验证 null 参数会抛出异常

## 依赖配置

需要在 `build.gradle` 中添加：

```gradle
implementation 'org.springframework.boot:spring-boot-starter-validation'
```

## 最佳实践

1. **在接口层定义校验规则** - 保证契约的一致性
2. **使用 @Validated 启用校验** - 在实现类上添加注解
3. **统一异常处理** - 在上层（如 Service 或 Controller）统一捕获和处理 `ConstraintViolationException`
4. **完善的错误消息** - 可以通过 `@NotBlank(message = "命名空间不能为空")` 自定义错误消息
5. **测试覆盖** - 为所有校验场景编写测试用例

## 性能考虑

- 参数校验通过 AOP 实现，有轻微的性能开销
- 校验在方法调用时执行，失败时快速返回（fail-fast）
- 对于高频调用的方法，可以考虑在调用方提前校验以减少不必要的调用

## 与领域层校验的区别

| 层次 | 校验类型 | 目的 | 示例 |
|------|---------|------|------|
| Repository | 参数完整性校验 | 确保参数不为空/空白 | `@NotNull`, `@NotBlank` |
| Domain | 业务规则校验 | 确保业务逻辑正确 | `version.validate()`, `variable.validate()` |

两者互补，共同保证数据完整性和业务正确性。
