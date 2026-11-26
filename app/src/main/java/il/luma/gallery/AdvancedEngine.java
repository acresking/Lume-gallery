package il.luma.gallery;

import ai.onnxruntime.*;
import android.content.Context;
import android.graphics.*;
import java.nio.*;
import java.util.*;

/**
 * Unified local image-editing backend. CPU only; original pixels outside a repair mask are
 * retained.
 */
final class AdvancedEngine implements AutoCloseable {
  final Context context;
  final OrtEnvironment environment = OrtEnvironment.getEnvironment();
  final Map<String, OrtSession> sessions = new HashMap<>();

  AdvancedEngine(Context c) {
    context = c;
  }

  void checkCancelled() throws java.io.InterruptedIOException {
    if (context instanceof ModernActivity) {
      ModernActivity activity = (ModernActivity) context;
      if (activity.ai != null && activity.ai.cancel.get())
        throw new java.io.InterruptedIOException("הפעולה בוטלה");
    }
  }

  OrtSession session(String id) throws Exception {
    checkCancelled();
    OrtSession session = sessions.get(id);
    if (session == null) {
      OrtSession.SessionOptions options = new OrtSession.SessionOptions();
      options.setIntraOpNumThreads(Math.min(4, Runtime.getRuntime().availableProcessors()));
      options.setInterOpNumThreads(1);
      try {
        session = environment.createSession(LocalModels.path(context, id), options);
      } finally {
        options.close();
      }
      sessions.put(id, session);
    }
    return session;
  }

  static int clamp(float value) {
    return Math.max(0, Math.min(255, Math.round(value)));
  }

  Bitmap repair(Bitmap original, Bitmap mask) throws Exception {
    int w = original.getWidth(), h = original.getHeight();
    float scale = Math.min(1, 512f / Math.max(w, h));
    int sw = Math.max(8, Math.round(w * scale)),
        sh = Math.max(8, Math.round(h * scale)),
        iw = (sw + 7) / 8 * 8,
        ih = (sh + 7) / 8 * 8;
    Bitmap resized = Bitmap.createScaledBitmap(original, sw, sh, true),
        resizedMask = Bitmap.createScaledBitmap(mask, sw, sh, true);
    int[] rgb = new int[sw * sh], marks = new int[sw * sh];
    resized.getPixels(rgb, 0, sw, 0, 0, sw, sh);
    resizedMask.getPixels(marks, 0, sw, 0, 0, sw, sh);
    float[] image = new float[3 * iw * ih], selection = new float[iw * ih];
    int count = 0;
    for (int y = 0; y < ih; y++)
      for (int x = 0; x < iw; x++) {
        int i = y * iw + x, p = rgb[Math.min(sh - 1, y) * sw + Math.min(sw - 1, x)];
        image[i] = Color.red(p) / 255f;
        image[i + iw * ih] = Color.green(p) / 255f;
        image[i + 2 * iw * ih] = Color.blue(p) / 255f;
        if (Color.alpha(marks[Math.min(sh - 1, y) * sw + Math.min(sw - 1, x)]) > 0) {
          selection[i] = 1;
          count++;
        }
      }
    if (count == 0) throw new java.io.IOException("סמן את האזור שברצונך להסיר");
    if (count > iw * ih * .85) throw new java.io.IOException("השאר יותר מהתמונה כרקע להשלמה");
    OrtSession session = session("repair");
    Bitmap generated;
    try (OnnxTensor in =
            OnnxTensor.createTensor(
                environment, FloatBuffer.wrap(image), new long[] {1, 3, ih, iw});
        OnnxTensor selected =
            OnnxTensor.createTensor(
                environment, FloatBuffer.wrap(selection), new long[] {1, 1, ih, iw});
        OrtSession.Result result = session.run(Map.of("image", in, "mask", selected))) {
      float[][][][] out = (float[][][][]) result.get(0).getValue();
      int[] pixels = new int[sw * sh];
      for (int y = 0; y < sh; y++)
        for (int x = 0; x < sw; x++)
          pixels[y * sw + x] =
              Color.rgb(clamp(out[0][0][y][x]), clamp(out[0][1][y][x]), clamp(out[0][2][y][x]));
      generated = Bitmap.createBitmap(pixels, sw, sh, Bitmap.Config.ARGB_8888);
    }
    checkCancelled();
    Bitmap fill = Bitmap.createScaledBitmap(generated, w, h, true);
    int[] output = new int[w * h], colors = new int[w * h], maskPixels = new int[w * h];
    original.getPixels(output, 0, w, 0, 0, w, h);
    fill.getPixels(colors, 0, w, 0, 0, w, h);
    mask.getPixels(maskPixels, 0, w, 0, 0, w, h);
    for (int i = 0; i < output.length; i++) {
      int alpha = Color.alpha(maskPixels[i]);
      if (alpha == 0) continue;
      float f = alpha / 255f;
      int old = output[i], fresh = colors[i];
      output[i] =
          Color.argb(
              Color.alpha(old),
              clamp(Color.red(fresh) * f + Color.red(old) * (1 - f)),
              clamp(Color.green(fresh) * f + Color.green(old) * (1 - f)),
              clamp(Color.blue(fresh) * f + Color.blue(old) * (1 - f)));
    }
    return Bitmap.createBitmap(output, w, h, Bitmap.Config.ARGB_8888);
  }

  Bitmap expand(Bitmap source, int percent) throws Exception {
    int padX = Math.max(1, source.getWidth() * percent / 100),
        padY = Math.max(1, source.getHeight() * percent / 100),
        w = source.getWidth() + 2 * padX,
        h = source.getHeight() + 2 * padY;
    Bitmap canvas = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    Canvas paint = new Canvas(canvas);
    paint.drawColor(Color.BLACK);
    Paint exact = new Paint();
    exact.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
    paint.drawBitmap(source, padX, padY, exact);
    Bitmap mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    Canvas mark = new Canvas(mask);
    mark.drawColor(Color.WHITE);
    Paint clear = new Paint();
    clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    mark.drawRect(padX, padY, padX + source.getWidth(), padY + source.getHeight(), clear);
    return repair(canvas, mask);
  }

