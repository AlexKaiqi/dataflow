# 代码质量检查工具使用指南

本项目集成了多种代码质量检查工具，确保代码的质量、风格一致性和安全性。所有工具都使用**业界标准配置**。

## 📋 目录

- [快速开始](#快速开始)
- [工具清单](#工具清单)
- [常用命令](#常用命令)
- [查看报告](#查看报告)
- [配置文件](#配置文件)
- [开发工作流](#开发工作流)
- [IDE 集成](#ide-集成)
- [自定义配置](#自定义配置)
- [CI/CD 集成](#cicd-集成)
- [常见问题](#常见问题)
- [参考资源](#参考资源)

## 快速开始

### 快速命令

```bash
# 1. 格式化代码（提交前必做）
./gradlew formatCode

# 2. 运行代码质量检查
./gradlew codeQuality

# 3. 完整验证（测试 + 质量检查 + 安全扫描）
./gradlew fullVerify

# 4. 构建项目（自动包含代码质量检查）
./gradlew build
```

### 提交代码前检查清单

- [ ] 运行 `./gradlew formatCode` 格式化代码
- [ ] 运行 `./gradlew test` 确保测试通过
- [ ] 运行 `./gradlew codeQuality` 检查代码质量
- [ ] 查看并修复报告中的问题

## 工具清单

| 工具                 | 用途         | 标准来源               | 报告位置                                       |
| -------------------- | ------------ | ---------------------- | ---------------------------------------------- |
| **Spotless**   | 代码格式化   | Google Java Format     | -                                              |
| **Checkstyle** | 代码风格检查 | Google 官方 ✅         | `build/reports/checkstyle/main.html`         |
| **PMD**        | 代码质量检查 | PMD 官方 Quickstart ✅ | `build/reports/pmd/main.html`                |
| **SpotBugs**   | Bug 检测     | SpotBugs + FindSecBugs | `build/reports/spotbugs/main.html`           |
| **OWASP**      | 安全漏洞扫描 | OWASP 官方             | `build/reports/dependency-check-report.html` |

### 1. Spotless - 代码格式化

- **用途**: 自动格式化 Java 代码，统一代码风格
- **标准**: Google Java Format (AOSP 风格，4空格缩进)
- **配置**: `build.gradle` 中的 `spotless` 配置块

### 2. Checkstyle - 代码风格检查

- **用途**: 检查代码风格是否符合规范
- **标准**: **Google Java Style (官方标准配置)** ✅
- **版本**: Checkstyle 10.20.0
- **配置**: `config/checkstyle/google_checks.xml`
- **来源**: [Google Checkstyle](https://github.com/checkstyle/checkstyle/blob/checkstyle-10.20.0/src/main/resources/google_checks.xml)
- **备选**: `sun_checks.xml` (传统标准)

### 3. PMD - 代码质量检查

- **用途**: 检测常见的编程缺陷（未使用的变量、空 catch 块、过度复杂的代码等）
- **标准**: **PMD 官方 Quickstart 规则集** ✅
- **配置**: `config/pmd/quickstart.xml` (PMD 官方推荐)
- **来源**: [PMD Quickstart](https://github.com/pmd/pmd/blob/master/pmd-java/src/main/resources/rulesets/java/quickstart.xml)
- **备选**: 自定义规则集（可根据项目需要创建）

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
gradle fullVerify

# 完整构建（自动包含代码质量检查）
gradle build
```

## 查看报告

所有检查报告都生成在 `build/reports/` 目录下：

```text
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

## 配置文件

- `config/checkstyle/google_checks.xml` - **Google 官方 Checkstyle 规则** ✅
- `config/checkstyle/sun_checks.xml` - Sun 官方标准（备选）
- `config/pmd/quickstart.xml` - **PMD 官方 Quickstart 规则集** ✅
- `config/dependency-check/suppressions.xml` - OWASP 漏洞抑制规则
- `.editorconfig` - 编辑器统一配置

### 更新配置文件

所有配置都来自官方仓库，可以使用以下命令更新：

```bash
# 更新 Checkstyle Google 配置
curl -o config/checkstyle/google_checks.xml \
  https://raw.githubusercontent.com/checkstyle/checkstyle/checkstyle-10.20.0/src/main/resources/google_checks.xml

# 更新 PMD Quickstart 配置
curl -o config/pmd/quickstart.xml \
  https://raw.githubusercontent.com/pmd/pmd/master/pmd-java/src/main/resources/rulesets/java/quickstart.xml

# 更新 Sun Checkstyle（备选）
curl -o config/checkstyle/sun_checks.xml \
  https://raw.githubusercontent.com/checkstyle/checkstyle/master/src/main/resources/sun_checks.xml
```

## 开发工作流

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

## IDE 集成

### IntelliJ IDEA

1. **安装插件**:

   - CheckStyle-IDEA
   - PMDPlugin
   - SpotBugs
2. **导入配置**:

   - Settings → Editor → Code Style → Java → Import Scheme
   - 选择 `config/checkstyle/google_checks.xml`
3. **启用自动格式化**:

   - Settings → Editor → Code Style → Java
   - 配置为使用 Google Java Style

### VS Code

1. **安装扩展**:

   - Checkstyle for Java
   - SonarLint
2. **配置 settings.json**:

```json
{
    "java.checkstyle.configuration": "${workspaceFolder}/config/checkstyle/google_checks.xml"
}
```

## 自定义配置

### 修改 Checkstyle 规则

⚠️ **不推荐直接修改官方配置文件**（会失去更新能力）

如需调整，在 `build.gradle` 中排除特定规则或创建自定义配置文件。

### 修改 PMD 规则

可以创建自定义 `ruleset.xml`：

```xml
<rule ref="category/java/bestpractices.xml">
    <exclude name="UnusedPrivateMethod"/>
</rule>
```

然后在 `build.gradle` 中切换：

```gradle
pmd {
    ruleSetFiles = files(
        rootProject.file('config/pmd/ruleset.xml')  // 使用自定义规则
    )
}
```

### 抑制特定漏洞警告

编辑 `config/dependency-check/suppressions.xml`：

```xml
<suppress>
    <notes><![CDATA[
    H2 仅用于测试环境
    ]]></notes>
    <gav regex="true">^com\.h2database:h2:.*$</gav>
</suppress>
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

## 常见问题

### Q: 格式化和风格检查有什么区别？

**A**: Spotless 会自动修复格式问题，Checkstyle 只报告风格问题不会修复。

### Q: 代码格式化失败怎么办？

**A**: 运行 `gradle spotlessApply` 自动修复大部分格式问题。

### Q: 如何查看详细的检查报告？

**A**: 打开 `build/reports/` 下对应的 HTML 文件。

### Q: Checkstyle 报告太多警告？

**A**: 当前配置为警告模式（`ignoreFailures = true`），不会中断构建。可以逐步修复问题。

### Q: 依赖扫描报告误报怎么办？

**A**: 在 `config/dependency-check/suppressions.xml` 中添加抑制规则。

### Q: 如何临时跳过某个检查？

**A**: 使用 `-x` 参数：

```bash
gradle build -x checkstyleMain -x pmdMain
```

### Q: 构建时间太长？

**A**: 可以将安全扫描从日常构建中移除，只在 CI 中运行：

```bash
# 快速构建（跳过安全扫描）
gradle build -x dependencyCheckAnalyze
```

### Q: 检查太严格怎么办？

**A**: 目前配置为警告模式（`ignoreFailures = true`），不会中断构建。待代码质量提升后可改为严格模式。

### Q: 如何切换到 Sun Checkstyle 标准？

**A**: 在 `build.gradle` 中修改：

```gradle
checkstyle {
    configFile = rootProject.file('config/checkstyle/sun_checks.xml')
}
```

## 规则更新

定期更新工具版本以获取最新的规则和漏洞数据库：

```gradle
// build.gradle
checkstyle {
    toolVersion = '10.20.0'  // 更新版本号
}

pmd {
    toolVersion = '7.7.0'    // 更新版本号
}

spotbugs {
    toolVersion = '4.8.6'    // 更新版本号
}
```

## 参考资源

### 官方文档

- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [Checkstyle Documentation](https://checkstyle.org/)
- [PMD Rules](https://pmd.github.io/latest/pmd_rules_java.html)
- [PMD Quickstart Guide](https://docs.pmd-code.org/latest/pmd_userdocs_quickstart.html)
- [SpotBugs Documentation](https://spotbugs.github.io/)
- [OWASP Dependency Check](https://owasp.org/www-project-dependency-check/)

### 配置文件来源

- [Google Checkstyle Config](https://github.com/checkstyle/checkstyle/blob/checkstyle-10.20.0/src/main/resources/google_checks.xml)
- [PMD Quickstart Ruleset](https://github.com/pmd/pmd/blob/master/pmd-java/src/main/resources/rulesets/java/quickstart.xml)
- [PMD Official Rulesets](https://github.com/pmd/pmd/tree/master/pmd-java/src/main/resources/rulesets/java)

### 其他标准参考

- [阿里巴巴 Java 开发手册](https://github.com/alibaba/p3c)
- [Spring Framework 代码规范](https://github.com/spring-projects/spring-framework/wiki/Code-Style)

---

💡 **提示**: 所有配置都使用业界标准，无需手动编写规则！
