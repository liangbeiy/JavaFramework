#!/bin/bash
# complexity-score.sh — 变更复杂度评分（delta-熵增粗粒度评估）
# 基于 git diff + 路由索引，在无 AST/SonarQube 的条件下评估本次变更的复杂度
#
# 评分模型（满分制，分越高越复杂）：
#   ΔS = S_scale + S_coupling + S_spread + S_cycle + S_cognitive
#
#   S_scale    — 变更规模熵增（新增/修改行数、文件数）
#   S_coupling — 耦合度熵增（跨模块调用边变化，基于 call-graph）
#   S_spread   — 扩散度熵增（影响的模块数、直接调用者数）
#   S_cycle    — 循环依赖熵增（模块级循环检测）
#   S_cognitive— 认知熵增（新增文件占比、单人修改文件）
#
# 风险分级：
#   ΔS ≤ 2    低风险    自动通过
#   2 < ΔS ≤ 5  中风险    通过，记录到 progress.md，人工 CR 时重点关注
#   5 < ΔS ≤ 10 高风险    通过，需要在人工 CR 中说明熵增理由
#   ΔS > 10    极高风险  阻断，必须拆分变更或提供架构优化方案
#
# 用法: complexity-score.sh
# 退出码: 0=低/中风险通过, 1=极高风险阻断, 2=高风险警告通过

set -uo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/../../.." && pwd)"
ROUTER_DIR="$PROJECT_DIR/knowledge/current/router"
CALL_GRAPH="$ROUTER_DIR/call-graph.tsv"
MODULE_DEPS="$ROUTER_DIR/module-deps.tsv"
SYMBOL_TABLE="$ROUTER_DIR/symbol-table.tsv"

echo "================================================================"
echo "  [Harness 门禁] 变更复杂度评分（delta-熵增评估）"
echo "================================================================"
echo ""

# ===== 获取变更文件 =====
cd "$PROJECT_DIR"
DIFF_STAT=$(git diff --stat HEAD 2>/dev/null || git diff --stat --cached 2>/dev/null || echo "")
CHANGED_FILES=$(git diff --name-only --diff-filter=ACMR HEAD 2>/dev/null || git diff --name-only --cached 2>/dev/null || echo "")

if [ -z "$CHANGED_FILES" ]; then
    echo "  未检测到变更文件。"
    echo ""
    echo "  ΔS = 0.0（无变更）"
    echo "  风险等级: 无"
    echo ""
    echo "COMPLEXITY_SCORE=0.0"
    echo "RISK_LEVEL=none"
    exit 0
fi

SOURCE_FILES=$(echo "$CHANGED_FILES" | grep -E '\.(java|kt)$' | grep -v '/test/' | grep -v '/androidTest/' || true)
ALL_FILES_COUNT=$(echo "$CHANGED_FILES" | wc -l | tr -d ' ')
SOURCE_FILES_COUNT=0
if [ -n "$SOURCE_FILES" ]; then
    SOURCE_FILES_COUNT=$(echo "$SOURCE_FILES" | wc -l | tr -d ' ')
fi

# 计算新增/修改行数
LINES_ADDED=$(git diff --numstat HEAD 2>/dev/null | awk '{s+=$1} END {print s+0}' || echo "0")
LINES_DELETED=$(git diff --numstat HEAD 2>/dev/null | awk '{s+=$2} END {print s+0}' || echo "0")
LINES_CHANGED=$((LINES_ADDED + LINES_DELETED))
NEW_FILES=$(git diff --name-only --diff-filter=A HEAD 2>/dev/null | grep -E '\.(java|kt)$' | grep -v '/test/' || true)
NEW_FILES_COUNT=0
if [ -n "$NEW_FILES" ]; then
    NEW_FILES_COUNT=$(echo "$NEW_FILES" | wc -l | tr -d ' ')
fi

echo "--- 变更概况 ---"
echo "  文件总数: ${ALL_FILES_COUNT}（源码文件: ${SOURCE_FILES_COUNT}）"
echo "  新增文件: ${NEW_FILES_COUNT}"
echo "  行数变化: +${LINES_ADDED} -${LINES_DELETED}（共 ${LINES_CHANGED} 行）"
echo ""

# ======================================================================
# S_scale — 变更规模熵增
# 归一化: 行数/500 + 文件数/10，上限 3.0
# ======================================================================
SCALE_RAW=$(awk "BEGIN {v = ${LINES_CHANGED}/500.0 + ${SOURCE_FILES_COUNT}/10.0; print (v > 3.0 ? 3.0 : v)}")
echo "--- S_scale（变更规模）= ${SCALE_RAW} ---"
echo "  公式: min(行数变化/500 + 源码文件数/10, 3.0)"
echo ""

