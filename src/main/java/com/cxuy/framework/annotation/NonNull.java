package com.cxuy.framework.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NonNull {
    String MSG = "this must be not null";
    // 可选的错误消息
    String message() default MSG;
}
