#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <cerrno>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <memory>
#include <mutex>
#include <unordered_map>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/error.h>
#include <libavutil/mathematics.h>
#include <libavutil/pixfmt.h>
#include <libswscale/swscale.h>
}

#define LOG_TAG "FfmpegFrameDecoder"
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))
#define LOGW(...) ((void)__android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__))

#define IO_BUFFER_SIZE (1 << 15)
#define JNI_ARRAY_SIZE (1 << 20)

// 已读这么多视频包仍一帧都没解出，即判定解码器不支持该流并提前放弃（见循环内注释）。
#define NO_FRAME_ABORT_PACKETS 64

namespace {

// 解码失败原因（与 Kotlin 侧 FfmpegFrameDecoder.ERR_* 常量一一对应）。
// 上层据此决定「该文件不值得再用 ffmpeg（记入不支持名单）」还是「本次只是抖动/取消」。
constexpr int kErrNone = 0;
constexpr int kErrUnsupported = 1;
constexpr int kErrCancelled = 2;
constexpr int kErrIo = 3;

struct DecodeIo {
  JavaVM* vm;
  jobject source;
  jmethodID readAt;
  jmethodID getSize;
  jbyteArray buffer;
  int64_t position;
  int64_t size;
  int reads;
  int seeks;
  int64_t bytesRead;
  // 取消标志（可空）。置位后所有读/寻址立即返回错误，用于「松手即放弃当前取帧」。
  std::atomic<bool>* cancel;
};

/**
 * 取帧会话：把 avio/解封装/解码器常驻下来，多次取帧只做 seek+decode。
 *
 * 动机：拖动进度条的实时预览会高频取帧，而一次性取帧每次都要
 * avformat_open_input + avformat_find_stream_info（远程源还要重新建连、重新 probe 索引），
 * 单帧成本远超「seek + 解一帧」。会话把这些一次性成本摊到整个预览过程。
 *
 * 线程模型：解码与关闭都需持有 [mutex]（ffmpeg 上下文非线程安全）；
 * [cancel] 是原子的，取消方无需拿锁即可打断正在进行的解码。
 */
struct Session {
  JavaVM* vm = nullptr;
  jobject source = nullptr;  // global ref
  jmethodID readAt = nullptr;
  jmethodID getSize = nullptr;
  jbyteArray buffer = nullptr;  // global ref
  DecodeIo io;
  AVIOContext* avio = nullptr;
  uint8_t* ioBuffer = nullptr;
  AVFormatContext* format = nullptr;
  AVCodecContext* decoder = nullptr;
  int videoStream = -1;
  int64_t durationUs = 0;
  int targetWidth = 0;
  SwsContext* sws = nullptr;
  int swsSrcW = 0;
  int swsSrcH = 0;
  int swsSrcFormat = -1;
  bool closed = false;
  int lastError = kErrNone;
  std::atomic<bool> cancel{false};
  std::mutex mutex;
};

std::mutex gSessionsMutex;
std::unordered_map<jlong, std::shared_ptr<Session>> gSessions;
jlong gNextSessionId = 1;

std::shared_ptr<Session> findSession(jlong handle) {
  std::lock_guard<std::mutex> lock(gSessionsMutex);
  auto it = gSessions.find(handle);
  if (it == gSessions.end()) return nullptr;
  return it->second;
}

JNIEnv* attachEnv(JavaVM* vm) {
  JNIEnv* env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    return nullptr;
  }
  return env;
}

int ioRead(void* opaque, uint8_t* buf, int bufSize) {
  DecodeIo* io = static_cast<DecodeIo*>(opaque);
  if (io->cancel && io->cancel->load()) return AVERROR_EXIT;
  JNIEnv* env = attachEnv(io->vm);
  if (!env) return AVERROR_EXIT;
  int toRead = bufSize;
  if (toRead > JNI_ARRAY_SIZE) toRead = JNI_ARRAY_SIZE;
  if (io->size > 0) {
    int64_t remain = io->size - io->position;
    if (remain <= 0) return AVERROR_EOF;
    if (remain < toRead) toRead = static_cast<int>(remain);
  }
  jint n = env->CallIntMethod(io->source, io->readAt,
                              static_cast<jlong>(io->position), io->buffer, 0, toRead);
  if (env->ExceptionCheck()) {
    env->ExceptionClear();
    return AVERROR_EXIT;
  }
  if (n <= 0) return AVERROR_EOF;
  env->GetByteArrayRegion(io->buffer, 0, n, reinterpret_cast<jbyte*>(buf));
  io->position += n;
  io->reads++;
  io->bytesRead += n;
  return n;
}

