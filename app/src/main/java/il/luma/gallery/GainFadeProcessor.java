package il.luma.gallery;

import androidx.media3.common.C;
import androidx.media3.common.audio.*;
import java.nio.*;

/** Channel-independent PCM gain/fades. Runs before speed adjustment in the export pipeline. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class GainFadeProcessor extends BaseAudioProcessor {
  final float gain;
  final long fadeInMs, fadeOutMs, durationMs;
  long frames;

  GainFadeProcessor(float gain, long fadeIn, long fadeOut, long duration) {
    if (!Float.isFinite(gain) || gain < 0 || gain > 1) throw new IllegalArgumentException();
    this.gain = gain;
    fadeInMs = Math.max(0, fadeIn);
    fadeOutMs = Math.max(0, fadeOut);
    durationMs = Math.max(0, duration);
  }

  @Override
  protected AudioFormat onConfigure(AudioFormat f) throws UnhandledAudioFormatException {
    if (f.encoding != C.ENCODING_PCM_16BIT && f.encoding != C.ENCODING_PCM_FLOAT)
      throw new UnhandledAudioFormatException(f);
    return f;
  }

  @Override
  protected void onFlush() {
    frames = 0;
  }

  float at(long frame) {
    double ms = frame * 1000d / inputAudioFormat.sampleRate;
    double f = 1;
    if (fadeInMs > 0) f = Math.min(f, ms / fadeInMs);
    if (fadeOutMs > 0 && durationMs > 0) f = Math.min(f, Math.max(0, durationMs - ms) / fadeOutMs);
    return gain * (float) Math.max(0, Math.min(1, f));
  }

  @Override
  public void queueInput(ByteBuffer in) {
    in.order(ByteOrder.nativeOrder());
    ByteBuffer out = replaceOutputBuffer(in.remaining()).order(ByteOrder.nativeOrder());
    int channels = inputAudioFormat.channelCount, index = 0;
    if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
      while (in.remaining() >= 4) {
        out.putFloat(Math.max(-1, Math.min(1, in.getFloat() * at(frames))));
        if (++index == channels) {
          frames++;
          index = 0;
        }
      }
    } else
      while (in.remaining() >= 2) {
        out.putShort(
            (short)
                Math.max(
                    Short.MIN_VALUE,
                    Math.min(Short.MAX_VALUE, Math.round(in.getShort() * at(frames)))));
        if (++index == channels) {
          frames++;
          index = 0;
        }
      }
    out.flip();
  }
}
