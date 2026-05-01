#!/bin/bash
# sonar-delta-scan.sh — SonarQube 变更区间扫描门禁
# 仅扫描 git diff 涉及的 .java/.kt 源码文件，通过 Quality Gate + Issues 严重性双重检查决定是否阻断
#
# 工作流程：
#   1. git diff 获取变更的 .java/.kt 文件（排除 test 路径）
#   2. 路径转换：
#      - sonar.inclusions: strip "^[^/]+/src/main/(java|kotlin)/" 前缀（相对于 sonar.sources）
#      - API files 参数: 使用完整 git diff 路径（如 ModuleName/src/main/java/com/...）
#   3. 运行 ./gradlew sonar -Dsonar.inclusions=<patterns>
#   4. curl API 查询 Quality Gate 状态
#   5. curl API 查询 BLOCKER/CRITICAL issues（按变更文件过滤）
#   6. 输出结果，决定退出码
#
# SonarQube API `files` 参数格式调研结果（Task 0.1）：
#   - 测试: files=src/main/java/com/.../GameScreenUtils.java → total=0（不匹配）
#   - 测试: files=com/.../GameScreenUtils.java → total=0（不匹配）
#   - 测试: files=GameBaseLib/src/main/java/com/.../GameScreenUtils.java → total=3（匹配）
#   结论: API files 参数需要完整 git diff 路径（含模块名和 src/main/java 前缀）
#
# 退出码: 0=通过, 1=阻断（Quality Gate ERROR 或 BLOCKER/CRITICAL >= 1）, 2=警告通过
# 用法: sonar-delta-scan.sh

set -uo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/../../.." && pwd)"

# ===== 配置 =====
SONAR_HOST="https://sonarqube.mioffice.cn"
SONAR_TOKEN="sqp_49c82723db30a816c1fd09680ccc3c1f97ca7ca0"
SONAR_PROJECT_KEY="Minigame"
SCAN_TIMEOUT=300  # 5 分钟
API_TIMEOUT=30    # 30 秒

# ===== jq 可用性检测 =====
USE_JQ=false
if command -v jq &>/dev/null; then
	USE_JQ=true
else
	echo "  ⚠️  jq 未安装，使用降级解析（grep+sed）"
	echo ""
fi

echo "================================================================"
echo "  [Harness 门禁] SonarQube 变更区间扫描"
echo "================================================================"
echo ""

