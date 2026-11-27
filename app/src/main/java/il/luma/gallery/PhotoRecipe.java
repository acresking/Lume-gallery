package il.luma.gallery;

import android.graphics.*;

/** One immutable-by-copy recipe for non-destructive history, preview and export. */
final class PhotoRecipe {
  int rotation, crop, filter, format, quality = 92, max = 4096;
  boolean flipX, flipY;
  float brightness, contrast = 1, saturation = 1, warmth, straighten, vertical, horizontal, glare;
  RectF freeCrop = new RectF(0, 0, 1, 1);
  PhotoEffects.Settings details = new PhotoEffects.Settings();
  PhotoMarks marks = new PhotoMarks();
  Bitmap base;

  PhotoRecipe copy() {
    PhotoRecipe r = new PhotoRecipe();
    r.rotation = rotation;
    r.crop = crop;
    r.filter = filter;
    r.format = format;
    r.quality = quality;
    r.max = max;
    r.flipX = flipX;
    r.flipY = flipY;
    r.brightness = brightness;
    r.contrast = contrast;
    r.saturation = saturation;
    r.warmth = warmth;
    r.straighten = straighten;
    r.vertical = vertical;
    r.horizontal = horizontal;
    r.glare = glare;
    r.freeCrop = new RectF(freeCrop);
    r.details = details.copy();
    r.marks = marks.copy();
    r.base = base;
    return r;
  }

  ColorMatrix colors() {
    ColorMatrix m = new ColorMatrix();
    m.setSaturation(filter == 2 ? 0 : saturation);
    float c = contrast + (filter == 1 ? .15f : 0),
        offset = 128 * (1 - c) + brightness,
        warm = warmth + (filter == 3 ? 18 : filter == 4 ? -18 : 0);
    m.postConcat(
        new ColorMatrix(
            new float[] {
              c,
              0,
              0,
              0,
              offset + warm,
              0,
              c,
              0,
              0,
              offset,
              0,
              0,
              c,
              0,
              offset - warm,
              0,
              0,
              0,
              1,
              0
            }));
    if (filter == 5)
      m.postConcat(
          new ColorMatrix(
              new float[] {
                .85f, 0, 0, 0, 25, 0, .85f, 0, 0, 25, 0, 0, .85f, 0, 25, 0, 0, 0, 1, 0
              }));
    if (filter == 6)
      m.postConcat(
          new ColorMatrix(
              new float[] {
                .393f, .769f, .189f, 0, 0, .349f, .686f, .168f, 0, 0, .272f, .534f, .131f, 0, 0, 0,
                0, 0, 1, 0
              }));
    if (filter == 7)
      m.postConcat(
          new ColorMatrix(
              new float[] {1.05f, 0, 0, 0, -8, 0, 1, 0, 0, 0, 0, 0, .9f, 0, 12, 0, 0, 0, 1, 0}));
    if (filter == 8)
      m.postConcat(
          new ColorMatrix(
              new float[] {.9f, 0, 0, 0, 20, 0, .85f, 0, 0, 14, 0, 0, .75f, 0, 16, 0, 0, 0, 1, 0}));
    return m;
  }

  Bitmap geometry(Bitmap input) {
    int w = input.getWidth(), h = input.getHeight(), cw = w, ch = h;
    float[] ratios = {0, 1, 4f / 3, 16f / 9, 3f / 4, 9f / 16, 3f / 2, 2f / 3};
    if (crop > 0) {
      float aspect = ratios[Math.min(crop, ratios.length - 1)];
      if (w / (float) h > aspect) cw = Math.max(1, Math.round(h * aspect));
      else ch = Math.max(1, Math.round(w / aspect));
    }
    Matrix matrix = new Matrix();
    matrix.postScale(flipX ? -1 : 1, flipY ? -1 : 1);
    matrix.postRotate(rotation + straighten);
    Bitmap stage = Bitmap.createBitmap(input, (w - cw) / 2, (h - ch) / 2, cw, ch, matrix, true);
    if (vertical != 0 || horizontal != 0)
      stage = AdvancedEngine.perspective(stage, vertical, horizontal);
    if (glare > 0) stage = AdvancedEngine.glare(stage, glare);
    w = stage.getWidth();
    h = stage.getHeight();
    int left = Math.max(0, Math.min(w - 1, (int) (freeCrop.left * w))),
        top = Math.max(0, Math.min(h - 1, (int) (freeCrop.top * h))),
        right = Math.max(left + 1, Math.min(w, Math.round(freeCrop.right * w))),
        bottom = Math.max(top + 1, Math.min(h, Math.round(freeCrop.bottom * h)));
    return Bitmap.createBitmap(stage, left, top, right - left, bottom - top);
  }

  Bitmap render(Bitmap input, boolean annotations) {
    Bitmap stage = geometry(input),
        out = Bitmap.createBitmap(stage.getWidth(), stage.getHeight(), Bitmap.Config.ARGB_8888);
    Paint p = new Paint(3);
    p.setColorFilter(new ColorMatrixColorFilter(colors()));
    new Canvas(out).drawBitmap(stage, 0, 0, p);
    PhotoEffects.apply(out, details);
    if (annotations) marks.draw(new Canvas(out), out);
    return out;
  }

  static Bitmap sampled(Bitmap bitmap, int max) {
    int side = Math.max(bitmap.getWidth(), bitmap.getHeight());
    if (side <= max) return bitmap;
    float s = max / (float) side;
    return Bitmap.createScaledBitmap(
        bitmap,
        Math.max(1, Math.round(bitmap.getWidth() * s)),
        Math.max(1, Math.round(bitmap.getHeight() * s)),
        true);
  }
}
