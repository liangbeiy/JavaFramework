#!/bin/bash
# impact-analysis.sh — 变更影响面分析工具
# 输入一个类名或模块名，输出受影响的调用者、依赖模块和影响范围评估
#
# 用法:
#   impact-analysis.sh <类名或模块名> [--depth <1-3>]
#
# 示例:
#   impact-analysis.sh GameCacheManager              # 分析修改该类的影响面
#   impact-analysis.sh GameDownloadLib               # 分析修改该模块的影响面
#   impact-analysis.sh GameCacheManager --depth 2    # 2 跳影响面
#
# 输出: 结构化的影响面报告
# 退出码: 0=分析完成, 1=未找到目标, 2=参数错误

set -uo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/../../.." && pwd)"
ROUTER_DIR="$PROJECT_DIR/knowledge/current/router"

SYMBOL_TABLE="$ROUTER_DIR/symbol-table.tsv"
CALL_GRAPH="$ROUTER_DIR/call-graph.tsv"
MODULE_DEPS="$ROUTER_DIR/module-deps.tsv"

# ===== 参数解析 =====
TARGET=""
MAX_DEPTH=1

while [[ $# -gt 0 ]]; do
    case "$1" in
        --depth)
            MAX_DEPTH="$2"
            if [ "$MAX_DEPTH" -lt 1 ] || [ "$MAX_DEPTH" -gt 3 ]; then
                echo "[impact-analysis] 错误: depth 必须在 1-3 之间" >&2
                exit 2
            fi
            shift 2
            ;;
        --help|-h)
            echo "用法: impact-analysis.sh <类名或模块名> [--depth <1-3>]"
            exit 0
            ;;
        -*)
            echo "[impact-analysis] 未知参数: $1" >&2
            exit 2
            ;;
        *)
            TARGET="$1"
            shift
            ;;
    esac
done

if [ -z "$TARGET" ]; then
    echo "[impact-analysis] 错误: 必须提供分析目标（类名或模块名）" >&2
    exit 2
fi

if [ ! -f "$CALL_GRAPH" ]; then
    echo "[impact-analysis] 错误: 路由索引不存在" >&2
    exit 1
fi

# ===== 判断目标类型：类 or 模块 =====
IS_MODULE=false
if grep -q "^${TARGET}	" "$MODULE_DEPS" 2>/dev/null || grep -q "	${TARGET}	" "$MODULE_DEPS" 2>/dev/null; then
    IS_MODULE=true
fi

IS_CLASS=false
if grep -qi "^${TARGET}	" "$SYMBOL_TABLE" 2>/dev/null; then
    IS_CLASS=true
fi

if ! $IS_MODULE && ! $IS_CLASS; then
    echo "[impact-analysis] 未在索引中找到 '${TARGET}'（既不是已知类也不是已知模块）"
    echo "  建议: 检查拼写，或用 route-query.sh 先定位"
    exit 1
fi

echo "================================================================"
echo "  影响面分析报告: $TARGET"
echo "  分析深度: ${MAX_DEPTH} 跳"
echo "================================================================"
echo ""

# ===== 类级影响面分析 =====
if $IS_CLASS; then
    # 获取所属模块
    CLASS_MODULE=$(grep -i "^${TARGET}	" "$SYMBOL_TABLE" 2>/dev/null | head -1 | cut -f2)
    CLASS_PATH=$(grep -i "^${TARGET}	" "$SYMBOL_TABLE" 2>/dev/null | head -1 | cut -f3)
    echo "--- 目标类信息 ---"
    echo "  类名: $TARGET"
    echo "  模块: $CLASS_MODULE"
    echo "  路径: $CLASS_PATH"
    echo ""

    # 第 1 跳：直接调用者
    echo "--- 直接调用者（第 1 跳） ---"
    DIRECT_CALLERS=$(grep -i "	${TARGET}	" "$CALL_GRAPH" 2>/dev/null || true)
    DIRECT_CALLER_COUNT=0

    if [ -n "$DIRECT_CALLERS" ]; then
        DIRECT_CALLER_COUNT=$(echo "$DIRECT_CALLERS" | wc -l | tr -d ' ')
        echo "  共 ${DIRECT_CALLER_COUNT} 个直接调用者:"
        echo ""
        echo "$DIRECT_CALLERS" | while IFS=$'\t' read -r caller callee c_mod e_mod rel conf evidence detail; do
            CROSS=""
            if [ "$c_mod" != "$e_mod" ]; then
                CROSS=" [跨模块]"
            fi
            IPC=""
            if [ "$rel" = "ipc_delegate" ] || [ "$rel" = "ipc_call" ]; then
                IPC=" [IPC]"
            fi
            echo "    ← $caller ($c_mod) [${rel}/${conf}]${CROSS}${IPC}"
        done
    else
        echo "  (无直接调用者)"
    fi
    echo ""

    # 第 2 跳（如果 depth >= 2）
    if [ "$MAX_DEPTH" -ge 2 ] && [ -n "$DIRECT_CALLERS" ]; then
        echo "--- 间接调用者（第 2 跳） ---"
        SECOND_HOP_COUNT=0
        echo "$DIRECT_CALLERS" | cut -f1 | sort -u | while read -r caller1; do
            SECOND=$(grep -i "	${caller1}	" "$CALL_GRAPH" 2>/dev/null || true)
            if [ -n "$SECOND" ]; then
                echo "  经由 ${caller1}:"
                echo "$SECOND" | while IFS=$'\t' read -r caller callee c_mod e_mod rel conf evidence detail; do
                    echo "    ←← $caller ($c_mod) [${rel}]"
                done
                SECOND_HOP_COUNT=$((SECOND_HOP_COUNT + 1))
            fi
        done
        echo ""
    fi

    # 第 3 跳（如果 depth >= 3）
    if [ "$MAX_DEPTH" -ge 3 ] && [ -n "$DIRECT_CALLERS" ]; then
        echo "--- 第 3 跳调用者（可能不完整） ---"
        echo "  (第 3 跳结果较多，建议结合源码 Grep 验证)"
        echo "$DIRECT_CALLERS" | cut -f1 | sort -u | while read -r caller1; do
            SECOND_CALLERS=$(grep -i "	${caller1}	" "$CALL_GRAPH" 2>/dev/null | cut -f1 | sort -u || true)
            for caller2 in $SECOND_CALLERS; do
                THIRD=$(grep -i "	${caller2}	" "$CALL_GRAPH" 2>/dev/null || true)
                if [ -n "$THIRD" ]; then
                    echo "$THIRD" | while IFS=$'\t' read -r caller callee c_mod e_mod rel conf evidence detail; do
                        echo "    ←←← $caller ($c_mod)"
                    done
                fi
            done
        done | sort -u | head -20
        echo ""
    fi

    # 涉及的模块统计
    echo "--- 受影响模块统计 ---"
    if [ -n "$DIRECT_CALLERS" ]; then
        AFFECTED_MODULES=$(echo "$DIRECT_CALLERS" | cut -f3 | sort -u)
        AFFECTED_COUNT=$(echo "$AFFECTED_MODULES" | wc -l | tr -d ' ')
        echo "  涉及 ${AFFECTED_COUNT} 个模块:"
        echo "$AFFECTED_MODULES" | while read -r mod; do
            SAME=""
            if [ "$mod" = "$CLASS_MODULE" ]; then
                SAME=" (同模块)"
            fi
            echo "    - ${mod}${SAME}"
        done
    else
        echo "  无受影响模块"
    fi
    echo ""

    # IPC 风险评估
    IPC_EDGES=$(echo "$DIRECT_CALLERS" | grep -E "ipc_delegate|ipc_call" 2>/dev/null || true)
    if [ -n "$IPC_EDGES" ]; then
        IPC_COUNT=$(echo "$IPC_EDGES" | wc -l | tr -d ' ')
        echo "--- IPC 风险提醒 ---"
        echo "  发现 ${IPC_COUNT} 条 IPC 调用边，修改此类可能影响跨进程通信:"
        echo "$IPC_EDGES" | while IFS=$'\t' read -r caller callee c_mod e_mod rel conf evidence detail; do
            echo "    ! $caller ($c_mod) ←[${rel}]→ $callee ($e_mod)"
        done
        echo ""
    fi
