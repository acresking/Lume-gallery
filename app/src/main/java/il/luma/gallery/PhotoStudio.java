package il.luma.gallery;

import android.app.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Unified non-destructive photo workbench. Recipes drive preview, history and copy export. */
final class PhotoStudio extends Dialog {
  final ModernActivity a;
  final MainActivity.Media media;
  PhotoRecipe edit = new PhotoRecipe();
  final EditorHistory<PhotoRecipe> history = new EditorHistory<>(20);
  LinearLayout panel, tabs;
  ImageView preview;
  PhotoOverlay overlay;
  TextView hint;
  View undoButton, redoButton;
  Button saveButton;
  Bitmap originalPreview;
  String mode = "adjust";
  boolean loading = true, aiRunning;
  long generation;
  final Handler handler = new Handler(Looper.getMainLooper());
  Runnable pending;
  final ThreadPoolExecutor renderer =
      new ThreadPoolExecutor(
          1,
          1,
          15,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(1),
          new ThreadPoolExecutor.DiscardOldestPolicy());

  PhotoStudio(ModernActivity a, MainActivity.Media m) {
    super(a, android.R.style.Theme_Material_NoActionBar);
    this.a = a;
    media = m;
  }

  TextView text(String s, int size) {
    return a.label(s, size, 0xffeee8f6, false);
  }

  Button control(String s, Runnable action) {
    return StudioUI.control(a, s, action, false);
  }

  void record() {
    history.record(edit.copy());
    buttons();
  }

