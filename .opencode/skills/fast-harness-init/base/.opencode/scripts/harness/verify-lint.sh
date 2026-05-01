#!/bin/bash
# verify-lint.sh — 门禁 5: Lint/Sonar 验证（0 ERROR, 0 Warning）
# Verifier 阶段必须通过此脚本验证代码质量
#
# 当前使用 Android Lint，后续接入 SonarQube 后切换 SCAN_MODE=sonar
#
# 用法: .opencode/scripts/harness/verify-lint.sh
#
# 退出码: 0=通过, 1=有 ERROR 或 Warning

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/../../.." && pwd)"
# 切换扫描模式: lint (默认) | sonar
SCAN_MODE="${SCAN_MODE:-lint}"

echo "[Harness 门禁] 代码质量验证开始..."
echo "  扫描模式: ${SCAN_MODE}"
echo ""

cd "$PROJECT_DIR"

if [ "$SCAN_MODE" = "sonar" ]; then
    # ===== SonarQube 模式（未来启用）=====
    echo "  命令: ./gradlew sonarqube"
    if ! ./gradlew sonarqube; then
        echo ""
        echo "[Harness 门禁] ❌ SonarQube 扫描执行失败"
        exit 1
    fi
    echo ""
    echo "[Harness 门禁] ✅ SonarQube 扫描完成，请在 SonarQube 控制台查看结果。"
    # 注意：SonarQube 的 Quality Gate 判断需要在服务端配置，此处仅执行扫描
    exit 0
else
    # ===== Android Lint 模式（当前使用）=====
    LINT_REPORT="$PROJECT_DIR/app/build/reports/lint-results-debug.xml"

    echo "  命令: ./gradlew :app:lintDebug"
    if ! ./gradlew :app:lintDebug; then
        echo ""
        echo "[Harness 门禁] ❌ Lint 执行失败"
        exit 1
    fi

    # 解析 Lint XML 报告
    if [ ! -f "$LINT_REPORT" ]; then
        echo "[Harness 门禁] ⚠️ Lint 报告文件不存在: ${LINT_REPORT}"
        echo "  尝试查找其他位置..."
        LINT_REPORT=$(find "$PROJECT_DIR/app/build/reports" -name "lint-results*.xml" -type f | head -1)
        if [ -z "$LINT_REPORT" ]; then
            echo "[Harness 门禁] ❌ 未找到 Lint 报告文件"
            exit 1
        fi
        echo "  找到: ${LINT_REPORT}"
    fi

    # 统计 ERROR 和 WARNING 数量
    ERROR_COUNT=$(grep -c 'severity="Error"' "$LINT_REPORT" 2>/dev/null || echo "0")
    WARNING_COUNT=$(grep -c 'severity="Warning"' "$LINT_REPORT" 2>/dev/null || echo "0")

    echo ""
    echo "  Lint 结果:"
    echo "    Error:   ${ERROR_COUNT}"
    echo "    Warning: ${WARNING_COUNT}"
    echo ""

    FAILED=0

    if [ "$ERROR_COUNT" -gt 0 ]; then
        echo "[Harness 门禁] ❌ Lint 发现 ${ERROR_COUNT} 个 ERROR，必须全部修复。"
        # 输出前 10 个 ERROR 详情
        echo "  前 10 个 ERROR:"
        grep 'severity="Error"' "$LINT_REPORT" | head -10 | while read -r line; do
            echo "    $line"
        done
        FAILED=1
    fi

    if [ "$WARNING_COUNT" -gt 0 ]; then
        echo "[Harness 门禁] ❌ Lint 发现 ${WARNING_COUNT} 个 WARNING，必须全部修复。"
        echo "  前 10 个 WARNING:"
        grep 'severity="Warning"' "$LINT_REPORT" | head -10 | while read -r line; do
            echo "    $line"
        done
        FAILED=1
    fi

    if [ "$FAILED" -eq 1 ]; then
        echo ""
        echo "[Harness 门禁] ❌ 代码质量验证失败: 要求 0 ERROR, 0 WARNING"
        echo "  完整报告: ${LINT_REPORT}"
        exit 1
    fi

    echo "[Harness 门禁] ✅ 代码质量验证通过: 0 ERROR, 0 WARNING"
    exit 0
fi