fi

# ===== 模块级影响面分析 =====
if $IS_MODULE; then
    echo "--- 模块依赖分析: $TARGET ---"
    echo ""

    # 该模块依赖了谁
    DEPS_OUT=$(grep "^${TARGET}	" "$MODULE_DEPS" 2>/dev/null || true)
    if [ -n "$DEPS_OUT" ]; then
        DEPS_OUT_COUNT=$(echo "$DEPS_OUT" | wc -l | tr -d ' ')
        echo "  $TARGET 依赖了 ${DEPS_OUT_COUNT} 个模块:"
        echo "$DEPS_OUT" | while IFS=$'\t' read -r from to type; do
            echo "    → $to ($type)"
        done
    else
        echo "  $TARGET 不依赖其他模块"
    fi
    echo ""

    # 谁依赖了该模块
    DEPS_IN=$(grep "	${TARGET}	" "$MODULE_DEPS" 2>/dev/null || true)
    if [ -n "$DEPS_IN" ]; then
        DEPS_IN_COUNT=$(echo "$DEPS_IN" | wc -l | tr -d ' ')
        echo "  ${DEPS_IN_COUNT} 个模块依赖了 ${TARGET}（修改此模块可能影响它们）:"
        echo "$DEPS_IN" | while IFS=$'\t' read -r from to type; do
            echo "    ← $from ($type)"
        done
    else
        echo "  没有模块依赖 $TARGET"
    fi
    echo ""

    # 该模块内的所有类
    MODULE_CLASSES=$(grep "	${TARGET}	" "$SYMBOL_TABLE" 2>/dev/null || true)
    if [ -n "$MODULE_CLASSES" ]; then
        CLASS_COUNT=$(echo "$MODULE_CLASSES" | wc -l | tr -d ' ')
        echo "  $TARGET 包含 ${CLASS_COUNT} 个类（索引覆盖）"
    fi
    echo ""

    # 跨模块调用边统计：callee_module 是第 4 列（值为 TARGET），但 caller_module（第 3 列）不是 TARGET
    CROSS_MODULE_CALLS=$(awk -F'\t' -v mod="$TARGET" '$4 == mod && $3 != mod' "$CALL_GRAPH" 2>/dev/null || true)
    if [ -n "$CROSS_MODULE_CALLS" ]; then
        CROSS_COUNT=$(echo "$CROSS_MODULE_CALLS" | wc -l | tr -d ' ')
        echo "--- 跨模块调用入口（共 ${CROSS_COUNT} 条） ---"
        echo "$CROSS_MODULE_CALLS" | head -15 | while IFS=$'\t' read -r caller callee c_mod e_mod rel conf evidence detail; do
            echo "    $caller ($c_mod) → $callee ($e_mod) [${rel}]"
        done
        if [ "$CROSS_COUNT" -gt 15 ]; then
            echo "    ... (共 ${CROSS_COUNT} 条，仅展示前 15)"
        fi
    fi
fi

echo ""
echo "================================================================"
echo "  分析完成。关键路径上的调用关系建议 Read 源码确认。"
echo "================================================================"
exit 0
