#!/bin/bash
# route-query.sh — 路由索引一键查询工具
# 封装 symbol-table + method-index + call-graph 的多步查询为单次调用
#
# 用法:
#   route-query.sh <query> [--method <method_name>] [--calls] [--callers]
#
# 示例:
#   route-query.sh GameCacheManager                     # 定位类
#   route-query.sh GameCacheManager --method init       # 定位方法
#   route-query.sh GameCacheManager --calls             # 正向调用链（调用了谁）
#   route-query.sh GameCacheManager --callers           # 反向调用链（谁调用了它）
#   route-query.sh GameCacheManager --method init --callers  # 方法级反向查询
#
# 输出: 结构化文本，包含定位信息 + 调用关系
# 退出码: 0=找到结果, 1=未找到, 2=参数错误

set -uo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/../../.." && pwd)"
ROUTER_DIR="$PROJECT_DIR/knowledge/current/router"

SYMBOL_TABLE="$ROUTER_DIR/symbol-table.tsv"
METHOD_INDEX="$ROUTER_DIR/method-index.tsv"
CALL_GRAPH="$ROUTER_DIR/call-graph.tsv"
MODULE_DEPS="$ROUTER_DIR/module-deps.tsv"
ROUTER_META="$ROUTER_DIR/ROUTER-META.md"

# ===== 参数解析 =====
QUERY=""
METHOD=""
SHOW_CALLS=false
SHOW_CALLERS=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --method)
            METHOD="$2"
            shift 2
            ;;
        --calls)
            SHOW_CALLS=true
            shift
            ;;
        --callers)
            SHOW_CALLERS=true
            shift
            ;;
        --help|-h)
            echo "用法: route-query.sh <类名> [--method <方法名>] [--calls] [--callers]"
            echo ""
            echo "选项:"
            echo "  --method <name>  定位指定方法"
            echo "  --calls          显示正向调用链（调用了谁）"
            echo "  --callers        显示反向调用链（谁调用了它）"
            exit 0
            ;;
        -*)
            echo "[route-query] 未知参数: $1" >&2
            exit 2
            ;;
        *)
            QUERY="$1"
            shift
            ;;
    esac
done

if [ -z "$QUERY" ]; then
    echo "[route-query] 错误: 必须提供查询目标（类名）" >&2
    exit 2
fi

# ===== 索引存在性检查 =====
if [ ! -f "$SYMBOL_TABLE" ]; then
    echo "[route-query] 错误: 路由索引不存在，请先执行 /route-index" >&2
    exit 1
fi

# ===== 新鲜度检查 =====
BASELINE=$(grep 'baseline_commit' "$ROUTER_META" 2>/dev/null | head -1 | awk '{print $2}')
if [ -n "$BASELINE" ]; then
    COMMITS_BEHIND=$(cd "$PROJECT_DIR" && git rev-list "$BASELINE"..HEAD --count 2>/dev/null || echo "unknown")
    if [ "$COMMITS_BEHIND" != "unknown" ] && [ "$COMMITS_BEHIND" -gt 30 ]; then
        echo "[route-query] 警告: 索引落后 ${COMMITS_BEHIND} 个 commit（基线: ${BASELINE:0:9}），结果可能过期"
        echo ""
    fi
fi

FOUND=false

# ===== 1. 符号表定位 =====
echo "=== 类定位: $QUERY ==="
SYMBOL_RESULTS=$(grep -i "^${QUERY}	" "$SYMBOL_TABLE" 2>/dev/null || true)

if [ -z "$SYMBOL_RESULTS" ]; then
    # 模糊匹配降级
    SYMBOL_RESULTS=$(grep -i "${QUERY}" "$SYMBOL_TABLE" 2>/dev/null | head -10 || true)
    if [ -n "$SYMBOL_RESULTS" ]; then
        echo "(精确匹配无结果，以下为模糊匹配)"
    fi
fi

if [ -n "$SYMBOL_RESULTS" ]; then
    FOUND=true
    echo "$SYMBOL_RESULTS" | while IFS=$'\t' read -r class module path kind extends implements key_methods; do
        echo "  类名: $class"
        echo "  模块: $module"
        echo "  路径: $path"
        echo "  类型: $kind"
        [ "$extends" != "-" ] && echo "  继承: $extends"
        [ "$implements" != "-" ] && echo "  实现: $implements"
        [ "$key_methods" != "-" ] && echo "  关键方法: $key_methods"
        echo ""
    done
else
    echo "  (未找到)"
    echo ""
fi

# ===== 2. 方法索引定位（如果指定了 --method）=====
if [ -n "$METHOD" ]; then
    echo "=== 方法定位: ${QUERY}.${METHOD}() ==="
    METHOD_RESULTS=$(grep -i "^${QUERY}	.*${METHOD}" "$METHOD_INDEX" 2>/dev/null || true)

    if [ -n "$METHOD_RESULTS" ]; then
        FOUND=true
        echo "$METHOD_RESULTS" | while IFS=$'\t' read -r class signature module visibility kind called_by; do
            echo "  签名: $signature"
            echo "  模块: $module"
            echo "  可见性: $visibility"
            echo "  种类: $kind"
            echo "  调用者摘要: $called_by"
            echo ""
        done
    else
        echo "  (未在方法索引中找到，可尝试源码 Grep)"
        echo ""
    fi
fi

