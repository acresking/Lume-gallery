package il.luma.gallery;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import androidx.recyclerview.widget.RecyclerView;
import java.util.*;

/** Canvas-only pinch feedback keeps touch coordinates stable while cells reflow. */
final class GalleryGrid extends RecyclerView {
  private float previewScale = 1, pivotX, pivotY;
  private ValueAnimator settling;
  private int transition;

  GalleryGrid(Context c) {
    super(c);
  }

  static void resetCell(View cell) {
    cell.animate().cancel();
    cell.setTranslationX(0);
    cell.setTranslationY(0);
    cell.setScaleX(1);
    cell.setScaleY(1);
    cell.setAlpha(1);
  }

  void preview(float scale, float x, float y) {
    if (settling != null) settling.cancel();
    previewScale = Math.max(.72f, Math.min(1.35f, scale));
    pivotX = x;
    pivotY = y;
    invalidate();
  }

  float contentX(float x) {
    return pivotX + (x - pivotX) / previewScale;
  }

  float contentY(float y) {
    return pivotY + (y - pivotY) / previewScale;
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    canvas.save();
    canvas.scale(previewScale, previewScale, pivotX, pivotY);
    super.dispatchDraw(canvas);
    canvas.restore();
  }

  Map<String, RectF> positions() {
    Map<String, RectF> out = new HashMap<>();
    for (int i = 0; i < getChildCount(); i++) {
      View v = getChildAt(i);
      if (!(v.getTag() instanceof String)) continue;
      float left = v.getLeft() + v.getTranslationX(), top = v.getTop() + v.getTranslationY();
      out.put(
          (String) v.getTag(),
          new RectF(
              pivotX + (left - pivotX) * previewScale,
              pivotY + (top - pivotY) * previewScale,
              pivotX + (left + v.getWidth() * v.getScaleX() - pivotX) * previewScale,
              pivotY + (top + v.getHeight() * v.getScaleY() - pivotY) * previewScale));
      v.animate().cancel();
      v.setTranslationX(0);
      v.setTranslationY(0);
      v.setScaleX(1);
      v.setScaleY(1);
    }
    return out;
  }

  void morph(Map<String, RectF> before, boolean motion) {
    previewScale = 1;
    invalidate();
    int token = ++transition;
    if (!motion) return;
    getViewTreeObserver()
        .addOnPreDrawListener(
            new ViewTreeObserver.OnPreDrawListener() {
              public boolean onPreDraw() {
                if (getViewTreeObserver().isAlive())
                  getViewTreeObserver().removeOnPreDrawListener(this);
                if (token != transition) return true;
                int animated = 0;
                for (int i = 0; i < getChildCount(); i++) {
                  View child = getChildAt(i);
                  RectF old = before.get(child.getTag());
                  if (old == null
                      || child.getWidth() == 0
                      || child.getHeight() == 0
                      || animated++ >= 128) continue;
                  child.setPivotX(0);
                  child.setPivotY(0);
                  child.setTranslationX(old.left - child.getLeft());
                  child.setTranslationY(old.top - child.getTop());
                  child.setScaleX(old.width() / child.getWidth());
                  child.setScaleY(old.height() / child.getHeight());
                  child
                      .animate()
                      .translationX(0)
                      .translationY(0)
                      .scaleX(1)
                      .scaleY(1)
                      .setDuration(190)
                      .setInterpolator(new android.view.animation.DecelerateInterpolator())
                      .start();
                }
                return true;
              }
            });
  }

  void settle(boolean motion) {
    if (settling != null) settling.cancel();
    if (!motion) {
      previewScale = 1;
      invalidate();
      return;
    }
    settling = ValueAnimator.ofFloat(previewScale, 1);
    settling.setDuration(140);
    settling.addUpdateListener(
        v -> {
          previewScale = (float) v.getAnimatedValue();
          invalidate();
        });
    settling.start();
  }

  @Override
  protected void onDetachedFromWindow() {
    if (settling != null) settling.cancel();
    transition++;
    super.onDetachedFromWindow();
  }
}
