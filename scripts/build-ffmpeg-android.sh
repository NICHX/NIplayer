#!/usr/bin/env bash
#
# 交叉编译 ffmpeg 静态库（Android arm64-v8a / armeabi-v7a）并覆盖仓库内的预置库。
#
# 用途：player:ffmpeg 模块用这三件事
#   1) 缩略图取帧（ffmpeg_frame_jni.cc，经 AVIOContext 桥接 MediaDataSource）
#   2) media3 音频软解（ffmpeg_jni.cc）
#   3) MKV 两跳索引 Remux 取帧
#
# 与仓库里旧库的唯一差异是 **组件清单**：旧构建用 `--disable-everything` + 只开 matroska，
# 导致 mp4/mov/ts 等容器 `avformat_open_input` 直接失败、只能回退 MediaExtractor（高读放大）。
# 本脚本把常见容器 demuxer 与常见视频解码器一次性开齐，之后新增格式无需再重编。
#
# 前置条件：
#   - NDK（默认 $HOME/Library/Android/sdk/ndk/28.2.13676358，可用 NDK_VERSION 覆盖）
#   - 可访问网络以下载 ffmpeg 源（已解压过则跳过下载）
#   - macOS 主机；make/curl/tar
#
# 用法：
#   ./scripts/build-ffmpeg-android.sh                  # 两个 ABI 全编并覆盖仓库库
#   ./scripts/build-ffmpeg-android.sh --abi arm64-v8a  # 只编一个 ABI
#   ./scripts/build-ffmpeg-android.sh --check-only     # 只校验已解压源版本，不编译
#
# 可覆盖的环境变量：
#   FFMPEG_VERSION  默认 9.0.2（必须与仓库头文件版本一致：avformat/avcodec 63.1.102）
#   NDK_VERSION     默认 28.2.13676358
#   WORKDIR         源码与构建目录，默认 /tmp/ffbuild
#   API             Android API level，默认 26（与 minSdk 一致）
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FFMPEG_VERSION="${FFMPEG_VERSION:-9.0.2}"
NDK_VERSION="${NDK_VERSION:-28.2.13676358}"
NDK="${ANDROID_NDK_HOME:-$HOME/Library/Android/sdk/ndk/$NDK_VERSION}"
WORKDIR="${WORKDIR:-/tmp/ffbuild}"
API="${API:-26}"
HOST_TAG="darwin-x86_64"
OUT_ROOT="$REPO_ROOT/player/ffmpeg/src/main/jni/ffmpeg/android-libs"
SRC_DIR="$WORKDIR/ffmpeg-$FFMPEG_VERSION"
TARBALL="$WORKDIR/ffmpeg-$FFMPEG_VERSION.tar.xz"

# 期望的库版本（与仓库头文件一致；不一致会导致 JNI 编译/运行期错配）
EXPECT_AVFORMAT_MINOR=1
EXPECT_AVFORMAT_MICRO=102

# ---- 组件清单 ----
# 解码器：保留原有（音频软解 + h264/hevc），并补齐常见视频编码（含 RealVideo，rmvb 用 rv40）。
DECODERS="aac,mp3,ac3,eac3,truehd,dca,vorbis,opus,amrnb,amrwb,flac,alac,pcm_mulaw,pcm_alaw,h264,hevc,av1,vp9,vp8,mpeg2video,mpeg1video,mpeg4,vc1,rv40,rv30,rv20,rv10"
# 解析器：与解码器配套（RealVideo 无需独立 parser，由 demuxer 直接喂完整包）。
PARSERS="aac,ac3,dca,mpegaudio,flac,opus,vorbis,h264,hevc,av1,vp9,mpeg4video,mpegvideo"
# 解封装：matroska 覆盖 mkv/webm；mov 覆盖 mp4/m4v/3gp；mpegts 覆盖 ts/m2ts；mpegps 覆盖 mpg/vob；
#         rm 覆盖 rm/rmvb；其余覆盖常见容器。
DEMUXERS="matroska,mov,mpegts,mpegps,avi,flv,asf,ogg,rm,mp3,wav,aac,flac"