# ======================================================================
# S_coupling — 耦合度熵增
# 统计变更类涉及的跨模块调用边数
# ======================================================================
COUPLING_SCORE="0.0"
if [ -n "$SOURCE_FILES" ] && [ -f "$CALL_GRAPH" ] && [ -f "$SYMBOL_TABLE" ]; then
    CROSS_MODULE_EDGES=0

    for FILE in $SOURCE_FILES; do
        # 从文件路径提取模块名
        FILE_MODULE=$(echo "$FILE" | cut -d'/' -f1)

        # 从文件路径推断类名（简化：取文件名去掉扩展名）
        CLASS_NAME=$(basename "$FILE" | sed 's/\.\(java\|kt\)$//')

        # 查 call-graph 中该类的跨模块调用
        OUTGOING=$(grep -i "^${CLASS_NAME}	" "$CALL_GRAPH" 2>/dev/null || true)
        if [ -n "$OUTGOING" ]; then
            CROSS_OUT=$(echo "$OUTGOING" | awk -F'\t' -v mod="$FILE_MODULE" '$3 != $4 {count++} END {print count+0}')
            CROSS_MODULE_EDGES=$((CROSS_MODULE_EDGES + CROSS_OUT))
        fi

        INCOMING=$(grep -i "	${CLASS_NAME}	" "$CALL_GRAPH" 2>/dev/null || true)
        if [ -n "$INCOMING" ]; then
            CROSS_IN=$(echo "$INCOMING" | awk -F'\t' -v mod="$FILE_MODULE" '$3 != $4 {count++} END {print count+0}')
            CROSS_MODULE_EDGES=$((CROSS_MODULE_EDGES + CROSS_IN))
        fi
    done

    # 归一化: 跨模块边数/5，上限 3.0
    COUPLING_SCORE=$(awk "BEGIN {v = ${CROSS_MODULE_EDGES}/5.0; print (v > 3.0 ? 3.0 : v)}")
    echo "--- S_coupling（耦合度）= ${COUPLING_SCORE} ---"
    echo "  跨模块调用边: ${CROSS_MODULE_EDGES}"
    echo "  公式: min(跨模块边数/5, 3.0)"
else
    echo "--- S_coupling（耦合度）= 0.0 ---"
    echo "  (无源码变更或索引缺失，跳过)"
fi
echo ""

# ======================================================================
# S_spread — 扩散度熵增
# 变更涉及多少个不同模块
# ======================================================================
SPREAD_SCORE="0.0"
if [ -n "$SOURCE_FILES" ]; then
    AFFECTED_MODULES=$(echo "$SOURCE_FILES" | cut -d'/' -f1 | sort -u)
    MODULE_COUNT=$(echo "$AFFECTED_MODULES" | wc -l | tr -d ' ')

    # 归一化: (模块数-1)/3，单模块内变更 = 0，上限 2.0
    SPREAD_SCORE=$(awk "BEGIN {v = (${MODULE_COUNT}-1)/3.0; if(v<0)v=0; print (v > 2.0 ? 2.0 : v)}")
    echo "--- S_spread（扩散度）= ${SPREAD_SCORE} ---"
    echo "  涉及模块数: ${MODULE_COUNT}"
    echo "  模块列表: $(echo "$AFFECTED_MODULES" | tr '\n' ' ')"
    echo "  公式: min((模块数-1)/3, 2.0)"
else
    echo "--- S_spread（扩散度）= 0.0 ---"
fi
echo ""

# ======================================================================
# S_cycle — 循环依赖熵增
# 检测变更涉及的模块之间是否存在循环依赖（A→B 且 B→A）
# ======================================================================
CYCLE_SCORE="0.0"
CYCLE_COUNT=0
CYCLE_DETAILS=""

