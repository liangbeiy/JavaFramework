/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.annotation.processor;

import com.cxuy.framework.annotation.NonNull;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.Set;

public class NonNullProcessor extends AbstractProcessor {

    private Messager messager; // 用于向编译器输出警告/错误
    private Types typeUtils;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        typeUtils = processingEnv.getTypeUtils();
    }


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // 遍历所有被@NonNull标记的元素
        for (Element element : roundEnv.getElementsAnnotatedWith(NonNull.class)) {
            checkNullability(element);
        }
        return true; // 表示该注解已被处理
    }

    private void checkNullability(Element element) {
        NonNull annotation = element.getAnnotation(NonNull.class);
        String message = annotation.message();

        // 1. 检查方法参数是否可能为null
        if (element.getKind() == ElementKind.PARAMETER) {
            checkParameterNull((VariableElement) element, message);
        }

        // 2. 检查方法返回值是否可能为null
        else if (element.getKind() == ElementKind.METHOD) {
            checkReturnValueNull((ExecutableElement) element, message);
        }

        // 3. 检查字段是否可能为null（如未初始化的非空字段）
        else if (element.getKind() == ElementKind.FIELD) {
            checkFieldNull((VariableElement) element, message);
        }
    }

    // 检查参数：如果调用方法时传入null，标黄警告
    private void checkParameterNull(VariableElement param, String message) {
        // 获取参数所在的方法
        ExecutableElement method = (ExecutableElement) param.getEnclosingElement();
        // 简单示例：检测参数类型是否为基本类型（基本类型不能为null，无需检查）
        if (param.asType().getKind().isPrimitive()) {
            return;
        }
        // 向编译器发送警告（IDE会标黄显示）
        messager.printMessage(Diagnostic.Kind.ERROR, "parameter " + param.getSimpleName() + "has marked not null" + message,
                param);
    }

    // 检查返回值：如果方法可能返回null，标黄警告
    private void checkReturnValueNull(ExecutableElement method, String message) {
        // 如果返回类型是void，无需检查
        if (method.getReturnType().getKind() == TypeKind.VOID) {
            return;
        }
        // 向编译器发送警告
        messager.printMessage(
                Diagnostic.Kind.WARNING,
                "方法'" + method.getSimpleName() + "'返回值不允许为null: " + message,
                method
        );
    }

    // 检查字段：如果非空字段未初始化，标黄警告
    private void checkFieldNull(VariableElement field, String message) {
        // 检查字段是否有初始值
        if (field.getConstantValue() == null && !isFinalAndInitialized(field)) {
            messager.printMessage(
                    Diagnostic.Kind.WARNING,
                    "字段'" + field.getSimpleName() + "'不允许为null: " + message,
                    field
            );
        }
    }

    // 判断final字段是否已初始化（简化逻辑）
    private boolean isFinalAndInitialized(VariableElement field) {
        return field.getModifiers().contains(Modifier.FINAL) && field.getConstantValue() != null;
    }
}