# ===== JSON 解析辅助函数 =====
# 从 JSON 中提取简单字段值（降级模式使用）
json_value() {
	local json="$1"
	local key="$2"
	if $USE_JQ; then
		echo "$json" | jq -r "$key" 2>/dev/null
	else
		# 降级：用 grep+sed 提取 "key":"value" 或 "key":number
		echo "$json" | grep -o "\"${key}\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | head -1 | sed 's/.*:[[:space:]]*"\([^"]*\)"/\1/' || \
		echo "$json" | grep -o "\"${key}\"[[:space:]]*:[[:space:]]*[0-9]*" | head -1 | sed 's/.*:[[:space:]]*//'
	fi
}

# 从 JSON 中提取嵌套字段（如 .projectStatus.status）
json_nested() {
	local json="$1"
	local jq_path="$2"
	local grep_key="$3"
	if $USE_JQ; then
		echo "$json" | jq -r "$jq_path" 2>/dev/null
	else
		json_value "$json" "$grep_key"
	fi
}

# ===== 获取变更文件 =====
cd "$PROJECT_DIR"
CHANGED_FILES=$(git diff --name-only HEAD 2>/dev/null || git diff --name-only --cached 2>/dev/null || echo "")

if [ -z "$CHANGED_FILES" ]; then
	echo "  未检测到变更文件。"
	echo ""
	echo "[Harness 门禁] ✅ SonarQube 扫描跳过（无变更文件）"
	exit 0
fi

# 过滤 .java/.kt 源码文件，排除 test 路径
SOURCE_FILES=$(echo "$CHANGED_FILES" | grep -E '\.(java|kt)$' | grep -v '/test/' | grep -v '/androidTest/' || true)

if [ -z "$SOURCE_FILES" ]; then
	echo "  无源码变更，跳过 SonarQube 扫描。"
	echo "  （变更文件中无 .java/.kt 文件，或全部为测试文件）"
	echo ""
	echo "[Harness 门禁] ✅ SonarQube 扫描跳过（无源码变更）"
	exit 0
fi

SOURCE_COUNT=$(echo "$SOURCE_FILES" | wc -l | tr -d ' ')
echo "--- 变更源码文件（${SOURCE_COUNT} 个）---"
echo "$SOURCE_FILES" | while read -r f; do echo "  $f"; done
echo ""

# ===== 路径转换 =====
# 两种路径格式：
#   1. sonar_inclusions: strip "ModuleName/src/main/(java|kotlin)/" → 给 Gradle 插件
#   2. api_files: 保留完整 git diff 路径 → 给 API files 参数
SONAR_INCLUSIONS=""
API_FILES=""
SKIPPED_FILES=""

while IFS= read -r filepath; do
	# 尝试转换为 sonar.inclusions 格式
	# 使用 # 作为 sed 分隔符，避免 macOS BSD sed 将 | 分隔符与 ERE 交替操作符混淆
	converted=$(echo "$filepath" | sed -E 's#^[^/]+/src/main/(java|kotlin)/##')
	if [ "$converted" = "$filepath" ]; then
		# 未匹配，说明路径格式不符合 ModuleName/src/main/(java|kotlin)/... 模式
		SKIPPED_FILES="${SKIPPED_FILES}${filepath}\n"
		echo "  ⚠️  跳过无法转换的路径: ${filepath}"
	else
		if [ -n "$SONAR_INCLUSIONS" ]; then
			SONAR_INCLUSIONS="${SONAR_INCLUSIONS},${converted}"
			API_FILES="${API_FILES},${filepath}"
		else
			SONAR_INCLUSIONS="${converted}"
			API_FILES="${filepath}"
		fi
	fi
done <<< "$SOURCE_FILES"

if [ -z "$SONAR_INCLUSIONS" ]; then
	echo ""
	echo "  所有源码文件路径均无法转换为 sonar.inclusions 格式。"
	echo ""
	echo "[Harness 门禁] ✅ SonarQube 扫描跳过（无可扫描路径）"
	exit 0
fi

echo ""
echo "--- sonar.inclusions ---"
echo "  ${SONAR_INCLUSIONS}" | tr ',' '\n' | while read -r p; do [ -n "$p" ] && echo "  $p"; done
echo ""

# ===== 执行 SonarQube 扫描 =====
echo "--- 执行 SonarQube 扫描 ---"
SCAN_START=$(date +%s)

# macOS 兼容的 timeout 替代：优先使用 timeout/gtimeout，回退到 bash 后台进程实现
_timeout_cmd() {
	local secs="$1"; shift
	if command -v timeout &>/dev/null; then
		timeout "$secs" "$@"
	elif command -v gtimeout &>/dev/null; then
		gtimeout "$secs" "$@"
	else
		# 纯 bash 实现：后台运行命令，超时则 kill
		"$@" &
		local cmd_pid=$!
		( sleep "$secs" && kill "$cmd_pid" 2>/dev/null ) &
		local watchdog_pid=$!
		wait "$cmd_pid" 2>/dev/null
		local exit_code=$?
		kill "$watchdog_pid" 2>/dev/null
		wait "$watchdog_pid" 2>/dev/null
		return $exit_code
	fi
}

run_sonar() {
	local extra_args="${1:-}"
	_timeout_cmd "${SCAN_TIMEOUT}" ./gradlew sonar \
		-Dsonar.inclusions="${SONAR_INCLUSIONS}" \
		${extra_args} \
		2>&1
}

SCAN_OUTPUT=""
SCAN_EXIT=0
SCAN_OUTPUT=$(run_sonar) || SCAN_EXIT=$?

# Gradle daemon 冲突检测与重试（D6）
if [ "$SCAN_EXIT" -ne 0 ]; then
	if echo "$SCAN_OUTPUT" | grep -qiE "Could not dispatch|daemon.*busy|daemon.*lock"; then
		echo "  ⚠️  检测到 Gradle daemon 冲突，使用 --no-daemon 重试..."
		SCAN_EXIT=0
		SCAN_OUTPUT=$(run_sonar "--no-daemon") || SCAN_EXIT=$?
	fi
fi

SCAN_END=$(date +%s)
SCAN_DURATION=$((SCAN_END - SCAN_START))

if [ "$SCAN_EXIT" -eq 124 ]; then
	echo "  ❌ SonarQube 扫描超时（${SCAN_TIMEOUT}秒）"
	echo ""
	echo "[Harness 门禁] ❌ SonarQube 扫描未通过（扫描超时）"
	exit 1
fi

if [ "$SCAN_EXIT" -ne 0 ]; then
	echo "  ❌ SonarQube 扫描执行失败（退出码: ${SCAN_EXIT}）"
	# 输出最后 10 行帮助诊断
	echo "$SCAN_OUTPUT" | tail -10 | while read -r line; do echo "    $line"; done
	echo ""
	echo "[Harness 门禁] ❌ SonarQube 扫描未通过（扫描执行失败）"
	exit 1
fi

echo "  ✅ 扫描完成（耗时 ${SCAN_DURATION}秒）"
echo ""

# ===== 等待服务端处理（短暂延迟确保数据可查） =====
sleep 3

# ===== Quality Gate 状态查询 =====
echo "--- Quality Gate 状态 ---"
QG_RESPONSE=""
QG_STATUS=""
QG_EXIT=0

QG_RESPONSE=$(curl -sf --max-time "${API_TIMEOUT}" \
	-u "${SONAR_TOKEN}:" \
	"${SONAR_HOST}/api/qualitygates/project_status?projectKey=${SONAR_PROJECT_KEY}" 2>&1) || QG_EXIT=$?

if [ "$QG_EXIT" -ne 0 ] || [ -z "$QG_RESPONSE" ]; then
	echo "  ⚠️  Quality Gate 查询失败（网络不可达或服务端异常）"
	echo ""
	echo "[Harness 门禁] ⚠️ SonarQube 扫描警告通过（Quality Gate 查询失败）"
	exit 2
fi

QG_STATUS=$(json_nested "$QG_RESPONSE" ".projectStatus.status" "status")

if [ "$QG_STATUS" = "ERROR" ]; then
	echo "  ❌ Quality Gate: ERROR"
elif [ "$QG_STATUS" = "OK" ]; then
	echo "  ✅ Quality Gate: OK"
else
	echo "  ⚠️  Quality Gate: ${QG_STATUS}（未知状态）"
fi
echo ""

# ===== Issues 严重性查询 =====
echo "--- Issues 查询（变更文件范围）---"

# 查询 BLOCKER + CRITICAL
BC_RESPONSE=""
BC_TOTAL=0
BC_EXIT=0

BC_RESPONSE=$(curl -sf --max-time "${API_TIMEOUT}" \
	-u "${SONAR_TOKEN}:" \
	--data-urlencode "componentKeys=${SONAR_PROJECT_KEY}" \
	--data-urlencode "severities=BLOCKER,CRITICAL" \
	--data-urlencode "statuses=OPEN,CONFIRMED,REOPENED" \
	--data-urlencode "files=${API_FILES}" \
	--data-urlencode "ps=1" \
	-G "${SONAR_HOST}/api/issues/search" 2>&1) || BC_EXIT=$?

if [ "$BC_EXIT" -ne 0 ] || [ -z "$BC_RESPONSE" ]; then
	echo "  ⚠️  Issues 查询失败（网络不可达或服务端异常）"
	echo ""
	echo "[Harness 门禁] ⚠️ SonarQube 扫描警告通过（Issues 查询失败）"
	exit 2
fi

BC_TOTAL=$(json_nested "$BC_RESPONSE" ".total" "total")
BC_TOTAL=${BC_TOTAL:-0}

# 查询全部严重性（用于报告）
ALL_RESPONSE=""
ALL_TOTAL=0
ALL_EXIT=0

ALL_RESPONSE=$(curl -sf --max-time "${API_TIMEOUT}" \
	-u "${SONAR_TOKEN}:" \
	--data-urlencode "componentKeys=${SONAR_PROJECT_KEY}" \
	--data-urlencode "statuses=OPEN,CONFIRMED,REOPENED" \
	--data-urlencode "files=${API_FILES}" \
	--data-urlencode "ps=1" \
	--data-urlencode "facets=severities" \
	-G "${SONAR_HOST}/api/issues/search" 2>&1) || ALL_EXIT=$?

# 解析各严重性数量
BLOCKER_COUNT=0
CRITICAL_COUNT=0
MAJOR_COUNT=0
MINOR_COUNT=0
INFO_COUNT=0
ALL_TOTAL=0

if [ "$ALL_EXIT" -eq 0 ] && [ -n "$ALL_RESPONSE" ]; then
	ALL_TOTAL=$(json_nested "$ALL_RESPONSE" ".total" "total")
	ALL_TOTAL=${ALL_TOTAL:-0}
	if $USE_JQ; then
		BLOCKER_COUNT=$(echo "$ALL_RESPONSE" | jq -r '.facets[0].values[] | select(.val=="BLOCKER") | .count' 2>/dev/null || echo "0")
		CRITICAL_COUNT=$(echo "$ALL_RESPONSE" | jq -r '.facets[0].values[] | select(.val=="CRITICAL") | .count' 2>/dev/null || echo "0")
		MAJOR_COUNT=$(echo "$ALL_RESPONSE" | jq -r '.facets[0].values[] | select(.val=="MAJOR") | .count' 2>/dev/null || echo "0")
		MINOR_COUNT=$(echo "$ALL_RESPONSE" | jq -r '.facets[0].values[] | select(.val=="MINOR") | .count' 2>/dev/null || echo "0")
		INFO_COUNT=$(echo "$ALL_RESPONSE" | jq -r '.facets[0].values[] | select(.val=="INFO") | .count' 2>/dev/null || echo "0")
	else
		# 降级：从 facets 中按序提取
		BLOCKER_COUNT=$(echo "$ALL_RESPONSE" | grep -o '"BLOCKER"[^}]*"count":[0-9]*' | grep -o '[0-9]*$' || echo "0")
		CRITICAL_COUNT=$(echo "$ALL_RESPONSE" | grep -o '"CRITICAL"[^}]*"count":[0-9]*' | grep -o '[0-9]*$' || echo "0")
		MAJOR_COUNT=$(echo "$ALL_RESPONSE" | grep -o '"MAJOR"[^}]*"count":[0-9]*' | grep -o '[0-9]*$' || echo "0")
		MINOR_COUNT=$(echo "$ALL_RESPONSE" | grep -o '"MINOR"[^}]*"count":[0-9]*' | grep -o '[0-9]*$' || echo "0")
		INFO_COUNT=$(echo "$ALL_RESPONSE" | grep -o '"INFO"[^}]*"count":[0-9]*' | grep -o '[0-9]*$' || echo "0")
	fi
fi

# 确保数值有效
BLOCKER_COUNT=${BLOCKER_COUNT:-0}
CRITICAL_COUNT=${CRITICAL_COUNT:-0}
MAJOR_COUNT=${MAJOR_COUNT:-0}
MINOR_COUNT=${MINOR_COUNT:-0}
INFO_COUNT=${INFO_COUNT:-0}

echo "  BLOCKER:  ${BLOCKER_COUNT}"
echo "  CRITICAL: ${CRITICAL_COUNT}"
echo "  MAJOR:    ${MAJOR_COUNT}"
echo "  MINOR:    ${MINOR_COUNT}"
echo "  INFO:     ${INFO_COUNT}"
echo "  总计:     ${ALL_TOTAL}"
echo ""

# ===== 阻断决策 =====
echo "================================================================"
echo "  扫描结果汇总"
echo "================================================================"
echo ""
echo "  Quality Gate:     ${QG_STATUS}"
echo "  BLOCKER/CRITICAL: ${BC_TOTAL}"
echo "  MAJOR:            ${MAJOR_COUNT}"
echo "  扫描耗时:         ${SCAN_DURATION}秒"
echo ""

EXIT_CODE=0

if [ "$QG_STATUS" = "ERROR" ] && [ "${BC_TOTAL}" -gt 0 ] 2>/dev/null; then
	# Cap6-WHEN1: QG ERROR + BLOCKER/CRITICAL >= 1
	echo "[Harness 门禁] ❌ SonarQube 扫描未通过"
	echo "  原因: Quality Gate ERROR 且变更文件存在 BLOCKER/CRITICAL issues（${BC_TOTAL} 个）"
	echo "  建议:"
	echo "    1. 修复变更文件中的 BLOCKER/CRITICAL issues"
	echo "    2. 查看详情: ${SONAR_HOST}/project/issues?id=${SONAR_PROJECT_KEY}&severities=BLOCKER,CRITICAL&files=${API_FILES}"
	EXIT_CODE=1
elif [ "$QG_STATUS" = "ERROR" ] && [ "${BC_TOTAL}" -eq 0 ] 2>/dev/null; then
	# Cap6-WHEN2: QG ERROR + 0 BLOCKER/CRITICAL（存量问题导致）
	echo "[Harness 门禁] ❌ SonarQube 扫描未通过"
	echo "  原因: Quality Gate ERROR（可能因存量问题，变更文件无 BLOCKER/CRITICAL issues）"
	echo "  建议:"
	echo "    1. 检查 Quality Gate 条件: ${SONAR_HOST}/dashboard?id=${SONAR_PROJECT_KEY}"
	echo "    2. 如确认为存量问题，可在人工 CR 中决定是否降级处理"
	EXIT_CODE=1
elif [ "$QG_STATUS" != "ERROR" ] && [ "${BC_TOTAL}" -gt 0 ] 2>/dev/null; then
	# Cap6-WHEN3: QG OK + BLOCKER/CRITICAL >= 1
	echo "[Harness 门禁] ❌ SonarQube 扫描未通过"
	echo "  原因: 变更文件引入 BLOCKER/CRITICAL issues（${BC_TOTAL} 个）"
	echo "  建议:"
	echo "    1. 修复变更文件中的 BLOCKER/CRITICAL issues"
	echo "    2. 查看详情: ${SONAR_HOST}/project/issues?id=${SONAR_PROJECT_KEY}&severities=BLOCKER,CRITICAL&files=${API_FILES}"
	EXIT_CODE=1
elif [ "${MAJOR_COUNT}" -gt 0 ] 2>/dev/null; then
	# Cap6-WHEN4: QG OK + 0 BLOCKER/CRITICAL + MAJOR > 0
	echo "[Harness 门禁] ⚠️ SonarQube 扫描警告通过"
	echo "  原因: 变更文件存在 MAJOR issues（${MAJOR_COUNT} 个），但无 BLOCKER/CRITICAL"
	echo "  建议: 人工 CR 时关注 MAJOR issues"
	EXIT_CODE=2
else
	# Cap6-WHEN5: QG OK + 0 BLOCKER/CRITICAL + 0 MAJOR
	echo "[Harness 门禁] ✅ SonarQube 扫描通过"
	EXIT_CODE=0
fi

echo ""
echo "SONAR_QG_STATUS=${QG_STATUS}"
echo "SONAR_BC_ISSUES=${BC_TOTAL}"
echo "SONAR_MAJOR_ISSUES=${MAJOR_COUNT}"
echo "SONAR_SCAN_DURATION=${SCAN_DURATION}s"

exit $EXIT_CODE
