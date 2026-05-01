#!/bin/bash
# verify-secrets.sh — 门禁 6: 硬编码密钥/Token 扫描
# Verifier 阶段必须通过此脚本验证代码中不包含硬编码的密钥和 Token
#
# 用法: .opencode/scripts/harness/verify-secrets.sh
#
# 退出码: 0=通过, 1=发现硬编码密钥/Token

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/../../.." && pwd)"

echo "[Harness 门禁] 硬编码密钥/Token 扫描开始..."
echo ""

# 只扫描本次变更涉及的 Java/Kotlin 文件（未提交的变更）
CHANGED_FILES=$(cd "$PROJECT_DIR" && git diff --name-only --diff-filter=ACMR HEAD 2>/dev/null || git diff --name-only --cached 2>/dev/null || echo "")

if [ -z "$CHANGED_FILES" ]; then
    echo "  未检测到变更文件，跳过扫描。"
    echo "[Harness 门禁] ✅ 密钥扫描通过（无变更文件）"
    exit 0
fi

# 过滤出 Java/Kotlin 源码文件
SOURCE_FILES=$(echo "$CHANGED_FILES" | grep -E '\.(java|kt)$' || true)

if [ -z "$SOURCE_FILES" ]; then
    echo "  变更文件中无 Java/Kotlin 源码，跳过扫描。"
    echo "[Harness 门禁] ✅ 密钥扫描通过（无源码变更）"
    exit 0
fi

echo "  扫描 $(echo "$SOURCE_FILES" | wc -l | tr -d ' ') 个源码文件..."
echo ""

FOUND_SECRETS=0
REPORT=""

# 硬编码密钥/Token 的正则模式
# 注意：排除测试文件、注释中的示例、以及已知的常量名
PATTERNS=(
    # API Key 模式: "key" = "实际值"（至少16位字母数字混合）
    '["\x27](api[_-]?key|apikey|api[_-]?secret)["\x27]\s*[:=]\s*["\x27][A-Za-z0-9+/=]{16,}["\x27]'
    # Token 模式: "token" = "实际值"
    '["\x27](access[_-]?token|auth[_-]?token|bearer[_-]?token|secret[_-]?token)["\x27]\s*[:=]\s*["\x27][A-Za-z0-9+/=._-]{16,}["\x27]'
    # 密码模式: "password" = "实际值"
    '["\x27](password|passwd|pwd)["\x27]\s*[:=]\s*["\x27][^\x27"]{8,}["\x27]'
    # AWS 风格密钥
    'AKIA[0-9A-Z]{16}'
    # 私钥
    '-----BEGIN (RSA |EC |DSA )?PRIVATE KEY-----'
    # 硬编码的 Base64 密钥（至少32位，赋值给变量）
    '(SECRET|PRIVATE|CREDENTIAL|SIGNING)[_A-Z]*\s*=\s*["\x27][A-Za-z0-9+/=]{32,}["\x27]'
)

cd "$PROJECT_DIR"

for file in $SOURCE_FILES; do
    # 跳过不存在的文件（可能已删除）
    [ -f "$file" ] || continue

    # 跳过测试文件
    if echo "$file" | grep -qE '(Test|test|Mock|mock|Fake|fake|Sample|sample)'; then
        continue
    fi

    for pattern in "${PATTERNS[@]}"; do
        MATCHES=$(grep -nE "$pattern" "$file" 2>/dev/null || true)
        if [ -n "$MATCHES" ]; then
            # 二次过滤：排除注释行和空字符串赋值
            REAL_MATCHES=$(echo "$MATCHES" | grep -vE '^\s*//' | grep -vE '""' | grep -vE "''" || true)
            if [ -n "$REAL_MATCHES" ]; then
                FOUND_SECRETS=1
                REPORT="${REPORT}\n  📄 ${file}:\n"
                while IFS= read -r line; do
                    REPORT="${REPORT}    ${line}\n"
                done <<< "$REAL_MATCHES"
            fi
        fi
    done
done

if [ "$FOUND_SECRETS" -eq 1 ]; then
    echo "[Harness 门禁] ❌ 发现疑似硬编码密钥/Token:"
    echo -e "$REPORT"
    echo ""
    echo "  处理方式:"
    echo "    1. 如果是真实密钥: 立即移除，改用环境变量或配置中心"
    echo "    2. 如果是误报（常量名/占位符）: 在该行添加注释 // harness:ignore-secret"
    echo ""
    echo "[Harness 门禁] ❌ 密钥扫描失败"
    exit 1
fi

echo "[Harness 门禁] ✅ 密钥扫描通过：未发现硬编码密钥/Token"
exit 0