  void change(Runnable action) {
    if (aiRunning) return;
    record();
    action.run();
    refresh();
    showPanel();
  }

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    LinearLayout root = a.column();
    root.setBackgroundColor(StudioUI.BACKGROUND);
    root.setOnApplyWindowInsetsListener(
        (v, i) -> {
          var bars = i.getInsets(WindowInsets.Type.systemBars());
          v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return i;
        });
    LinearLayout top = a.row();
    top.setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(6));
    top.addView(StudioUI.close(a, this::dismiss));
    TextView title = text("עריכת תמונה", 19);
    title.setGravity(Gravity.CENTER);
    top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    saveButton = StudioUI.control(a, "שמור עותק", this::save, true);
    top.addView(saveButton, new LinearLayout.LayoutParams(a.dp(104), -2));
    root.addView(top);
    LinearLayout historyRow = a.row();
    historyRow.setPadding(a.dp(10), 0, a.dp(10), 0);
    undoButton =
        darkIcon(
            "undo",
            "בטל עריכה",
            () -> {
              if (!aiRunning) {
                edit = history.undo(edit.copy());
                refresh();
                showPanel();
              }
            });
    redoButton =
        darkIcon(
            "redo",
            "שחזר עריכה",
            () -> {
              if (!aiRunning) {
                edit = history.redo(edit.copy());
                refresh();
                showPanel();
              }
            });
    historyRow.addView(undoButton);
    historyRow.addView(redoButton);
    TextView original = text("החזק בתצוגה להשוואה למקור", 11);
    original.setGravity(Gravity.CENTER);
    historyRow.addView(original, new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(historyRow);
    FrameLayout stage = new FrameLayout(a);
    stage.setBackground(a.rounded(0xff14121b, 24));
    stage.setClipToOutline(true);
    preview = new ImageView(a);
    preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
    stage.addView(preview, new FrameLayout.LayoutParams(-1, -1));
    overlay = new PhotoOverlay(a);
    stage.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    overlay.beforeStroke = this::record;
    overlay.changed =
        () -> {
          generation++;
          buttons();
        };
    LinearLayout.LayoutParams stageLp = new LinearLayout.LayoutParams(-1, 0, 1);
    stageLp.setMargins(a.dp(8), a.dp(5), a.dp(8), a.dp(5));
    root.addView(stage, stageLp);
    preview.setOnTouchListener(
        (v, e) -> {
          if (loading || aiRunning || mode.equals("draw")) return false;
          if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            preview.setImageBitmap(originalPreview);
            overlay.setVisibility(View.INVISIBLE);
            hint.setText("המקור · לא משתנה");
            return true;
          }
          if (e.getActionMasked() == MotionEvent.ACTION_UP
              || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            overlay.setVisibility(View.VISIBLE);
            refresh();
            if (e.getActionMasked() == MotionEvent.ACTION_UP) v.performClick();
            return true;
          }
          return true;
        });
    hint = text("טוען תמונה…", 11);
    hint.setGravity(Gravity.CENTER);
    root.addView(hint);
    panel = a.column();
    panel.setPadding(a.dp(18), a.dp(14), a.dp(18), a.dp(14));
    StudioUI.panel(a, panel);
    ScrollView scroll = new ScrollView(a);
    scroll.addView(panel);
    root.addView(
        scroll,
        new LinearLayout.LayoutParams(
            -1,
            Math.max(
                a.dp(110),
                Math.min(
                    a.dp(240), (int) (a.getResources().getDisplayMetrics().heightPixels * .29f)))));
    tabs = a.row();
    tabs.setPadding(a.dp(6), a.dp(8), a.dp(6), a.dp(8));
    HorizontalScrollView tabScroll = new HorizontalScrollView(a);
    tabScroll.setHorizontalScrollBarEnabled(false);
    tabScroll.addView(tabs);
    root.addView(tabScroll);
    setContentView(root);
    showPanel();
    buttons();
    a.thumbs.execute(
        () -> {
          try {
            Bitmap image = a.decode(media.uri, 1200);
            a.runOnUiThread(
                () -> {
                  if (!isShowing()) return;
                  originalPreview = image;
                  loading = false;
                  refresh();
                });
          } catch (Exception e) {
            a.runOnUiThread(
                () -> {
                  if (isShowing()) hint.setText("התמונה אינה זמינה");
                });
          }
        });
  }

  View darkIcon(String icon, String label, Runnable action) {
    View v = a.iconButton(icon, label, action);
    ((ImageView) ((FrameLayout) v).getChildAt(0))
        .setImageDrawable(new GalleryIcons(icon, StudioUI.ACCENT));
    return v;
  }

  void buttons() {
    if (undoButton == null) return;
    undoButton.setEnabled(!aiRunning && history.canUndo());
    undoButton.setAlpha(undoButton.isEnabled() ? 1 : .35f);
    redoButton.setEnabled(!aiRunning && history.canRedo());
    redoButton.setAlpha(redoButton.isEnabled() ? 1 : .35f);
    saveButton.setEnabled(!loading && !aiRunning);
  }

  void refresh() {
    buttons();
    if (originalPreview == null) return;
    overlay.marks = edit.marks;
    overlay.drawing = mode.equals("draw");
    overlay.setEnabled(false);
    PhotoRecipe snapshot = edit.copy();
    Bitmap input =
        snapshot.base == null ? originalPreview : PhotoRecipe.sampled(snapshot.base, 1200);
    long token = ++generation;
    if (pending != null) handler.removeCallbacks(pending);
    pending =
        () -> {
          if (renderer.isShutdown()) return;
          renderer.execute(
              () -> {
                try {
                  Bitmap rendered = snapshot.render(input, false);
                  a.runOnUiThread(
                      () -> {
                        if (!isShowing() || token != generation) return;
                        preview.setImageBitmap(rendered);
                        overlay.base = rendered;
                        overlay.marks = edit.marks;
                        overlay.drawing = mode.equals("draw");
                        overlay.setEnabled(!aiRunning);
                        overlay.invalidate();
                        hint.setText(
                            snapshot.base == null
                                ? "תצוגה מקדימה · המקור נשמר"
                                : "תוצאת AI בעורך · רזולוציה מוגבלת לגודל התוצאה");
                      });
                } catch (OutOfMemoryError | RuntimeException error) {
                  a.runOnUiThread(
                      () -> {
                        if (isShowing() && token == generation)
                          hint.setText("התצוגה לא עודכנה · נסה פחות אפקטים או גודל קטן יותר");
                      });
                }
              });
        };
    handler.postDelayed(pending, 45);
  }

  void slider(String name, int value, int max, IntConsumer apply) {
    TextView current = a.label(String.valueOf(value), 12, StudioUI.ACCENT, true);
    LinearLayout setting = StudioUI.setting(a, panel, name, current);
    SeekBar seek = new SeekBar(a);
    seek.setMax(max);
    seek.setProgress(value);
    StudioUI.slider(seek);
    seek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar s, int p, boolean user) {
            current.setText(String.valueOf(p));
            if (user && !aiRunning) {
              apply.accept(p);
              refresh();
            }
          }

          public void onStartTrackingTouch(SeekBar s) {
            if (!aiRunning) record();
          }

          public void onStopTrackingTouch(SeekBar s) {
            refresh();
          }
        });
    setting.addView(seek, new LinearLayout.LayoutParams(-1, a.dp(48)));
  }

  void choices(String[] names, int selected, IntConsumer action) {
    for (int i = 0; i < names.length; i += StudioUI.choiceColumns(a)) {
      LinearLayout row = a.row();
      for (int j = i; j < Math.min(names.length, i + StudioUI.choiceColumns(a)); j++) {
        final int n = j;
        Button button =
            StudioUI.control(a, names[j], () -> change(() -> action.accept(n)), selected == j);
        button.setSelected(selected == j);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(a.dp(3), a.dp(3), a.dp(3), a.dp(3));
        row.addView(button, lp);
      }
      panel.addView(row);
    }
  }

  void showPanel() {
    if (panel == null) return;
    panel.removeAllViews();
    tabs.removeAllViews();
    for (String[] t :
        new String[][] {
          {"adjust", "צבע"},
          {"light", "אור"},
          {"crop", "חיתוך"},
          {"filters", "מסננים"},
          {"detail", "פרטים"},
          {"draw", "ציור וטקסט"},
          {"ai", "Lume AI"},
          {"export", "יצוא"}
        })
      tabs.addView(
          StudioUI.tab(
              a,
              t[1],
              mode.equals(t[0]),
              () -> {
                mode = t[0];
                showPanel();
                overlay.drawing = mode.equals("draw");
              }));
    switch (mode) {
      case "adjust":
        panel.addView(
            control(
                "התאמה אוטומטית",
                () ->
                    change(
                        () -> {
                          edit.details.exposure =
                              originalPreview == null
                                  ? 0
                                  : PhotoEffects.automaticExposure(originalPreview);
                          edit.details.vibrance = .18f;
                          edit.details.highlights = -.12f;
                        })));
        slider("בהירות", Math.round(edit.brightness) + 100, 200, p -> edit.brightness = p - 100);
        slider("ניגודיות", Math.round(edit.contrast * 100), 200, p -> edit.contrast = p / 100f);
        slider("רוויה", Math.round(edit.saturation * 100), 200, p -> edit.saturation = p / 100f);
        slider("חום", Math.round(edit.warmth) + 50, 100, p -> edit.warmth = p - 50);
        slider(
            "גוון ירוק / מג׳נטה",
            Math.round(edit.details.tint * 100) + 100,
            200,
            p -> edit.details.tint = (p - 100) / 100f);
        slider(
            "חיות צבע",
            Math.round(edit.details.vibrance * 100) + 100,
            200,
            p -> edit.details.vibrance = (p - 100) / 100f);
        break;
      case "light":
        slider(
            "חשיפה · מרכז = 0",
            Math.round(edit.details.exposure * 50) + 100,
            200,
            p -> edit.details.exposure = (p - 100) / 50f);
        slider(
            "אזורים בהירים",
            Math.round(edit.details.highlights * 100) + 100,
            200,
            p -> edit.details.highlights = (p - 100) / 100f);
        slider(
            "צללים",
            Math.round(edit.details.shadows * 100) + 100,
            200,
            p -> edit.details.shadows = (p - 100) / 100f);
        slider(
            "לבנים",
            Math.round(edit.details.whites * 100) + 100,
            200,
            p -> edit.details.whites = (p - 100) / 100f);
        slider(
            "שחורים",
            Math.round(edit.details.blacks * 100) + 100,
            200,
            p -> edit.details.blacks = (p - 100) / 100f);
        slider(
            "גאמה · 100 = רגיל",
            Math.round(edit.details.gamma * 100) - 50,
            150,
            p -> edit.details.gamma = (p + 50) / 100f);
        break;
      case "crop":
        choices(
            new String[] {"סיבוב 90°", "היפוך אופקי", "היפוך אנכי"},
            -1,
            n -> {
              if (n == 0) edit.rotation = (edit.rotation + 90) % 360;
              else if (n == 1) edit.flipX = !edit.flipX;
              else edit.flipY = !edit.flipY;
            });
        choices(
            new String[] {"מקור", "1:1", "4:3", "16:9", "3:4", "9:16", "3:2", "2:3"},
            edit.crop,
            n -> edit.crop = n);
        panel.addView(control("חיתוך חופשי בגרירת פינות", this::freeCrop));
        slider(
            "יישור · מרכז = 0°",
            Math.round(edit.straighten) + 15,
            30,
            p -> edit.straighten = p - 15);
        slider(
            "פרספקטיבה אנכית",
            Math.round(edit.vertical * 100) + 100,
            200,
            p -> edit.vertical = (p - 100) / 100f);
        slider(
            "פרספקטיבה אופקית",
            Math.round(edit.horizontal * 100) + 100,
            200,
            p -> edit.horizontal = (p - 100) / 100f);
        panel.addView(text("יישור עשוי להשאיר שוליים שקופים; אפשר להסירם בחיתוך חופשי.", 11));
        break;
      case "filters":
        choices(
            new String[] {
              "מקורי", "חי", "שחור־לבן", "חם", "קר", "דהוי", "ספיה", "קולנועי", "וינטג׳"
            },
            edit.filter,
            n -> edit.filter = n);
        break;
      case "detail":
        slider(
            "חידוד",
            Math.round(edit.details.sharpness * 100),
            100,
            p -> edit.details.sharpness = p / 100f);
        slider(
            "ריכוך רעש מקומי",
            Math.round(edit.details.denoise * 100),
            100,
            p -> edit.details.denoise = p / 100f);
        slider(
            "הכהיית שוליים",
            Math.round(edit.details.vignette * 100),
            100,
            p -> edit.details.vignette = p / 100f);
        slider(
            "גרעיניות",
            Math.round(edit.details.grain * 100),
            100,
            p -> edit.details.grain = p / 100f);
        slider("הפחתת בוהק בהיר", Math.round(edit.glare * 100), 100, p -> edit.glare = p / 100f);
        panel.addView(
            text(
                "ריכוך ובוהק הם עיבוד מקומי רגיל. הסרת תוכן שהוסתר בהשתקפות דורשת תיקון משוער"
                    + " ב־AI.",
                11));
        break;
      case "draw":
        choices(
            new String[] {"עט", "פסיפס", "הוסף טקסט"},
            overlay.mosaic ? 1 : 0,
            n -> {
              if (n == 0) overlay.mosaic = false;
              else if (n == 1) overlay.mosaic = true;
              else caption();
            });
        choices(
            new String[] {"לבן", "שחור", "אדום", "צהוב", "כחול", "סגול"},
            -1,
            n -> {
              overlay.color =
                  new int[] {
                        Color.WHITE, Color.BLACK, 0xffef5350, 0xffffd54f, 0xff42a5f5, 0xffb388ff
                      }
                      [n];
              edit.marks.textColor = overlay.color;
            });
        slider(
            "עובי מברשת",
            Math.round(overlay.width * 1000) - 3,
            77,
            p -> overlay.width = (p + 3) / 1000f);
        if (!edit.marks.caption.isEmpty()) {
          slider(
              "גובה הטקסט",
              Math.round(edit.marks.textY * 100),
              100,
              p -> edit.marks.textY = p / 100f);
          slider(
              "גודל הטקסט",
              Math.round(edit.marks.textSize * 1000) - 20,
              100,
              p -> edit.marks.textSize = (p + 20) / 1000f);
        }
        panel.addView(
            control("מחק ציורים וטקסט", () -> change(() -> edit.marks = new PhotoMarks())));
        break;
      case "ai":
        panel.addView(
            text(
                "עיבוד במכשיר על העריכה הנוכחית. נדרשת התקנת Lume AI. בדוק תוצאה לפני שמירה.", 12));
        String[] names = {
          "הפרדת אדם לרקע שקוף",
          "טשטוש רקע דיוקן",
          "טשטוש פנים שזוהו",
          "הגדלה חכמה ×3",
          "הרחבת שוליים 10%",
          "הרחבת שוליים 20%",
          "הסרת פריט / תיקון השתקפות בסימון"
        };
        for (int i = 0; i < names.length; i++) {
          final int tool = i;
          panel.addView(control(names[i], () -> runAi(tool)));
        }
        break;
      case "export":
        choices(new String[] {"JPEG", "PNG", "WebP"}, edit.format, n -> edit.format = n);
        slider("איכות דחיסה", edit.quality, 100, p -> edit.quality = Math.max(1, p));
        choices(
            new String[] {"1280", "2048", "4096"},
            edit.max == 1280 ? 0 : edit.max == 2048 ? 1 : 2,
            n -> edit.max = new int[] {1280, 2048, 4096}[n]);
        panel.addView(
            text("שומר עותק חדש ללא EXIF/GPS. כלי AI מוגבלים לרזולוציית התוצאה שלהם.", 12));
        break;
    }
    panel.addView(
        control(
            "איפוס כל העריכות",
            () ->
                change(
                    () -> {
                      PhotoRecipe fresh = new PhotoRecipe();
                      fresh.base = edit.base;
                      edit = fresh;
                    })));
    buttons();
  }

  void caption() {
    EditText input = new EditText(a);
    input.setText(edit.marks.caption);
    input.setTextColor(Color.WHITE);
    input.setFilters(
        new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(200)});
    new PrivacyDialogBuilder(a, false)
        .setTitle("טקסט על התמונה")
        .setView(input)
        .setPositiveButton(
            "החל", (d, n) -> change(() -> edit.marks.caption = input.getText().toString()))
        .setNegativeButton("ביטול", null)
        .show();
  }

  void freeCrop() {
    if (loading || aiRunning) return;
    PhotoRecipe snapshot = edit.copy();
    snapshot.freeCrop = new RectF(0, 0, 1, 1);
    Bitmap input =
        snapshot.base == null ? originalPreview : PhotoRecipe.sampled(snapshot.base, 1200);
    renderer.execute(
        () -> {
          try {
            Bitmap image = snapshot.render(input, false);
            a.runOnUiThread(
                () -> {
                  if (isShowing())
                    new FreeCropDialog(
                            a, image, edit.freeCrop, rect -> change(() -> edit.freeCrop = rect))
                        .show();
                });
          } catch (RuntimeException | OutOfMemoryError error) {
            a.runOnUiThread(
                () -> {
                  if (isShowing())
                    a.message("חיתוך", "לא ניתן להכין את התצוגה. נסה גודל קטן יותר.");
                });
          }
        });
  }

  void runAi(int tool) {
    if (loading || aiRunning || a.ai.busy) return;
    String id = tool == 2 ? "faces" : tool == 3 ? "enhance" : tool >= 4 ? "repair" : "portrait";
    if (!a.ai.require(id)) return;
    PhotoRecipe snapshot = edit.copy();
    Bitmap base =
        snapshot.base == null
            ? originalPreview
            : PhotoRecipe.sampled(snapshot.base, tool == 3 ? 768 : 1200);
    aiRunning = true;
    buttons();
    hint.setText("מעבד במכשיר…");
    a.ai.task(
        "עריכת תמונה ב־Lume AI",
        () -> {
          try {
            Bitmap input = snapshot.render(base, true);
            if (tool == 6) {
              MainActivity.Media staged = EditorFiles.stage(a, input, "עריכה נוכחית.png");
              a.ai.ui(
                  () -> {
                    aiRunning = false;
                    buttons();
                    if (isShowing()) new RepairStudio(a, staged).show();
                  });
              return;
            }
            Bitmap result;
            if (tool <= 2) {
              try (AiEngine engine = new AiEngine(a)) {
                result =
                    tool == 2 ? engine.pixelateFaces(input) : engine.portrait(input, tool == 0);
              }
            } else {
              try (AdvancedEngine engine = new AdvancedEngine(a)) {
                result =
                    tool == 3
                        ? engine.upscale(PhotoRecipe.sampled(input, 768))
                        : engine.expand(input, tool == 4 ? 10 : 20);
              }
            }
            a.ai.ui(
                () -> {
                  aiRunning = false;
                  if (!isShowing()) return;
                  history.clear();
                  history.record(snapshot);
                  PhotoRecipe fresh = new PhotoRecipe();
                  fresh.base = result;
                  fresh.format = 1;
                  fresh.max = snapshot.max;
                  fresh.quality = snapshot.quality;
                  edit = fresh;
                  refresh();
                  showPanel();
                });
          } catch (Exception | OutOfMemoryError error) {
            a.ai.ui(
                () -> {
                  aiRunning = false;
                  buttons();
                  if (isShowing())
                    a.message(
                        "העריכה לא הוחלה",
                        error instanceof OutOfMemoryError
                            ? "אין מספיק זיכרון. נסה תמונה קטנה יותר."
                            : error.getMessage());
                });
          }
        });
  }

  void save() {
    if (loading || aiRunning) return;
    PhotoRecipe snapshot = edit.copy();
    dismiss();
    a.status.setText("שומר עותק ערוך…");
    a.work(
        () -> {
          try {
            Bitmap base =
                snapshot.base == null
                    ? a.decode(media.uri, snapshot.max)
                    : PhotoRecipe.sampled(snapshot.base, snapshot.max);
            Bitmap result = snapshot.render(base, true);
            if (snapshot.format == 0) {
              new Canvas(result).drawColor(Color.WHITE, PorterDuff.Mode.DST_OVER);
            }
            String ext = snapshot.format == 0 ? "jpg" : snapshot.format == 1 ? "png" : "webp";
            Uri out = a.createOutput(false, "image/" + (snapshot.format == 0 ? "jpeg" : ext), ext);
            try (OutputStream stream = a.getContentResolver().openOutputStream(out)) {
              if (stream == null
                  || !result.compress(
                      snapshot.format == 0
                          ? Bitmap.CompressFormat.JPEG
                          : snapshot.format == 1
                              ? Bitmap.CompressFormat.PNG
                              : Bitmap.CompressFormat.WEBP_LOSSY,
                      snapshot.quality,
                      stream)) throw new IOException("השמירה נכשלה");
            } catch (Exception e) {
              a.getContentResolver().delete(out, null, null);
              throw e;
            }
            EditorFiles.publish(a, out);
            a.runOnUiThread(
                () -> {
                  a.load();
                  Toast.makeText(a, "עותק ערוך נשמר בגלריה", Toast.LENGTH_SHORT).show();
                });
          } catch (OutOfMemoryError error) {
            throw new IOException("אין מספיק זיכרון. בחר גודל יצוא קטן יותר.", error);
          }
        });
  }

  @Override
  public void show() {
    AppPrivacy.apply(getWindow(), a);
    getWindow().setWindowAnimations(a.motion() ? R.style.GallerySheetMotion : 0);
    super.show();
    getWindow().setLayout(-1, -1);
    getWindow().setStatusBarColor(StudioUI.BACKGROUND);
    getWindow().setNavigationBarColor(StudioUI.BACKGROUND);
    getWindow().getDecorView().setSystemUiVisibility(0);
  }

  @Override
  public void dismiss() {
    generation++;
    handler.removeCallbacksAndMessages(null);
    renderer.shutdownNow();
    if (aiRunning) a.ai.cancel.set(true);
    super.dismiss();
  }
}