if [ -n "$SOURCE_FILES" ] && [ -f "$MODULE_DEPS" ]; then
    AFFECTED_MODULES=$(echo "$SOURCE_FILES" | cut -d'/' -f1 | sort -u)

    # 对变更涉及的每对模块检查双向依赖
    MOD_ARRAY=($AFFECTED_MODULES)
    for ((i=0; i<${#MOD_ARRAY[@]}; i++)); do
        MOD_A="${MOD_ARRAY[$i]}"
        # 获取 MOD_A 依赖的所有模块
        DEPS_OF_A=$(grep "^${MOD_A}	" "$MODULE_DEPS" 2>/dev/null | cut -f2 || true)

        for DEP in $DEPS_OF_A; do
            # 检查 DEP 是否也依赖 MOD_A（双向=循环）
            REVERSE=$(grep "^${DEP}	${MOD_A}	" "$MODULE_DEPS" 2>/dev/null || true)
            if [ -n "$REVERSE" ]; then
                CYCLE_COUNT=$((CYCLE_COUNT + 1))
                CYCLE_DETAILS="${CYCLE_DETAILS}\n    ! ${MOD_A} ↔ ${DEP}（双向依赖）"
            fi
        done
    done

    if [ "$CYCLE_COUNT" -gt 0 ]; then
        # 循环依赖惩罚：每个循环 5 分
        CYCLE_SCORE=$(awk "BEGIN {print ${CYCLE_COUNT} * 5.0}")
        echo "--- S_cycle（循环依赖）= ${CYCLE_SCORE} ---"
        echo "  发现 ${CYCLE_COUNT} 对循环依赖:"
        echo -e "$CYCLE_DETAILS"
        echo "  公式: 循环数 × 5.0（无上限，循环依赖是架构硬伤）"
    else
        echo "--- S_cycle（循环依赖）= 0.0 ---"
        echo "  未发现循环依赖"
    fi
else
    echo "--- S_cycle（循环依赖）= 0.0 ---"
    echo "  (索引缺失，跳过)"
fi
echo ""

# ======================================================================
# S_cognitive — 认知熵增
# 新增文件占比 + 缺少现有调用者的孤立新类
# ======================================================================
COGNITIVE_SCORE="0.0"
if [ "$SOURCE_FILES_COUNT" -gt 0 ]; then
    NEW_RATIO=$(awk "BEGIN {print ${NEW_FILES_COUNT}/${SOURCE_FILES_COUNT}.0}")

    # 归一化: 新增文件占比 × 2，上限 2.0
    COGNITIVE_SCORE=$(awk "BEGIN {v = ${NEW_RATIO} * 2.0; print (v > 2.0 ? 2.0 : v)}")
    echo "--- S_cognitive（认知熵增）= ${COGNITIVE_SCORE} ---"
    echo "  新增文件占比: ${NEW_FILES_COUNT}/${SOURCE_FILES_COUNT} = ${NEW_RATIO}"
    echo "  公式: min(新增文件占比 × 2, 2.0)"
else
    echo "--- S_cognitive（认知熵增）= 0.0 ---"
fi
echo ""

# ======================================================================
# 综合评分
# ======================================================================
TOTAL=$(awk "BEGIN {printf \"%.1f\", ${SCALE_RAW} + ${COUPLING_SCORE} + ${SPREAD_SCORE} + ${CYCLE_SCORE} + ${COGNITIVE_SCORE}}")

echo "================================================================"
echo "  综合 delta-熵增评分"
echo "================================================================"
echo ""
echo "  S_scale    (变更规模)  = ${SCALE_RAW}"
echo "  S_coupling (耦合度)    = ${COUPLING_SCORE}"
echo "  S_spread   (扩散度)    = ${SPREAD_SCORE}"
echo "  S_cycle    (循环依赖)  = ${CYCLE_SCORE}"
echo "  S_cognitive(认知熵增)  = ${COGNITIVE_SCORE}"
echo "  ──────────────────────────────"
echo "  ΔS (总分)              = ${TOTAL}"
echo ""

# ===== 风险分级 =====
RISK_LEVEL=""
EXIT_CODE=0

# 用 awk 做浮点比较
LEVEL=$(awk "BEGIN {
    s = ${TOTAL};
    if (s <= 2.0) print \"low\";
    else if (s <= 5.0) print \"medium\";
    else if (s <= 10.0) print \"high\";
    else print \"critical\";
}")

case "$LEVEL" in
    low)
        RISK_LEVEL="低风险"
        echo "  风险等级: ${RISK_LEVEL}（ΔS ≤ 2）"
        echo "  处理: 自动通过"
        EXIT_CODE=0
        ;;
    medium)
        RISK_LEVEL="中风险"
        echo "  风险等级: ${RISK_LEVEL}（2 < ΔS ≤ 5）"
        echo "  处理: 通过，记录到 progress.md，人工 CR 时重点关注"
        EXIT_CODE=0
        ;;
    high)
        RISK_LEVEL="高风险"
        echo "  风险等级: ${RISK_LEVEL}（5 < ΔS ≤ 10）"
        echo "  处理: 通过，但需要在人工 CR 中说明熵增理由"
        EXIT_CODE=2
        ;;
    critical)
        RISK_LEVEL="极高风险"
        echo "  风险等级: ${RISK_LEVEL}（ΔS > 10）"
        echo "  处理: 阻断！必须拆分变更或提供架构优化方案"
        EXIT_CODE=1
        ;;
esac

echo ""
echo "COMPLEXITY_SCORE=${TOTAL}"
echo "RISK_LEVEL=${LEVEL}"
echo ""

if [ "$EXIT_CODE" -eq 1 ]; then
    echo "[Harness 门禁] ❌ 变更复杂度评分未通过（ΔS=${TOTAL}，${RISK_LEVEL}）"
    echo "  建议:"
    echo "    1. 拆分为多个小变更，每个 ΔS ≤ 5"
    echo "    2. 减少跨模块依赖，降低 S_coupling"
    echo "    3. 消除循环依赖，降低 S_cycle"
elif [ "$EXIT_CODE" -eq 2 ]; then
    echo "[Harness 门禁] ⚠️ 变更复杂度评分警告（ΔS=${TOTAL}，${RISK_LEVEL}）"
    echo "  人工 CR 时需要重点审查复杂度，说明熵增理由"
else
    echo "[Harness 门禁] ✅ 变更复杂度评分通过（ΔS=${TOTAL}，${RISK_LEVEL}）"
fi

exit $EXIT_CODE