int64_t ioSeek(void* opaque, int64_t offset, int whence) {
  DecodeIo* io = static_cast<DecodeIo*>(opaque);
  if (io->cancel && io->cancel->load()) return AVERROR_EXIT;
  if (whence & AVSEEK_SIZE) return io->size;
  whence &= ~AVSEEK_SIZE;
  int64_t next = io->position;
  switch (whence) {
    case SEEK_SET:
      next = offset;
      break;
    case SEEK_CUR:
      next = io->position + offset;
      break;
    case SEEK_END:
      next = io->size + offset;
      break;
    default:
      return AVERROR(EINVAL);
  }
  if (next < 0) next = 0;
  io->position = next;
  io->seeks++;
  return next;
}

int meanLuma(const AVFrame* frame) {
  const int step = 16;
  int64_t sum = 0;
  int count = 0;
  switch (frame->format) {
    case AV_PIX_FMT_YUV420P:
    case AV_PIX_FMT_YUVJ420P: {
      for (int y = 0; y < frame->height; y += step) {
        const uint8_t* row = frame->data[0] + static_cast<size_t>(y) * frame->linesize[0];
        for (int x = 0; x < frame->width; x += step) {
          sum += row[x];
          count++;
        }
      }
      break;
    }
    case AV_PIX_FMT_YUV420P10LE:
    case AV_PIX_FMT_YUV420P10BE: {
      for (int y = 0; y < frame->height; y += step) {
        const uint16_t* row =
            reinterpret_cast<const uint16_t*>(frame->data[0] + static_cast<size_t>(y) * frame->linesize[0]);
        for (int x = 0; x < frame->width; x += step) {
          sum += row[x];
          count++;
        }
      }
      break;
    }
    default:
      return -1;
  }
  return count > 0 ? static_cast<int>(sum / count) : -1;
}

/** 释放会话持有的全部 ffmpeg/JNI 资源（不释放 [s] 本身）。 */
void releaseSession(JNIEnv* env, Session* s) {
  if (!s) return;
  if (s->decoder) avcodec_free_context(&s->decoder);
  if (s->format) avformat_close_input(&s->format);
  if (s->sws) {
    sws_freeContext(s->sws);
    s->sws = nullptr;
  }
  if (s->avio) {
    av_freep(&s->avio->buffer);
    avio_context_free(&s->avio);
  } else if (s->ioBuffer) {
    av_free(s->ioBuffer);
  }
  s->ioBuffer = nullptr;
  if (s->buffer && env) env->DeleteGlobalRef(s->buffer);
  if (s->source && env) env->DeleteGlobalRef(s->source);
  s->buffer = nullptr;
  s->source = nullptr;
  s->closed = true;
}

/**
 * 建立 avio + 解封装 + 解码器（只做一次，之后复用）。
 *
 * 失败时按原因写 [Session::lastError]：容器/编码器不识别记 [kErrUnsupported]，
 * 让上层可以把该文件记入「ffmpeg 不支持」名单，后续直接用 retriever 兜底。
 */
