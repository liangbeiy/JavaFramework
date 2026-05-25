# Context 模块概览

## 基本信息

| 属性 | 值 |
|------|-----|
| 模块类型 | 框架基础模块 |
| 包路径 | com.cxuy.framework.context |
| 业务域 | 框架上下文 / 生命周期管理 |
| 源文件数 | 3 |

## 模块定位

Context 模块是 JavaFramework 的上下文基础设施，提供框架运行时的环境信息和管理能力。`Context` 作为抽象基类，定义根目录、类加载器和 Provider 管理器，并持有 `FrameworkContext` 引用。`FrameworkContext` 继承自 Context，实现完整的生命周期管理（INITED → WILL_CREATE → DID_CREATE → WILL_DESTROY → DID_DESTROY）。

`BuildConfig` 作为全局配置单例，从系统属性读取构建类型（debug/release）和版本号，供框架各模块运行时查询。模块与 `LifecycleOwner` 接口深度集成，支持观察者模式监听生命周期状态变化。

## 直接依赖

```
com.cxuy.framework.context
├── com.cxuy.framework.annotation
│   ├── @NonNull
│   └── @Nullable
├── com.cxuy.framework.lifecycle
│   ├── LifecycleOwner（接口）
│   ├── LifecycleObserver
│   └── LifecycleState（枚举）
├── com.cxuy.framework.provider
│   └── ProviderManager
├── com.cxuy.framework.constant
│   └── Constant（PARAM_KEY_BUILD_TYPE 等）
└── com.cxuy.framework.util
    ├── Logger
    └── TextUtils
```

## 核心类列表

| 类/接口 | 职责 |
|---------|------|
| `Context` | 上下文抽象基类，定义根目录、ProviderManager、类加载器 |
| `FrameworkContext` | 框架上下文实现，提供完整生命周期管理 |
| `BuildConfig` | 构建配置单例，管理构建类型和版本号 |
| `LifecycleOwner` | 生命周期所有者接口（外部依赖） |
| `LifecycleObserver` | 生命周期观察者接口（外部依赖） |
| `LifecycleState` | 生命周期状态枚举（INITED/WILL_CREATE/DID_CREATE 等） |
| `ProviderManager` | Provider 管理器（外部依赖） |
