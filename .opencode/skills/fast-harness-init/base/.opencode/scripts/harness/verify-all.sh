#!/bin/bash
# verify-all.sh — Verifier 阶段统一入口脚本
# 串联执行所有可脚本化的验证步骤（步骤 1/3/4/5/6），确保不被遗漏。
# 纯 AI 分析步骤（2 单测/2b 覆盖度/7 方案对比）不在本脚本范围内，
# 由 Verifier 子 Agent 在本脚本执行后单独执行。
#
# 执行顺序：
#   步骤 1: 编译验证        (verify-build.sh)
#   步骤 3: Lint 代码质量    (verify-lint.sh)
#   步骤 4: SonarQube 扫描   (sonar-delta-scan.sh)  — 失败不阻断
#   步骤 5: 变更复杂度评分   (complexity-score.sh)
#   步骤 6: 密钥扫描         (verify-secrets.sh)
#
# 注意：步骤 2（单元测试）虽然有脚本命令（./gradlew test），但其结果解析、
# 失败信息提取和覆盖度验证（步骤 2b）需要 AI 参与，因此不纳入本脚本。
# Verifier 子 Agent 应在本脚本执行后，独立运行 ./gradlew test 并分析结果。
#
# 退出码:
#   0 = 全部通过（或仅有不阻断的跳过/警告）
#   1 = 存在阻断性失败
#
# 用法: verify-all.sh [build-type] [--sonar]
#   build-type: debug (默认) | release | production
#   --sonar:    启用 SonarQube 扫描（默认跳过）

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"

# 参数解析
BUILD_TYPE="debug"
SONAR_ENABLED=0
for arg in "$@"; do
    case "$arg" in
        --sonar) SONAR_ENABLED=1 ;;
        debug|release|production) BUILD_TYPE="$arg" ;;
    esac
done

# ===== 结果跟踪 =====
TOTAL_STEPS=5
PASSED=0
FAILED=0
SKIPPED=0
WARNED=0
RESULTS=()

record_result() {
    local step="$1"
    local status="$2"
    local detail="$3"
    RESULTS+=("$step|$status|$detail")
    case "$status" in
        PASS)   PASSED=$((PASSED + 1)) ;;
        FAIL)   FAILED=$((FAILED + 1)) ;;
        SKIP)   SKIPPED=$((SKIPPED + 1)) ;;
        WARN)   WARNED=$((WARNED + 1)) ;;
    esac
}

echo "================================================================"
echo "  [Harness] Verifier 统一验证入口"
echo "================================================================"
echo ""
echo "  项目: ${PROJECT_DIR}"
echo "  构建类型: ${BUILD_TYPE}"
if [ "$SONAR_ENABLED" = "1" ]; then
    echo "  执行步骤: 1(编译) → 3(Lint) → 4(Sonar) → 5(复杂度) → 6(密钥)"
else
    echo "  执行步骤: 1(编译) → 3(Lint) → 5(复杂度) → 6(密钥)  [Sonar:跳过]"
fi
echo "  后续 AI 步骤: 2(单测) → 2b(覆盖度) → 7(方案对比)"
echo ""
echo "================================================================"
echo ""

# ===== 步骤 1: 编译验证 =====
echo "┌─────────────────────────────────────────────────────┐"
echo "│  步骤 1/5: 编译验证 (verify-build.sh ${BUILD_TYPE})        │"
echo "└─────────────────────────────────────────────────────┘"
echo ""

if bash "$SCRIPT_DIR/verify-build.sh" "$BUILD_TYPE"; then
    record_result "步骤1:编译验证" "PASS" "编译成功"
    echo ""
else
    record_result "步骤1:编译验证" "FAIL" "编译失败"
    echo ""
    echo "[Harness] ❌ 编译失败，后续步骤中止。"
    echo ""
    # 编译失败直接中止所有后续步骤
    record_result "步骤3:Lint" "SKIP" "因编译失败跳过"
    record_result "步骤4:Sonar" "SKIP" "因编译失败跳过"
    record_result "步骤5:复杂度" "SKIP" "因编译失败跳过"
    record_result "步骤6:密钥" "SKIP" "因编译失败跳过"
    # 跳到汇总
    FAILED=1
    SKIPPED=4
    # 输出汇总后退出
    echo "================================================================"
    echo "  [Harness] 验证汇总"
    echo "================================================================"
    echo ""
    for r in "${RESULTS[@]}"; do
        IFS='|' read -r step status detail <<< "$r"
        case "$status" in
            PASS) echo "  ✅ ${step}: ${detail}" ;;
            FAIL) echo "  ❌ ${step}: ${detail}" ;;
            SKIP) echo "  ⏭️  ${step}: ${detail}" ;;
            WARN) echo "  ⚠️  ${step}: ${detail}" ;;
        esac
    done
    echo ""
    echo "  通过: ${PASSED} | 失败: ${FAILED} | 跳过: ${SKIPPED} | 警告: ${WARNED}"
    echo ""
    echo "VERIFY_ALL_RESULT=FAIL"
    echo "VERIFY_ALL_FAILED_STEPS=步骤1:编译验证"
    exit 1