bool openSessionFormat(Session* s) {
  s->ioBuffer = static_cast<uint8_t*>(av_malloc(IO_BUFFER_SIZE));
  if (!s->ioBuffer) {
    s->lastError = kErrUnsupported;
    return false;
  }
  s->avio = avio_alloc_context(s->ioBuffer, IO_BUFFER_SIZE, 0, &s->io, ioRead, nullptr, ioSeek);
  if (!s->avio) {
    s->lastError = kErrUnsupported;
    return false;
  }
  s->avio->seekable = AVIO_SEEKABLE_NORMAL;

  s->format = avformat_alloc_context();
  if (!s->format) {
    s->lastError = kErrUnsupported;
    return false;
  }
  s->format->pb = s->avio;
  s->format->flags |= AVFMT_FLAG_CUSTOM_IO;

  if (avformat_open_input(&s->format, nullptr, nullptr, nullptr) < 0) {
    LOGW("avformat_open_input failed");
    s->format = nullptr;
    s->lastError = kErrUnsupported;
    return false;
  }

  if (avformat_find_stream_info(s->format, nullptr) < 0) {
    LOGW("find_stream_info failed");
    s->lastError = kErrUnsupported;
    return false;
  }

  const AVCodec* codec = nullptr;
  s->videoStream = av_find_best_stream(s->format, AVMEDIA_TYPE_VIDEO, -1, -1, &codec, 0);
  if (s->videoStream < 0 || !codec) {
    LOGW("no video stream");
    s->lastError = kErrUnsupported;
    return false;
  }

  s->decoder = avcodec_alloc_context3(codec);
  if (!s->decoder) {
    s->lastError = kErrUnsupported;
    return false;
  }
  if (avcodec_parameters_to_context(s->decoder, s->format->streams[s->videoStream]->codecpar) < 0) {
    s->lastError = kErrUnsupported;
    return false;
  }
  s->decoder->pkt_timebase = s->format->streams[s->videoStream]->time_base;
  // 软件解码 4K HEVC 很吃 CPU：单线程是旧值，这里放开到 4 路并行。
  s->decoder->thread_count = 4;
  if (avcodec_open2(s->decoder, codec, nullptr) < 0) {
    LOGW("decoder open failed");
    s->lastError = kErrUnsupported;
    return false;
  }

  s->durationUs = s->format->duration > 0 ? s->format->duration : 0;
  s->lastError = kErrNone;
  return true;
}

/**
 * 在已打开的会话上 seek 到目标时刻并解出一帧（调用方须持有 [Session::mutex]）。
 *
 * @param pickNearest 非 0 = 取"第一个不早于目标时刻"的帧（拖动预览，精确到帧）；
 *   0 = 在目标时刻之后的一小段窗口里取最亮帧（缩略图，跳过黑帧）
 * @return BGRA 像素 + 头部 3 个 int（宽、高、HDR 类型）；失败返回 nullptr 并写 [Session::lastError]
 */
