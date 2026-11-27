package il.luma.gallery;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Interactive normalized pen/mosaic layer; export uses the same vector model. */
final class PhotoOverlay extends View {
  Bitmap base;
  PhotoMarks marks;
  boolean drawing, mosaic;
  int color = Color.WHITE;
  float width = .012f;
  Runnable beforeStroke = () -> {}, changed = () -> {};
  private PhotoMarks.Stroke active;
  private boolean multi;

  PhotoOverlay(Context c) {
    super(c);
    setContentDescription("ציור על התמונה");
  }

  RectF imageBounds() {
    if (base == null) return new RectF();
    float
        s = Math.min(getWidth() / (float) base.getWidth(), getHeight() / (float) base.getHeight()),
        w = base.getWidth() * s,
        h = base.getHeight() * s;
    return new RectF(
        (getWidth() - w) / 2, (getHeight() - h) / 2, (getWidth() + w) / 2, (getHeight() + h) / 2);
  }

  @Override
  protected void onDraw(Canvas c) {
    if (base == null || marks == null) return;
    RectF r = imageBounds();
    c.save();
    c.translate(r.left, r.top);
    c.scale(r.width() / base.getWidth(), r.height() / base.getHeight());
    marks.draw(c, base);
    c.restore();
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    if (!drawing || !isEnabled() || base == null || marks == null) return false;
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) multi = false;
    if (e.getPointerCount() > 1) multi = true;
    if (multi) return true;
    RectF r = imageBounds();
    float x = (e.getX() - r.left) / r.width(), y = (e.getY() - r.top) / r.height();
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      if (!r.contains(e.getX(), e.getY())) return false;
      beforeStroke.run();
      active = new PhotoMarks.Stroke();
      active.color = color;
      active.width = mosaic ? Math.max(.04f, width) : width;
      active.mosaic = mosaic;
      marks.strokes.add(active);
    }
    if (active != null
        && (e.getActionMasked() == MotionEvent.ACTION_DOWN
            || e.getActionMasked() == MotionEvent.ACTION_MOVE)) {
      active.points.add(new PointF(Math.max(0, Math.min(1, x)), Math.max(0, Math.min(1, y))));
      invalidate();
    }
    if (e.getActionMasked() == MotionEvent.ACTION_UP
        || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
      active = null;
      changed.run();
      if (e.getActionMasked() == MotionEvent.ACTION_UP) performClick();
    }
    return true;
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }
}
