# 代码质量检查 - 快速开始

## 快速命令

```bash
# 1. 格式化代码（提交前必做）
gradle spotlessApply

# 2. 运行代码质量检查
gradle codeQuality

# 3. 完整验证（测试 + 质量检查 + 安全扫描）
gradle fullVerify

# 4. 构建项目（自动包含代码质量检查）
gradle build
```

## 工具说明

| 工具 | 用途 | 报告位置 |
|------|------|---------|
| **Spotless** | 代码格式化 | - |
| **Checkstyle** | 代码风格检查 | `build/reports/checkstyle/main.html` |
| **PMD** | 代码质量检查 | `build/reports/pmd/main.html` |
| **SpotBugs** | Bug 检测 | `build/reports/spotbugs/main.html` |
| **OWASP** | 安全漏洞扫描 | `build/reports/dependency-check-report.html` |

## 提交代码前检查清单

- [ ] 运行 `gradle spotlessApply` 格式化代码
- [ ] 运行 `gradle test` 确保测试通过
- [ ] 运行 `gradle codeQuality` 检查代码质量
- [ ] 查看并修复报告中的问题

## 配置文件

- `config/checkstyle/checkstyle.xml` - Checkstyle 规则
- `config/pmd/ruleset.xml` - PMD 规则
- `config/dependency-check/suppressions.xml` - 漏洞抑制规则
- `.editorconfig` - 编辑器配置

## 常见问题

**Q: 格式化和风格检查有什么区别？**

A: Spotless 会自动修复格式问题，Checkstyle 只报告风格问题不会修复。

**Q: 如何查看详细的检查报告？**

A: 打开 `build/reports/` 下对应的 HTML 文件。

**Q: 如何临时跳过某个检查？**

A: 使用 `-x` 参数：`gradle build -x checkstyleMain -x pmdMain`

**Q: 检查太严格怎么办？**

A: 目前配置为警告模式（`ignoreFailures = true`），不会中断构建。待代码质量提升后可改为严格模式。

## 更多信息

详细文档请查看：`docs/代码开发规范/code-quality-tools.md`
