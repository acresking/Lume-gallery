package il.luma.gallery;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;

/** Live, non-destructive editor. Preview is sampled; export re-decodes the source. */
final class PhotoStudio extends Dialog {
  private final ModernActivity a;
  private final MainActivity.Media media;
  private LinearLayout panel, tabs;
  private ImageView preview;
  private TextView hint;
  private Bitmap source;
  private int rotation = 0, crop = 0, quality = 90, max = 4096, format = 0, filter = 0;
  private float brightness = 0, contrast = 1, saturation = 1, warmth = 0;
  private float vertical = 0, horizontal = 0, glare = 0;
  private boolean flip = false, loading = true;
  private String mode = "adjust";

  PhotoStudio(ModernActivity activity, MainActivity.Media media) {
    super(activity, android.R.style.Theme_Material_NoActionBar);
    a = activity;
    this.media = media;
  }

  TextView text(String s, int size, int color) {
    TextView t = new TextView(a);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    return t;
  }

  LinearLayout column() {
    LinearLayout l = new LinearLayout(a);
    l.setOrientation(LinearLayout.VERTICAL);
    l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    return l;
  }

  Button control(String value, Runnable run) {
    return StudioUI.control(a, value, run, false);
  }

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout root = column();
    root.setBackgroundColor(StudioUI.BACKGROUND);
    root.setOnApplyWindowInsetsListener(
        (v, i) -> {
          android.graphics.Insets bars = i.getInsets(WindowInsets.Type.systemBars());
          root.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return i;
        });
    LinearLayout top = a.row();
    top.setPadding(a.dp(16), a.dp(10), a.dp(16), a.dp(8));
    top.addView(
        StudioUI.close(a, this::dismiss), new LinearLayout.LayoutParams(a.dp(48), a.dp(48)));
    TextView title = text("עריכת תמונה", 17, Color.WHITE);
    title.setGravity(Gravity.CENTER);
    title.setTypeface(null, Typeface.BOLD);
    top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    Button save = StudioUI.control(a, "שמור עותק", this::save, true);

    top.addView(save, new LinearLayout.LayoutParams(a.dp(104), -2));
    root.addView(top);
    preview = new ImageView(a);
    preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
    preview.setBackground(a.rounded(0xff15131c, 24));
    preview.setClipToOutline(true);
    preview.setContentDescription("תצוגה מקדימה של העריכה");
    LinearLayout.LayoutParams photo = new LinearLayout.LayoutParams(-1, 0, 1);
    photo.setMargins(a.dp(10), a.dp(12), a.dp(10), a.dp(12));
    root.addView(preview, photo);
    hint = text("טוען תצוגה מקדימה…", 12, 0xffa3a6b0);
    hint.setGravity(Gravity.CENTER);
    root.addView(hint);
    ScrollView scroll = new ScrollView(a);
    panel = column();
    panel.setPadding(a.dp(24), a.dp(14), a.dp(24), a.dp(10));
    StudioUI.panel(a, panel);
    scroll.addView(panel);
    int panelDp =
        Math.max(
            100,
            Math.min(
                212,
                (int)
                    (a.getResources().getDisplayMetrics().heightPixels
                        / a.getResources().getDisplayMetrics().density
                        * .27f)));
    root.addView(scroll, new LinearLayout.LayoutParams(-1, a.dp(panelDp)));
    tabs = a.row();
    tabs.setPadding(a.dp(10), a.dp(6), a.dp(10), a.dp(12));
    HorizontalScrollView tabScroll = new HorizontalScrollView(a);
    tabScroll.setHorizontalScrollBarEnabled(false);
    tabScroll.addView(tabs);
    root.addView(tabScroll);
    setContentView(root);
    getWindow().setStatusBarColor(StudioUI.BACKGROUND);
    getWindow().setNavigationBarColor(StudioUI.BACKGROUND);
    getWindow().getDecorView().setSystemUiVisibility(0);
    showPanel();
    a.thumbs.execute(
        () -> {
          try {
            Bitmap b = a.decode(media.uri, 1200);
            a.runOnUiThread(
                () -> {
                  if (!isShowing()) return;
                  source = b;
                  loading = false;
                  refresh();
                });
          } catch (Exception e) {
            a.runOnUiThread(
                () -> {
                  hint.setText("התמונה לא נטענה");
                  a.message("פתיחת תמונה", e.getMessage());
                });
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

  ColorMatrix colors() {
    ColorMatrix matrix = new ColorMatrix();
    matrix.setSaturation(filter == 2 ? 0 : saturation);
    float c = contrast + (filter == 1 ? .15f : 0);
    float offset = 128 * (1 - c) + brightness;
    float warm = warmth + (filter == 3 ? 18 : filter == 4 ? -18 : 0);
    matrix.postConcat(
        new ColorMatrix(
            new float[] {
              c,
              0,
              0,
              0,
              offset + warm,
              0,
              c,
              0,
              0,
              offset,
              0,
              0,
              c,
              0,
              offset - warm,
              0,
              0,
              0,
              1,
              0
            }));
    if (filter == 5)
      matrix.postConcat(
          new ColorMatrix(
              new float[] {
                .85f, 0, 0, 0, 25, 0, .85f, 0, 0, 25, 0, 0, .85f, 0, 25, 0, 0, 0, 1, 0
              }));
    return matrix;
  }

  Bitmap geometry(Bitmap input) {
    int w = input.getWidth(), h = input.getHeight(), cw = w, ch = h;
    if (crop > 0) {
      float ratio = crop == 1 ? 1 : crop == 2 ? 4f / 3 : 16f / 9;
      if (w / (float) h > ratio) cw = Math.max(1, (int) (h * ratio));
      else ch = Math.max(1, (int) (w / ratio));
    }
    Matrix transform = new Matrix();
    transform.postScale(flip ? -1 : 1, 1);
    transform.postRotate(rotation);
    Bitmap result = Bitmap.createBitmap(input, (w - cw) / 2, (h - ch) / 2, cw, ch, transform, true);
    if (vertical != 0 || horizontal != 0)
      result = AdvancedEngine.perspective(result, vertical, horizontal);
    if (glare > 0) result = AdvancedEngine.glare(result, glare);
    return result;
  }

  void refresh() {
    if (source == null) return;
    preview.setImageBitmap(geometry(source));
    preview.setColorFilter(new ColorMatrixColorFilter(colors()));
    hint.setText("תצוגה מקדימה · המקור נשמר");
  }

  void slider(String name, int progress, int max, java.util.function.IntConsumer consumer) {
    LinearLayout row = a.row();
    row.addView(text(name, 13, 0xffdddddf), new LinearLayout.LayoutParams(0, -2, 1));
    TextView value = text("" + progress, 12, StudioUI.ACCENT);
    row.addView(value);
    panel.addView(row);
    SeekBar seek = new SeekBar(a);
    seek.setMax(max);
    seek.setProgress(progress);
    seek.setProgressTintList(android.content.res.ColorStateList.valueOf(StudioUI.ACCENT));
    seek.setThumbTintList(android.content.res.ColorStateList.valueOf(StudioUI.ACCENT));
    seek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar s, int p, boolean user) {
            value.setText(String.valueOf(p));
            if (user) {
              consumer.accept(p);
              refresh();
            }
          }

          public void onStartTrackingTouch(SeekBar s) {}

          public void onStopTrackingTouch(SeekBar s) {}
        });
    panel.addView(seek, new LinearLayout.LayoutParams(-1, a.dp(48)));
  }

