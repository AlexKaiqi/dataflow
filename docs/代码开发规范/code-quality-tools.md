# 代码质量检查工具使用指南

本项目集成了多种代码质量检查工具，确保代码的质量、风格一致性和安全性。

## 工具清单

### 1. Spotless - 代码格式化
- **用途**: 自动格式化 Java 代码，统一代码风格
- **标准**: Google Java Format (AOSP 风格，4空格缩进)
- **配置**: `build.gradle` 中的 `spotless` 配置块

### 2. Checkstyle - 代码风格检查
- **用途**: 检查代码风格是否符合规范
- **标准**: 基于 Google Java Style Guide 定制
- **配置**: `config/checkstyle/checkstyle.xml`

### 3. PMD - 代码质量检查
- **用途**: 检测常见的编程缺陷（未使用的变量、空 catch 块、过度复杂的代码等）
- **配置**: `config/pmd/ruleset.xml`

### 4. SpotBugs - 静态代码分析
- **用途**: 查找潜在的 bug 和安全漏洞
- **插件**: 集成 FindSecBugs 插件增强安全检查
- **配置**: `build.gradle` 中的 `spotbugs` 配置块

### 5. OWASP Dependency Check - 依赖安全扫描
- **用途**: 扫描第三方依赖的已知安全漏洞 (CVE)
- **阈值**: CVSS 评分 >= 7 时构建失败
- **配置**: `config/dependency-check/suppressions.xml`

## 常用命令

### 代码格式化

```bash
# 检查代码格式（不修改文件）
gradle spotlessCheck

# 自动格式化代码
gradle spotlessApply

# 或使用快捷命令
gradle formatCode
```

### 代码质量检查

```bash
# 运行所有代码质量检查
gradle codeQuality

# 单独运行各项检查
gradle checkstyleMain      # Checkstyle 检查
gradle pmdMain              # PMD 检查
gradle spotbugsMain         # SpotBugs 检查
```

### 安全漏洞扫描

```bash
# 扫描依赖安全漏洞
gradle dependencyCheckAnalyze

# 查看报告（生成在 build/reports/dependency-check-report.html）
open build/reports/dependency-check-report.html
```

### 完整验证

```bash
# 运行测试 + 所有质量检查 + 安全扫描
gradle verify

# 完整构建（自动包含代码质量检查）
gradle build
```

## 查看报告

所有检查报告都生成在 `build/reports/` 目录下：

```
build/reports/
├── checkstyle/
│   ├── main.html       # Checkstyle HTML 报告
│   └── main.xml        # Checkstyle XML 报告
├── pmd/
│   ├── main.html       # PMD HTML 报告
│   └── main.xml        # PMD XML 报告
├── spotbugs/
│   ├── main.html       # SpotBugs HTML 报告
│   └── main.xml        # SpotBugs XML 报告
└── dependency-check-report.html  # OWASP 依赖检查报告
```

## CI/CD 集成

在持续集成流程中，建议执行以下步骤：

```yaml
# 示例: GitHub Actions
- name: Code Quality Check
  run: ./gradlew codeQuality

- name: Run Tests
  run: ./gradlew test

- name: Security Scan
  run: ./gradlew dependencyCheckAnalyze

- name: Build
  run: ./gradlew build
```

## 配置自定义规则

### 修改 Checkstyle 规则

编辑 `config/checkstyle/checkstyle.xml`，例如修改行长度限制：

```xml
<module name="LineLength">
    <property name="max" value="150"/>  <!-- 默认 120 -->
</module>
```

### 修改 PMD 规则

编辑 `config/pmd/ruleset.xml`，例如排除某个规则：

```xml
<rule ref="category/java/bestpractices.xml">
    <exclude name="UnusedPrivateMethod"/>
</rule>
```

### 抑制特定漏洞警告

编辑 `config/dependency-check/suppressions.xml`，例如抑制测试依赖的漏洞：

```xml
<suppress>
    <notes><![CDATA[
    H2 仅用于测试环境
    ]]></notes>
    <gav regex="true">^com\.h2database:h2:.*$</gav>
</suppress>
```

## 开发工作流建议

### 提交代码前

```bash
# 1. 格式化代码
gradle formatCode

# 2. 运行质量检查
gradle codeQuality

# 3. 运行测试
gradle test

# 4. 提交代码
git add .
git commit -m "your message"
```

### IDE 集成

#### IntelliJ IDEA

1. **安装插件**:
   - CheckStyle-IDEA
   - PMDPlugin
   - SpotBugs

2. **导入配置**:
   - Settings → Editor → Code Style → Java → Import Scheme
   - 选择 `config/checkstyle/checkstyle.xml`

3. **启用自动格式化**:
   - Settings → Editor → Code Style → Java
   - 配置为使用 Google Java Style

#### VS Code

1. **安装扩展**:
   - Checkstyle for Java
   - SonarLint

2. **配置 settings.json**:
```json
{
    "java.checkstyle.configuration": "${workspaceFolder}/config/checkstyle/checkstyle.xml"
}
```

## 常见问题

### Q: 代码格式化失败怎么办？
A: 运行 `gradle spotlessApply` 自动修复大部分格式问题。

### Q: Checkstyle 报告太多警告？
A: 可以临时设置 `ignoreFailures = true`，逐步修复问题。

### Q: 依赖扫描报告误报怎么办？
A: 在 `config/dependency-check/suppressions.xml` 中添加抑制规则。

### Q: 如何跳过某个检查？
A: 使用 Gradle 参数：
```bash
gradle build -x checkstyleMain -x pmdMain
```

### Q: 构建时间太长？
A: 可以将安全扫描从日常构建中移除，只在 CI 中运行：
```bash
# 快速构建（跳过安全扫描）
gradle build -x dependencyCheckAnalyze
```

## 规则更新

定期更新工具版本以获取最新的规则和漏洞数据库：

```gradle
// build.gradle
checkstyle {
    toolVersion = '10.18.2'  // 更新版本号
}

pmd {
    toolVersion = '7.7.0'    // 更新版本号
}

spotbugs {
    toolVersion = '4.8.6'    // 更新版本号
}
```

## 参考资源

- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [Checkstyle Documentation](https://checkstyle.org/)
- [PMD Rules](https://pmd.github.io/latest/pmd_rules_java.html)
- [SpotBugs Documentation](https://spotbugs.github.io/)
- [OWASP Dependency Check](https://owasp.org/www-project-dependency-check/)
