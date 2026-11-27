package il.luma.gallery;

import android.content.Context;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.text.*;
import androidx.media3.common.*;
import androidx.media3.common.audio.*;
import androidx.media3.effect.*;
import androidx.media3.transformer.*;
import java.util.*;

/** Shared preview/export recipe. No encoder is started by these pure composition builders. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class VideoRecipe {
  static final class Clip {
    final Uri uri;
    final long duration;
    final int width, height;
    final boolean audio;

    Clip(Uri u, long d, int w, int h, boolean audio) {
      uri = u;
      duration = d;
      width = Math.max(1, w);
      height = Math.max(1, h);
      this.audio = audio;
    }

    static Clip read(Context c, Uri uri) throws Exception {
      MediaMetadataRetriever r = new MediaMetadataRetriever();
      try {
        r.setDataSource(c, uri);
        long d = Long.parseLong(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        int
            w =
                Integer.parseInt(
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)),
            h =
                Integer.parseInt(
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        String angle = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        if ("90".equals(angle) || "270".equals(angle)) {
          int t = w;
          w = h;
          h = t;
        }
        if (d <= 0 || w <= 0 || h <= 0) throw new java.io.IOException("הווידאו אינו קריא");
        return new Clip(
            uri,
            d,
            w,
            h,
            "yes".equals(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)));
      } finally {
        r.release();
      }
    }
  }

  final ArrayList<Clip> clips = new ArrayList<>();
  long start, end;
  int rotation, aspect, filter, resolution, codec, fps, bitrate;
  boolean flipX, flipY, mute;
  float brightness,
      contrast,
      saturation,
      warmth,
      speed = 1,
      pitch = 1,
      volume = 1,
      centerX = .5f,
      centerY = .5f,
      zoom = 1,
      fadeIn,
      fadeOut;
  String caption = "";
  int captionColor = Color.WHITE, captionPosition = 2;
  float captionScale = 1;

  VideoRecipe copy() {
    VideoRecipe v = new VideoRecipe();
    v.clips.addAll(clips);
    v.start = start;
    v.end = end;
    v.rotation = rotation;
    v.aspect = aspect;
    v.filter = filter;
    v.resolution = resolution;
    v.codec = codec;
    v.fps = fps;
    v.bitrate = bitrate;
    v.flipX = flipX;
    v.flipY = flipY;
    v.mute = mute;
    v.brightness = brightness;
    v.contrast = contrast;
    v.saturation = saturation;
    v.warmth = warmth;
    v.speed = speed;
    v.pitch = pitch;
    v.volume = volume;
    v.centerX = centerX;
    v.centerY = centerY;
    v.zoom = zoom;
    v.fadeIn = fadeIn;
    v.fadeOut = fadeOut;
    v.caption = caption;
    v.captionColor = captionColor;
    v.captionPosition = captionPosition;
    v.captionScale = captionScale;
    return v;
  }

  void validate() throws java.io.IOException {
    if (clips.isEmpty() || clips.size() > 6) throw new java.io.IOException("בחר 1–6 קטעי וידאו");
    if (start < 0 || end <= start || end > clips.get(0).duration)
      throw new java.io.IOException("זמני החיתוך אינם תקינים");
    if (!Float.isFinite(speed)
        || speed < .25f
        || speed > 4
        || !Float.isFinite(pitch)
        || pitch < .5f
        || pitch > 2) throw new java.io.IOException("המהירות או גובה הצליל אינם תקינים");
  }

  long sourceDuration(int index) {
    return index == 0 ? end - start : clips.get(index).duration;
  }

  long outputDuration() {
    double duration = 0;
    for (int i = 0; i < clips.size(); i++) duration += sourceDuration(i) / speed;
    return Math.round(duration);
  }

  int width(Clip clip) {
    return rotation % 180 == 0 ? clip.width : clip.height;
  }

  int height(Clip clip) {
    return rotation % 180 == 0 ? clip.height : clip.width;
  }

  RectF crop(Clip clip) {
    float[] aspects = {0, 1, 9f / 16, 16f / 9, 4f / 3, 3f / 4};
    float ratio = width(clip) / (float) height(clip),
        target = aspect == 0 ? ratio : aspects[Math.max(0, Math.min(aspects.length - 1, aspect))];
    float cw = 1, ch = 1;
    if (ratio > target) cw = target / ratio;
    else ch = ratio / target;
    cw /= zoom;
    ch /= zoom;
    float x = Math.max(cw / 2, Math.min(1 - cw / 2, centerX)),
        y = Math.max(ch / 2, Math.min(1 - ch / 2, centerY));
    return new RectF(x - cw / 2, y - ch / 2, x + cw / 2, y + ch / 2);
  }

  int[] outputSize() {
    Clip first = clips.get(0);
    RectF crop = crop(first);
    float ratio = width(first) * crop.width() / (height(first) * crop.height());
    int h =
        resolution == 0
            ? Math.round(height(first) * crop.height())
            : new int[] {0, 2160, 1080, 720, 480}[resolution];
    if (clips.size() > 1 && resolution == 0) h = Math.min(1080, h);
    h = Math.max(2, (h / 2) * 2);
    return new int[] {Math.max(2, Math.round(h * ratio / 2) * 2), h};
  }

  ArrayList<Effect> videoEffects(Clip clip, boolean exported) {
    ArrayList<Effect> out = new ArrayList<>();
    if (rotation != 0 || flipX || flipY)
      out.add(
          new ScaleAndRotateTransformation.Builder()
              .setScale(flipX ? -1 : 1, flipY ? -1 : 1)
              .setRotationDegrees(rotation)
              .build());
    RectF crop = crop(clip);
    if (crop.left > .0001f || crop.top > .0001f || crop.right < .9999f || crop.bottom < .9999f)
      out.add(
          new Crop(crop.left * 2 - 1, crop.right * 2 - 1, 1 - crop.bottom * 2, 1 - crop.top * 2));
    float bright = brightness, c = contrast, s = saturation, warm = warmth;
    if (filter == 1) {
      c += .12f;
      s += 20;
    }
    if (filter == 3) warm += .5f;
    if (filter == 4) warm -= .5f;
    if (filter == 5) {
      c -= .2f;
      bright += .06f;
    }
    if (bright != 0) out.add(new Brightness(Math.max(-1, Math.min(1, bright))));
    if (c != 0) out.add(new Contrast(Math.max(-.9f, Math.min(.9f, c))));
    if (s != 0)
      out.add(
          new HslAdjustment.Builder().adjustSaturation(Math.max(-100, Math.min(100, s))).build());
    if (warm != 0)
      out.add(
          new RgbAdjustment.Builder()
              .setRedScale(1 + Math.max(-.8f, Math.min(.8f, warm)) * .2f)
              .setBlueScale(1 - Math.max(-.8f, Math.min(.8f, warm)) * .2f)
              .build());
    if (filter == 2) out.add(RgbFilter.createGrayscaleFilter());
    if (!caption.isBlank()) {
      int actualWidth = Math.max(100, Math.round(width(clip) * crop.width()));
      Bitmap text = textBitmap(actualWidth);
      float y = captionPosition == 0 ? .86f : captionPosition == 1 ? 0 : -.86f;
      OverlaySettings settings =
          new OverlaySettings.Builder()
              .setScale(actualWidth * .9f / text.getWidth(), actualWidth * .9f / text.getWidth())
              .setBackgroundFrameAnchor(0, y)
              .setOverlayFrameAnchor(0, captionPosition == 0 ? 1 : captionPosition == 1 ? 0 : -1)
              .build();
      out.add(
          new OverlayEffect(
              Collections.singletonList(BitmapOverlay.createStaticBitmapOverlay(text, settings))));
    }
    if (resolution > 0 || clips.size() > 1) {
      int[] size = outputSize();
      out.add(
          Presentation.createForWidthAndHeight(size[0], size[1], Presentation.LAYOUT_SCALE_TO_FIT));
    }
    return out;
  }

  Bitmap textBitmap(int videoWidth) {
    int w = Math.min(1200, Math.max(160, videoWidth));
    TextPaint p = new TextPaint(3);
    p.setColor(captionColor);
    p.setTextSize(w * .055f * captionScale);
    p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    p.setShadowLayer(3, 0, 1, Color.BLACK);
    StaticLayout layout =
        StaticLayout.Builder.obtain(caption, 0, caption.length(), p, w)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setMaxLines(6)
            .build();
    Bitmap bitmap =
        Bitmap.createBitmap(w, Math.max(1, layout.getHeight() + 8), Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bitmap);
    c.translate(0, 4);
    layout.draw(c);
    return bitmap;
  }

  MediaItem mediaItem(int index) {
    Clip clip = clips.get(index);
    MediaItem.Builder b = new MediaItem.Builder().setUri(clip.uri);
    if (index == 0)
      b.setClippingConfiguration(
          new MediaItem.ClippingConfiguration.Builder()
              .setStartPositionMs(start)
              .setEndPositionMs(end)
              .build());
    return b.build();
  }

  Composition composition(boolean audioOnly) throws java.io.IOException {
    validate();
    if (audioOnly && (mute || clips.stream().noneMatch(c -> c.audio)))
      throw new java.io.IOException("אין שמע פעיל לחילוץ");
    ArrayList<EditedMediaItem> items = new ArrayList<>();
    boolean anyAudio = false;
    for (int i = 0; i < clips.size(); i++) {
      Clip clip = clips.get(i);
      anyAudio |= clip.audio;
      ArrayList<AudioProcessor> audio = new ArrayList<>();
      ArrayList<Effect> video = audioOnly ? new ArrayList<>() : videoEffects(clip, true);
      if (!mute && clip.audio)
        audio.add(
            new GainFadeProcessor(
                volume,
                (long) (fadeIn * 1000 * speed),
                (long) (fadeOut * 1000 * speed),
                sourceDuration(i)));
      if (speed != 1) {
        if (!audioOnly && !mute && clip.audio) {
          android.util.Pair<AudioProcessor, Effect> pair =
              Effects.createExperimentalSpeedChangingEffect(
                  new SpeedProvider() {
                    public float getSpeed(long timeUs) {
                      return speed;
                    }

                    public long getNextSpeedChangeTimeUs(long timeUs) {
                      return C.TIME_UNSET;
                    }
                  });
          audio.add(pair.first);
          video.add(pair.second);
        } else {
          if (!audioOnly) video.add(new SpeedChangeEffect(speed));
          if (!mute && clip.audio) {
            SonicAudioProcessor sonic = new SonicAudioProcessor();
            sonic.setSpeed(speed);
            sonic.setPitch(pitch);
            audio.add(sonic);
          }
        }
      }
      if (pitch != 1 && (speed == 1 || (!audioOnly && !mute && clip.audio))) {
        SonicAudioProcessor sonic = new SonicAudioProcessor();
        sonic.setPitch(pitch);
        audio.add(sonic);
      }
      EditedMediaItem.Builder edited =
          new EditedMediaItem.Builder(mediaItem(i))
              .setRemoveAudio(mute)
              .setRemoveVideo(audioOnly)
              .setEffects(new Effects(audio, video));
      if (fps > 0 && !audioOnly) edited.setFrameRate(fps);
      items.add(edited.build());
    }
    if (audioOnly && (mute || !anyAudio)) throw new java.io.IOException("אין שמע פעיל לחילוץ");
    return new Composition.Builder(new EditedMediaItemSequence(items))
        .experimentalSetForceAudioTrack(anyAudio && !mute)
        .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
        .build();
  }
}