  void showPanel() {
    panel.removeAllViews();
    tabs.removeAllViews();
    for (String[] item :
        new String[][] {
          {"adjust", "התאמה"},
          {"crop", "חיתוך"},
          {"filters", "מסננים"},
          {"export", "יצוא"},
          {"advanced", "כלים נוספים"}
        }) {
      TextView t =
          StudioUI.tab(
              a,
              item[1],
              mode.equals(item[0]),
              () -> {
                mode = item[0];
                showPanel();
              });
      tabs.addView(t);
    }
    if (mode.equals("adjust")) {
      slider("בהירות", Math.round(brightness) + 100, 200, p -> brightness = p - 100);
      slider("ניגודיות", Math.round(contrast * 100), 200, p -> contrast = p / 100f);
      slider("רוויה", Math.round(saturation * 100), 200, p -> saturation = p / 100f);
      slider("חום", Math.round(warmth) + 50, 100, p -> warmth = p - 50);
    } else if (mode.equals("crop")) {
      LinearLayout row = a.row();
      row.addView(
          control(
              "סיבוב 90°",
              () -> {
                rotation = (rotation + 90) % 360;
                refresh();
              }),
          new LinearLayout.LayoutParams(0, -2, 1));
      row.addView(
          control(
              "היפוך",
              () -> {
                flip = !flip;
                refresh();
              }),
          new LinearLayout.LayoutParams(0, -2, 1));
      panel.addView(row);
      panel.addView(text("יחס חיתוך מהמרכז", 13, 0xffa3a6b0));
      LinearLayout ratios = a.row();
      String[] labels = {"מקור", "1:1", "4:3", "16:9"};
      for (int i = 0; i < labels.length; i++) {
        final int n = i;
        Button b =
            control(
                labels[i],
                () -> {
                  crop = n;
                  refresh();
                  showPanel();
                });
        b.setTextColor(crop == i ? StudioUI.ACCENT : Color.WHITE);
        ratios.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
      }
      panel.addView(ratios);
      panel.addView(
          control(
              "איפוס כל העריכות",
              () -> {
                vertical = horizontal = glare = 0;
                rotation = 0;
                crop = 0;
                flip = false;
                brightness = 0;
                contrast = saturation = 1;
                warmth = 0;
                filter = 0;
                refresh();
                showPanel();
              }));
    } else if (mode.equals("filters")) {
      String[] labels = {"מקורי", "חי", "שחור־לבן", "חם", "קר", "דהוי"};
      for (int i = 0; i < labels.length; i += 3) {
        LinearLayout r = a.row();
        for (int j = i; j < Math.min(i + 3, labels.length); j++) {
          final int n = j;
          Button b =
              control(
                  labels[j],
                  () -> {
                    filter = n;
                    refresh();
                    showPanel();
                  });
          b.setTextColor(filter == j ? StudioUI.ACCENT : Color.WHITE);
          r.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        }
        panel.addView(r);
      }
      panel.addView(text("המסנן משתלב עם התאמות הצבע שבחרת.", 12, 0xffa3a6b0));
    } else if (mode.equals("advanced")) {
      slider(
          "פרספקטיבה אנכית",
          Math.round(vertical * 100) + 100,
          200,
          p -> vertical = (p - 100) / 100f);
      slider(
          "פרספקטיבה אופקית",
          Math.round(horizontal * 100) + 100,
          200,
          p -> horizontal = (p - 100) / 100f);
      slider("הפחתת בוהק בהיר", Math.round(glare * 100), 100, p -> glare = p / 100f);
      panel.addView(
          text(
              "הפחתת בוהק אינה שחזור מידע שמוסתר בהשתקפות. הכלים החכמים פועלים על המקור; שמור"
                  + " התאמות כעותק לפני המעבר.",
              11,
              0xffa3a6b0));
      panel.addView(
          control(
              "Lume AI · הסרה, הרחבה ושיפור",
              () -> {
                dismiss();
                a.ai.tools(media);
              }));
    } else {
      LinearLayout formats = a.row();
      String[] names = {"JPEG", "PNG", "WebP"};
      for (int i = 0; i < 3; i++) {
        final int n = i;
        Button b =
            control(
                names[i],
                () -> {
                  format = n;
                  showPanel();
                });
        b.setTextColor(format == i ? StudioUI.ACCENT : Color.WHITE);
        formats.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
      }
      panel.addView(formats);
      slider("איכות דחיסה", quality, 100, p -> quality = Math.max(1, p));
      LinearLayout sizes = a.row();
      for (int value : new int[] {1280, 2048, 4096}) {
        Button b =
            control(
                String.valueOf(value),
                () -> {
                  max = value;
                  showPanel();
                });
        b.setTextColor(max == value ? StudioUI.ACCENT : Color.WHITE);
        sizes.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
      }
      panel.addView(sizes);
      panel.addView(text("גודל מרבי בפיקסלים · נתוני מיקום אינם מועתקים", 12, 0xffa3a6b0));
    }
  }