  float[] faceVector(Bitmap aligned) throws Exception {
    Bitmap bitmap = Bitmap.createScaledBitmap(aligned, 112, 112, true);
    int[] pixels = new int[112 * 112];
    bitmap.getPixels(pixels, 0, 112, 0, 0, 112, 112);
    float[] flat = new float[3 * 112 * 112];
    for (int i = 0; i < pixels.length; i++) {
      flat[i] = Color.red(pixels[i]);
      flat[i + pixels.length] = Color.green(pixels[i]);
      flat[i + 2 * pixels.length] = Color.blue(pixels[i]);
    }
    OrtSession model = session("facegroup");
    try (OnnxTensor in =
            OnnxTensor.createTensor(
                environment, FloatBuffer.wrap(flat), new long[] {1, 3, 112, 112});
        OrtSession.Result result = model.run(Collections.singletonMap("data", in))) {
      float[] out = ((float[][]) result.get(0).getValue())[0];
      double norm = 0;
      for (float f : out) norm += f * f;
      norm = Math.sqrt(norm);
      if (norm == 0) throw new java.io.IOException("לא התקבל וקטור פנים");
      for (int i = 0; i < out.length; i++) out[i] /= (float) norm;
      return out;
    }
  }

  Bitmap upscale(Bitmap source) throws Exception {
    int w = source.getWidth(), h = source.getHeight(), ow = w * 3, oh = h * 3;
    Bitmap base = Bitmap.createScaledBitmap(source, ow, oh, true);
    int[] output = new int[ow * oh];
    base.getPixels(output, 0, ow, 0, 0, ow, oh);
    int[] pixels = new int[w * h];
    source.getPixels(pixels, 0, w, 0, 0, w, h);
    OrtSession model = session("enhance");
    for (int top = 0; top < h; top += 192)
      for (int left = 0; left < w; left += 192) {
        checkCancelled();
        float[] luminance = new float[224 * 224];
        for (int y = 0; y < 224; y++)
          for (int x = 0; x < 224; x++) {
            int sx = Math.min(w - 1, Math.max(0, left + x - 16)),
                sy = Math.min(h - 1, Math.max(0, top + y - 16)),
                color = pixels[sy * w + sx];
            luminance[y * 224 + x] =
                (.299f * Color.red(color) + .587f * Color.green(color) + .114f * Color.blue(color))
                    / 255f;
          }
        try (OnnxTensor input =
                OnnxTensor.createTensor(
                    environment, FloatBuffer.wrap(luminance), new long[] {1, 1, 224, 224});
            OrtSession.Result result = model.run(Collections.singletonMap("input", input))) {
          float[][] values = ((float[][][][]) result.get(0).getValue())[0][0];
          for (int y = 0; y < Math.min(192, h - top) * 3; y++)
            for (int x = 0; x < Math.min(192, w - left) * 3; x++) {
              int pos = (top * 3 + y) * ow + left * 3 + x, color = output[pos];
              float
                  before =
                      .299f * Color.red(color)
                          + .587f * Color.green(color)
                          + .114f * Color.blue(color),
                  delta = values[y + 48][x + 48] * 255 - before;
              output[pos] =
                  Color.rgb(
                      clamp(Color.red(color) + delta),
                      clamp(Color.green(color) + delta),
                      clamp(Color.blue(color) + delta));
            }
        }
      }
    return Bitmap.createBitmap(output, ow, oh, Bitmap.Config.ARGB_8888);
  }

  static Bitmap perspective(Bitmap input, float vertical, float horizontal) {
    int w = input.getWidth(), h = input.getHeight();
    float vx = Math.abs(vertical) * w * .28f, hy = Math.abs(horizontal) * h * .28f;
    float[] src = {0, 0, w, 0, w, h, 0, h};
    float[] dst = {
      vertical > 0 ? vx : 0,
      horizontal > 0 ? hy : 0,
      w - (vertical > 0 ? vx : 0),
      horizontal < 0 ? hy : 0,
      w - (vertical < 0 ? vx : 0),
      h - (horizontal < 0 ? hy : 0),
      vertical < 0 ? vx : 0,
      h - (horizontal > 0 ? hy : 0)
    };
    Matrix matrix = new Matrix();
    matrix.setPolyToPoly(src, 0, dst, 0, 4);
    Bitmap result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(result);
    canvas.drawColor(Color.TRANSPARENT);
    canvas.drawBitmap(input, matrix, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
    return result;
  }

  static Bitmap glare(Bitmap input, float amount) {
    int w = input.getWidth(), h = input.getHeight();
    int[] pixels = new int[w * h];
    input.getPixels(pixels, 0, w, 0, 0, w, h);
    for (int i = 0; i < pixels.length; i++) {
      int color = pixels[i];
      float lum =
          Math.max(Color.red(color), Math.max(Color.green(color), Color.blue(color))) / 255f;
      float reduction = amount * Math.max(0, (lum - .65f) / .35f) * .30f;
      pixels[i] =
          Color.argb(
              Color.alpha(color),
              clamp(Color.red(color) * (1 - reduction)),
              clamp(Color.green(color) * (1 - reduction)),
              clamp(Color.blue(color) * (1 - reduction)));
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
  }

  @Override
  public void close() {
    for (OrtSession session : sessions.values())
      try {
        session.close();
      } catch (Exception ignored) {
      }
    sessions.clear();
  }
}
