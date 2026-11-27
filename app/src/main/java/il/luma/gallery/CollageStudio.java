package il.luma.gallery;

import android.app.Dialog;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Offline collage creator. Sources are bounded decodes and output is always a new file. */
final class CollageStudio extends Dialog {
  final ModernActivity a;
  final java.util.List<MainActivity.Media> media;
  final ExecutorService worker = Executors.newSingleThreadExecutor();
  final ArrayList<Bitmap> sources = new ArrayList<>();
  ImageView preview;
  TextView status;
  Button save;
  Bitmap previewBitmap;
  volatile boolean closed;
  boolean loading = true, busy;
  int aspect, layout, background = Color.WHITE;
  float spacing = .008f;
  boolean fit, png;

  CollageStudio(ModernActivity a, java.util.List<MainActivity.Media> media) {
    super(a, android.R.style.Theme_Material_NoActionBar);
    if (media.size() < 2 || media.size() > 6)
      throw new IllegalArgumentException("בחר בין 2 ל־6 תמונות");
    this.a = a;
    this.media = new ArrayList<>(media);
  }

  @Override
  protected void onCreate(android.os.Bundle state) {
    super.onCreate(state);
    LinearLayout root = a.column();
    root.setBackgroundColor(Color.BLACK);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          android.graphics.Insets b = insets.getInsets(WindowInsets.Type.systemBars());
          root.setPadding(b.left, b.top, b.right, b.bottom);
          return insets;
        });
    LinearLayout top = a.row();
    top.setPadding(a.dp(8), a.dp(8), a.dp(8), a.dp(8));
    View close = a.iconButton("close", "סגירת קולאז׳", this::dismiss);
    ((ImageView) ((android.widget.FrameLayout) close).getChildAt(0))
        .setImageDrawable(new GalleryIcons("close", Color.WHITE));
    top.addView(close);
    TextView title = a.label("קולאז׳", 22, Color.WHITE, true);
    top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    save = a.button("שמור עותק", this::saveCopy);
    save.setEnabled(false);
    top.addView(save, new LinearLayout.LayoutParams(a.dp(100), -2));
    root.addView(top);
    preview = new ImageView(a);
    preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
    preview.setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(8));
    preview.setContentDescription("תצוגה מקדימה של הקולאז׳");
    root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
    ScrollView scroll = new ScrollView(a);
    LinearLayout controls = a.column();
    controls.setPadding(a.dp(12), a.dp(6), a.dp(12), a.dp(12));
    scroll.addView(controls);
    choices(
        controls,
        "יחס",
        new String[] {"ריבוע", "רחב 4:3", "אנכי 9:16"},
        i -> {
          aspect = i;
          refresh();
        });
    choices(
        controls,
        "פריסה",
        new String[] {"רשת", "שורות", "עמודות"},
        i -> {
          layout = i;
          refresh();
        });
    choices(
        controls,
        "מרווח",
        new String[] {"ללא", "קטן", "רחב"},
        i -> {
          spacing = i == 0 ? 0 : i == 1 ? .008f : .02f;
          refresh();
        });
    choices(
        controls,
        "רקע",
        new String[] {"לבן", "שחור", "Lume"},
        i -> {
          background = i == 0 ? Color.WHITE : i == 1 ? Color.BLACK : 0xffeee7fc;
          refresh();
        });
    choices(
        controls,
        "קובץ",
        new String[] {"JPEG", "PNG"},
        i -> {
          png = i == 1;
          refresh();
        });
    controls.addView(
        a.button(
            "החלף סדר תמונות",
            () -> {
              if (!busy && !loading) {
                Collections.rotate(sources, 1);
                refresh();
              }
            }));
    CheckBox full = new CheckBox(a);
    full.setText("הצג תמונות מלאות במקום לחתוך למסגרת");
    full.setTextColor(Color.WHITE);
    full.setChecked(false);
    full.setOnCheckedChangeListener(
        (v, on) -> {
          if (busy) {
            full.setChecked(fit);
            return;
          }
          fit = on;
          refresh();
        });
    controls.addView(full);
    status = a.label("טוען תמונות במכשיר…", 12, 0xffc7c0d9, false);
    status.setPadding(a.dp(10), a.dp(8), a.dp(10), a.dp(8));
    controls.addView(status);
    root.addView(
        scroll,
        new LinearLayout.LayoutParams(
            -1, Math.min(a.dp(240), a.getResources().getDisplayMetrics().heightPixels / 3)));
    setContentView(root);
    root.requestApplyInsets();
    worker.execute(
        () -> {
          ArrayList<Bitmap> decoded = new ArrayList<>();
          try {
            for (var m : media) {
              if (closed) {
                recycle(decoded);
                return;
              }
              if (m.video()) throw new IOException("קולאז׳ זמין לתמונות בלבד");
              decoded.add(MediaAccess.decode(a, m.uri, 1024));
            }
            a.runOnUiThread(
                () -> {
                  if (closed) {
                    recycle(decoded);
                    return;
                  }
                  sources.addAll(decoded);
                  loading = false;
                  save.setEnabled(true);
                  refresh();
                });
          } catch (Exception | OutOfMemoryError error) {
            recycle(decoded);
            a.runOnUiThread(
                () -> {
                  if (!closed && !a.destroyed) {
                    loading = false;
                    status.setText("לא ניתן לטעון את כל התמונות. סגור ובחר תמונות זמינות.");
                  }
                });
          }
        });
  }

  interface Pick {
    void choose(int index);
  }

  void choices(LinearLayout parent, String label, String[] names, Pick pick) {
    HorizontalScrollView scroll = new HorizontalScrollView(a);
    scroll.setHorizontalScrollBarEnabled(false);
    LinearLayout row = a.row();
    TextView caption = a.label(label, 12, 0xffc7c0d9, false);
    caption.setPadding(a.dp(8), 0, a.dp(10), 0);
    row.addView(caption);
    ArrayList<Button> choices = new ArrayList<>();
    int initial = label.equals("מרווח") ? 1 : 0;
    for (int i = 0; i < names.length; i++) {
      final int index = i;
      Button button =
          a.button(
              names[i],
              () -> {
                if (!busy) {
                  for (int k = 0; k < choices.size(); k++) styleChoice(choices.get(k), k == index);
                  pick.choose(index);
                }
              });
      choices.add(button);
      styleChoice(button, i == initial);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
      lp.setMargins(a.dp(3), a.dp(3), a.dp(3), a.dp(3));
      row.addView(button, lp);
    }
    scroll.addView(row);
    parent.addView(scroll);
  }

  void styleChoice(Button button, boolean selected) {
    button.setSelected(selected);
    button.setTextColor(selected ? Color.WHITE : a.ink);
    button.setBackgroundTintList(
        android.content.res.ColorStateList.valueOf(selected ? 0xff6d52c7 : a.card));
  }

  void refresh() {
    if (closed || loading || sources.size() != media.size()) return;
    Bitmap next;
    try {
      next = CollageEngine.render(sources, 600, aspect, layout, spacing, background, fit);
    } catch (OutOfMemoryError error) {
      save.setEnabled(false);
      status.setText("אין מספיק זיכרון לתצוגה. סגור את העורך ונסה שוב עם פחות תמונות.");
      return;
    }
    preview.setImageBitmap(next);
    if (previewBitmap != null) previewBitmap.recycle();
    previewBitmap = next;
    status.setText(
        "עותק חדש עד 3072 פיקסלים · ללא העתקת EXIF או מיקום\n"
            + "תמונות העבודה נטענות עד 1024 פיקסלים; מילוי מסגרת עשוי לחתוך שוליים.");
  }

  void saveCopy() {
    if (loading || busy || sources.size() != media.size()) return;
    busy = true;
    save.setEnabled(false);
    status.setText("שומר קולאז׳ במכשיר…");
    final var images = new ArrayList<>(sources);
    final int ratio = aspect, style = layout, color = background;
    final float gap = spacing;
    final boolean full = fit, isPng = png;
    worker.execute(
        () -> {
          Bitmap output = null;
          Uri uri = null;
          try {
            output = CollageEngine.render(images, 3072, ratio, style, gap, color, full);
            uri = a.createOutput(false, isPng ? "image/png" : "image/jpeg", isPng ? "png" : "jpg");
            try (OutputStream stream = a.getContentResolver().openOutputStream(uri)) {
              if (stream == null
                  || !output.compress(
                      isPng ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 94, stream))
                throw new IOException("שמירת הקולאז׳ נכשלה");
            }
            a.publish(uri);
            a.runOnUiThread(
                () -> {
                  if (a.destroyed) return;
                  a.load();
                  Toast.makeText(a, "הקולאז׳ נשמר באלבום Lume", Toast.LENGTH_SHORT).show();
                  if (!closed) dismiss();
                });
          } catch (Exception | OutOfMemoryError error) {
            if (uri != null)
              try {
                a.getContentResolver().delete(uri, null, null);
              } catch (Exception ignored) {
              }
            a.runOnUiThread(
                () -> {
                  if (!closed && !a.destroyed) {
                    busy = false;
                    save.setEnabled(true);
                    status.setText("השמירה נכשלה; המקורות לא השתנו. בדוק גישה ואחסון ונסה שוב.");
                  }
                });
          } finally {
            if (output != null) output.recycle();
          }
        });
  }

  @Override
  public void show() {
    AppPrivacy.apply(getWindow(), a);
    getWindow().setWindowAnimations(a.motion() ? R.style.GalleryViewerMotion : 0);
    super.show();
    getWindow().setLayout(-1, -1);
  }

  static void recycle(java.util.List<Bitmap> bitmaps) {
    for (Bitmap bitmap : bitmaps) if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
  }

  @Override
  public void dismiss() {
    if (closed) return;
    closed = true;
    if (a.collageStudio == this) a.collageStudio = null;
    super.dismiss();
    worker.execute(
        () -> {
          recycle(sources);
          sources.clear();
        });
    worker.shutdown();
    if (previewBitmap != null) {
      preview.setImageDrawable(null);
      previewBitmap.recycle();
      previewBitmap = null;
    }
  }
}
