package com.cxuy.server.annotation;

import com.cxuy.framework.constant.Constant;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Param {
    String value() default Constant.EMPTY_STR;
}
