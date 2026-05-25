# Util 模块 API 文档

## 对外暴露接口

### TextUtils

```java
public final class TextUtils {
    public static boolean isEmpty(CharSequence text);
    public static boolean equals(CharSequence t1, CharSequence t2);
}
```

### JsonUtil

```java
public final class JsonUtil {
    // 序列化
    public static String toJson(Object object);
    
    // 反序列化
    public static <T> T fromJson(String json, Type type);
    public static <T> T fromJson(String json, Type rawType, Type... templateTypes);
    public static <T> T[] fromJsonToArray(String json, Class<T[]> clazz);
    public static <T> List<T> fromJsonToList(String jsonList, Class<T> elementClass);
    
    // 从 JSON 中获取值
    public static <T> T getValue(String jsonStr, String key, Type type);
    public static <T> T getValue(String jsonStr, String key, T defaultValue, Type rawType, Type... templateTypes);
    
    // 获取 JSON 中的字符串值
    public static String optString(String jsonStr, String key);
    public static String optString(String jsonStr, String key, String defaultValue);
    
    // LocalDateTime 类型适配器（内部类）
    public static class LocalDateTimeTypeAdapter 
        implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> { }
}
```

### Logger

```java
public final class Logger {
    // 日志级别枚举
    public enum Level { VERBOSE(0), DEBUG(1), INFO(2), WARN(3), ERROR(4), ASSERT(5) }
    
    // 处理器接口
    @FunctionalInterface
    public interface Handler {
        void handle(Logger.Level level, String tag, String message, Throwable throwable);
    }
    
    // 打印处理器
    public static class PrintHandler implements Handler {
        public void print(String log);
        public static String generateLog(Level level, String tag, String message, Throwable throwable);
    }
    
    // 配置方法
    public static void addHandler(Handler handler);
    public static void removeHandler(Handler handler);
    public static void setLevel(Level targetLevel);
    public static void setPrintHandle(Handler printer);
    
    // 日志输出方法（无异常）
    public static void v(String tag, String message);
    public static void d(String tag, String message);
    public static void i(String tag, String message);
    public static void w(String tag, String message);
    public static void e(String tag, String message);
    public static void a(String tag, String message);
    
    // 日志输出方法（带异常）
    public static void v(String tag, String message, Throwable throwable);
    public static void d(String tag, String message, Throwable throwable);
    public static void i(String tag, String message, Throwable throwable);
    public static void w(String tag, String message, Throwable throwable);
    public static void e(String tag, String message, Throwable throwable);
    public static void a(String tag, String message, Throwable throwable);
}
```

### TimeUtil

```java
public final class TimeUtil {
    public static final String FORMAT_ISO = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    public static final String FORMAT_COMMON = "yyyy-MM-dd HH:mm:ss.SSS";
    public static final String FORMAT_SIMPLE = "yyyy/MM/dd HH:mm:ss";
    public static final String FORMAT_DATETIME_SECOND = "yyyy-MM-dd HH:mm:ss";
    public static final String FORMAT_DATETIME = "yyyy-MM-dd HH:mm";
    public static final String FORMAT_DATE = "yyyy-MM-dd";
    
    public static String iso();
    public static String common();
    public static String dateTime();
    public static String format(String format);
    public static String format(long millis, String format);
    public static long now();
}
```

### CachePool

```java
public final class CachePool<K, M> {
    // 回调接口
    @FunctionalInterface
    public static interface CachePoolCheckCallback<K, M> {
        void callback(CachePool<K, M> pool, K key, M model);
    }
    
    @FunctionalInterface
    public static interface ItemHasDeleted<K, M> {
        void hasRemoved(CachePool<K, M> pool, K key, M model);
    }
    
    public CachePool();  // 构造，初始化双向链表
    
    public void put(K key, M model);
    public void remove(K key);
    public void remove(K key, ItemHasDeleted<K, M> listener);
    public void get(K key, CachePoolCheckCallback<K, M> callback);
    
    public void addRemoveListener(ItemHasDeleted<K, M> listener);
    public void removeDeleteListener(ItemHasDeleted<K, M> listener);
}
```

## 组件注册表

| 组件 | 访问方式 | 说明 |
|------|---------|------|
| Logger | 静态方法 | 全局日志系统，默认输出到控制台和文件 |
| JsonUtil | 静态方法 | 内部 Gson 单例 |
| TextUtils | 静态方法 | 无状态工具类 |
| TimeUtil | 静态方法 | 无状态工具类 |
| CachePool | new 实例 | 泛型类，每个实例独立 |

## 内部 API

### Logger 内部初始化

```java
private static final class Holder {
    public static final Logger INSTANCE;
    static {
        INSTANCE = new Logger();
        Logger.addHandler(INSTANCE.printer);  // 控制台打印
        Logger.addHandler((level, tag, message, throwable) -> {
            String log = PrintHandler.generateLog(level, tag, message, throwable);
            FileManager.getInstance().append("./logger/logcat.log", log);
        });
    }
}
```
