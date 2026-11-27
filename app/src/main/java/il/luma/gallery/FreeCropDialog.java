package il.luma.gallery;

import android.app.Dialog;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.function.Consumer;

/** Drag the rectangle or its corners. Selection is normalized and resolution independent. */
final class FreeCropDialog extends Dialog {
  final ModernActivity host;
  final Bitmap source;
  final Consumer<RectF> accept;
  CropView board;

  FreeCropDialog(ModernActivity a, Bitmap b, RectF initial, Consumer<RectF> result) {
    super(a, android.R.style.Theme_Material_NoActionBar);
    host = a;
    source = b;
    accept = result;
    board = new CropView(a, b, initial);
  }

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    LinearLayout root = host.column();
    root.setBackgroundColor(StudioUI.BACKGROUND);
    root.setPadding(host.dp(12), host.dp(24), host.dp(12), host.dp(24));
    root.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          var bars = insets.getInsets(WindowInsets.Type.systemBars());
          view.setPadding(
              bars.left + host.dp(12),
              bars.top + host.dp(12),
              bars.right + host.dp(12),
              bars.bottom + host.dp(12));
          return insets;
        });
    root.addView(host.label("חיתוך חופשי · גרור את הפינות או את המסגרת", 16, Color.WHITE, true));
    root.addView(board, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout actions = host.row();
    actions.addView(
        StudioUI.control(host, "ביטול", this::dismiss, false),
        new LinearLayout.LayoutParams(0, -2, 1));
    actions.addView(
        StudioUI.control(
            host,
            "החל חיתוך",
            () -> {
              accept.accept(new RectF(board.selection));
              dismiss();
            },
            true),
        new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(actions);
    setContentView(root);
  }

  @Override
  public void show() {
    AppPrivacy.apply(getWindow(), host);
    super.show();
    getWindow().setLayout(-1, -1);
    getWindow().getDecorView().setSystemUiVisibility(0);
  }

  static final class CropView extends View {
    final Bitmap image;
    final RectF selection;
    final Paint paint = new Paint(3);
    RectF start;
    float downX, downY;
    int corner = -1;

    CropView(android.content.Context c, Bitmap b, RectF initial) {
      super(c);
      image = b;
      selection = new RectF(initial);
      setContentDescription("מסגרת חיתוך · גרור את הפינות או המרכז");
    }

    RectF bounds() {
      float
          s =
              Math.min(
                  getWidth() / (float) image.getWidth(), getHeight() / (float) image.getHeight()),
          w = image.getWidth() * s,
          h = image.getHeight() * s;
      return new RectF(
          (getWidth() - w) / 2, (getHeight() - h) / 2, (getWidth() + w) / 2, (getHeight() + h) / 2);
    }

    @Override
    protected void onDraw(Canvas c) {
      RectF frame = bounds();
      c.drawBitmap(image, null, frame, paint);
      RectF crop =
          new RectF(
              frame.left + selection.left * frame.width(),
              frame.top + selection.top * frame.height(),
              frame.left + selection.right * frame.width(),
              frame.top + selection.bottom * frame.height());
      paint.setColor(0x99000000);
      c.drawRect(frame.left, frame.top, frame.right, crop.top, paint);
      c.drawRect(frame.left, crop.bottom, frame.right, frame.bottom, paint);
      c.drawRect(frame.left, crop.top, crop.left, crop.bottom, paint);
      c.drawRect(crop.right, crop.top, frame.right, crop.bottom, paint);
      paint.setColor(StudioUI.ACCENT);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(3);
      c.drawRect(crop, paint);
      paint.setStrokeWidth(1);
      for (int i = 1; i <= 2; i++) {
        c.drawLine(
            crop.left + crop.width() * i / 3,
            crop.top,
            crop.left + crop.width() * i / 3,
            crop.bottom,
            paint);
        c.drawLine(
            crop.left,
            crop.top + crop.height() * i / 3,
            crop.right,
            crop.top + crop.height() * i / 3,
            paint);
      }
      paint.setStyle(Paint.Style.FILL);
      for (float x : new float[] {crop.left, crop.right})
        for (float y : new float[] {crop.top, crop.bottom}) c.drawCircle(x, y, 7, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
      if (e.getPointerCount() > 1) return true;
      RectF b = bounds();
      if (b.width() <= 0) return true;
      float x = (e.getX() - b.left) / b.width(), y = (e.getY() - b.top) / b.height();
      if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
        start = new RectF(selection);
        downX = x;
        downY = y;
        corner = -1;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
          float cx = i % 2 == 0 ? selection.left : selection.right,
              cy = i < 2 ? selection.top : selection.bottom;
          float d = (cx - x) * (cx - x) + (cy - y) * (cy - y);
          if (d < best) {
            best = d;
            corner = i;
          }
        }
        if (best > .035f) corner = 4;
        return true;
      }
      if (e.getActionMasked() == MotionEvent.ACTION_MOVE && start != null) {
        float dx = x - downX, dy = y - downY;
        if (corner == 4) {
          dx = Math.max(-start.left, Math.min(1 - start.right, dx));
          dy = Math.max(-start.top, Math.min(1 - start.bottom, dy));
          selection.set(start.left + dx, start.top + dy, start.right + dx, start.bottom + dy);
        } else {
          selection.set(start);
          if (corner % 2 == 0)
            selection.left = Math.max(0, Math.min(start.right - .08f, start.left + dx));
          else selection.right = Math.min(1, Math.max(start.left + .08f, start.right + dx));
          if (corner < 2)
            selection.top = Math.max(0, Math.min(start.bottom - .08f, start.top + dy));
          else selection.bottom = Math.min(1, Math.max(start.top + .08f, start.bottom + dy));
        }
        invalidate();
      }
      if (e.getActionMasked() == MotionEvent.ACTION_UP) performClick();
      return true;
    }

    @Override
    public boolean performClick() {
      super.performClick();
      return true;
    }
  }
}