jintArray decodeSessionFrame(Session* s, JNIEnv* env, jlong startMs, jdouble fraction, jint pickNearest) {
  if (!s->format || !s->decoder) {
    s->lastError = kErrUnsupported;
    return nullptr;
  }
  // 本次解码重新开始：清掉上一次的取消标志（否则松手后第一次取帧会被立刻打断）
  s->cancel.store(false);
  s->lastError = kErrNone;

  jintArray result = nullptr;
  AVPacket* packet = nullptr;
  AVFrame* frame = nullptr;
  AVFrame* best = nullptr;
  uint8_t* rgb = nullptr;

  const int64_t durationUs = s->durationUs;
  int64_t posUs;
  if (fraction > 0 && durationUs > 0) {
    posUs = static_cast<int64_t>(static_cast<double>(durationUs) * fraction);
  } else if (startMs >= 0) {
    posUs = startMs * 1000;
  } else {
    posUs = durationUs > 0 ? durationUs / 10 : 5000000;
  }
  if (durationUs > 0 && posUs > durationUs - 500000) posUs = durationUs - 500000;
  if (posUs < 0) posUs = 0;

  int seekRet = av_seek_frame(s->format, -1, posUs, AVSEEK_FLAG_BACKWARD);
  bool seeked = seekRet >= 0;
  // seek 后必须清空解码器内部残留帧，否则会解出跳转前的旧帧。
  avcodec_flush_buffers(s->decoder);
  // 兜底对齐：无论 libavformat 的 seek 是否丢弃了读缓冲（会话复用下这一步尤其关键），
  // 这里都强制把 AVIO 读缓冲置空。自定义 IO 的读取位置由 io.position 决定，而它已被
  // ioSeek 更新到目标字节，故置空缓冲后下一次读取必然从新位置开始，不会解到陈旧数据。
  if (s->avio) {
    s->avio->buf_ptr = s->avio->buffer;
    s->avio->buf_end = s->avio->buffer;
    s->avio->eof_reached = 0;
  }

  packet = av_packet_alloc();
  frame = av_frame_alloc();
  if (!packet || !frame) {
    s->lastError = kErrUnsupported;
    goto cleanup;
  }

  {
    const AVRational timeBase = s->format->streams[s->videoStream]->time_base;
    const int64_t targetPts = (seeked && timeBase.num > 0 && timeBase.den > 0)
                                  ? av_rescale_q(posUs, AV_TIME_BASE_Q, timeBase)
                                  : AV_NOPTS_VALUE;

    int bestLuma = -1;
    int evaluated = 0;
    int decoded = 0;
    const int cap = seeked ? 12 : 240;
    const int maxDecode = seeked ? 600 : 240;
    bool reached = false;
    bool done = false;
    int readRet = 0;
    int videoPackets = 0;
    int sendFailures = 0;
    int firstSendError = 0;
    bool cancelled = false;

    while (true) {
      if (s->cancel.load()) {
        cancelled = true;
        LOGD("decode cancelled");
        break;
      }
      readRet = av_read_frame(s->format, packet);
      if (readRet < 0) break;
      if (packet->stream_index != s->videoStream) {
        av_packet_unref(packet);
        continue;
      }
      videoPackets++;
      int sendRet = avcodec_send_packet(s->decoder, packet);
      if (sendRet < 0) {
        // 诊断用：解码器拒收所有包（如超出解码能力/缺参数集）时，靠这个计数定位
        if (sendFailures == 0) firstSendError = sendRet;
        sendFailures++;
        av_packet_unref(packet);
        continue;
      }
      av_packet_unref(packet);
      while (true) {
        int r = avcodec_receive_frame(s->decoder, frame);
        if (r == AVERROR(EAGAIN) || r == AVERROR_EOF) break;
        if (r < 0) break;
        decoded++;
        if (!reached) {
          bool preRoll = false;
          if (targetPts != AV_NOPTS_VALUE) {
            int64_t pts = frame->best_effort_timestamp;
            if (pts != AV_NOPTS_VALUE && pts < targetPts) preRoll = true;
          }
          if (preRoll && decoded < maxDecode) continue;
          reached = true;
        }
        if (pickNearest != 0) {
          // 预览取帧：取"第一个不早于目标时刻"的帧即可，不做亮度优选 —— 否则会在
          // 场景切换处被窗口内的后续亮帧带走，导致预览画面与目标时间对不上。
          best = av_frame_alloc();
          if (best) av_frame_ref(best, frame);
          evaluated++;
          done = true;
          break;
        }
        int luma = meanLuma(frame);
        if (luma > bestLuma) {
          bestLuma = luma;
          if (!best) best = av_frame_alloc();
          if (best) {
            av_frame_unref(best);
            if (av_frame_ref(best, frame) < 0) av_frame_unref(best);
          }
        }
        evaluated++;
        if (evaluated >= cap || decoded >= maxDecode) {
          done = true;
          break;
        }
      }
      if (done) break;
      // 提前中止：读了相当多的视频包却一帧都解不出来，说明解码器吃不下这个流（实测 8K 远程
      // MP4 会 sendFail=ENOSYS）。此时继续读只会把整个读取预算耗光、白白拖慢（1.8s+65MB），
      // 直接放弃让上层回退 retriever。
      if (decoded == 0 && videoPackets >= NO_FRAME_ABORT_PACKETS) {
        LOGW("abort: %d video packets, no frame decoded (sendFail=%d firstSendErr=%d)", videoPackets,
             sendFailures, firstSendError);
        s->lastError = kErrUnsupported;
        break;
      }
    }

    if (cancelled) {
      s->lastError = kErrCancelled;
      goto cleanup;
    }

    if (!best) {
      LOGW("no frame decoded (readRet=%d videoPkts=%d sendFail=%d firstSendErr=%d frames=%d reads=%d "
           "seeks=%d bytes=%lld)",
           readRet, videoPackets, sendFailures, firstSendError, decoded, s->io.reads, s->io.seeks,
           static_cast<long long>(s->io.bytesRead));
      // 一帧都没解出：open 已成功，说明不是「容器不识别」，按 IO/临时失败处理（可重试，
      // 但不把该文件拉黑）；若上面已判定 send 全线失败（NO_FRAME_ABORT），保留不支持结论。
      if (s->lastError == kErrNone) s->lastError = kErrIo;
      goto cleanup;
    }

    const int srcWidth = best->width;
    const int srcHeight = best->height;
    int dstWidth = srcWidth;
    int dstHeight = srcHeight;
    if (srcWidth > s->targetWidth) {
      dstWidth = s->targetWidth;
      dstHeight = static_cast<int>(std::lround(static_cast<double>(srcHeight) * s->targetWidth / srcWidth));
      if (dstHeight < 1) dstHeight = 1;
    }

    // sws 上下文按「源尺寸+像素格式」缓存复用；变了才重建（缩放器构造不便宜）。
    if (!s->sws || s->swsSrcW != srcWidth || s->swsSrcH != srcHeight || s->swsSrcFormat != best->format) {
      if (s->sws) {
        sws_freeContext(s->sws);
        s->sws = nullptr;
      }
      s->sws = sws_getContext(srcWidth, srcHeight, static_cast<AVPixelFormat>(best->format), dstWidth,
                              dstHeight, AV_PIX_FMT_BGRA, SWS_BILINEAR, nullptr, nullptr, nullptr);
      s->swsSrcW = srcWidth;
      s->swsSrcH = srcHeight;
      s->swsSrcFormat = best->format;
    }
    if (!s->sws) {
      LOGW("sws_getContext failed");
      s->lastError = kErrUnsupported;
      goto cleanup;
    }

    const int stride = dstWidth * 4;
    rgb = static_cast<uint8_t*>(av_malloc(static_cast<size_t>(stride) * dstHeight));
    if (!rgb) {
      s->lastError = kErrUnsupported;
      goto cleanup;
    }
    uint8_t* dstData[4] = {rgb, nullptr, nullptr, nullptr};
    int dstLinesize[4] = {stride, 0, 0, 0};
    sws_scale(s->sws, best->data, best->linesize, 0, srcHeight, dstData, dstLinesize);

    // 第 3 个头部字段：该帧的 HDR 类型（0=非 HDR，1=PQ/HDR10，2=HLG）。ffmpeg 直解出的是原始
    // 码值、无系统 tone map，Java 侧据此决定用哪条曲线做色调映射（否则 HDR 预览会发暗或过曝）。
    const int hdrKind = (best->color_trc == AVCOL_TRC_SMPTE2084)
                            ? 1
                            : (best->color_trc == AVCOL_TRC_ARIB_STD_B67) ? 2 : 0;
    const int total = 3 + dstWidth * dstHeight;
    jintArray out = env->NewIntArray(total);
    if (!out) {
      s->lastError = kErrUnsupported;
      goto cleanup;
    }
    jint* pixels = static_cast<jint*>(av_malloc(sizeof(jint) * total));
    if (!pixels) {
      s->lastError = kErrUnsupported;
      goto cleanup;
    }
    pixels[0] = dstWidth;
    pixels[1] = dstHeight;
    pixels[2] = hdrKind;
    memcpy(pixels + 3, rgb, static_cast<size_t>(stride) * dstHeight);
    env->SetIntArrayRegion(out, 0, total, pixels);
    av_free(pixels);
    result = out;
    LOGD("decoded %dx%d -> %dx%d dur=%.1fs seekPos=%.1fs seekRet=%d frames=%d luma=%d hdrKind=%d "
         "transfer=%d range=%d primaries=%d videoPkts=%d sendFail=%d reads=%d seeks=%d bytes=%lld",
         srcWidth, srcHeight, dstWidth, dstHeight, durationUs / 1000000.0, posUs / 1000000.0, seekRet,
         evaluated, bestLuma, hdrKind, best->color_trc, best->color_range,
         best->color_primaries, videoPackets, sendFailures, s->io.reads, s->io.seeks,
         static_cast<long long>(s->io.bytesRead));
  }

cleanup:
  if (best) av_frame_free(&best);
  if (frame) av_frame_free(&frame);
  if (packet) av_packet_free(&packet);
  if (rgb) av_free(rgb);
  return result;
}