build_abi() {
  local abi="$1" arch="$2" cpu="$3" cc="$4" cxx="$5" want_neon="$6"
  local toolchain="$NDK/toolchains/llvm/prebuilt/$HOST_TAG"
  local build_dir="$WORKDIR/build-$abi"
  local prefix="$build_dir/prefix"

  echo "====> [$abi] 配置 (arch=$arch cpu=$cpu)"
  rm -rf "$build_dir"
  mkdir -p "$build_dir" "$prefix"
  (
    cd "$build_dir"
    local extra=""
    [[ "$want_neon" == "1" ]] && extra="--enable-neon"

    "$SRC_DIR/configure" \
      --prefix="$prefix" \
      --target-os=android \
      --arch="$arch" \
      --cpu="$cpu" \
      --enable-cross-compile \
      --cc="$toolchain/bin/$cc" \
      --cxx="$toolchain/bin/$cxx" \
      --nm="$toolchain/bin/llvm-nm" \
      --ar="$toolchain/bin/llvm-ar" \
      --strip="$toolchain/bin/llvm-strip" \
      --ranlib="$toolchain/bin/llvm-ranlib" \
      --sysroot="$toolchain/sysroot" \
      --enable-pic \
      --enable-static \
      --disable-shared \
      --disable-programs \
      --disable-doc \
      --disable-network \
      --disable-autodetect \
      --disable-avdevice \
      --disable-avfilter \
      --disable-gpl \
      --disable-nonfree \
      --disable-everything \
      --enable-decoder="$DECODERS" \
      --enable-parser="$PARSERS" \
      --enable-demuxer="$DEMUXERS" \
      --enable-protocol=file \
      --enable-swscale \
      $extra

    echo "====> [$abi] 编译 (make -j$(sysctl -n hw.ncpu))"
    make -j"$(sysctl -n hw.ncpu)" >/dev/null
    make install >/dev/null
  )

  echo "====> [$abi] 覆盖仓库静态库"
  mkdir -p "$OUT_ROOT/$abi"
  for lib in libavutil libswresample libavcodec libavformat libswscale; do
    cp -f "$prefix/lib/$lib.a" "$OUT_ROOT/$abi/$lib.a"
  done

  echo "====> [$abi] 校验关键 demuxer 是否已链入"
  local fmt_a="$OUT_ROOT/$abi/libavformat.a"
  # 必须用 NDK 的 llvm-nm：macOS 自带 nm 读不了 ELF 静态库。
  # 注意：**不要**写成 `nm ... | grep -q ...` —— grep -q 命中即退出会给 nm 发 SIGPIPE，
  # 在 `set -o pipefail` 下整条管道返回非零，导致「明明有符号却判为未找到」。故先取全量再匹配。
  local llvm_nm="$toolchain/bin/llvm-nm"
  local nm_out
  nm_out="$("$llvm_nm" "$fmt_a" 2>/dev/null || true)"
  for sym in ff_mov_demuxer ff_matroska_demuxer ff_mpegts_demuxer ff_mpegps_demuxer ff_avi_demuxer ff_flv_demuxer ff_asf_demuxer ff_rm_demuxer; do
    if grep -qE "^[0-9a-f]+ [A-Z] $sym$" <<<"$nm_out"; then
      echo "     ok: $sym"
    else
      echo "     错误：未找到 ${sym}（组件清单可能未生效）" >&2
      exit 1
    fi
  done
  ls -l "$OUT_ROOT/$abi"/*.a | awk '{printf "     %-18s %s\n", $NF, $5}'
}

# ---------- 参数 ----------
ONLY_ABI=""
CHECK_ONLY=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --abi) ONLY_ABI="$2"; shift 2 ;;
    --check-only) CHECK_ONLY=1; shift ;;
    -h|--help) sed -n '2,30p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "错误：未知参数 $1" >&2; exit 1 ;;
  esac
done

if [[ ! -d "$NDK" ]]; then
  echo "错误：找不到 NDK：$NDK（可用 NDK_VERSION= 或 ANDROID_NDK_HOME= 覆盖）" >&2
  exit 1
fi

# ---------- 取源码 ----------
mkdir -p "$WORKDIR"
if [[ ! -d "$SRC_DIR" ]]; then
  if [[ ! -f "$TARBALL" ]]; then
    echo "====> 下载 ffmpeg-$FFMPEG_VERSION 源码"
    curl -# -L -o "$TARBALL" "https://ffmpeg.org/releases/ffmpeg-$FFMPEG_VERSION.tar.xz"
  fi
  echo "====> 解压源码"
  tar -C "$WORKDIR" -xf "$TARBALL"
fi

# ---------- 校验源码版本与仓库头文件一致 ----------
fmt_minor="$(grep -E '^#define LIBAVFORMAT_VERSION_MINOR' "$SRC_DIR/libavformat/version.h" | awk '{print $3}')"
fmt_micro="$(grep -E '^#define LIBAVFORMAT_VERSION_MICRO' "$SRC_DIR/libavformat/version.h" | awk '{print $3}')"
echo "====> 源码 libavformat = ${fmt_minor}.${fmt_micro}（期望 ${EXPECT_AVFORMAT_MINOR}.${EXPECT_AVFORMAT_MICRO}）"
if [[ "$fmt_minor" != "$EXPECT_AVFORMAT_MINOR" || "$fmt_micro" != "$EXPECT_AVFORMAT_MICRO" ]]; then
  echo "错误：源码版本与仓库头文件不一致，编出的库会与 JNI 错配。" >&2
  exit 1
fi
(( CHECK_ONLY )) && { echo "校验通过（--check-only，未编译）。"; exit 0; }

# ---------- 编译 ----------
case "$ONLY_ABI" in
  "")
    build_abi "arm64-v8a"   "aarch64" "armv8-a" "aarch64-linux-android${API}-clang"          "aarch64-linux-android${API}-clang++"          0
    build_abi "armeabi-v7a" "arm"     "armv7-a" "armv7a-linux-androideabi${API}-clang"      "armv7a-linux-androideabi${API}-clang++"      1
    ;;
  arm64-v8a)
    build_abi "arm64-v8a"   "aarch64" "armv8-a" "aarch64-linux-android${API}-clang"          "aarch64-linux-android${API}-clang++"          0
    ;;
  armeabi-v7a)
    build_abi "armeabi-v7a" "arm"     "armv7-a" "armv7a-linux-androideabi${API}-clang"      "armv7a-linux-androideabi${API}-clang++"      1
    ;;
  *) echo "错误：未知 ABI：$ONLY_ABI" >&2; exit 1 ;;
esac

echo "====> 完成。仓库库已更新：$OUT_ROOT"