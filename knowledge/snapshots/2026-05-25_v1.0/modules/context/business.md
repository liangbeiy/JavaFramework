# Context 模块 业务流程

## FrameworkContext 生命周期流程

```
┌─────────────────────────────────────────────────────────────┐
│              FrameworkContext 生命周期                          │
└─────────────────────────────────────────────────────────────┘

  创建                     启动                        销毁
     │                        │                          │
     ▼                        ▼                          ▼
┌─────────┐            ┌──────────────┐          ┌──────────┐
│new      │            │create()       │          │destroy() │
│Framework│            └──────┬───────┘          └────┬─────┘
│Context()│                   │                        │
└────┬────┘                   ▼                        ▼
     │                 ┌──────────────┐        ┌──────────────┐
     │                 │setState(     │        │setState(     │
     │                 │ WILL_CREATE) │        │ WILL_DESTROY)│
     │                 └──────┬───────┘        └──────┬───────┘
     │                        │                        │
     │                        ▼                        ▼
     │                 ┌──────────────┐        ┌──────────────┐
     │                 │onCreate()    │        │onDestroy()   │
     │                 │(用户重写)    │        │(用户重写)    │
     │                 └──────┬───────┘        └──────┬───────┘
     │                        │                        │
     │                        ▼                        ▼
     │                 ┌──────────────┐        ┌──────────────┐
     │                 │setState(     │        │setState(     │
     │                 │ DID_CREATE)  │        │ DID_DESTROY) │
     │                 └──────┬───────┘        └──────┬───────┘
     │                        │                        │
     └────────────────────────┴────────────────────────┘
                              │
                              ▼
                     ┌──────────────────┐
                     │通知所有           │
                     │LifecycleObserver  │
                     └──────────────────┘
```

### 生命周期状态转换

```
INITED → WILL_CREATE → DID_CREATE → WILL_DESTROY → DID_DESTROY
```

### 生命周期详解

1. **INITED**：构造 `FrameworkContext` 时自动设置
2. **WILL_CREATE**：调用 `create()` 时，在 `onCreate()` 之前设置
3. **DID_CREATE**：`onCreate()` 执行完毕后设置
4. **WILL_DESTROY**：调用 `destroy()` 时，在 `onDestroy()` 之前设置
5. **DID_DESTROY**：`onDestroy()` 执行完毕后设置

### 观察者通知机制

每次状态变化（`setState()`）时：
1. 更新内部 `state` 变量（同步锁保护）
2. 遍历 `observers` 集合，调用每个 `LifecycleObserver.lifecycleOnChanged(this, state)`

## BuildConfig 初始化流程

```
JVM 启动
    │
    ▼
BuildConfig.INSTANCE 首次访问
    │
    ▼
静态字段初始化 → 构造 BuildConfig()
    │
    ├── System.getProperty("build.type") → debug/release
    └── System.getProperty("build.version") → versionCode (int)
```

## Context 获取 FrameworkContext 流程

```java
public FrameworkContext getFrameworkContext() {
    if(this instanceof FrameworkContext) {
        return (FrameworkContext) this;  // 自身就是 FrameworkContext
    }
    return getFrameworkContextInternal();  // 委托子类实现
}
```

## 边界条件和异常处理

| 场景 | 处理方式 |
|------|---------|
| root 为 null | 构造时不检查，运行时可能 NPE |
| getClassLoader 失败 | 降级：线程上下文 → 当前类 → 系统类加载器 |
| 重复调用 create() | 无保护，可能重复触发生命周期 |
| 重复调用 destroy() | 无保护，可能重复触发生命周期 |
| observer 为 null | add/remove 时直接返回，不操作 |
| observer 重复添加 | 使用 HashSet，自动去重 |
| BuildConfig 版本号格式错误 | 捕获 NumberFormatException，versionCode=0 |
| ProviderManager 创建返回 null | createProviderManagerInternal() 返回默认空 ProviderManager |