/** 重置会话的读取统计（会话被多次复用时，读取预算是"单次取帧"语义，需按帧清零）。 */
void resetReadBudget(Session* s) {
  s->io.reads = 0;
  s->io.seeks = 0;
  s->io.bytesRead = 0;
}

void setErrorCode(JNIEnv* env, jintArray errorOut, int code) {
  if (!errorOut) return;
  jint value = code;
  env->SetIntArrayRegion(errorOut, 0, 1, &value);
}

}  // namespace

extern "C" {

/**
 * 一次性取帧：内部按「开会话 → 解一帧 → 关会话」执行，等价于旧的 nativeDecode。
 *
 * @param errorOut 长度 >=1 的 int 数组，回传失败原因（0=成功/无错，1=不支持，2=已取消）
 */
JNIEXPORT jintArray JNICALL
Java_com_nichx_niplayer_thumbnail_FfmpegFrameDecoder_nativeDecode(
    JNIEnv* env, jobject /*thiz*/, jobject source, jint targetWidth, jlong startMs, jdouble fraction,
    jint pickNearest, jintArray errorOut) {
  if (!source || targetWidth <= 0) {
    setErrorCode(env, errorOut, kErrUnsupported);
    return nullptr;
  }

  JavaVM* vm = nullptr;
  if (env->GetJavaVM(&vm) != JNI_OK) {
    setErrorCode(env, errorOut, kErrUnsupported);
    return nullptr;
  }

  jclass sourceClass = env->GetObjectClass(source);
  jmethodID readAt = env->GetMethodID(sourceClass, "readAt", "(J[BII)I");
  jmethodID getSize = env->GetMethodID(sourceClass, "getSize", "()J");
  env->DeleteLocalRef(sourceClass);
  if (!readAt || !getSize) {
    env->ExceptionClear();
    setErrorCode(env, errorOut, kErrUnsupported);
    return nullptr;
  }

  jbyteArray localBuffer = env->NewByteArray(JNI_ARRAY_SIZE);
  if (!localBuffer) {
    setErrorCode(env, errorOut, kErrUnsupported);
    return nullptr;
  }

  std::shared_ptr<Session> owned(new Session());
  Session* s = owned.get();
  s->vm = vm;
  s->targetWidth = targetWidth;
  s->source = env->NewGlobalRef(source);
  s->readAt = readAt;
  s->getSize = getSize;
  s->buffer = static_cast<jbyteArray>(env->NewGlobalRef(localBuffer));
  env->DeleteLocalRef(localBuffer);
  s->io.vm = vm;
  s->io.source = s->source;
  s->io.readAt = readAt;
  s->io.getSize = getSize;
  s->io.buffer = s->buffer;
  s->io.position = 0;
  s->io.size = env->CallLongMethod(source, getSize);
  if (env->ExceptionCheck()) {
    env->ExceptionClear();
    s->io.size = 0;
  }
  s->io.reads = 0;
  s->io.seeks = 0;
  s->io.bytesRead = 0;
  s->io.cancel = &s->cancel;

  jintArray result = nullptr;
  {
    std::lock_guard<std::mutex> lock(s->mutex);
    if (!openSessionFormat(s)) {
      setErrorCode(env, errorOut, s->lastError);
      releaseSession(env, s);
      return nullptr;
    }
    result = decodeSessionFrame(s, env, startMs, fraction, pickNearest);
    setErrorCode(env, errorOut, result ? kErrNone : s->lastError);
    releaseSession(env, s);
  }
  return result;
}

/**
 * 打开取帧会话（远程预览专用）：常驻 avio/解封装/解码器，供多次 seek 取帧复用。
 *
 * @return 会话句柄（>0）；失败返回 0 并经 [errorOut] 回传原因
 */
JNIEXPORT jlong JNICALL
Java_com_nichx_niplayer_thumbnail_FfmpegFrameDecoder_nativeOpenSession(
    JNIEnv* env, jobject /*thiz*/, jobject source, jint targetWidth, jintArray errorOut) {
  if (!source || targetWidth <= 0) {
    setErrorCode(env, errorOut, kErrUnsupported);
    return 0L;
  }

  JavaVM* vm = nullptr;
  if (env->GetJavaVM(&vm) != JNI_OK) {
    setErrorCode(env, errorOut, kErrUnsupported);
    return 0L;
  }

  jclass sourceClass = env->GetObjectClass(source);
  jmethodID readAt = env->GetMethodID(sourceClass, "readAt", "(J[BII)I");
  jmethodID getSize = env->GetMethodID(sourceClass, "getSize", "()J");
  env->DeleteLocalRef(sourceClass);
  if (!readAt || !getSize) {
    env->ExceptionClear();
    setErrorCode(env, errorOut, kErrUnsupported);
    return 0L;
  }

  jbyteArray localBuffer = env->NewByteArray(JNI_ARRAY_SIZE);
  if (!localBuffer) {
    setErrorCode(env, errorOut, kErrUnsupported);
    return 0L;
  }

  std::shared_ptr<Session> session(new Session());
  Session* s = session.get();
  s->vm = vm;
  s->targetWidth = targetWidth;
  s->source = env->NewGlobalRef(source);
  s->readAt = readAt;
  s->getSize = getSize;
  s->buffer = static_cast<jbyteArray>(env->NewGlobalRef(localBuffer));
  env->DeleteLocalRef(localBuffer);
  s->io.vm = vm;
  s->io.source = s->source;
  s->io.readAt = readAt;
  s->io.getSize = getSize;
  s->io.buffer = s->buffer;
  s->io.position = 0;
  s->io.size = env->CallLongMethod(source, getSize);
  if (env->ExceptionCheck()) {
    env->ExceptionClear();
    s->io.size = 0;
  }
  s->io.reads = 0;
  s->io.seeks = 0;
  s->io.bytesRead = 0;
  s->io.cancel = &s->cancel;

  {
    std::lock_guard<std::mutex> lock(s->mutex);
    if (!openSessionFormat(s)) {
      setErrorCode(env, errorOut, s->lastError);
      releaseSession(env, s);
      return 0L;
    }
  }

  std::lock_guard<std::mutex> lock(gSessionsMutex);
  jlong handle = gNextSessionId++;
  gSessions[handle] = session;
  setErrorCode(env, errorOut, kErrNone);
  LOGD("preview session opened id=%lld", static_cast<long long>(handle));
  return handle;
}

/** 在会话上解一帧（自动 seek 到目标时刻）。 */
JNIEXPORT jintArray JNICALL
Java_com_nichx_niplayer_thumbnail_FfmpegFrameDecoder_nativeSessionDecode(
    JNIEnv* env, jobject /*thiz*/, jlong handle, jlong startMs, jdouble fraction, jint pickNearest,
    jintArray errorOut) {
  std::shared_ptr<Session> session = findSession(handle);
  if (!session) {
    // 会话已被关闭（换片/收起预览）——与「文件解不出」无关，按取消上报，
    // 否则上层会把一次正常的关闭误判成该文件 ffmpeg 不支持。
    setErrorCode(env, errorOut, kErrCancelled);
    return nullptr;
  }
  Session* s = session.get();
  // 会话复用时把读取预算按帧清零：预算是「单次取帧」语义（防无索引容器全文件扫描），
  // 若跨帧累计，长时间拖动会把预算耗光、预览中途永久失效。
  resetReadBudget(s);
  std::lock_guard<std::mutex> lock(s->mutex);
  if (s->closed) {
    setErrorCode(env, errorOut, kErrCancelled);
    return nullptr;
  }
  jintArray result = decodeSessionFrame(s, env, startMs, fraction, pickNearest);
  setErrorCode(env, errorOut, result ? kErrNone : s->lastError);
  return result;
}

/**
 * 取消会话上正在进行的解码（立即置位取消标志，无需等待解码锁）。
 *
 * 用于「松手即放弃当前预览帧」：正在解的那一帧会尽快收手，把解码资源让给真实播放。
 */
JNIEXPORT void JNICALL
Java_com_nichx_niplayer_thumbnail_FfmpegFrameDecoder_nativeSessionCancel(JNIEnv* /*env*/, jobject /*thiz*/,
                                                                         jlong handle) {
  std::shared_ptr<Session> session = findSession(handle);
  if (!session) return;
  session->cancel.store(true);
}

/** 关闭会话并释放其持有的 ffmpeg/JNI 资源（幂等）。 */
JNIEXPORT void JNICALL
Java_com_nichx_niplayer_thumbnail_FfmpegFrameDecoder_nativeSessionClose(JNIEnv* env, jobject /*thiz*/,
                                                                       jlong handle) {
  std::shared_ptr<Session> session;
  {
    std::lock_guard<std::mutex> lock(gSessionsMutex);
    auto it = gSessions.find(handle);
    if (it == gSessions.end()) return;
    session = it->second;
    gSessions.erase(it);
  }
  // 先取消（无锁）再等解码退出，避免长解码卡住调用方
  session->cancel.store(true);
  std::lock_guard<std::mutex> lock(session->mutex);
  releaseSession(env, session.get());
  LOGD("preview session closed id=%lld", static_cast<long long>(handle));
}

}  // extern "C"
