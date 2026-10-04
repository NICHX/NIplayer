#include <jni.h>
#include <android/log.h>
#include <cerrno>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/error.h>
#include <libavutil/mathematics.h>
#include <libavutil/pixfmt.h>
#include <libswscale/swscale.h>
}

#define LOG_TAG "MkvFfmpegDecoder"
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))
#define LOGW(...) ((void)__android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__))

#define IO_BUFFER_SIZE (1 << 15)
#define JNI_ARRAY_SIZE (1 << 20)

namespace {

struct JniIo {
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
};

JNIEnv* attachEnv(JavaVM* vm) {
  JNIEnv* env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    return nullptr;
  }
  return env;
}

int ioRead(void* opaque, uint8_t* buf, int bufSize) {
  JniIo* io = static_cast<JniIo*>(opaque);
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
  JniIo* io = static_cast<JniIo*>(opaque);
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
  if (io->seeks <= 50) {
    LOGD("seek #%d off=%lld whence=%d -> pos=%lld", io->seeks, static_cast<long long>(offset), whence,
         static_cast<long long>(next));
  }
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
          sum += (row[x] >> 2);
          count++;
        }
      }
      break;
    }
    default:
      return 128;
  }
  return count > 0 ? static_cast<int>(sum / count) : 128;
}

}  // namespace

