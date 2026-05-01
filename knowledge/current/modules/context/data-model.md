# Context 模块 数据模型

## 核心数据结构

### Context 内部状态

```java
public abstract class Context implements LifecycleOwner {
    @NonNull
    private final String rootDir;  // 根目录路径
    
    @NonNull
    private final ProviderManager providerManager;  // Provider 管理器
    
    // 子类需实现
    protected abstract FrameworkContext getFrameworkContextInternal();
}
```

### FrameworkContext 内部状态

```java
public class FrameworkContext extends Context {
    private final Object stateLock = new Object();
    private LifecycleState state;  // 当前生命周期状态
    
    private final Set<LifecycleObserver> observers;  // 观察者集合（线程安全）
    
    // 状态转换
    INITED → WILL_CREATE → DID_CREATE → WILL_DESTROY → DID_DESTROY
}
```

### BuildConfig 数据模型

```java
public enum BuildConfig {
    INSTANCE;  // 枚举单例
    
    public final Type buildType;    // 构建类型：DEBUG 或 RELEASE
    private final int versionCode;  // 构建版本号
    
    // 初始化：从系统属性读取
    // build.type → debug/release
    // build.version → int
}
```

### LifecycleState 枚举

```java
public enum LifecycleState {
    ANY(-1),         // 任意状态
    INITED(0),       // 已初始化
    WILL_CREATE(1),  // 即将创建
    DID_CREATE(2),   // 已创建
    WILL_START(3),   // 即将启动
    DID_START(4),    // 已启动
    WILL_STOP(5),    // 即将停止
    DID_STOP(6),     // 已停止
    WILL_DESTROY(7), // 即将销毁
    DID_DESTROY(8);  // 已销毁
    
    public final int rawValue;  // 状态原始值，用于比较
}
```

## 缓存策略

| 数据 | 缓存方式 | 说明 |
|------|---------|------|
| BuildConfig | 枚举单例 | JVM 级，首次访问时初始化 |
| FrameworkContext.state | volatile + synchronized | 状态变量，支持并发读写 |
| FrameworkContext.observers | synchronizedSet HashSet | 线程安全的观察者集合 |
| Context.providerManager | final 字段 | 构造时初始化，不可变 |
| Context.rootDir | final 字段 | 构造时初始化，不可变 |

## 数据流向

```
【FrameworkContext 创建流】
new FrameworkContext(root)
         ↓
   setState(INITED)
         ↓
   createProviderManager() → ProviderManager
         ↓
   observers = synchronizedSet(HashSet)

【生命周期状态流】
create() → setState(WILL_CREATE) → onCreate() → setState(DID_CREATE)
                                             ↓
                              notifyObservers(state)

destroy() → setState(WILL_DESTROY) → onDestroy() → setState(DID_DESTROY)
                                                ↓
                                 notifyObservers(state)

【BuildConfig 读取流】
System.getProperty("build.type") → "debug"/"release" → Type.DEBUG/RELEASE
System.getProperty("build.version") → "123" → parseInt → versionCode

【Context 获取 FrameworkContext 流】
context.getFrameworkContext()
         ↓
   instanceof FrameworkContext?
         ├── Y → return (FrameworkContext) context
         └── N → getFrameworkContextInternal() → 子类实现
```
