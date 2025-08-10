package com.cxuy.framework.util;

import com.cxuy.framework.annotation.NonNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;

public final class Logger {

    @FunctionalInterface
    public interface Handler {
        void handle(Logger.Level level, String tag, String message, Throwable throwable); 
    }

    public enum Level {
        VERBOSE(0, "V"), 
        DEBUG(1, "D"), 
        INFO(2, "I"), 
        WARN(3, "W"), 
        ERROR(4, "E"), 
        ASSERT(5, "A");

        private final int rawValue; 
        private final String tag; 
        Level(int rawValue, String tag) {
            this.rawValue = rawValue; 
            this.tag = tag; 
        }
    }

    private static final class Holder {
        public static final Logger INSTANCE; 
        static {
            INSTANCE = new Logger(); 
            Logger.addHandler(INSTANCE.printer);
            Logger.addHandler((level, tag, message, throwable) -> {
                String log = PrintHandler.generateLog(level, tag, message, throwable);
                FileUtil.append("./logger/logcat.log", log);
            });
        }
    }

    private static final String LOGGER_GAP = "\t"; 

    private final Object levelLock = new Object(); 
    private Level level = Level.DEBUG;

    private final Object handlerLock = new Object(); 
    private final Set<Handler> handlers = new HashSet<>(); 

    private final Object printerLock = new Object(); 
    private Handler printer = new PrintHandler(); 

    public static void addHandler(Handler handler) {
        if(handler == null) {
            return; 
        }
        synchronized(Holder.INSTANCE.handlerLock) {
            if(Holder.INSTANCE.handlers.contains(handler)) {
                return; 
            }
            Holder.INSTANCE.handlers.add(handler); 
        }
    }

    public static void removeHandler(Handler handler) {
        if(handler == null) {
            return; 
        }
        synchronized(Holder.INSTANCE.handlerLock) {
            Holder.INSTANCE.handlers.remove(handler); 
        }
    }

    public static void setLevel(Level targetLevel) {
        Logger logger = Logger.Holder.INSTANCE; 
        synchronized(logger.levelLock) {
            if(logger.level != targetLevel) {
                logger.level = targetLevel; 
            }
        }
    }

    public static void setPrintHandle(Handler printer) {
        removeHandler(Holder.INSTANCE.printer);
        synchronized(Holder.INSTANCE.printerLock) {
            Holder.INSTANCE.printer = printer; 
        }
        if(printer == null) {
            return; 
        }
        addHandler(printer);
    }

    public static void v(String tag, String message) {
        v(tag, message, null);
    }

    public static void d(String tag, String message) {
        d(tag, message, null);
    }

    public static void i(String tag, String message) {
        i(tag, message, null);
    }

    public static void w(String tag, String message) {
        w(tag, message, null);
    }

    public static void e(String tag, String message) {
        e(tag, message, null);
    }

    public static void a(String tag, String message) {
        a(tag, message, null);
    }

    public static void v(String tag, String message, Throwable throwable) {
        log(Level.VERBOSE, tag, message, throwable);
    }

    public static void d(String tag, String message, Throwable throwable) {
        log(Level.DEBUG, tag, message, throwable);
    }

    public static void i(String tag, String message, Throwable throwable) {
        log(Level.INFO, tag, message, throwable);
    }

    public static void w(String tag, String message, Throwable throwable) {
        log(Level.WARN, tag, message, throwable);
    }

    public static void e(String tag, String message, Throwable throwable) {
        log(Level.ERROR, tag, message, throwable);
    }

    public static void a(String tag, String message, Throwable throwable) {
        log(Level.ASSERT, tag, message, throwable);
    }

    private static void log(@NonNull Level level, String tag, String message, Throwable throwable) {
        Logger logger = Logger.Holder.INSTANCE; 
        synchronized(logger.levelLock) {
            if(level.rawValue < logger.level.rawValue) {
                return; 
            }
        }
        synchronized(logger.handlerLock) {
            for(Handler handler : logger.handlers) {
                handler.handle(level, tag, message, throwable);
            }
        }
    }

    public static class PrintHandler implements Handler {
        private static final String DEFAULT_PUCKER = "    "; 
        @Override
        public void handle(Level level, String tag, String message, Throwable throwable) {
            String log = generateLog(level, tag, message, throwable);
            print(log);
        }