# ===== 3. 正向调用链（--calls）=====
if $SHOW_CALLS; then
    echo "=== 正向调用链: ${QUERY} 调用了谁 ==="
    CALL_RESULTS=$(grep -i "^${QUERY}	" "$CALL_GRAPH" 2>/dev/null || true)

    if [ -n "$CALL_RESULTS" ]; then
        FOUND=true
        echo "$CALL_RESULTS" | while IFS=$'\t' read -r caller callee c_mod e_mod rel conf evidence detail; do
            echo "  → $callee ($e_mod) [${rel}] [${conf}]"
            [ -n "$detail" ] && echo "    细节: $detail"
        done
    else
        echo "  (无正向调用记录)"
    fi
    echo ""
fi

# ===== 4. 反向调用链（--callers）=====
if $SHOW_CALLERS; then
    if [ -n "$METHOD" ]; then
        echo "=== 反向调用链: 谁调用了 ${QUERY}.${METHOD}() ==="
        # 方法级反向查询：在 call-graph 的 detail 列搜索方法名
        CALLER_RESULTS=$(grep -i "	${QUERY}	" "$CALL_GRAPH" 2>/dev/null | grep -i "${METHOD}" || true)
        # 补充：在 method-index 的 called_by_summary 中查找
        METHOD_CALLERS=$(grep -i "^${QUERY}	.*${METHOD}" "$METHOD_INDEX" 2>/dev/null | cut -f6 || true)
        if [ -n "$METHOD_CALLERS" ] && [ "$METHOD_CALLERS" != "-" ]; then
            echo "  [method-index] 调用者摘要: $METHOD_CALLERS"
        fi
    else
        echo "=== 反向调用链: 谁调用了 ${QUERY} ==="
        CALLER_RESULTS=$(grep -i "	${QUERY}	" "$CALL_GRAPH" 2>/dev/null || true)
    fi

    if [ -n "$CALLER_RESULTS" ]; then
        FOUND=true
        echo "$CALLER_RESULTS" | while IFS=$'\t' read -r caller callee c_mod e_mod rel conf evidence detail; do
            echo "  ← $caller ($c_mod) [${rel}] [${conf}]"
            [ -n "$detail" ] && echo "    细节: $detail"
        done
    else
        echo "  (无反向调用记录，建议用 Grep 源码补充)"
    fi
    echo ""
fi

# ===== 5. 默认行为：未指定 --calls/--callers 时，同时展示双向（限 5 条）=====
if ! $SHOW_CALLS && ! $SHOW_CALLERS && [ -z "$METHOD" ]; then
    FORWARD=$(grep -i "^${QUERY}	" "$CALL_GRAPH" 2>/dev/null | head -5 || true)
    BACKWARD=$(grep -i "	${QUERY}	" "$CALL_GRAPH" 2>/dev/null | head -5 || true)

    if [ -n "$FORWARD" ]; then
        FOUND=true
        FORWARD_COUNT=$(grep -ic "^${QUERY}	" "$CALL_GRAPH" 2>/dev/null || echo "0")
        echo "=== 正向调用 (前5条，共 ${FORWARD_COUNT} 条) ==="
        echo "$FORWARD" | while IFS=$'\t' read -r caller callee c_mod e_mod rel conf evidence detail; do
            echo "  → $callee ($e_mod) [${rel}]"
        done
        echo ""
    fi

    if [ -n "$BACKWARD" ]; then
        FOUND=true
        BACKWARD_COUNT=$(grep -ic "	${QUERY}	" "$CALL_GRAPH" 2>/dev/null || echo "0")
        echo "=== 反向调用 (前5条，共 ${BACKWARD_COUNT} 条) ==="
        echo "$BACKWARD" | while IFS=$'\t' read -r caller callee c_mod e_mod rel conf evidence detail; do
            echo "  ← $caller ($c_mod) [${rel}]"
        done
        echo ""
    fi
fi

# ===== 6. 所属模块的依赖信息 =====
if [ -n "$SYMBOL_RESULTS" ]; then
    MODULE_NAME=$(echo "$SYMBOL_RESULTS" | head -1 | cut -f2)
    if [ -n "$MODULE_NAME" ]; then
        DEP_OUT=$(grep "^${MODULE_NAME}	" "$MODULE_DEPS" 2>/dev/null || true)
        DEP_IN=$(grep "	${MODULE_NAME}	" "$MODULE_DEPS" 2>/dev/null || true)

        if [ -n "$DEP_OUT" ] || [ -n "$DEP_IN" ]; then
            echo "=== 模块依赖: ${MODULE_NAME} ==="
            if [ -n "$DEP_OUT" ]; then
                DEP_OUT_COUNT=$(echo "$DEP_OUT" | wc -l | tr -d ' ')
                echo "  依赖了 ${DEP_OUT_COUNT} 个模块:"
                echo "$DEP_OUT" | while IFS=$'\t' read -r from to type; do
                    echo "    → $to ($type)"
                done
            fi
            if [ -n "$DEP_IN" ]; then
                DEP_IN_COUNT=$(echo "$DEP_IN" | wc -l | tr -d ' ')
                echo "  被 ${DEP_IN_COUNT} 个模块依赖:"
                echo "$DEP_IN" | while IFS=$'\t' read -r from to type; do
                    echo "    ← $from ($type)"
                done
            fi
            echo ""
        fi
    fi
fi

if $FOUND; then
    exit 0
else
    echo "[route-query] 未找到与 '${QUERY}' 相关的任何记录"
    echo "  建议: 尝试模糊搜索或回退到源码 Grep"
    exit 1
fi