extern "C" JNIEXPORT jintArray JNICALL
Java_com_nichx_niplayer_thumbnail_MkvFfmpegDecoder_nativeDecode(
    JNIEnv* env, jobject /*thiz*/, jobject source, jint targetWidth, jlong startMs, jdouble fraction) {
  if (!source || targetWidth <= 0) return nullptr;

  JavaVM* vm = nullptr;
  if (env->GetJavaVM(&vm) != JNI_OK) return nullptr;

  jclass sourceClass = env->GetObjectClass(source);
  jmethodID readAt = env->GetMethodID(sourceClass, "readAt", "(J[BII)I");
  jmethodID getSize = env->GetMethodID(sourceClass, "getSize", "()J");
  env->DeleteLocalRef(sourceClass);
  if (!readAt || !getSize) {
    env->ExceptionClear();
    return nullptr;
  }

  jbyteArray localBuffer = env->NewByteArray(JNI_ARRAY_SIZE);
  if (!localBuffer) return nullptr;

  JniIo io;
  io.vm = vm;
  io.source = env->NewGlobalRef(source);
  io.readAt = readAt;
  io.getSize = getSize;
  io.buffer = static_cast<jbyteArray>(env->NewGlobalRef(localBuffer));
  io.position = 0;
  io.size = env->CallLongMethod(source, getSize);
  if (env->ExceptionCheck()) {
    env->ExceptionClear();
    io.size = 0;
  }
  io.reads = 0;
  io.seeks = 0;
  io.bytesRead = 0;

  AVFormatContext* format = nullptr;
  AVCodecContext* decoder = nullptr;
  AVPacket* packet = nullptr;
  AVFrame* frame = nullptr;
  AVFrame* best = nullptr;
  SwsContext* sws = nullptr;
  uint8_t* ioBuffer = nullptr;
  uint8_t* rgb = nullptr;
  AVIOContext* avio = nullptr;
  jintArray result = nullptr;

  do {
    ioBuffer = static_cast<uint8_t*>(av_malloc(IO_BUFFER_SIZE));
    if (!ioBuffer) break;
    avio = avio_alloc_context(ioBuffer, IO_BUFFER_SIZE, 0, &io, ioRead, nullptr, ioSeek);
    if (!avio) break;
    avio->seekable = AVIO_SEEKABLE_NORMAL;

    format = avformat_alloc_context();
    if (!format) break;
    format->pb = avio;
    format->flags |= AVFMT_FLAG_CUSTOM_IO;

    if (avformat_open_input(&format, nullptr, nullptr, nullptr) < 0) {
      LOGW("avformat_open_input failed");
      format = nullptr;
      break;
    }
    LOGD("open_input ok reads=%d seeks=%d bytes=%lld", io.reads, io.seeks,
         static_cast<long long>(io.bytesRead));

    if (avformat_find_stream_info(format, nullptr) < 0) {
      LOGW("find_stream_info failed");
      break;
    }
    LOGD("find_stream_info ok reads=%d seeks=%d bytes=%lld", io.reads, io.seeks,
         static_cast<long long>(io.bytesRead));

    const AVCodec* codec = nullptr;
    int videoStream = av_find_best_stream(format, AVMEDIA_TYPE_VIDEO, -1, -1, &codec, 0);
    if (videoStream < 0 || !codec) {
      LOGW("no video stream");
      break;
    }

    decoder = avcodec_alloc_context3(codec);
    if (!decoder) break;
    if (avcodec_parameters_to_context(decoder, format->streams[videoStream]->codecpar) < 0) break;
    decoder->pkt_timebase = format->streams[videoStream]->time_base;
    decoder->thread_count = 1;
    if (avcodec_open2(decoder, codec, nullptr) < 0) {
      LOGW("decoder open failed");
      break;
    }

    int64_t durationUs = format->duration > 0 ? format->duration : 0;
    int64_t posUs;
    if (fraction > 0 && durationUs > 0) {
      posUs = static_cast<int64_t>(static_cast<double>(durationUs) * fraction);
    } else if (startMs >= 0) {
      posUs = startMs * 1000;
    } else {
      posUs = durationUs > 0 ? durationUs / 10 : 5000000;
    }
    if (durationUs > 0) {
      int64_t minUs = durationUs / 10;
      if (posUs < minUs) posUs = minUs;
      if (posUs > durationUs - 500000) posUs = durationUs - 500000;
    }
    if (posUs < 0) posUs = 0;

    int seekRet = av_seek_frame(format, -1, posUs, AVSEEK_FLAG_BACKWARD);
    bool seeked = seekRet >= 0;
    LOGD("open dur=%.1fs vstream=%d tb=%d/%d seekPos=%.1fs seekRet=%d reads=%d seeks=%d bytes=%lld",
         durationUs / 1000000.0, videoStream, format->streams[videoStream]->time_base.num,
         format->streams[videoStream]->time_base.den, posUs / 1000000.0, seekRet, io.reads, io.seeks,
         static_cast<long long>(io.bytesRead));

    packet = av_packet_alloc();
    frame = av_frame_alloc();
    if (!packet || !frame) break;

    int bestLuma = -1;
    int framesSeen = 0;
    int cap = seeked ? 12 : 240;
    bool done = false;
    int readRet = 0;

    while ((readRet = av_read_frame(format, packet)) >= 0) {
      if (packet->stream_index != videoStream) {
        av_packet_unref(packet);
        continue;
      }
      if (avcodec_send_packet(decoder, packet) < 0) {
        av_packet_unref(packet);
        continue;
      }
      av_packet_unref(packet);
      while (true) {
        int r = avcodec_receive_frame(decoder, frame);
        if (r == AVERROR(EAGAIN) || r == AVERROR_EOF) break;
        if (r < 0) break;
        int luma = meanLuma(frame);
        if (luma > bestLuma) {
          bestLuma = luma;
          if (!best) best = av_frame_alloc();
          if (best) {
            av_frame_unref(best);
            if (av_frame_ref(best, frame) < 0) av_frame_unref(best);
          }
        }
        framesSeen++;
        if (framesSeen >= cap) {
          done = true;
          break;
        }
      }
      if (done) break;
    }

    if (!best) {
      LOGW("no frame decoded (readRet=%d frames=%d reads=%d seeks=%d bytes=%lld)", readRet, framesSeen,
           io.reads, io.seeks, static_cast<long long>(io.bytesRead));
      break;
    }

    int srcWidth = best->width;
    int srcHeight = best->height;
    int dstWidth = srcWidth;
    int dstHeight = srcHeight;
    if (srcWidth > targetWidth) {
      dstWidth = targetWidth;
      dstHeight = static_cast<int>(std::lround(static_cast<double>(srcHeight) * targetWidth / srcWidth));
      if (dstHeight < 1) dstHeight = 1;
    }

    sws = sws_getContext(srcWidth, srcHeight, static_cast<AVPixelFormat>(best->format), dstWidth, dstHeight,
                         AV_PIX_FMT_BGRA, SWS_BILINEAR, nullptr, nullptr, nullptr);
    if (!sws) {
      LOGW("sws_getContext failed");
      break;
    }

    int stride = dstWidth * 4;
    rgb = static_cast<uint8_t*>(av_malloc(static_cast<size_t>(stride) * dstHeight));
    if (!rgb) break;
    uint8_t* dstData[4] = {rgb, nullptr, nullptr, nullptr};
    int dstLinesize[4] = {stride, 0, 0, 0};
    sws_scale(sws, best->data, best->linesize, 0, srcHeight, dstData, dstLinesize);

    int total = 2 + dstWidth * dstHeight;
    jintArray out = env->NewIntArray(total);
    if (!out) break;
    jint* pixels = static_cast<jint*>(av_malloc(sizeof(jint) * total));
    if (!pixels) break;
    pixels[0] = dstWidth;
    pixels[1] = dstHeight;
    memcpy(pixels + 2, rgb, static_cast<size_t>(stride) * dstHeight);
    env->SetIntArrayRegion(out, 0, total, pixels);
    av_free(pixels);
    result = out;
    LOGD("decoded %dx%d -> %dx%d frames=%d luma=%d seeked=%d", srcWidth, srcHeight, dstWidth, dstHeight,
         framesSeen, bestLuma, seeked ? 1 : 0);
  } while (false);

  if (best) av_frame_free(&best);
  if (frame) av_frame_free(&frame);
  if (packet) av_packet_free(&packet);
  if (decoder) avcodec_free_context(&decoder);
  if (format) avformat_close_input(&format);
  if (sws) sws_freeContext(sws);
  if (rgb) av_free(rgb);
  if (avio) {
    av_freep(&avio->buffer);
    avio_context_free(&avio);
  } else if (ioBuffer) {
    av_free(ioBuffer);
  }
  if (io.buffer) env->DeleteGlobalRef(io.buffer);
  if (localBuffer) env->DeleteLocalRef(localBuffer);
  if (io.source) env->DeleteGlobalRef(io.source);
  return result;
}
