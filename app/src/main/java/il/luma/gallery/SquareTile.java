package il.luma.gallery;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/** Re-measures to the current grid width, including holders recycled across pinch steps. */
final class SquareTile extends FrameLayout {
  SquareTile(Context context) {
    super(context);
  }

  @Override
  protected void onMeasure(int widthSpec, int heightSpec) {
    int side = View.MeasureSpec.getSize(widthSpec);
    super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(side, View.MeasureSpec.EXACTLY));
  }
}
