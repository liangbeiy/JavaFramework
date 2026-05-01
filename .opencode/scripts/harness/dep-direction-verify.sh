#!/bin/bash
# dep-direction-verify.sh — 依赖方向校验工具
# 检查本次变更是否引入了违反架构约束的反向依赖
#
# 架构约束: app → Platform 层 → Game 层 → Base 层，禁止反向
#
# 用法: dep-direction-verify.sh
#
# 校验逻辑:
#   1. 扫描本次变更涉及的 Java/Kotlin 文件中的 import 语句
#   2. 对照 module-deps.tsv 中的合法依赖关系
#   3. 检测是否存在底层模块 import 上层模块的类
#
# 退出码: 0=通过, 1=发现反向依赖

set -uo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/../../.." && pwd)"
ROUTER_DIR="$PROJECT_DIR/knowledge/current/router"
MODULE_DEPS="$ROUTER_DIR/module-deps.tsv"
SYMBOL_TABLE="$ROUTER_DIR/symbol-table.tsv"

echo "[Harness 门禁] 依赖方向校验开始..."
echo ""

if [ ! -f "$MODULE_DEPS" ]; then
    echo "  警告: module-deps.tsv 不存在，跳过校验"
    echo "[Harness 门禁] ⚠️ 依赖方向校验跳过（索引缺失）"
    exit 0
fi

# ===== 定义层级关系 =====
# 层级越高数字越大，高层可以依赖低层，反之不行
# 层级定义基于 AGENTS.md: app → Platform 层 → Game 层 → Base 层
# 注意：macOS 自带 bash 3.x 不支持 declare -A，用函数查表替代
get_layer() {
    case "$1" in
        # Base 层 (1)
        GameBaseLib|PlatformBaseLib|AnnotationLib) echo 1 ;;
        # Game 层 (2)
        GameCoreLib|GameBridgeLib|GameRuntimeLib|GameDownloadLib|GameQuickDownloadLib) echo 2 ;;
        GameCloudConfigLib|GamePermissionLib|GameFeatureLib|GameAdLib) echo 2 ;;
        GameDebuggerLib|GamePlatformLib|AnnotationProcessorLib) echo 2 ;;
        # Platform 层 (3)
        PlatformCoreLib|PlatformProcessLib|PlatformPreloadProcessLib|QuickGamePlatformSDK) echo 3 ;;
        # App 层 (4)
        app) echo 4 ;;
        # 未知模块
        *) echo 0 ;;
    esac
}

# ===== 获取变更文件 =====
CHANGED_FILES=$(cd "$PROJECT_DIR" && git diff --name-only --diff-filter=ACMR HEAD 2>/dev/null || git diff --name-only --cached 2>/dev/null || echo "")

if [ -z "$CHANGED_FILES" ]; then
    echo "  未检测到变更文件，跳过校验。"
    echo "[Harness 门禁] ✅ 依赖方向校验通过（无变更文件）"
    exit 0
fi

# 过滤出 Java/Kotlin 源码
SOURCE_FILES=$(echo "$CHANGED_FILES" | grep -E '\.(java|kt)$' | grep -v '/test/' | grep -v '/androidTest/' || true)

if [ -z "$SOURCE_FILES" ]; then
    echo "  变更文件中无 Java/Kotlin 源码，跳过校验。"
    echo "[Harness 门禁] ✅ 依赖方向校验通过（无源码变更）"
    exit 0
fi

echo "  扫描 $(echo "$SOURCE_FILES" | wc -l | tr -d ' ') 个变更文件..."
echo ""

VIOLATIONS=""
VIOLATION_COUNT=0

for FILE in $SOURCE_FILES; do
    [ -f "$PROJECT_DIR/$FILE" ] || continue

    # 确定该文件所属模块（从文件路径的第一段提取）
    FILE_MODULE=$(echo "$FILE" | cut -d'/' -f1)

    # 如果无法确定模块，跳过
    [ -z "$FILE_MODULE" ] && continue
    FILE_LAYER=$(get_layer "$FILE_MODULE")
    [ "$FILE_LAYER" -eq 0 ] && continue

    # 扫描 import 语句，提取包名
    IMPORTS=$(grep -E '^\s*import\s+com\.xiaomi\.minigame\.' "$PROJECT_DIR/$FILE" 2>/dev/null || true)
    [ -z "$IMPORTS" ] && continue

    # 对每个 import，确定其所属模块
    while IFS= read -r import_line; do
        # 提取全限定类名
        FQN=$(echo "$import_line" | sed 's/^\s*import\s\+//' | sed 's/\s*;\s*$//' | sed 's/\s*$//')
        # 提取简单类名（最后一个 . 之后的部分）
        SIMPLE_CLASS=$(echo "$FQN" | awk -F. '{print $NF}')

        # 在 symbol-table 中查找该类所属模块
        IMPORT_MODULE=$(grep "^${SIMPLE_CLASS}	" "$SYMBOL_TABLE" 2>/dev/null | head -1 | cut -f2 || true)
        [ -z "$IMPORT_MODULE" ] && continue

        IMPORT_LAYER=$(get_layer "$IMPORT_MODULE")
        [ "$IMPORT_LAYER" -eq 0 ] && continue

        # 检查: 如果低层模块 import 了高层模块的类 → 反向依赖
        if [ "$FILE_LAYER" -lt "$IMPORT_LAYER" ]; then
            VIOLATIONS="${VIOLATIONS}\n  ❌ ${FILE}:${FILE_MODULE}(层${FILE_LAYER}) → ${SIMPLE_CLASS}:${IMPORT_MODULE}(层${IMPORT_LAYER})"
            VIOLATIONS="${VIOLATIONS}\n     底层模块不应依赖上层模块"
            VIOLATION_COUNT=$((VIOLATION_COUNT + 1))
        fi

        # 检查: 同层内是否存在 module-deps.tsv 中未声明的依赖
        if [ "$FILE_MODULE" != "$IMPORT_MODULE" ]; then
            DECLARED=$(grep "^${FILE_MODULE}	${IMPORT_MODULE}	" "$MODULE_DEPS" 2>/dev/null || true)
            if [ -z "$DECLARED" ] && [ "$FILE_LAYER" -eq "$IMPORT_LAYER" ]; then
                VIOLATIONS="${VIOLATIONS}\n  ⚠️ ${FILE}:${FILE_MODULE} → ${SIMPLE_CLASS}:${IMPORT_MODULE}"
                VIOLATIONS="${VIOLATIONS}\n     同层跨模块依赖未在 module-deps.tsv 中声明（可能是新增依赖）"
                VIOLATION_COUNT=$((VIOLATION_COUNT + 1))
            fi
        fi

    done <<< "$IMPORTS"
done

if [ "$VIOLATION_COUNT" -gt 0 ]; then
    echo "[Harness 门禁] ❌ 发现 ${VIOLATION_COUNT} 处依赖方向问题:"
    echo -e "$VIOLATIONS"
    echo ""
    echo "  架构约束: app(4) → Platform(3) → Game(2) → Base(1)，禁止反向"
    echo "  修复方式:"
    echo "    1. 反向依赖: 通过接口/回调/EventBus 解耦"
    echo "    2. 未声明依赖: 在 build.gradle 中声明并更新路由索引"
    echo ""
    echo "[Harness 门禁] ❌ 依赖方向校验失败"
    exit 1
fi

echo "[Harness 门禁] ✅ 依赖方向校验通过：未发现反向依赖"
exit 0
