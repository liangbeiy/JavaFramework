# Context 模块 API 文档

## 对外暴露接口

### Context 抽象类

```java
package com.cxuy.framework.context;

public abstract class Context implements LifecycleOwner {
    @NonNull
    private final String rootDir;
    @NonNull
    private final ProviderManager providerManager;
    
    protected Context(String root);
    
    public abstract ClassLoader getClassLoader();
    protected abstract ProviderManager createProviderManager();
    protected abstract FrameworkContext getFrameworkContextInternal();
    
    public ProviderManager getProviderManager();
    public String getRootDir();
    public FrameworkContext getFrameworkContext();
}
```

### FrameworkContext 类

```java
public class FrameworkContext extends Context implements LifecycleOwner {
    public FrameworkContext(String root);
    
    // 生命周期方法
    public void onCreate();      // 默认空实现
    public void onDestroy();     // 设置状态为 WILL_DESTROY
    
    public void create();        // 触发 WILL_CREATE → onCreate() → DID_CREATE
    public void destroy();       // 触发 WILL_DESTROY → onDestroy() → DID_DESTROY
    
    // LifecycleOwner 接口实现
    public LifecycleState getState();
    public void setState(LifecycleState state);
    public void addObserver(LifecycleObserver observer);
    public void removeObserver(LifecycleObserver observer);
    
    @Override
    public ClassLoader getClassLoader();  // 线程上下文 → 当前类 → 系统类加载器
}
```

### BuildConfig 枚举

```java
public enum BuildConfig {
    INSTANCE;  // 单例
    
    public final Type buildType;  // DEBUG 或 RELEASE
    private final int versionCode;
    
    public enum Type {
        DEBUG("debug"),
        RELEASE("release");
        public final String rawName;
    }
}
```

## 组件注册表

| 组件 | 注册方式 | 生命周期 |
|------|---------|---------|
| FrameworkContext | `new FrameworkContext(root)` | 由使用者管理，需手动调用 create/destroy |
| BuildConfig | `BuildConfig.INSTANCE` 单例 | 进程级，随 JVM 存活 |
| ProviderManager | `createProviderManager()` 创建 | 随 Context 实例生命周期 |

## LifecycleOwner 接口（外部依赖）

```java
package com.cxuy.framework.lifecycle;

public interface LifecycleOwner {
    LifecycleState getState();
    void setState(LifecycleState state);
    void addObserver(LifecycleObserver observer);
    void removeObserver(LifecycleObserver observer);
}
```

## LifecycleState 枚举（外部依赖）

```java
public enum LifecycleState {
    ANY(-1), INITED(0), WILL_CREATE(1), DID_CREATE(2),
    WILL_START(3), DID_START(4), WILL_STOP(5), DID_STOP(6),
    WILL_DESTROY(7), DID_DESTROY(8);
    
    public final int rawValue;
}
```

## ProviderManager（外部依赖）

```java
package com.cxuy.framework.provider;

public class ProviderManager {
    // 管理各种 Provider 实例
    // 具体 API 由 provider 模块定义
}
```
