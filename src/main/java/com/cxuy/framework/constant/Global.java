/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.constant;

public enum Global {
    DEFAULT("1.0.0", 10000000);

    /**
     * <h3>版本号</h3>
     * 版本名称：八位或九位，为一个整数，如: 200120230；
     * <p>
     * 以200120230，分隔为20 01 20 230，对应版本名称为20.1.20; <p>
     * 说明：<p>分隔为20 01 20 230 = A B C D<p>
     * 其中<p>
     * A 表示最高版本位为两位数，表示大版本，大版本不满两位时不补齐两位；<p>
     * B 表示中间版本位为两位数，表示大版本内一般性升级发版；<p>
     * C 表示最小版本位为两位数，表示升级版本的修复版本发版；<p>
     * D 表示内部发版号，三位从000开始自增直至对外发版。
     */
    public final int versionCode;
    /**
     * <h3>版本名称</h3>
     * <p>
     * 版本名称：三位，以.作为分隔符，如: 1.20.50；<p>
     * 最高版本位为两位数，表示大版本；<p>
     * 中间版本位为两位数，表示大版本内一般性升级发版；<p>
     * 最小版本位为两位数，表示升级版本的修复版本发版。<p>
     * <p>
     * 除此之外，还可能包含单词后缀，与版本号之间使用“-”间隔，如：1.20.51-beta，可用于指定版本面向用户 <p>
     * - alpha: 内部发版用，用于面向项目开发者，不对外发版<p>
     * - beta: 内部发版用，用于面向全体项目组，不对外发版<p>
     * - develop: 用于面向开发者，可对外发版<p>
     * - canary: 用于面向开发者、尝鲜用户，对外发版<p>
     * - release: 用于全体用户，对外发版<p>
     */
    public final String versionName;

    Global(String versionName, int versionCode) {
        this.versionName = versionName;
        this.versionCode = versionCode;
    }
}
