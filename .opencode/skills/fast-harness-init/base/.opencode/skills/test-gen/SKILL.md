---
name: "test-gen"
description: "为目标类/方法快速生成 JUnit 4 单元测试。Invoke when user asks to generate tests, 生成单测, or write unit tests."
---

# test-gen

为目标类/方法快速生成 JUnit 4 单元测试。可在流水线内（基于 `unit-task.md`）或流水线外（独立分析源码）使用。

## 触发条件

用户要求生成测试时使用。典型指令：
- "给这个类写单元测试"
- "生成测试用例"
- "补充测试覆盖"

---

## 使用模式

### 模式 A：流水线内使用

当存在活跃变更且 `.knowledge-work/<change>/unit-task.md` 已产出时：

```
1. 读取 unit-task.md，获取测试任务设计（测试类名、方法清单、Mock 策略、断言策略）
2. 严格按照 unit-task.md 的七要素生成测试代码
3. 不得自行变更 unit-task.md 中的设计（方法名、Mock 策略、断言策略等）
4. 发现设计问题时向 Orchestrator 报告阻塞
```

### 模式 B：流水线外独立使用

不存在活跃变更或 `unit-task.md` 时：

```
1. 自行分析目标源码，设计测试场景
2. 按本文件定义的覆盖要求和代码模板生成测试
3. 生成的测试独立完整，可直接编译运行
```

---

## 输入

- **目标文件/类/方法**：需要测试的代码位置
- **unit-task.md**（模式 A）：`.knowledge-work/<change>/unit-task.md` 提供完整测试设计
- **spec.md**（可选）：`.knowledge-work/<change>/spec.md` 提供行为规约

---

## 执行步骤

### 1. 目标代码分析

```
1. Read 目标文件完整内容
2. 识别 public 方法列表及其签名（入参、返回值、异常声明）
3. 通过 call-graph.tsv 分析调用关系（可选，复杂类推荐）
4. 识别外部依赖（需要 mock 的对象）
5. 识别单例、static 方法等特殊模式（影响 Mock 策略）
```

### 2. 测试场景设计

模式 A 时直接使用 `unit-task.md` 中的测试方法清单，跳过本步骤。

模式 B 时，为每个 public 方法**至少**覆盖以下三类场景：

| 场景类型 | 标记 | 说明 | 示例 |
|---------|------|------|------|
| 核心流程 | core | 正常路径，验证方法的主要功能 | 传入合法参数，返回预期结果 |
| 边界场景 | boundary | null、空集合、最大/最小值、边界条件 | 传入 null → 返回默认值或抛出 NPE |
| 意外输入 | unexpected | 非法参数、异常类型、并发冲突 | 传入负数 → 抛出 IllegalArgumentException |
| 回归场景 | regression | bugFix 时验证原有行为不被破坏 | **仅 bugFix 场景必需** |

```
[MUST-DO] 每个 public 方法至少 3 个测试方法（core + boundary + unexpected 各至少 1 个）
[MUST-DO] bugFix 场景额外增加 regression 测试
```

### 3. 测试代码生成

```
测试框架：JUnit 4（org.junit）
Mock 框架：Mockito（mockito-core + mockito-inline）
Runner：@RunWith(MockitoJUnitRunner.class)
测试文件位置：与源文件对应的 src/test/java/<包路径>/ 目录
命名规则：<ClassName>Test.java
```

测试方法命名：三段式 `<方法名>_<条件>_<预期结果>()`

```
命名示例：
  scheduleCleanup_configNull_doesNotSubmitTask()
  downloadFile_urlEmpty_throwsIllegalArgument()
  getGameInfo_normalInput_returnsCorrectInfo()
  parseConfig_maxIntValue_handlesOverflow()
```

```
[MUST-DO] 方法名不加 test_ 前缀（JUnit 4 的 @Test 注解已标识测试方法）
[MUST-DO] 三段之间用下划线分隔，每段内部用驼峰命名
```

代码风格严格遵循 `knowledge/current/style/code-style.md`。

### 4. 测试结构模板

#### 基本模板（无 static mock）

```java
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public final class XxxManagerTest {

    @Mock
    private DependencyA mMockDependencyA;

    @Mock
    private DependencyB mMockDependencyB;

    @InjectMocks
    private XxxManager mTarget;

    @Before
    public void setUp() {
        // 额外初始化（如有）
    }

    /**
     * 验证正常输入时返回预期结果。
     */
    @Test
    public void doSomething_normalInput_returnsExpected() {
        // Arrange
        when(mMockDependencyA.getData()).thenReturn("testData");

        // Act
        String result = mTarget.doSomething("input");

        // Assert
        assertEquals("expected", result);
        verify(mMockDependencyA).getData();
    }

    /**
     * 验证传入 null 时抛出 NullPointerException。
     */
    @Test(expected = NullPointerException.class)
    public void doSomething_nullInput_throwsNpe() {
        // Act
        mTarget.doSomething(null);
    }

    /**
     * 验证传入非法参数时抛出 IllegalArgumentException。
     */
    @Test(expected = IllegalArgumentException.class)
    public void doSomething_negativeValue_throwsIllegalArgument() {
        // Act
        mTarget.doSomething(-1);
    }
}
```

