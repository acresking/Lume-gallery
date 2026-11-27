package il.luma.gallery;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Editable alpha mask with erase/restore brushes and an explicit transparent checkerboard. */
final class SubjectBoard extends View {
  final Bitmap image, original;
  boolean erase = true, picking;
  float brushSize = .045f;
  final Bitmap initial;
  final EditorHistory<Bitmap> history = new EditorHistory<>(6);
  Runnable changed = () -> {};
  java.util.function.Consumer<String> error = text -> {};

  void remember() {
    history.record(image.copy(Bitmap.Config.ARGB_8888, false));
  }

  void replace(Bitmap bitmap) {
    image.eraseColor(Color.TRANSPARENT);
    new Canvas(image).drawBitmap(bitmap, 0, 0, null);
    invalidate();
    changed.run();
  }

  void undo() {
    picking = false;
    if (history.canUndo()) replace(history.undo(image.copy(Bitmap.Config.ARGB_8888, false)));
  }

  void redo() {
    picking = false;
    if (history.canRedo()) replace(history.redo(image.copy(Bitmap.Config.ARGB_8888, false)));
  }

  void reset() {
    picking = false;
    remember();
    replace(initial);
  }

  private boolean multi;
  private final Paint paint = new Paint(3);
  private float lastX, lastY;

  SubjectBoard(Context c, Bitmap selected, Bitmap foreground) {
    super(c);
    image = selected.copy(Bitmap.Config.ARGB_8888, true);
    original = foreground;
    initial = selected;
    setContentDescription("מסכת האדם · גרור למחיקה או שחזור");
  }

  float fit() {
    return Math.min(getWidth() / (float) image.getWidth(), getHeight() / (float) image.getHeight());
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    for (int y = 0; y < getHeight(); y += 24)
      for (int x = 0; x < getWidth(); x += 24) {
        paint.setColor(((x / 24 + y / 24) % 2) == 0 ? 0xff282432 : 0xff393341);
        c.drawRect(x, y, x + 24, y + 24, paint);
      }
    float s = fit();
    paint.setColorFilter(null);
    c.save();
    c.translate((getWidth() - image.getWidth() * s) / 2, (getHeight() - image.getHeight() * s) / 2);
    c.scale(s, s);
    c.drawBitmap(picking ? original : image, 0, 0, paint);
    c.restore();
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    if (!isEnabled()) return false;
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) multi = false;
    if (e.getPointerCount() > 1) multi = true;
    if (multi) {
      if ((e.getActionMasked() == MotionEvent.ACTION_UP
              || e.getActionMasked() == MotionEvent.ACTION_CANCEL)
          && getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
      return true;
    }
    float s = fit();
    if (s <= 0) return true;
    float x = (e.getX() - (getWidth() - image.getWidth() * s) / 2) / s,
        y = (e.getY() - (getHeight() - image.getHeight() * s) / 2) / s;
    if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) return true;
    int action = e.getActionMasked();
    if (picking) {
      if (action == MotionEvent.ACTION_UP) {
        try {
          Bitmap choice =
              SubjectCutout.select(original, x / image.getWidth(), y / image.getHeight());
          remember();
          picking = false;
          replace(choice);
        } catch (java.io.IOException e1) {
          error.accept(e1.getMessage());
        }
        performClick();
      }
      return true;
    }
    if (action == MotionEvent.ACTION_DOWN) {
      remember();
      lastX = x;
      lastY = y;
      if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
    }
    if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_DOWN) {
      Paint brush = new Paint(3);
      brush.setStyle(Paint.Style.STROKE);
      brush.setStrokeCap(Paint.Cap.ROUND);
      brush.setStrokeWidth(Math.max(image.getWidth(), image.getHeight()) * brushSize);
      if (erase) brush.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
      else {
        brush.setShader(new BitmapShader(original, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        brush.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
      }
      new Canvas(image).drawLine(lastX, lastY, x, y, brush);
      lastX = x;
      lastY = y;
      invalidate();
    }
    if (action == MotionEvent.ACTION_UP) {
      performClick();
      changed.run();
      if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }
    return true;
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }
}
