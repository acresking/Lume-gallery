package il.luma.gallery;

import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Renders the supplied artwork unchanged; crops only its presentation margin at draw time. */
final class BrandMark extends Drawable {
  private static Bitmap artwork;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final int intrinsic;

  BrandMark(Resources resources) {
    intrinsic = (int) (48 * resources.getDisplayMetrics().density);
    synchronized (BrandMark.class) {
      if (artwork == null) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 2;
        artwork = BitmapFactory.decodeResource(resources, R.drawable.brand_logo, options);
      }
    }
  }

  @Override
  public void draw(Canvas canvas) {
    if (artwork == null) return;
    Rect bounds = getBounds();
    int w = artwork.getWidth(), h = artwork.getHeight();
    Rect source =
        new Rect(
            Math.round(w * .1335f),
            Math.round(h * .1335f),
            Math.round(w * .8665f),
            Math.round(h * .8665f));
    Path clip = new Path();
    RectF destination = new RectF(bounds);
    clip.addRoundRect(
        destination, bounds.width() * .22f, bounds.height() * .22f, Path.Direction.CW);
    canvas.save();
    canvas.clipPath(clip);
    canvas.drawBitmap(artwork, source, destination, paint);
    canvas.restore();
  }

  @Override
  public int getIntrinsicWidth() {
    return intrinsic;
  }

  @Override
  public int getIntrinsicHeight() {
    return intrinsic;
  }

  @Override
  public void setAlpha(int alpha) {
    paint.setAlpha(alpha);
  }

  @Override
  public void setColorFilter(ColorFilter color) {
    paint.setColorFilter(color);
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
