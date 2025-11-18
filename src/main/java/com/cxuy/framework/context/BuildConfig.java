/*
 * Copyright (c) 2025 liangbeiyuan.
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package com.cxuy.framework.context;

import com.cxuy.framework.constant.Constant;
import com.cxuy.framework.util.Logger;
import com.cxuy.framework.util.TextUtil;

public enum BuildConfig {
    INSTANCE;

    private static final String TAG = "BuildConfig";

    private static final String NAME_DEBUG = "debug";
    private static final String NAME_RELEASE = "release";

    public final Type buildType;
    private final int versionCode;

    BuildConfig() {
        String buildTypeStr = System.getProperty(Constant.PARAM_KEY_BUILD_TYPE);
        if(TextUtil.equals(NAME_DEBUG, buildTypeStr)) {
            this.buildType = Type.DEBUG;
        } else {
            this.buildType = Type.RELEASE;
        }
        String versionCodeStr = System.getProperty(Constant.PARAM_KEY_BUILD_VERSION);
        if(TextUtil.isEmpty(versionCodeStr)) {
            versionCode = 0;
            return;
        }
        int buildVersion = 0;
        try {
            buildVersion = Integer.parseInt(versionCodeStr);
        } catch (NumberFormatException e) {
            Logger.e(TAG, "decode build version failure, check!!! build version=" + versionCodeStr, e);
        }
        versionCode = buildVersion;
    }

    public enum Type {
        DEBUG(BuildConfig.NAME_DEBUG), RELEASE(BuildConfig.NAME_RELEASE);
        public final String rawName;
        Type(String name) {
            rawName = name;
        }
    }
}

