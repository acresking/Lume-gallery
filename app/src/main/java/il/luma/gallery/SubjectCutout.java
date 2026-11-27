package il.luma.gallery;

import android.graphics.*;

/** Selects one connected foreground region near the held point; never modifies the source. */
final class SubjectCutout {
  static Bitmap select(Bitmap mask, float nx, float ny) throws java.io.IOException {
    int w = mask.getWidth(), h = mask.getHeight(), count = w * h;
    int[] pixels = new int[count];
    mask.getPixels(pixels, 0, w, 0, 0, w, h);
    int x = Math.max(0, Math.min(w - 1, Math.round(nx * (w - 1)))),
        y = Math.max(0, Math.min(h - 1, Math.round(ny * (h - 1))));
    int seed = y * w + x;
    if (Color.alpha(pixels[seed]) < 32) {
      int best = -1;
      double distance = Double.MAX_VALUE;
      int radius = Math.max(8, Math.min(w, h) / 8);
      for (int yy = Math.max(0, y - radius); yy <= Math.min(h - 1, y + radius); yy++)
        for (int xx = Math.max(0, x - radius); xx <= Math.min(w - 1, x + radius); xx++) {
          double d = (xx - x) * (double) (xx - x) + (yy - y) * (double) (yy - y);
          if (Color.alpha(pixels[yy * w + xx]) >= 128 && d < distance) {
            best = yy * w + xx;
            distance = d;
          }
        }
      if (best < 0)
        throw new java.io.IOException("לא נמצא אדם באזור שסימנת. נסה לחיצה ארוכה על מרכז האדם.");
      seed = best;
    }
    boolean[] keep = new boolean[count];
    int[] queue = new int[count];
    int head = 0, tail = 0;
    queue[tail++] = seed;
    keep[seed] = true;
    while (head < tail) {
      int p = queue[head++], px = p % w, py = p / w;
      for (int direction = 0; direction < 4; direction++) {
        int next =
            direction == 0
                ? (px > 0 ? p - 1 : -1)
                : direction == 1
                    ? (px < w - 1 ? p + 1 : -1)
                    : direction == 2 ? (py > 0 ? p - w : -1) : (py < h - 1 ? p + w : -1);
        if (next >= 0 && !keep[next] && Color.alpha(pixels[next]) >= 32) {
          keep[next] = true;
          queue[tail++] = next;
        }
      }
    }
    if (tail < Math.max(8, count / 2000))
      throw new java.io.IOException("האזור קטן מדי. בחר אדם ברור יותר.");
    for (int i = 0; i < count; i++) if (!keep[i]) pixels[i] = 0;
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
  }

  static Bitmap tight(Bitmap source) throws java.io.IOException {
    int w = source.getWidth(), h = source.getHeight(), left = w, top = h, right = -1, bottom = -1;
    int[] row = new int[w];
    for (int y = 0; y < h; y++) {
      source.getPixels(row, 0, w, 0, y, w, 1);
      for (int x = 0; x < w; x++)
        if (Color.alpha(row[x]) > 16) {
          left = Math.min(left, x);
          right = Math.max(right, x);
          top = Math.min(top, y);
          bottom = Math.max(bottom, y);
        }
    }
    if (right < 0) throw new java.io.IOException("המסכה ריקה. שחזר אזור לפני שמירה.");
    left = Math.max(0, left - 4);
    top = Math.max(0, top - 4);
    right = Math.min(w - 1, right + 4);
    bottom = Math.min(h - 1, bottom + 4);
    return Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1);
  }

  static Bitmap sticker(Bitmap source) throws java.io.IOException {
    Bitmap cropped = tight(source), out = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(out);
    float scale = 472f / Math.max(cropped.getWidth(), cropped.getHeight());
    float w = cropped.getWidth() * scale, h = cropped.getHeight() * scale;
    RectF rect = new RectF((512 - w) / 2, (512 - h) / 2, (512 + w) / 2, (512 + h) / 2);
    Paint p = new Paint(3);
    p.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
    for (int dy = -4; dy <= 4; dy += 2)
      for (int dx = -4; dx <= 4; dx += 2) {
        RectF outline = new RectF(rect);
        outline.offset(dx, dy);
        c.drawBitmap(cropped, null, outline, p);
      }
    p.setColorFilter(null);
    c.drawBitmap(cropped, null, rect, p);
    return out;
  }
}
