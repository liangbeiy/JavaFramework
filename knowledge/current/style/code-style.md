# Code Style Guide

## Java Code Style

### Formatting

- 缩进：4 空格，禁止使用 Tab
- 行宽：最大 120 字符
- 文件编码：UTF-8
- 换行符：LF (\n)

### Naming Conventions

| Element | Case | Example |
|---------|------|---------|
| Package | all lower, no underscores | `com.cxuy.framework.eventbus` |
| Class | UpperCamelCase | `LocalEventBus` |
| Interface | UpperCamelCase | `EventBus` |
| Method | lowerCamelCase | `subscribe()` |
| Variable | lowerCamelCase | `eventBus` |
| Constant | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| Type Parameter | Upper single char | `T`, `E` |

### Code Organization

```java
package com.cxuy.framework.example;

import java.util.List;
import java.util.Map;

/**
 * 类注释使用 Javadoc 格式
 */
public class ExampleClass {
    // 1. 静态常量
    private static final int MAX_SIZE = 100;

    // 2. 实例变量
    private String name;

    // 3. 构造方法
    public ExampleClass(String name) {
        this.name = name;
    }

    // 4. 公共方法
    public void doSomething() {
        // ...
    }

    // 5. 私有方法
    private void helper() {
        // ...
    }

    // 6. 内部类
    class InnerClass {
        // ...
    }
}
```

### Annotations

- @Subscribe 注解用于事件订阅方法
- 注解单独一行，放在方法/类上方

### Comments

- 公共 API 使用 Javadoc `/** */`
- 实现说明使用 `//` 单行注释
- 禁止留下 `// TODO` 除非已关联 issue

### Exception Handling

- 使用 try-catch 处理受检异常
- 自定义异常继承 RuntimeException
- 禁止空 catch 块

### Imports

- 禁止使用通配符导入 `import java.util.*;`
- 导入顺序：java → javax → third-party → project
