#!/usr/bin/env bash
#
# 构建并安装 release 版到已连接的 adb 设备。
#
# 同一 release 密钥签名的新旧版本之间可直接覆盖安装（-r，保留应用数据）；
# 但 debug 包与 release 包签名不同（app/build.gradle.kts 中 debug 使用独立 debug.keystore），
# 从 debug 切到 release（或反向）需先卸载，否则会报签名不一致。
#
# 前置条件：
#   1. 仓库根目录存在 keystore.properties，且其中 storeFile 指向的 keystore 文件也存在
#      （两者都在 .gitignore 中，不入库）
#   2. 设备已通过 adb 连接并授权
#
# 用法：
#   ./scripts/install-release.sh                 # 构建 + 安装（单设备时自动选择）
#   ./scripts/install-release.sh --no-build      # 跳过构建，直接安装现有 APK
#   ADB_SERIAL=<serial> ./scripts/install-release.sh   # 多设备时显式指定
#
# 可覆盖的环境变量：
#   ADB          adb 可执行文件路径，默认 $HOME/Library/Android/sdk/platform-tools/adb
#   ADB_SERIAL   目标设备 serial
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="com.nichx.niplayer"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
APK="$REPO_ROOT/app/build/outputs/apk/release/app-release.apk"

BUILD=1
if [[ "${1:-}" == "--no-build" ]]; then
  BUILD=0
fi

if [[ ! -x "$ADB" ]]; then
  echo "错误：找不到可执行的 adb：$ADB" >&2
  echo "       可用 ADB=/path/to/adb 覆盖。" >&2
  exit 1
fi

# ---------- 选择目标设备 ----------
DEVICES=()
while IFS= read -r line; do
  if [[ -n "$line" ]]; then
    DEVICES+=("$line")
  fi
done < <("$ADB" devices 2>/dev/null | awk 'NR > 1 && $2 == "device" { print $1 }')

if [[ -n "${ADB_SERIAL:-}" ]]; then
  SERIAL="$ADB_SERIAL"
elif (( ${#DEVICES[@]} == 0 )); then
  echo "错误：没有已连接且已授权的 adb 设备（可用 adb devices 查看）。" >&2
  exit 1
elif (( ${#DEVICES[@]} == 1 )); then
  SERIAL="${DEVICES[0]}"
else
  echo "错误：检测到多个设备，请用 ADB_SERIAL=<serial> 指定其一：" >&2
  printf '  %s\n' "${DEVICES[@]}" >&2
  exit 1
fi
echo "==> 目标设备：$SERIAL"

# ---------- 构建 ----------
if (( BUILD )); then
  echo "==> 构建 release APK（:app:assembleRelease）…"
  ( cd "$REPO_ROOT" && ./gradlew :app:assembleRelease )
fi

if [[ ! -f "$APK" ]]; then
  echo "错误：未找到 $APK（去掉 --no-build 重跑以先构建）。" >&2
  exit 1
fi

# ---------- 安装 ----------
echo "==> 覆盖安装（-r，保留应用数据）…"
"$ADB" -s "$SERIAL" install -r "$APK"

# ---------- 校验 ----------
echo "==> 安装结果："
"$ADB" -s "$SERIAL" shell dumpsys package "$PKG" \
  | grep -E 'versionName=|versionCode=|pkgFlags=' | head -3

if "$ADB" -s "$SERIAL" shell dumpsys package "$PKG" | grep -q 'pkgFlags=\[.*DEBUGGABLE'; then
  echo "注意：当前装的是 debug 包（pkgFlags 含 DEBUGGABLE）。" >&2
else
  echo "✅ 已安装 release 包（pkgFlags 无 DEBUGGABLE）。"
fi