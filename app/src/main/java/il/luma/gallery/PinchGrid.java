package il.luma.gallery;

import android.view.*;
import androidx.recyclerview.widget.*;

/** Discrete density steps with an anchor; no scrolling or selection during a pinch. */
final class PinchGrid extends RecyclerView.SimpleOnItemTouchListener {
  private final ModernActivity host;
  private final RecyclerView grid;
  private final ScaleGestureDetector detector;
  private boolean scaling;
  private float product = 1;

  PinchGrid(ModernActivity activity, RecyclerView view) {
    host = activity;
    grid = view;
    detector =
        new ScaleGestureDetector(
            host,
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
              @Override
              public boolean onScaleBegin(ScaleGestureDetector d) {
                scaling = true;
                product = 1;
                grid.stopScroll();
                return true;
              }

              @Override
              public boolean onScale(ScaleGestureDetector d) {
                product *= d.getScaleFactor();
                if (product > 1.22f) {
                  change(-1);
                  product = 1;
                } else if (product < .82f) {
                  change(1);
                  product = 1;
                }
                return true;
              }
            });
  }

  static int nextColumns(int current, int delta) {
    return Math.max(2, Math.min(5, current + delta));
  }

  void change(int delta) {
    if (!(grid.getLayoutManager() instanceof GridLayoutManager)) return;
    if (host.densityOverview(delta)) return;
    GridLayoutManager manager = (GridLayoutManager) grid.getLayoutManager();
    int next = nextColumns(manager.getSpanCount(), delta);
    if (next == manager.getSpanCount()) return;
    int anchor = manager.findFirstVisibleItemPosition();
    View first = anchor < 0 ? null : manager.findViewByPosition(anchor);
    int top = first == null ? 0 : first.getTop() - grid.getPaddingTop();
    manager.setSpanCount(next);
    host.prefs.edit().putInt("columns", next).apply();
    if (anchor >= 0) manager.scrollToPositionWithOffset(anchor, top);
    if (host.motion()) {
      grid.animate().cancel();
      grid.setAlpha(.84f);
      grid.animate().alpha(1).setDuration(160).start();
    }
    grid.announceForAccessibility(next + " עמודות");
    host.feedback(grid);
  }

  @Override
  public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent event) {
    if (!host.prefs.getBoolean("pinchGrid", true)) return false;
    detector.onTouchEvent(event);
    if (event.getPointerCount() > 1) {
      scaling = true;
      rv.stopScroll();
    }
    boolean consume = scaling;
    if (event.getActionMasked() == MotionEvent.ACTION_UP
        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) scaling = false;
    return consume;
  }

  @Override
  public void onTouchEvent(RecyclerView rv, MotionEvent event) {
    detector.onTouchEvent(event);
    if (event.getActionMasked() == MotionEvent.ACTION_UP
        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) scaling = false;
  }
}
