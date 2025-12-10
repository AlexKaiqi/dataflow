# 代码质量检查配置文件说明

本目录包含项目使用的代码质量检查工具的配置文件。所有配置均基于业界标准。

## 📁 目录结构

```
config/
├── checkstyle/
│   ├── google_checks.xml       # ✅ Google 官方标准（推荐使用）
│   ├── sun_checks.xml          # ✅ Sun 官方标准（备选）
│   └── checkstyle.xml          # ⚠️ 旧的自定义配置（已废弃）
├── pmd/
│   └── ruleset.xml             # PMD 规则集（基于官方规则）
└── dependency-check/
    └── suppressions.xml        # OWASP 漏洞抑制规则
```

## 🎯 Checkstyle 配置

### 当前使用：Google Java Style（官方标准）

**文件**：`checkstyle/google_checks.xml`

**来源**：[Checkstyle 官方 GitHub](https://github.com/checkstyle/checkstyle/blob/master/src/main/resources/google_checks.xml)

**特点**：
- ✅ Google 官方维护
- ✅ 2空格缩进
- ✅ 严格的命名规范
- ✅ 完整的文档要求

**下载/更新命令**：
```bash
curl -o config/checkstyle/google_checks.xml \
  https://raw.githubusercontent.com/checkstyle/checkstyle/master/src/main/resources/google_checks.xml
```

### 备选：Sun Java Style（传统标准）

**文件**：`checkstyle/sun_checks.xml`

**来源**：[Checkstyle 官方 GitHub](https://github.com/checkstyle/checkstyle/blob/master/src/main/resources/sun_checks.xml)

**特点**：
- ✅ Sun/Oracle 官方标准
- ✅ 4空格缩进
- ✅ 相对宽松的规则

**下载/更新命令**：
```bash
curl -o config/checkstyle/sun_checks.xml \
  https://raw.githubusercontent.com/checkstyle/checkstyle/master/src/main/resources/sun_checks.xml
```

### 切换配置

在 `build.gradle` 中修改：

```gradle
checkstyle {
    // 使用 Google 风格（当前）
    configFile = file("${rootDir}/config/checkstyle/google_checks.xml")
    
    // 或使用 Sun 风格
    // configFile = file("${rootDir}/config/checkstyle/sun_checks.xml")
}
```

## 📋 PMD 配置

**文件**：`pmd/ruleset.xml`

**来源**：基于 [PMD 官方规则集](https://docs.pmd-code.org/latest/pmd_rules_java.html)

**规则集**：
- `category/java/bestpractices.xml` - 最佳实践
- `category/java/codestyle.xml` - 代码风格
- `category/java/design.xml` - 设计原则
- `category/java/documentation.xml` - 文档
- `category/java/errorprone.xml` - 错误倾向
- `category/java/multithreading.xml` - 多线程
- `category/java/performance.xml` - 性能
- `category/java/security.xml` - 安全

**自定义说明**：

当前配置基于 PMD 官方规则集，并针对项目特点做了以下调整：

1. **排除了过于严格的规则**（如强制注释）
2. **放宽了复杂度限制**（适应业务逻辑）
3. **兼容 Lombok**（排除与 Lombok 冲突的规则）
4. **兼容测试代码**（允许测试中的 System.out.println）

**参考资源**：
- [PMD Java Rules](https://docs.pmd-code.org/latest/pmd_rules_java.html)
- [PMD Rule Sets](https://github.com/pmd/pmd/tree/master/pmd-java/src/main/resources/category/java)

## 🛡️ OWASP Dependency Check 配置

**文件**：`dependency-check/suppressions.xml`

**用途**：抑制误报或已知不影响项目的漏洞

**格式示例**：

```xml
<suppress>
   <notes><![CDATA[
   CVE-2021-12345 不影响我们的使用场景
   ]]></notes>
   <cve>CVE-2021-12345</cve>
</suppress>
```

**参考资源**：
- [OWASP Dependency Check](https://jeremylong.github.io/DependencyCheck/)
- [Suppression File](https://jeremylong.github.io/DependencyCheck/general/suppression.html)

## 🔄 配置更新策略

### 自动更新（推荐）

定期运行以下命令更新到最新的官方配置：

```bash
# 更新 Checkstyle 配置
curl -o config/checkstyle/google_checks.xml \
  https://raw.githubusercontent.com/checkstyle/checkstyle/master/src/main/resources/google_checks.xml

curl -o config/checkstyle/sun_checks.xml \
  https://raw.githubusercontent.com/checkstyle/checkstyle/master/src/main/resources/sun_checks.xml
```

### 版本锁定

当前配置对应的工具版本：

```gradle
checkstyle: 10.18.2
pmd: 7.7.0
spotbugs: 4.8.6
dependency-check: 11.1.1
```

## 📚 参考资源

### 官方文档

- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [Checkstyle](https://checkstyle.org/)
- [PMD](https://pmd.github.io/)
- [SpotBugs](https://spotbugs.github.io/)
- [OWASP Dependency Check](https://owasp.org/www-project-dependency-check/)

### 其他标准

- [阿里巴巴 Java 开发手册](https://github.com/alibaba/p3c)
- [Spring Framework 代码规范](https://github.com/spring-projects/spring-framework/wiki/Code-Style)
- [Airbnb Java Style Guide](https://github.com/airbnb/javascript)

## ❓ 常见问题

### Q: 为什么使用 Google Style 而不是 Sun Style？

**A**: Google Style 更现代，规则更严格，有助于提高代码质量。但如果团队习惯 Sun Style（4空格），可以切换。

### Q: 如何定制规则？

**A**: 
1. **不推荐修改官方配置文件**（会失去更新能力）
2. **推荐方式**：在 `build.gradle` 中排除特定规则
3. **或者**：复制官方配置，重命名后修改

### Q: 配置文件冲突怎么办？

**A**: 
- Spotless 和 Checkstyle 可能有不同的格式要求
- 建议以 Spotless (Google Java Format) 为准
- Checkstyle 设置 `ignoreFailures = true` 作为警告

### Q: 如何查看规则详情？

**A**: 
- 打开对应的 XML 文件
- 查看官方文档中的规则说明
- 使用 IDE 插件查看规则描述