#### 含 MockedStatic 模板（需要 mock 静态方法时）

```java
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public final class YyyServiceTest {

    @Mock
    private DependencyA mMockDependencyA;

    private MockedStatic<StaticHelper> mMockedStaticHelper;

    private YyyService mTarget;

    @Before
    public void setUp() {
        mMockedStaticHelper = mockStatic(StaticHelper.class);
        mTarget = new YyyService(mMockDependencyA);
    }

    @After
    public void tearDown() {
        mMockedStaticHelper.close();
    }

    /**
     * 验证静态方法返回预期值时的正常流程。
     */
    @Test
    public void process_staticReturnsValid_completesSuccessfully() {
        // Arrange
        mMockedStaticHelper.when(StaticHelper::getValue).thenReturn("mocked");

        // Act
        boolean result = mTarget.process();

        // Assert
        assertTrue(result);
    }
}
```

#### 单例被测类模板（需要反射重置单例状态）

```java
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public final class SingletonManagerTest {

    @Before
    public void setUp() throws Exception {
        // 反射重置单例实例，确保测试间隔离
        Field instance = SingletonManager.class.getDeclaredField("sInstance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @After
    public void tearDown() throws Exception {
        Field instance = SingletonManager.class.getDeclaredField("sInstance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    /**
     * 验证首次调用 getInstance 返回非空实例。
     */
    @Test
    public void getInstance_firstCall_returnsNonNull() {
        // Act
        SingletonManager result = SingletonManager.getInstance();

        // Assert
        assertNotNull(result);
    }
}
```

### 5. 验证

```
1. 确认测试文件位于正确的 src/test/java/<包路径>/ 目录
2. 确认 import 路径正确（org.junit.Test 而非 org.junit.jupiter）
3. 确认使用 @RunWith(MockitoJUnitRunner.class) 而非 @ExtendWith
4. 确认 mock 对象覆盖所有外部依赖
5. 确认每个 public 方法至少覆盖 core + boundary + unexpected 三类场景
6. 确认 MockedStatic 在 @Before 中打开、@After 中关闭
7. 确认单例类在 @Before/@After 中通过反射重置字段状态
8. 确认测试方法命名遵循三段式（无 test_ 前缀）
```

---

## 覆盖度自检

生成完成后，以表格形式自检覆盖度：

```
| 被测方法        | core | boundary | unexpected | regression |
|----------------|------|----------|------------|------------|
| doSomething()  | ✓    | ✓        | ✓          | N/A        |
| process()      | ✓    | ✓        | ✓          | N/A        |
```

```
[MUST-DO] 每个 public 方法的 core / boundary / unexpected 列必须有 ✓
[MUST-DO] bugFix 场景下 regression 列必须有 ✓
[MUST-DO] 无覆盖时必须标注理由（如「纯 getter，无逻辑分支」）
```

---

## 约束

```
[MUST-DO] 测试框架必须使用 JUnit 4（org.junit），禁止使用 JUnit 5（org.junit.jupiter）
[MUST-DO] 使用 @RunWith(MockitoJUnitRunner.class)，禁止使用 @ExtendWith(MockitoExtension.class)
[MUST-DO] 测试类声明为 public final class
[MUST-DO] 生成的测试必须可编译（import 路径正确）
[MUST-DO] 每个 public 方法至少覆盖 core + boundary + unexpected 三类场景
[MUST-DO] Mock 不能 mock 被测类本身
[MUST-DO] 测试方法使用三段式命名（<方法名>_<条件>_<预期结果>），无 test_ 前缀
[MUST-DO] 每个测试方法包含 JavaDoc 注释，说明测试意图
[MUST-DO] 每个测试方法遵循 AAA 模式（Arrange → Act → Assert）
[MUST-DO] MockedStatic 必须在 @Before 中打开、@After 中关闭（防 mock 泄漏）
[MUST-DO] 单例被测类必须在 @Before/@After 中通过反射重置字段状态（防测试间污染）
[DISALLOW] 禁止生成空测试方法（仅有方法签名无断言）
[DISALLOW] 禁止使用 @Ignore 注解
[DISALLOW] 禁止使用 Thread.sleep 等硬编码延时
[DISALLOW] 禁止依赖外部网络、文件系统或其他不可控环境
```

---

## 测试依赖

项目使用以下测试依赖（JVM 单元测试 `src/test/`）：

```groovy
testImplementation 'junit:junit:4.13.2'
testImplementation 'org.mockito:mockito-core:5.x'
testImplementation 'org.mockito:mockito-inline:5.x'
```

```
[MUST-DO] 生成测试前确认目标模块 build.gradle 已包含上述依赖，缺少时提醒用户添加
[RECOMMEND] 需要 Android 组件测试（Context/Activity 等）时，优先尝试 Mockito mock 隔离；
            无法隔离时使用 Instrumented 测试（src/androidTest/），按 builder.md 规范编写
```
