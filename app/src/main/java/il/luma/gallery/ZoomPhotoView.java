package il.luma.gallery;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Bounded pinch / pan viewer. Double-tap returns to fit or zooms to 2x. */
final class ZoomPhotoView extends View {
  private Bitmap bitmap;
  java.util.function.BiConsumer<Float, Float> subjectHeld;
  private boolean multiple;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private float zoom = 1, panX, panY, lastX, lastY;
  private final ScaleGestureDetector scale;
  private final GestureDetector gestures;

  boolean isZoomed() {
    return zoom > 1.01f;
  }

  ZoomPhotoView(Context context) {
    super(context);
    setBackgroundColor(Color.BLACK);
    scale =
        new ScaleGestureDetector(
            context,
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
              @Override
              public boolean onScale(ScaleGestureDetector detector) {
                zoom = Math.max(1, Math.min(6, zoom * detector.getScaleFactor()));
                bound();
                invalidate();
                return true;
              }
            });
    gestures =
        new GestureDetector(
            context,
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onDown(MotionEvent e) {
                return true;
              }

              @Override
              public boolean onSingleTapConfirmed(MotionEvent e) {
                performClick();
                return true;
              }

              @Override
              public void onLongPress(MotionEvent e) {
                if (multiple || bitmap == null || subjectHeld == null) return;
                PointF p = imagePoint(e.getX(), e.getY());
                if (p != null) subjectHeld.accept(p.x, p.y);
              }

              @Override
              public boolean onDoubleTap(MotionEvent e) {
                zoom = zoom > 1 ? 1 : 2;
                panX = panY = 0;
                invalidate();
                return true;
              }
            });
  }

  void setBitmap(Bitmap bitmap) {
    this.bitmap = bitmap;
    zoom = 1;
    panX = panY = 0;
    invalidate();
  }

  PointF imagePoint(float x, float y) {
    if (bitmap == null) return null;
    float s = fit() * zoom;
    float px = (x - (getWidth() - bitmap.getWidth() * s) / 2 - panX) / s;
    float py = (y - (getHeight() - bitmap.getHeight() * s) / 2 - panY) / s;
    if (px < 0 || py < 0 || px >= bitmap.getWidth() || py >= bitmap.getHeight()) return null;
    return new PointF(px / bitmap.getWidth(), py / bitmap.getHeight());
  }

  float fit() {
    return bitmap == null
        ? 1
        : Math.min(
            getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight());
  }

  void bound() {
    if (bitmap == null) return;
    float x = Math.max(0, (bitmap.getWidth() * fit() * zoom - getWidth()) / 2);
    float y = Math.max(0, (bitmap.getHeight() * fit() * zoom - getHeight()) / 2);
    panX = Math.max(-x, Math.min(x, panX));
    panY = Math.max(-y, Math.min(y, panY));
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldW, int oldH) {
    super.onSizeChanged(w, h, oldW, oldH);
    bound();
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    if (bitmap == null) return;
    float s = fit() * zoom;
    c.save();
    c.translate(
        (getWidth() - bitmap.getWidth() * s) / 2 + panX,
        (getHeight() - bitmap.getHeight() * s) / 2 + panY);
    c.scale(s, s);
    c.drawBitmap(bitmap, 0, 0, paint);
    c.restore();
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) multiple = false;
    if (e.getPointerCount() > 1) multiple = true;
    if (getParent() != null)
      getParent()
          .requestDisallowInterceptTouchEvent(
              zoom > 1 || scale.isInProgress() || e.getPointerCount() > 1);
    scale.onTouchEvent(e);
    gestures.onTouchEvent(e);
    if (e.getActionMasked() == MotionEvent.ACTION_MOVE
        && !scale.isInProgress()
        && e.getPointerCount() == 1
        && zoom > 1) {
      panX += e.getX() - lastX;
      panY += e.getY() - lastY;
      bound();
      invalidate();
    }
    lastX = e.getX();
    lastY = e.getY();
    return true;
  }
}
