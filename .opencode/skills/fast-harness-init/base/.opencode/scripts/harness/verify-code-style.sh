#!/bin/bash
# verify-code-style.sh — 代码风格硬约束校验
# 在 git commit 前自动执行，检查暂存区的 Java/Kotlin 文件
#
# 校验规则来源: knowledge/current/style/code-style.md
#
# 可检测项（硬约束）：
#   1. 行宽不超过 170 字符硬上限（120 软限制 + 豁免场景容量）
#   2. Tab 缩进检测（必须使用空格）
#
# 不检测项（由 Committer 角色人工审查）：
#   - 120 字符软限制（含豁免判断：方法≤5参数、条件≤3个、其他≤3调用或≤30字符）
#   - 不满 120 字符的不必要换行（ASSERT 级）
#   - 访问权限完整性
#   - JavaDoc 注释
#   - 类成员排列顺序
#   - 换行语义完整性

set -u

# 硬上限 170：超过豁免可能的最大范围（120 基准 + 豁免场景容量）
# 120 字符软限制由 Committer 角色人工审查（含豁免判断）
MAX_LINE_WIDTH=170
ERRORS=""
ERROR_COUNT=0

# 获取暂存区中变更的 Java/Kotlin 文件
if [ "${1:-}" = "--all" ]; then
    # --all 模式：检查所有传入的文件
    shift
    STAGED_FILES="$@"
else
    # 默认模式：检查 git 暂存区
    STAGED_FILES=$(git diff --cached --name-only --diff-filter=ACM 2>/dev/null | grep -E '\.(java|kt)$' | grep -v '/test/' | grep -v '/androidTest/' || true)
fi

if [ -z "$STAGED_FILES" ]; then
    exit 0
fi

for FILE in $STAGED_FILES; do
    if [ ! -f "$FILE" ]; then
        continue
    fi

    LINE_NUM=0
    while IFS= read -r LINE || [ -n "$LINE" ]; do
        LINE_NUM=$((LINE_NUM + 1))
        LINE_LEN=${#LINE}

        # 豁免标记: 行内包含 harness:ignore-style 则跳过该行所有检查
        if echo "$LINE" | grep -q 'harness:ignore-style'; then
            continue
        fi

        # 检查 1: 行宽超过 170 字符
        if [ "$LINE_LEN" -gt "$MAX_LINE_WIDTH" ]; then
            # 豁免: import 语句
            if echo "$LINE" | grep -qE '^\s*import '; then
                continue
            fi
            # 豁免: package 语句
            if echo "$LINE" | grep -qE '^\s*package '; then
                continue
            fi
            # 豁免: 注释中的 URL
            if echo "$LINE" | grep -qE '^\s*(//|\*|/\*\*).*https?://'; then
                continue
            fi
            # 豁免: 字符串字面量（整行主要是字符串拼接）
            if echo "$LINE" | grep -qE '^\s*(LogUtils\.|Log\.)|"[^"]{80,}"'; then
                continue
            fi

            ERRORS="${ERRORS}\n  ${FILE}:${LINE_NUM} 行宽 ${LINE_LEN} 字符（上限 ${MAX_LINE_WIDTH}）"
            ERRORS="${ERRORS}\n    误报? 在行尾添加: // harness:ignore-style"
            ERROR_COUNT=$((ERROR_COUNT + 1))
        fi

        # 检查 2: Tab 缩进
        if printf '%s' "$LINE" | grep -q '	'; then
            ERRORS="${ERRORS}\n  ${FILE}:${LINE_NUM} 使用了 Tab 缩进（必须使用空格）"
            ERRORS="${ERRORS}\n    误报? 在行尾添加: // harness:ignore-style"
            ERROR_COUNT=$((ERROR_COUNT + 1))
        fi

    done < "$FILE"
done

if [ "$ERROR_COUNT" -gt 0 ]; then
    echo ""
    echo "[Harness] 代码风格校验失败（${ERROR_COUNT} 处违规）:"
    echo -e "$ERRORS"
    echo ""
    echo "  规范参考: knowledge/current/style/code-style.md"
    echo ""
    echo "STYLE_CHECK_RESULT=FAIL"
    exit 1
fi

echo "STYLE_CHECK_RESULT=PASS"
exit 0
