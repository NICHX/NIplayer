#!/usr/bin/env bash
#
# 构建并安装 release 版到已连接的 adb 设备。
#
# 同一签名的新旧版本之间可直接覆盖安装（-r，保留应用数据）；但 debug 包与 release 包签名不同
# （app/build.gradle.kts 中 debug 使用独立 debug.keystore），从 debug 切到 release（或反向）
# 需先卸载，否则会报签名不一致。且 Android **不允许跨签名保留数据**：`uninstall -k` 也救不回来。
#
# 因此提供两种模式：
#   默认：用 keystore.properties 里的正式 release.keystore 构建并安装（签名与正式包一致，可分发）。
#   --keep-data（别名 --debug-sign）：
#       用 debug.keystore 给 release 构建签名，从而能覆盖已装的 debug 包、**保留应用数据**。
#       代价：非正式 release 签名，不能分发；以后换正式签名包仍需先卸载（会清数据）。
#
# 前置条件：
#   1. 默认模式需仓库根目录存在 keystore.properties 且 storeFile 指向的 keystore 存在
#      （两者都在 .gitignore 中，不入库）
#   2. 设备已通过 adb 连接并授权
#
# 用法：
#   ./scripts/install-release.sh                 # 构建 + 安装（正式 release 签名）
#   ./scripts/install-release.sh --keep-data     # 构建 + 覆盖安装，保留数据（debug 签名）
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

# debug.keystore 的固定凭据（与 app/build.gradle.kts 的 debug signingConfig 一致）。
DEBUG_KEYSTORE_REL="app/debug.keystore"
DEBUG_STORE_PASSWORD="android"
DEBUG_KEY_ALIAS="androiddebugkey"
DEBUG_KEY_PASSWORD="android"

BUILD=1
KEEP_DATA=0

usage() {
  sed -n '3,30p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-build) BUILD=0 ;;
    --keep-data|--debug-sign) KEEP_DATA=1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "错误：未知参数 $1（可用 --no-build / --keep-data / --help）" >&2; exit 1 ;;
  esac
  shift
done

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
  if (( KEEP_DATA )); then
    if [[ ! -f "$REPO_ROOT/$DEBUG_KEYSTORE_REL" ]]; then
      echo "错误：未找到 debug keystore：$REPO_ROOT/$DEBUG_KEYSTORE_REL" >&2
      exit 1
    fi
    echo "==> 构建 release APK（用 debug 签名，保留数据）…"
    rm -f "$APK"
    # RELEASE_STORE_* 优先于 keystore.properties，故此处覆盖为 debug 证书。
    # --no-configuration-cache：签名环境变量在配置期读取，配置缓存不会感知其变化，必须关闭。
    (
      cd "$REPO_ROOT"
      RELEASE_STORE_FILE="$DEBUG_KEYSTORE_REL" \
      RELEASE_STORE_PASSWORD="$DEBUG_STORE_PASSWORD" \
      RELEASE_KEY_ALIAS="$DEBUG_KEY_ALIAS" \
      RELEASE_KEY_PASSWORD="$DEBUG_KEY_PASSWORD" \
        ./gradlew :app:assembleRelease --no-configuration-cache
    )
  else
    echo "==> 构建 release APK（:app:assembleRelease，正式签名）…"
    ( cd "$REPO_ROOT" && ./gradlew :app:assembleRelease )
  fi
fi

if [[ ! -f "$APK" ]]; then
  echo "错误：未找到 $APK（去掉 --no-build 重跑以先构建）。" >&2
  exit 1
fi

# ---------- 安装（-r 覆盖，保留应用数据）----------
echo "==> 覆盖安装（-r，保留应用数据）…"
if ! "$ADB" -s "$SERIAL" install -r "$APK"; then
  echo "错误：安装失败。" >&2
  if (( KEEP_DATA )); then
    echo "提示：--keep-data 用 debug 签名，仅能覆盖同为 debug 签名的旧包；" >&2
    echo "      若设备上当前是正式 release 包，请改用默认模式并先卸载（会清数据）。" >&2
  else
    echo "提示：设备上当前若是 debug 包（签名不同），请先卸载旧包，或改用 --keep-data 保数据安装。" >&2
  fi
  exit 1
fi

# ---------- 校验 ----------
echo "==> 安装结果："
"$ADB" -s "$SERIAL" shell dumpsys package "$PKG" \
  | grep -E 'versionName=|versionCode=|pkgFlags=' | head -3

if "$ADB" -s "$SERIAL" shell dumpsys package "$PKG" | grep -q 'pkgFlags=\[.*DEBUGGABLE'; then
  echo "注意：当前装的是 debug 包（pkgFlags 含 DEBUGGABLE）。" >&2
elif (( KEEP_DATA )); then
  echo "✅ 已安装 release 构建（debug 签名，保留数据；不可分发，换正式签名需先卸载）。"
else
  echo "✅ 已安装 release 包（pkgFlags 无 DEBUGGABLE）。"
fi
