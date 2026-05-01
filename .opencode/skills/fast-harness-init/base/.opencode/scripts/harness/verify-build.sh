#!/bin/bash
# verify-build.sh — 门禁 4: 编译验证
# Verifier 阶段必须通过此脚本验证编译
#
# 用法: .opencode/scripts/harness/verify-build.sh [build-type]
#   build-type: debug (默认) | release | production
#
# 退出码: 0=成功, 1=编译失败

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/../../.." && pwd)"
BUILD_TYPE="${1:-debug}"

echo "[Harness 门禁] 编译验证开始..."
echo "  项目: ${PROJECT_DIR}"
echo "  构建类型: ${BUILD_TYPE}"
echo ""

case "$BUILD_TYPE" in
    debug)
        CMD="./gradlew :app:assembleDebug"
        ;;
    release)
        CMD="./gradlew :app:assembleRelease"
        ;;
    production)
        CMD="./gradlew :app:assembleRelease -PproductModel=true"
        ;;
    *)
        echo "[Harness 门禁] ❌ 未知构建类型: ${BUILD_TYPE}"
        echo "  支持: debug | release | production"
        exit 1
        ;;
esac

echo "  命令: ${CMD}"
echo ""

cd "$PROJECT_DIR"
if $CMD; then
    echo ""
    echo "[Harness 门禁] ✅ 编译验证通过"
    echo "BUILD_RESULT=PASS"
    exit 0
else
    echo ""
    echo "[Harness 门禁] ❌ 编译验证失败"
    echo "BUILD_RESULT=FAIL"
    echo "  请修复编译错误后重新验证。"
    exit 1
fi
