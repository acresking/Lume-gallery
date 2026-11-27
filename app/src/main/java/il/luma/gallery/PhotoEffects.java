package il.luma.gallery;

import android.graphics.*;

/** Ordinary local adjustments, deliberately not labeled as learned AI. */
final class PhotoEffects {
  static final class Settings {
    float exposure,
        highlights,
        shadows,
        whites,
        blacks,
        tint,
        vibrance,
        sharpness,
        denoise,
        vignette,
        grain;
    float gamma = 1;

    Settings copy() {
      Settings s = new Settings();
      s.exposure = exposure;
      s.highlights = highlights;
      s.shadows = shadows;
      s.whites = whites;
      s.blacks = blacks;
      s.tint = tint;
      s.vibrance = vibrance;
      s.sharpness = sharpness;
      s.denoise = denoise;
      s.vignette = vignette;
      s.grain = grain;
      s.gamma = gamma;
      return s;
    }

    boolean neutral() {
      return exposure == 0
          && highlights == 0
          && shadows == 0
          && whites == 0
          && blacks == 0
          && tint == 0
          && vibrance == 0
          && sharpness == 0
          && denoise == 0
          && vignette == 0
          && grain == 0
          && gamma == 1;
    }
  }

  static int clamp(float x) {
    return Math.max(0, Math.min(255, Math.round(x)));
  }

  static void apply(Bitmap image, Settings s) {
    if (s.neutral()) return;
    int w = image.getWidth(), h = image.getHeight();
    int[] row = new int[w];
    float exposure = (float) Math.pow(2, s.exposure);
    for (int y = 0; y < h; y++) {
      if (Thread.currentThread().isInterrupted())
        throw new java.util.concurrent.CancellationException();
      image.getPixels(row, 0, w, 0, y, w, 1);
      for (int x = 0; x < w; x++) {
        int c = row[x], alpha = Color.alpha(c);
        if (alpha == 0) continue;
        float r = Color.red(c) * exposure,
            g = Color.green(c) * exposure,
            b = Color.blue(c) * exposure;
        float lum = (.2126f * r + .7152f * g + .0722f * b) / 255f;
        float shadow = Math.max(0, 1 - lum), high = Math.max(0, Math.min(1, lum));
        float add =
            s.shadows * 75 * shadow * shadow
                + s.highlights * 70 * high * high
                + s.whites * 35 * high * high * high
                + s.blacks * 35 * shadow * shadow * shadow;
        r += add + s.tint * 12;
        g += add - s.tint * 15;
        b += add + s.tint * 12;
        if (s.gamma != 1) {
          r = 255 * (float) Math.pow(Math.max(0, r) / 255, 1 / s.gamma);
          g = 255 * (float) Math.pow(Math.max(0, g) / 255, 1 / s.gamma);
          b = 255 * (float) Math.pow(Math.max(0, b) / 255, 1 / s.gamma);
        }
        float mid = (r + g + b) / 3,
            range = (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b))) / 255f;
        float vivid = 1 + s.vibrance * (1 - Math.min(1, range));
        r = mid + (r - mid) * vivid;
        g = mid + (g - mid) * vivid;
        b = mid + (b - mid) * vivid;
        float nx = 2f * x / Math.max(1, w - 1) - 1, ny = 2f * y / Math.max(1, h - 1) - 1;
        float shade = 1 - s.vignette * Math.min(.85f, Math.max(0, nx * nx + ny * ny - .15f) * .5f);
        int hash = x * 73856093 ^ y * 19349663;
        float noise = (((hash ^ (hash >>> 13)) & 255) / 255f - .5f) * s.grain * 36;
        row[x] =
            Color.argb(
                alpha,
                clamp(r * shade + noise),
                clamp(g * shade + noise),
                clamp(b * shade + noise));
      }
      image.setPixels(row, 0, w, 0, y, w, 1);
    }
    if (s.sharpness == 0 && s.denoise == 0) return;
    int[] previous = new int[w], current = new int[w], next = new int[w], output = new int[w];
    image.getPixels(current, 0, w, 0, 0, w, 1);
    System.arraycopy(current, 0, previous, 0, w);
    image.getPixels(next, 0, w, 0, Math.min(1, h - 1), w, 1);
    for (int y = 0; y < h; y++) {
      if (Thread.currentThread().isInterrupted())
        throw new java.util.concurrent.CancellationException();
      for (int x = 0; x < w; x++) {
        int c = current[x];
        if (Color.alpha(c) == 0) {
          output[x] = c;
          continue;
        }
        float r = 0, g = 0, b = 0;
        int n = 0;
        for (int yy = 0; yy < 3; yy++) {
          int[] line = yy == 0 ? previous : yy == 1 ? current : next;
          for (int xx = Math.max(0, x - 1); xx <= Math.min(w - 1, x + 1); xx++) {
            int p = line[xx];
            if (Color.alpha(p) == 0) continue;
            r += Color.red(p);
            g += Color.green(p);
            b += Color.blue(p);
            n++;
          }
        }
        float k = s.sharpness * 1.5f - s.denoise * .8f;
        output[x] =
            Color.argb(
                Color.alpha(c),
                clamp(Color.red(c) + k * (Color.red(c) - r / n)),
                clamp(Color.green(c) + k * (Color.green(c) - g / n)),
                clamp(Color.blue(c) + k * (Color.blue(c) - b / n)));
      }
      image.setPixels(output, 0, w, 0, y, w, 1);
      int[] old = previous;
      previous = current;
      current = next;
      next = old;
      if (y + 2 < h) image.getPixels(next, 0, w, 0, y + 2, w, 1);
      else System.arraycopy(current, 0, next, 0, w);
    }
  }

  static float automaticExposure(Bitmap image) {
    long sum = 0, n = 0;
    int step = Math.max(1, Math.min(image.getWidth(), image.getHeight()) / 80);
    for (int y = 0; y < image.getHeight(); y += step)
      for (int x = 0; x < image.getWidth(); x += step) {
        int c = image.getPixel(x, y);
        if (Color.alpha(c) > 32) {
          sum += (int) (.2126 * Color.red(c) + .7152 * Color.green(c) + .0722 * Color.blue(c));
          n++;
        }
      }
    if (n == 0 || sum == 0) return 0;
    return Math.max(
        -1.2f, Math.min(1.2f, (float) (Math.log(120d / (sum / (double) n)) / Math.log(2))));
  }
}