        public void print(String log) {
            System.out.print(log);
        }

        public static String generateLog(Level level, String tag, String message, Throwable throwable) {
            String log = TimeUtil.iso() + LOGGER_GAP + level.tag + LOGGER_GAP + tag + LOGGER_GAP + message + "\n";
            if(throwable != null) {
                String prefixTableString = LOGGER_GAP + LOGGER_GAP + LOGGER_GAP + LOGGER_GAP + LOGGER_GAP;
                log = log + prefixTableString + formatStackTrace(prefixTableString, throwable) + "\n";
            }
            return log;
        }

        /**
         * 手动拼接异常的堆栈跟踪信息为字符串，效果等同于 e.printStackTrace()
         */
        private static String formatStackTrace(String prefixString, Throwable throwable) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            printThrowable(throwable, prefixString, pw, new StringBuilder());
            return sw.toString();
        }

        private static void printThrowable(Throwable throwable, String prefix, PrintWriter pw, StringBuilder indention) {
            if (throwable == null) return;

            // 打印当前异常
            pw.println(indention + throwable.getClass().getName() + ": " + throwable.getMessage());

            // 打印堆栈跟踪元素
            StackTraceElement[] trace = throwable.getStackTrace();
            Throwable cause = throwable.getCause();

            if (cause != null) {
                // 计算与 cause 共享的堆栈帧数
                StackTraceElement[] causeTrace = cause.getStackTrace();
                int m = trace.length - 1;
                int n = causeTrace.length - 1;
                while (m >= 0 && n >= 0 && trace[m].equals(causeTrace[n])) {
                    m--;
                    n--;
                }
                int framesInCommon = trace.length - 1 - m;

                // 打印当前异常的堆栈（不包含共享部分）
                for (int i = 0; i <= m; i++) {
                    pw.println(prefix + indention + "at " + trace[i]);
                }
                pw.println(prefix + indention + "... " + framesInCommon + " more");

                // 递归打印 cause 异常
                String indentStr = indention.toString();
                indention.append("Caused by: ");
                printThrowable(cause, prefix, pw, indention);
                indention.setLength(indentStr.length()); // 恢复缩进
            } else {
                // 没有 cause 时打印完整堆栈
                for (StackTraceElement element : trace) {
                    pw.println(prefix + indention + "    at " + element);
                }
            }

            // 打印 suppressed 异常（Java 7+ try-with-resources 特性）
            Throwable[] suppressed = throwable.getSuppressed();
            if (suppressed.length > 0) {
                String indentStr = indention.toString();
                indention.append("Suppressed: ");
                for (Throwable sup : suppressed) {
                    // 恢复缩进，因为 printThrowable 会添加 "Caused by: "
                    indention.setLength(indentStr.length());
                    indention.append("Suppressed: ");
                    printThrowableAsCause(sup, prefix, pw, indention, throwable);
                }
                indention.setLength(indentStr.length()); // 恢复原始缩进
            }
        }

        private static void printThrowableAsCause(Throwable throwable, String prefix, PrintWriter pw, StringBuilder indention, Throwable causedBy) {
            if (throwable == null) return;

            // 打印异常信息
            pw.println(indention + throwable.getClass().getName() + ": " + throwable.getMessage());

            // 计算与 causedBy 共享的堆栈帧数
            StackTraceElement[] trace = throwable.getStackTrace();
            StackTraceElement[] causedByTrace = causedBy.getStackTrace();
            int m = trace.length - 1;
            int n = causedByTrace.length - 1;
            while (m >= 0 && n >= 0 && trace[m].equals(causedByTrace[n])) {
                m--;
                n--;
            }
            int framesInCommon = trace.length - 1 - m;

            // 打印堆栈（不包含共享部分）
            for (int i = 0; i <= m; i++) {
                pw.println(prefix + indention + DEFAULT_PUCKER + "at " + trace[i]);
            }
            pw.println(prefix + indention + DEFAULT_PUCKER + "... " + framesInCommon + " more");

            // 递归打印 cause 异常
            Throwable cause = throwable.getCause();
            if (cause != null) {
                String indentStr = indention.toString();
                indention.append("Caused by: ");
                printThrowableAsCause(cause, prefix, pw, indention, causedBy);
                indention.setLength(indentStr.length()); // 恢复缩进
            }
        }
    }
}

