---
name: "build-diagnosis"
description: "构建失败诊断。分析 Gradle 构建错误日志，定位失败原因并提出修复建议。Invoke when build fails or user asks to diagnose build errors."
---

# build-diagnosis

构建失败诊断。分析 Gradle 构建错误日志，定位失败原因并提出修复建议。

## 触发条件

构建失败时使用。典型指令：
- "构建失败了，帮我看看"
- "编译报错，分析一下"
- Verifier 阶段构建失败时自动触发

---

## 输入

- **构建日志**：Gradle 输出或错误摘要
- **构建命令**（可选）：使用的 gradlew 命令
- **最近变更**（可选）：git diff 或变更文件列表

---

## 执行步骤

### 1. 错误分类

解析构建日志，将错误分类为：

| 类型 | 特征 | 典型原因 |
|------|------|---------|
| 编译错误 | `error:` 开头 | 语法错误/类型不匹配/缺少导入 |
| 链接错误 | `Unresolved reference` | 依赖缺失/模块间可见性 |
| 资源错误 | `AAPT:` / `resource not found` | XML 资源/ID 冲突 |
| 依赖错误 | `Could not resolve` | Maven 仓库/版本冲突 |
| Lint 错误 | `Lint found errors` | 代码质量问题 |
| NDK 错误 | `CMake`/`ndk-build` | Native 编译问题 |
| OOM | `GC overhead limit` | 内存不足 |

### 2. 错误定位

```
1. 提取错误文件路径和行号
2. Read 错误文件查看上下文
3. 如为依赖错误，检查 build.gradle 和 gradle.properties
4. 如为模块间错误，检查 module-deps.tsv 确认依赖关系
```

### 3. 变更关联

```
1. git diff --name-only 获取最近变更文件
2. 交叉比对变更文件与错误文件
3. 确定是本次变更引入的问题还是预先存在的问题
```

### 4. 修复建议

产出诊断报告：

```markdown
# 构建诊断报告

## 错误摘要
- 错误类型：<分类>
- 错误数量：<N>
- 核心错误：<第一个需要修复的错误>

## 错误分析
（逐个错误分析，标注文件:行号和原因）

## 修复建议
（按优先级排序的修复步骤）

## 依赖链分析（如适用）
（模块依赖导致的级联错误说明）
```

### 5. 架构约束检查

修复建议中自动检查是否涉及约束：
- OkHttp 版本变更（约束 #4）
- MMKV 版本变更（约束 #5）
- 依赖方向违反（约束 #1）
- Annotation 模块发布顺序（约束 #7）

---

## 常见构建问题速查

| 问题 | 快速修复 |
|------|---------|
| `Cannot resolve symbol` | 检查 import + 模块 dependencies |
| `Duplicate class` | 检查 exclude group 配置 |
| `minSdkVersion 28` 不兼容 | 检查新依赖的 minSdk 要求 |
| AGP 8.x 兼容性 | 检查 namespace 和 buildFeatures |
| NDK 25 编译失败 | 检查 CMakeLists.txt 和 ABI 过滤 |

---

## 约束

```
[MUST-DO] 每个错误必须标注文件路径和行号
[MUST-DO] 修复建议必须考虑架构约束
[MUST-DO] 区分本次变更引入 vs 预先存在的问题
[DISALLOW] 禁止建议升级锁定版本的依赖（OkHttp/MMKV）
```
