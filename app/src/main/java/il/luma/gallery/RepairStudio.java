package il.luma.gallery;

import android.app.*;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Brush-mask eraser, with optional local object detection. All edits require preview + save. */
final class RepairStudio extends Dialog {
  final ModernActivity a;
  final MainActivity.Media media;
  MaskView canvas;
  TextView hint;
  Bitmap source;
  boolean working;

  RepairStudio(ModernActivity a, MainActivity.Media m) {
    super(a, android.R.style.Theme_Material_NoActionBar);
    this.a = a;
    media = m;
  }

  Button button(String text, Runnable run) {
    Button b = a.button(text, run);
    b.setTextColor(Color.WHITE);
    b.setBackground(a.rounded(0xff292b33, 16));
    return b;
  }

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    LinearLayout root = a.column();
    root.setBackgroundColor(0xff111216);
    root.setOnApplyWindowInsetsListener(
        (v, i) -> {
          var bars = i.getInsets(WindowInsets.Type.systemBars());
          root.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return i;
        });
    LinearLayout top = a.row();
    top.setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(8));
    top.addView(button("ביטול", this::dismiss), new LinearLayout.LayoutParams(a.dp(84), -2));
    TextView title = a.label("הסרת פריטים", 18, Color.WHITE, true);
    title.setGravity(Gravity.CENTER);
    top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    top.addView(button("תקן", this::repair), new LinearLayout.LayoutParams(a.dp(84), -2));
    root.addView(top);
    canvas = new MaskView();
    root.addView(canvas, new LinearLayout.LayoutParams(-1, 0, 1));
    hint = a.label("טוען תמונה…", 13, 0xffa3a6b0, false);
    hint.setPadding(a.dp(20), a.dp(8), a.dp(20), a.dp(8));
    root.addView(hint);
    LinearLayout options = a.row();
    options.setPadding(a.dp(12), a.dp(6), a.dp(12), a.dp(8));
    options.addView(button("איתור פריטים", this::detect), new LinearLayout.LayoutParams(0, -2, 1));
    options.addView(
        button(
            "בטל סימון אחרון",
            () -> {
              canvas.undo();
            }),
        new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(options);
    SeekBar brush = new SeekBar(a);
    brush.setMax(65);
    brush.setProgress(18);
    brush.setContentDescription("גודל מברשת");
    brush.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar bar, int p, boolean user) {
            canvas.brush = p + 5;
          }

          public void onStartTrackingTouch(SeekBar bar) {}

          public void onStopTrackingTouch(SeekBar bar) {}
        });
    root.addView(brush, new LinearLayout.LayoutParams(-1, a.dp(44)));
    setContentView(root);
    a.thumbs.execute(
        () -> {
          try {
            Bitmap bitmap = a.decode(media.uri, 1600);
            a.runOnUiThread(
                () -> {
                  if (!isShowing()) return;
                  source = bitmap;
                  canvas.initialize(bitmap);
                  hint.setText("צבע על פריט להסרה, או אתר פריטים והקש על מסגרת. המקור נשמר.");
                });
          } catch (Exception e) {
            a.runOnUiThread(() -> a.message("פתיחת תמונה", e.getMessage()));
          }
        });
  }

  @Override
  public void show() {
    AppPrivacy.apply(getWindow(), a);
    getWindow().setWindowAnimations(a.motion() ? R.style.GallerySheetMotion : 0);
    super.show();
    getWindow().setLayout(-1, -1);
  }

  void detect() {
    if (source == null || working || a.ai.busy || !a.ai.require("objects")) return;
    working = true;
    hint.setText("מאתר פריטים במכשיר…");
    a.ai.task(
        "איתור פריטים",
        () -> {
          try (AiEngine engine = new AiEngine(a)) {
            var boxes = engine.objects(source);
            a.ai.ui(
                () -> {
                  working = false;
                  canvas.boxes = boxes;
                  canvas.invalidate();
                  hint.setText(
                      boxes.isEmpty()
                          ? "לא אותרו פריטים. אפשר לסמן ידנית."
                          : "הקש על מסגרת כדי לסמן פריט להסרה. ייתכנו החמצות.");
                });
          } finally {
            working = false;
          }
        });
  }

  void repair() {
    if (source == null || working || a.ai.busy || !a.ai.require("repair")) return;
    Bitmap mask = canvas.mask();
    if (!canvas.hasMarks()) {
      a.message("סימון אזור", "סמן פריט בעזרת המברשת או זיהוי פריטים");
      return;
    }
    working = true;
    hint.setText("מתקן במכשיר… אפשר להמתין כמה שניות");
    a.ai.task(
        "תיקון אזור מסומן",
        () -> {
          try (AdvancedEngine engine = new AdvancedEngine(a)) {
            Bitmap result = engine.repair(source, mask);
            a.ai.ui(
                () -> {
                  working = false;
                  dismiss();
                  a.ai.preview(result, "מילוי משוער · בדוק את האזור שתוקן");
                });
          } finally {
            working = false;
          }
        });
  }

  final class MaskView extends View {
    Bitmap bitmap;
    final ArrayList<Path> paths = new ArrayList<>();
    final ArrayList<Float> widths = new ArrayList<>();
    java.util.List<AiEngine.ObjectBox> boxes = Collections.emptyList();
    Path current;
    float brush = 23, scale = 1, left, top;
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    boolean moved;
    float startX, startY;

    MaskView() {
      super(a);
      setBackgroundColor(Color.BLACK);
    }

    void initialize(Bitmap bitmap) {
      this.bitmap = bitmap;
      invalidate();
    }

    void undo() {
      if (!paths.isEmpty()) {
        paths.remove(paths.size() - 1);
        widths.remove(widths.size() - 1);
        invalidate();
      }
    }

    boolean hasMarks() {
      return !paths.isEmpty();
    }

    Bitmap mask() {
      Bitmap mask =
          Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
      Canvas c = new Canvas(mask);
      Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
      p.setColor(Color.WHITE);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeCap(Paint.Cap.ROUND);
      p.setStrokeJoin(Paint.Join.ROUND);
      for (int i = 0; i < paths.size(); i++) {
        p.setStrokeWidth(widths.get(i));
        c.drawPath(paths.get(i), p);
      }
      return mask;
    }

    @Override
    protected void onDraw(Canvas c) {
      if (bitmap == null) return;
      scale =
          Math.min(
              getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight());
      left = (getWidth() - bitmap.getWidth() * scale) / 2;
      top = (getHeight() - bitmap.getHeight() * scale) / 2;
      c.save();
      c.translate(left, top);
      c.scale(scale, scale);
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(Color.WHITE);
      c.drawBitmap(bitmap, 0, 0, paint);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setStrokeJoin(Paint.Join.ROUND);
      paint.setColor(0xaaff5577);
      for (int i = 0; i < paths.size(); i++) {
        paint.setStrokeWidth(widths.get(i));
        c.drawPath(paths.get(i), paint);
      }
      paint.setStrokeWidth(2 / scale);
      paint.setColor(0xffa9b9ff);
      for (AiEngine.ObjectBox box : boxes) c.drawRect(box.box, paint);
      c.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
      if (bitmap == null) return false;
      float x = Math.max(0, Math.min(bitmap.getWidth() - 1, (event.getX() - left) / scale)),
          y = Math.max(0, Math.min(bitmap.getHeight() - 1, (event.getY() - top) / scale));
      if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
        current = new Path();
        current.moveTo(x, y);
        current.lineTo(x + .01f, y + .01f);
        paths.add(current);
        widths.add(brush / scale);
        startX = x;
        startY = y;
        moved = false;
      } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
        if (current != null) current.lineTo(x, y);
        if (Math.hypot(x - startX, y - startY) > 6 / scale) moved = true;
      } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
        if (!moved) {
          for (AiEngine.ObjectBox box : boxes)
            if (box.box.contains(x, y)) {
              undo();
              Path selected = new Path();
              float inset = Math.max(3, box.box.width() * .05f);
              RectF rect = new RectF(box.box);
              rect.inset(-inset, -inset);
              selected.addRect(rect, Path.Direction.CW);
              paths.add(selected);
              widths.add(
                  Math.min(
                      rect.width(),
                      rect.height())); // replace the outline with dense horizontal strokes
              paths.remove(paths.size() - 1);
              widths.remove(widths.size() - 1);
              Path filled = new Path();
              for (float line = Math.max(0, rect.top);
                  line < Math.min(bitmap.getHeight(), rect.bottom);
                  line += 3) {
                filled.moveTo(Math.max(0, rect.left), line);
                filled.lineTo(Math.min(bitmap.getWidth(), rect.right), line);
              }
              paths.add(filled);
              widths.add(5f);
              break;
            }
        }
        current = null;
        performClick();
      }
      invalidate();
      return true;
    }

    @Override
    public boolean performClick() {
      super.performClick();
      return true;
    }
  }
}
