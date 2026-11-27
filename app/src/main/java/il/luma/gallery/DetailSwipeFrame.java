package il.luma.gallery;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import java.util.function.BooleanSupplier;

/** Vertical details gesture; horizontal paging and zoom gestures retain ownership. */
final class DetailSwipeFrame extends FrameLayout {
  BooleanSupplier allowed = () -> true;
  Runnable open = () -> {};
  private float startX, startY;
  private boolean blocked, dragging;
  private final float threshold;

  DetailSwipeFrame(Context context) {
    super(context);
    setClickable(true);
    threshold = 72 * getResources().getDisplayMetrics().density;
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent e) {
    // Observe pointers even when a zooming child disallows interception.
    if (e.getPointerCount() > 1 || e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN)
      blocked = true;
    return super.dispatchTouchEvent(e);
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent e) {
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      startX = e.getX();
      startY = e.getY();
      blocked = !allowed.getAsBoolean();
      dragging = false;
    }
    if (e.getPointerCount() > 1 || e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN)
      blocked = true;
    if (!allowed.getAsBoolean()) blocked = true;
    if (e.getActionMasked() == MotionEvent.ACTION_MOVE && !blocked) {
      float dy = startY - e.getY(), dx = Math.abs(e.getX() - startX);
      if (dx > threshold / 3 && dx > Math.abs(dy)) blocked = true;
      if (!blocked && dy > threshold && dx < dy * .65f) {
        dragging = true;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    if (!dragging) return super.onTouchEvent(e);
    if (e.getPointerCount() > 1) blocked = true;
    if (e.getActionMasked() == MotionEvent.ACTION_UP) {
      boolean valid =
          !blocked
              && allowed.getAsBoolean()
              && startY - e.getY() > threshold
              && Math.abs(e.getX() - startX) < (startY - e.getY()) * .65f;
      dragging = false;
      if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
      if (valid) open.run();
    } else if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
      dragging = false;
      blocked = true;
    }
    return true;
  }
}