fi

# ===== 步骤 3: Lint 代码质量 =====
echo "┌─────────────────────────────────────────────────────┐"
echo "│  步骤 2/5: Lint 代码质量 (verify-lint.sh)                │"
echo "└─────────────────────────────────────────────────────┘"
echo ""

if bash "$SCRIPT_DIR/verify-lint.sh"; then
    record_result "步骤3:Lint" "PASS" "0 ERROR, 0 WARNING"
else
    record_result "步骤3:Lint" "FAIL" "存在 ERROR 或 WARNING"
    echo ""
fi

# ===== 步骤 4: SonarQube 变更区间扫描（可选，HARNESS_SONAR=1 启用）=====
echo ""
if [ "$SONAR_ENABLED" = "1" ]; then
    echo "┌─────────────────────────────────────────────────────┐"
    echo "│  步骤 3/5: SonarQube 扫描 (sonar-delta-scan.sh)         │"
    echo "└─────────────────────────────────────────────────────┘"
    echo ""

    SONAR_EXIT=0
    bash "$SCRIPT_DIR/sonar-delta-scan.sh" || SONAR_EXIT=$?

    case "$SONAR_EXIT" in
        0) record_result "步骤4:Sonar" "PASS" "Quality Gate 通过" ;;
        2) record_result "步骤4:Sonar" "WARN" "警告通过（有非阻断 issues）" ;;
        *)
            # SonarQube 失败不阻断流水线（环境不可用时降级）
            record_result "步骤4:Sonar" "SKIP" "执行失败或环境不可用（exit=${SONAR_EXIT}），不阻断"
            ;;
    esac
else
    echo "┌─────────────────────────────────────────────────────┐"
    echo "│  步骤 3/5: SonarQube 扫描 — 跳过（可选，未启用）        │"
    echo "└─────────────────────────────────────────────────────┘"
    echo ""
    record_result "步骤4:Sonar" "SKIP" "可选步骤，用户未要求（传 --sonar 启用）"
fi
echo ""

# ===== 步骤 5: 变更复杂度评分 =====
echo "┌─────────────────────────────────────────────────────┐"
echo "│  步骤 4/5: 变更复杂度评分 (complexity-score.sh)         │"
echo "└─────────────────────────────────────────────────────┘"
echo ""

COMPLEXITY_EXIT=0
bash "$SCRIPT_DIR/complexity-score.sh" || COMPLEXITY_EXIT=$?

case "$COMPLEXITY_EXIT" in
    0) record_result "步骤5:复杂度" "PASS" "低/中风险，自动通过" ;;
    2) record_result "步骤5:复杂度" "WARN" "高风险，需 CR 说明熵增理由" ;;
    1) record_result "步骤5:复杂度" "FAIL" "极高风险（ΔS > 10），阻断" ;;
    *) record_result "步骤5:复杂度" "SKIP" "执行异常（exit=${COMPLEXITY_EXIT}）" ;;
esac
echo ""

# ===== 步骤 6: 密钥扫描 =====
echo "┌─────────────────────────────────────────────────────┐"
echo "│  步骤 5/5: 密钥扫描 (verify-secrets.sh)                 │"
echo "└─────────────────────────────────────────────────────┘"
echo ""

if bash "$SCRIPT_DIR/verify-secrets.sh"; then
    record_result "步骤6:密钥" "PASS" "未发现硬编码密钥"
else
    record_result "步骤6:密钥" "FAIL" "发现疑似硬编码密钥"
fi
echo ""

# ===== 汇总 =====
echo "================================================================"
echo "  [Harness] 验证汇总（脚本步骤）"
echo "================================================================"
echo ""

FAILED_STEPS=""
for r in "${RESULTS[@]}"; do
    IFS='|' read -r step status detail <<< "$r"
    case "$status" in
        PASS) echo "  ✅ ${step}: ${detail}" ;;
        FAIL)
            echo "  ❌ ${step}: ${detail}"
            FAILED_STEPS="${FAILED_STEPS}${step}, "
            ;;
        SKIP) echo "  ⏭️  ${step}: ${detail}" ;;
        WARN) echo "  ⚠️  ${step}: ${detail}" ;;
    esac
done

echo ""
echo "  通过: ${PASSED} | 失败: ${FAILED} | 跳过: ${SKIPPED} | 警告: ${WARNED}"
echo ""
echo "  📌 后续 AI 步骤（由 Verifier 子 Agent 执行）："
echo "     - 步骤 2: 单元测试执行 (./gradlew test) + 结果分析"
echo "     - 步骤 2b: 单测覆盖度验证 (unit-task.md 比对)"
echo "     - 步骤 7: 方案实现对比验证 (spec.md/tasks.md/design.md 比对)"
echo ""

if [ "$FAILED" -gt 0 ]; then
    echo "VERIFY_ALL_RESULT=FAIL"
    echo "VERIFY_ALL_FAILED_STEPS=${FAILED_STEPS%, }"
    exit 1
else
    echo "VERIFY_ALL_RESULT=PASS"
    exit 0
fi
