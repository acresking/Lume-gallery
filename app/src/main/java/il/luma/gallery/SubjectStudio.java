package il.luma.gallery;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;

/** Local subject extraction with preview/correction before any explicit PNG save/share. */
final class SubjectStudio extends Dialog {
  private final ModernActivity host;
  SubjectBoard board;
  private boolean saving;
  private boolean sticker;

  SubjectStudio(ModernActivity a, Bitmap selected, Bitmap foreground) {
    super(a, android.R.style.Theme_Material_NoActionBar);
    host = a;
    board = new SubjectBoard(a, selected, foreground);
  }

  static void begin(
      ModernActivity a,
      MainActivity.Media media,
      float x,
      float y,
      java.util.function.BooleanSupplier valid) {
    if (a.ai == null || !a.ai.require("portrait")) return;
    android.widget.Toast.makeText(a, "מפריד את האדם במכשיר…", android.widget.Toast.LENGTH_SHORT)
        .show();
    a.ai.task(
        "מפריד את האדם שבחרת",
        () -> {
          Bitmap source = a.decode(media.uri, 1200);
          try (AiEngine engine = new AiEngine(a)) {
            Bitmap foreground = engine.portrait(source, true);
            Bitmap selected = SubjectCutout.select(foreground, x, y);
            a.ai.ui(
                () -> {
                  if (!valid.getAsBoolean()) return;
                  if (a.subjectStudio != null) a.subjectStudio.dismiss();
                  a.subjectStudio = new SubjectStudio(a, selected, foreground);
                  a.subjectStudio.show();
                });
          } catch (OutOfMemoryError memory) {
            throw new IOException("אין מספיק זיכרון להפרדה כרגע. סגור עורך אחר ונסה שוב.");
          }
        });
  }

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    LinearLayout root = host.column();
    root.setBackgroundColor(StudioUI.BACKGROUND);
    root.setOnApplyWindowInsetsListener(
        (v, i) -> {
          android.graphics.Insets bars = i.getInsets(WindowInsets.Type.systemBars());
          v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return i;
        });
    LinearLayout top = host.row();
    top.addView(StudioUI.close(host, this::dismiss));
    TextView title = host.label("האדם שבחרת", 22, 0xfff4efff, true);
    top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(top);
    root.addView(board, new LinearLayout.LayoutParams(-1, 0, 1));
    TextView note =
        host.label(
            "בדוק שנותר רק האדם הרצוי. אנשים שנוגעים זה בזה עשויים להישאר יחד; גרור למחיקה או"
                + " שחזור. המקור נשמר.",
            13,
            0xffd4c8e3,
            false);
    note.setPadding(host.dp(16), host.dp(12), host.dp(16), host.dp(12));
    root.addView(note);
    LinearLayout brushes = host.row();
    TextView erase = StudioUI.tab(host, "מחק", true, () -> {}),
        restore = StudioUI.tab(host, "שחזר", false, () -> {});
    erase.setOnClickListener(
        v -> {
          board.erase = true;
          brushState(erase, true);
          brushState(restore, false);
        });
    restore.setOnClickListener(
        v -> {
          board.erase = false;
          brushState(restore, true);
          brushState(erase, false);
        });
    brushes.addView(erase);
    brushes.addView(restore);
    root.addView(brushes);
    CheckBox format = new CheckBox(host);
    format.setText("סטיקר PNG · 512×512 עם מסגרת לבנה");
    format.setTextColor(0xfff4efff);
    format.setPadding(host.dp(16), host.dp(8), host.dp(16), host.dp(8));
    format.setOnCheckedChangeListener((v, on) -> sticker = on);
    root.addView(format);
    LinearLayout actions = host.row();
    actions.setPadding(host.dp(12), host.dp(8), host.dp(12), host.dp(16));
    actions.addView(
        StudioUI.control(host, "שמור PNG", () -> save(false), false),
        new LinearLayout.LayoutParams(0, -2, 1));
    actions.addView(
        StudioUI.control(host, "שתף רק את האדם", () -> save(true), true),
        new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(actions);
    setContentView(root);
  }

  void brushState(TextView button, boolean selected) {
    button.setSelected(selected);
    button.setTextColor(selected ? 0xff241536 : 0xffd1c9de);
    button.setBackground(host.rounded(selected ? StudioUI.ACCENT : StudioUI.SURFACE, 24));
  }

  void save(boolean share) {
    if (saving) return;
    saving = true;
    final Bitmap snapshot = board.image.copy(Bitmap.Config.ARGB_8888, false);
    final boolean asSticker = sticker;
    host.io.execute(
        () -> {
          Uri uri = null;
          try {
            Bitmap output =
                asSticker ? SubjectCutout.sticker(snapshot) : SubjectCutout.tight(snapshot);
            uri = host.createOutput(false, "image/png", "png");
            try (OutputStream out = host.getContentResolver().openOutputStream(uri)) {
              if (out == null || !output.compress(Bitmap.CompressFormat.PNG, 100, out))
                throw new IOException("השמירה נכשלה");
            }
            android.content.ContentValues ready = new android.content.ContentValues();
            ready.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0);
            if (host.getContentResolver().update(uri, ready, null, null) < 1)
              throw new IOException("פרסום ה־PNG לא הושלם");
            final Uri saved = uri;
            host.runOnUiThread(
                () -> {
                  saving = false;
                  if (host.destroyed || !isShowing()) return;
                  if (share) {
                    Intent intent =
                        new Intent(Intent.ACTION_SEND)
                            .setType("image/png")
                            .putExtra(Intent.EXTRA_STREAM, saved)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.setClipData(ClipData.newRawUri("Lume subject", saved));
                    host.startActivity(
                        Intent.createChooser(
                            intent, asSticker ? "שיתוף PNG לייבוא כסטיקר" : "שיתוף האדם שבחרת"));
                  } else
                    Toast.makeText(host, "עותק PNG שקוף נשמר בגלריה", Toast.LENGTH_LONG).show();
                });
          } catch (Exception error) {
            if (uri != null)
              try {
                host.getContentResolver().delete(uri, null, null);
              } catch (Exception ignored) {
              }
            host.runOnUiThread(
                () -> {
                  saving = false;
                  if (!host.destroyed) host.message("שמירת האדם", error.getMessage());
                });
          } finally {
            snapshot.recycle();
          }
        });
  }

  @Override
  public void show() {
    AppPrivacy.apply(getWindow(), host);
    getWindow().setWindowAnimations(host.motion() ? R.style.GallerySheetMotion : 0);
    super.show();
    getWindow().setLayout(-1, -1);
    getWindow().setStatusBarColor(StudioUI.BACKGROUND);
    getWindow().setNavigationBarColor(StudioUI.BACKGROUND);
    getWindow().getDecorView().setSystemUiVisibility(0);
  }
}
