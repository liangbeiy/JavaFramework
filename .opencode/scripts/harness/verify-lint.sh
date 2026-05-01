#!/bin/bash
# verify-lint.sh — 门禁 5: Lint/Checkstyle 验证（0 ERROR, 0 Warning）
# Verifier 阶段必须通过此脚本验证代码质量
#
# 用法: .opencode/scripts/harness/verify-lint.sh
#
# 退出码: 0=通过, 1=有 ERROR 或 Warning

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/../../.." && pwd)"

echo "[Harness 门禁] 代码质量验证开始..."
echo "  命令: ./gradlew check"
echo ""

cd "$PROJECT_DIR"

if ./gradlew check; then
    echo ""
    echo "[Harness 门禁] ✅ 代码质量验证通过"
    exit 0
else
    echo ""
    echo "[Harness 门禁] ❌ 代码质量验证失败"
    echo "  请检查 check 任务输出，修复后重新验证。"
    exit 1
fi
