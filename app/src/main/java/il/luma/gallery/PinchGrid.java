package il.luma.gallery;

import android.graphics.RectF;
import android.view.*;
import androidx.recyclerview.widget.*;
import java.util.*;

/** Reference-style photo grids: large, regular, compact, month and year mosaics. */
final class PinchGrid extends RecyclerView.SimpleOnItemTouchListener {
  private final ModernActivity host;
  private final RecyclerView grid;
  private final ScaleGestureDetector detector;
  private boolean scaling;
  private float product = 1, focusX, focusY;

  PinchGrid(ModernActivity a, RecyclerView view) {
    host = a;
    grid = view;
    detector =
        new ScaleGestureDetector(
            a,
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
              @Override
              public boolean onScaleBegin(ScaleGestureDetector d) {
                scaling = true;
                product = 1;
                focusX = d.getFocusX();
                focusY = d.getFocusY();
                grid.stopScroll();
                return true;
              }

              @Override
              public boolean onScale(ScaleGestureDetector d) {
                product *= d.getScaleFactor();
                focusX = d.getFocusX();
                focusY = d.getFocusY();
                if (grid instanceof GalleryGrid && host.motion())
                  ((GalleryGrid) grid).preview(product, focusX, focusY);
                if (product > 1.25f) {
                  changeAt(-1, focusX, focusY);
                  product = 1;
                } else if (product < .78f) {
                  changeAt(1, focusX, focusY);
                  product = 1;
                }
                return true;
              }

              @Override
              public void onScaleEnd(ScaleGestureDetector d) {
                if (grid instanceof GalleryGrid) ((GalleryGrid) grid).settle(host.motion());
              }
            });
  }

  static int nextColumns(int current, int delta) {
    int[] stops = {2, 3, 5, 9, 15};
    if (delta > 0) {
      for (int n : stops) if (n > current) return n;
      return 15;
    }
    for (int i = stops.length - 1; i >= 0; i--) if (stops[i] < current) return stops[i];
    return 2;
  }

  void change(int delta) {
    changeAt(delta, grid.getWidth() / 2f, grid.getHeight() / 2f);
  }

  void changeAt(int delta, float x, float y) {
    if (!(grid.getLayoutManager() instanceof GridLayoutManager)) return;
    GridLayoutManager layout = (GridLayoutManager) grid.getLayoutManager();
    int current = host.prefs.getInt("columns", 3), next = nextColumns(current, delta);
    if (host.selectingForGrid()) next = Math.min(5, next);
    if (next == current && host.viewMode.equals("photos")) return;
    float px = grid instanceof GalleryGrid ? ((GalleryGrid) grid).contentX(x) : x,
        py = grid instanceof GalleryGrid ? ((GalleryGrid) grid).contentY(y) : y;
    View under = grid.findChildViewUnder(px, py);
    int position =
        under == null ? layout.findFirstVisibleItemPosition() : grid.getChildAdapterPosition(under);
    MainActivity.Media item = host.gridMedia(position);
    float fraction =
        under == null || under.getHeight() == 0
            ? .5f
            : Math.max(0, Math.min(1, (py - under.getTop()) / under.getHeight()));
    if (item == null) {
      int first = layout.findFirstVisibleItemPosition();
      item = host.gridMedia(first + 1);
      if (item == null) item = host.gridMedia(first + 2);
    }
    Map<String, RectF> before =
        grid instanceof GalleryGrid ? ((GalleryGrid) grid).positions() : new HashMap<>();
    int oldTop = under == null ? 0 : under.getTop() - grid.getPaddingTop();
    host.applyGridDensity(next);
    layout = (GridLayoutManager) grid.getLayoutManager();
    int target = item == null ? Math.max(0, position) : host.gridPosition(item);
    if (target >= 0) {
      int height =
          Math.max(1, (grid.getWidth() - grid.getPaddingLeft() - grid.getPaddingRight()) / next);
      layout.scrollToPositionWithOffset(
          target, item == null ? oldTop : Math.round(y - grid.getPaddingTop() - fraction * height));
    }
    if (grid instanceof GalleryGrid) ((GalleryGrid) grid).morph(before, host.motion());
    grid.announceForAccessibility(
        next + " עמודות · " + (next >= 15 ? "שנים" : next >= 9 ? "חודשים" : "תמונות"));
    host.feedback(grid);
  }

  @Override
  public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
    if (!host.prefs.getBoolean("pinchGrid", true)) return false;
    detector.onTouchEvent(e);
    if (e.getPointerCount() > 1) {
      scaling = true;
      rv.stopScroll();
    }
    boolean consume = scaling;
    if (e.getActionMasked() == MotionEvent.ACTION_UP
        || e.getActionMasked() == MotionEvent.ACTION_CANCEL) scaling = false;
    return consume;
  }

  @Override
  public void onTouchEvent(RecyclerView rv, MotionEvent e) {
    detector.onTouchEvent(e);
    if (e.getActionMasked() == MotionEvent.ACTION_UP
        || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
      scaling = false;
      if (grid instanceof GalleryGrid) ((GalleryGrid) grid).settle(host.motion());
    }
  }
}