  void save() {
    if (loading) {
      a.message("התמונה נטענת", "המתן לטעינה לפני שמירה");
      return;
    }
    final int exportMax = max, exportFormat = format, exportQuality = quality;
    final ColorMatrix color = colors();
    dismiss();
    a.status.setText("שומר תמונה…");
    a.work(
        () -> {
          Bitmap original = a.decode(media.uri, exportMax);
          Bitmap transformed = geometry(original);
          Bitmap result =
              Bitmap.createBitmap(
                  transformed.getWidth(), transformed.getHeight(), Bitmap.Config.ARGB_8888);
          Canvas canvas = new Canvas(result);
          if (exportFormat == 0) canvas.drawColor(Color.WHITE);
          Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
          paint.setColorFilter(new ColorMatrixColorFilter(color));
          canvas.drawBitmap(transformed, 0, 0, paint);
          String ext = exportFormat == 0 ? "jpg" : exportFormat == 1 ? "png" : "webp";
          Uri output = a.createOutput(false, "image/" + (exportFormat == 0 ? "jpeg" : ext), ext);
          try {
            try (OutputStream os = a.getContentResolver().openOutputStream(output)) {
              if (!result.compress(
                  exportFormat == 0
                      ? Bitmap.CompressFormat.JPEG
                      : exportFormat == 1
                          ? Bitmap.CompressFormat.PNG
                          : Bitmap.CompressFormat.WEBP_LOSSY,
                  exportQuality,
                  os)) throw new IOException("שמירת התמונה נכשלה");
            }
            a.publish(output);
          } catch (Exception e) {
            a.getContentResolver().delete(output, null, null);
            throw e;
          }
          a.runOnUiThread(
              () -> {
                a.load();
                Toast.makeText(a, "העותק נשמר באלבום Lume", Toast.LENGTH_SHORT).show();
              });
        });
  }
}
