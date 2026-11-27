package il.luma.gallery;

import android.app.*;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.transformer.*;
import java.io.*;
import java.util.*;
import java.util.function.*;

/** Multi-tool video editor. A shared recipe builds real preview effects and export composition. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class VideoStudio extends Dialog {
  final ModernActivity a;
  final MainActivity.Media media;
  VideoRecipe edit = new VideoRecipe();
  final EditorHistory<VideoRecipe> history = new EditorHistory<>(20);
  VideoPreview preview;
  LinearLayout panel, tabs;
  SeekBar from, to;
  TextView time, note;
  Button playButton, saveButton;
  View undoButton, redoButton;
  long duration;
  String mode = "trim";
  boolean ready, aiRunning;
  final Handler handler = new Handler(Looper.getMainLooper());
  Runnable updatePreview;

  VideoStudio(ModernActivity a, MainActivity.Media m) {
    super(a, android.R.style.Theme_Material_NoActionBar);
    this.a = a;
    media = m;
    duration = m.duration;
    edit.clips.add(
        new VideoRecipe.Clip(
            m.uri, Math.max(1, m.duration), Math.max(1, m.width), Math.max(1, m.height), true));
    edit.end = Math.max(1, m.duration);
  }

  TextView text(String s, int size) {
    return a.label(s, size, 0xfff2ebfa, false);
  }

  Button button(String s, Runnable r) {
    return StudioUI.control(a, s, r, false);
  }

  void record() {
    history.record(edit.copy());
    buttons();
  }

  void change(Runnable action) {
    if (aiRunning) return;
    record();
    action.run();
    showPanel();
    refresh();
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
    TextView title = text("עריכת סרטון", 19);
    title.setGravity(Gravity.CENTER);
    top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    saveButton = StudioUI.control(a, "יצוא", () -> export(false), true);
    top.addView(saveButton, new LinearLayout.LayoutParams(a.dp(84), -2));
    root.addView(top);
    LinearLayout historyRow = a.row();
    historyRow.setPadding(a.dp(10), 0, a.dp(10), 0);
    undoButton =
        icon(
            "undo",
            "בטל עריכת סרטון",
            () -> {
              if (!aiRunning) {
                edit = history.undo(edit.copy());
                showPanel();
                refresh();
              }
            });
    redoButton =
        icon(
            "redo",
            "שחזר עריכת סרטון",
            () -> {
              if (!aiRunning) {
                edit = history.redo(edit.copy());
                showPanel();
                refresh();
              }
            });
    historyRow.addView(undoButton);
    historyRow.addView(redoButton);
    time = text("טוען סרטון…", 12);
    time.setGravity(Gravity.CENTER);
    historyRow.addView(time, new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(historyRow);
    preview = new VideoPreview(a);
    preview.setBackground(a.rounded(0xff14121b, 24));
    preview.setClipToOutline(true);
    preview.error =
        s -> {
          if (note != null) note.setText(s);
        };
    LinearLayout.LayoutParams stageLp = new LinearLayout.LayoutParams(-1, 0, 1);
    stageLp.setMargins(a.dp(8), a.dp(4), a.dp(8), a.dp(4));
    root.addView(preview, stageLp);
    LinearLayout playback = a.row();
    playback.setPadding(a.dp(12), a.dp(4), a.dp(12), a.dp(4));
    playButton =
        button(
            "נגן / השהה",
            () -> {
              if (preview.isPlaying()) preview.pause();
              else preview.start();
            });
    playback.addView(playButton, new LinearLayout.LayoutParams(0, -2, 1));
    playback.addView(
        button("פריים לתמונה", () -> frame(false)), new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(playback);
    panel = a.column();
    panel.setPadding(a.dp(18), a.dp(14), a.dp(18), a.dp(14));
    StudioUI.panel(a, panel);
    ScrollView controls = new ScrollView(a);
    controls.addView(panel);
    root.addView(
        controls,
        new LinearLayout.LayoutParams(
            -1,
            Math.max(
                a.dp(110),
                Math.min(
                    a.dp(250), (int) (a.getResources().getDisplayMetrics().heightPixels * .29f)))));
    note = text("המקור נשמר · תצוגה מקדימה עם אפקטים · יצוא כעותק", 11);
    note.setGravity(Gravity.CENTER);
    root.addView(note);
    tabs = a.row();
    tabs.setPadding(a.dp(6), a.dp(6), a.dp(6), a.dp(8));
    HorizontalScrollView strip = new HorizontalScrollView(a);
    strip.setHorizontalScrollBarEnabled(false);
    strip.addView(tabs);
    root.addView(strip);
    setContentView(root);
    showPanel();
    buttons();
    a.io.execute(
        () -> {
          try {
            VideoRecipe.Clip info = VideoRecipe.Clip.read(a, media.uri);
            a.runOnUiThread(
                () -> {
                  if (!isShowing()) return;
                  long old = edit.clips.get(0).duration;
                  edit.clips.set(0, info);
                  duration = info.duration;
                  edit.start = Math.min(edit.start, Math.max(0, duration - 1));
                  edit.end = edit.end == old ? duration : Math.min(duration, edit.end);
                  if (edit.end <= edit.start) edit.end = duration;
                  ready = true;
                  showPanel();
                  refresh();
                });
          } catch (Exception e) {
            a.runOnUiThread(
                () -> {
                  if (isShowing()) note.setText("לא ניתן לקרוא את הסרטון; בדוק הרשאות או קידוד");
                });
          }
        });
    handler.postDelayed(
        new Runnable() {
          public void run() {
            if (!isShowing()) return;
            preview.updateVolume();
            updateTimes();
            handler.postDelayed(this, 100);
          }
        },
        100);
  }

  View icon(String name, String label, Runnable r) {
    View v = a.iconButton(name, label, r);
    ((ImageView) ((FrameLayout) v).getChildAt(0))
        .setImageDrawable(new GalleryIcons(name, StudioUI.ACCENT));
    return v;
  }

  void buttons() {
    if (saveButton == null) return;
    saveButton.setEnabled(ready && !aiRunning);
    undoButton.setEnabled(history.canUndo() && !aiRunning);
    redoButton.setEnabled(history.canRedo() && !aiRunning);
    undoButton.setAlpha(undoButton.isEnabled() ? 1 : .35f);
    redoButton.setAlpha(redoButton.isEnabled() ? 1 : .35f);
  }

  void refresh() {
    buttons();
    if (!ready || preview.released) return;
    if (updatePreview != null) handler.removeCallbacks(updatePreview);
    VideoRecipe recipe = edit.copy();
    updatePreview =
        () -> {
          if (isShowing() && !preview.released) preview.apply(recipe);
        };
    handler.postDelayed(updatePreview, 65);
    updateTimes();
  }

  void updateTimes() {
    if (time == null) return;
    String span = stamp(edit.outputDuration());
    time.setText(
        (edit.clips.size() > 1 ? (preview.currentClip() + 1) + "/" + edit.clips.size() + " · " : "")
            + stamp(preview.getCurrentPosition())
            + " · אורך יצוא "
            + span);
  }

  String stamp(long ms) {
    return String.format(
        Locale.ROOT,
        "%d:%02d.%01d",
        Math.max(0, ms) / 60000,
        (Math.max(0, ms) / 1000) % 60,
        (Math.max(0, ms) % 1000) / 100);
  }

  void slider(String title, int value, int max, IntConsumer apply) {
    LinearLayout row = a.row();
    row.addView(text(title, 13), new LinearLayout.LayoutParams(0, -2, 1));
    TextView label = a.label(String.valueOf(value), 12, StudioUI.ACCENT, true);
    row.addView(label);
    panel.addView(row);
    SeekBar slider = new SeekBar(a);
    slider.setMax(max);
    slider.setProgress(value);
    StudioUI.slider(slider);
    slider.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar s, int p, boolean user) {
            label.setText(String.valueOf(p));
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
    panel.addView(slider, new LinearLayout.LayoutParams(-1, a.dp(48)));
  }

  void choices(String[] labels, int selected, IntConsumer apply) {
    for (int i = 0; i < labels.length; i += 3) {
      LinearLayout row = a.row();
      for (int j = i; j < Math.min(labels.length, i + 3); j++) {
        final int n = j;
        Button b =
            StudioUI.control(a, labels[j], () -> change(() -> apply.accept(n)), n == selected);
        b.setSelected(n == selected);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(a.dp(3), a.dp(3), a.dp(3), a.dp(3));
        row.addView(b, lp);
      }
      panel.addView(row);
    }
  }

  void showPanel() {
    if (panel == null) return;
    panel.removeAllViews();
    tabs.removeAllViews();
    for (String[] tab :
        new String[][] {
          {"trim", "חיתוך זמן"},
          {"crop", "מסגור"},
          {"color", "צבע"},
          {"speed", "מהירות"},
          {"sound", "שמע"},
          {"text", "טקסט"},
          {"sequence", "חיבור"},
          {"ai", "AI ופריימים"},
          {"export", "יצוא"}
        })
      tabs.addView(
          StudioUI.tab(
              a,
              tab[1],
              mode.equals(tab[0]),
              () -> {
                mode = tab[0];
                showPanel();
              }));
    switch (mode) {
      case "trim":
        panel.addView(text("התחלה: " + stamp(edit.start), 13));
        from = new SeekBar(a);
        from.setMax(1000);
        from.setProgress((int) (edit.start * 1000 / Math.max(1, duration)));
        StudioUI.slider(from);
        panel.addView(from, new LinearLayout.LayoutParams(-1, a.dp(48)));
        panel.addView(text("סיום: " + stamp(edit.end), 13));
        to = new SeekBar(a);
        to.setMax(1000);
        to.setProgress((int) (edit.end * 1000 / Math.max(1, duration)));
        StudioUI.slider(to);
        panel.addView(to, new LinearLayout.LayoutParams(-1, a.dp(48)));
        SeekBar.OnSeekBarChangeListener trim =
            new SeekBar.OnSeekBarChangeListener() {
              public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (!user || aiRunning) return;
                if (s == from)
                  edit.start = Math.min(Math.max(0, edit.end - 1), duration * p / 1000);
                else edit.end = Math.max(edit.start + 1, Math.min(duration, duration * p / 1000));
                updateTimes();
              }

              public void onStartTrackingTouch(SeekBar s) {
                record();
                preview.pause();
              }

              public void onStopTrackingTouch(SeekBar s) {
                refresh();
              }
            };
        from.setOnSeekBarChangeListener(trim);
        to.setOnSeekBarChangeListener(trim);
        panel.addView(button("זמנים מדויקים בשניות", this::preciseTrim));
        panel.addView(
            button(
                "איפוס חיתוך זמן",
                () ->
                    change(
                        () -> {
                          edit.start = 0;
                          edit.end = duration;
                        })));
        break;
      case "crop":
        choices(
            new String[] {"מקור", "1:1", "9:16", "16:9", "4:3", "3:4"},
            edit.aspect,
            n -> edit.aspect = n);
        choices(
            new String[] {"סיבוב 90°", "היפוך אופקי", "היפוך אנכי"},
            -1,
            n -> {
              if (n == 0) edit.rotation = (edit.rotation + 90) % 360;
              else if (n == 1) edit.flipX = !edit.flipX;
              else edit.flipY = !edit.flipY;
            });
        slider("מרכז אופקי", Math.round(edit.centerX * 100), 100, p -> edit.centerX = p / 100f);
        slider("מרכז אנכי", Math.round(edit.centerY * 100), 100, p -> edit.centerY = p / 100f);
        slider(
            "זום חיתוך · 100 = מקורי",
            Math.round(edit.zoom * 100) - 100,
            200,
            p -> edit.zoom = (p + 100) / 100f);
        break;
      case "color":
        choices(
            new String[] {"מקורי", "חי", "שחור־לבן", "חם", "קר", "דהוי"},
            edit.filter,
            n -> edit.filter = n);
        slider(
            "בהירות",
            Math.round(edit.brightness * 100) + 50,
            100,
            p -> edit.brightness = (p - 50) / 100f);
        slider(
            "ניגודיות",
            Math.round(edit.contrast * 100) + 80,
            160,
            p -> edit.contrast = (p - 80) / 100f);
        slider("רוויה", Math.round(edit.saturation) + 100, 200, p -> edit.saturation = p - 100);
        slider(
            "חום", Math.round(edit.warmth * 100) + 100, 200, p -> edit.warmth = (p - 100) / 100f);
        panel.addView(button("התאמת אור אוטומטית לפי הפריים", this::automaticTone));
        break;
      case "speed":
        choices(
            new String[] {"0.25×", "0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×", "4×"},
            speedIndex(),
            n -> edit.speed = new float[] {.25f, .5f, .75f, 1, 1.25f, 1.5f, 2, 4}[n]);
        panel.addView(
            text(
                "משך חדש: "
                    + stamp(edit.outputDuration())
                    + " · מהירות משנה תזמון, ללא יצירת פריימים ב־AI.",
                12));
        slider(
            "גובה צליל · 100 = רגיל",
            Math.round(edit.pitch * 100) - 50,
            150,
            p -> edit.pitch = (p + 50) / 100f);
        break;
      case "sound":
        choices(new String[] {"שמע מקורי", "ללא שמע"}, edit.mute ? 1 : 0, n -> edit.mute = n == 1);
        slider("עוצמת שמע %", Math.round(edit.volume * 100), 100, p -> edit.volume = p / 100f);
        slider(
            "כניסת שמע · עשיריות שנייה",
            Math.round(edit.fadeIn * 10),
            30,
            p -> edit.fadeIn = p / 10f);
        slider(
            "יציאת שמע · עשיריות שנייה",
            Math.round(edit.fadeOut * 10),
            30,
            p -> edit.fadeOut = p / 10f);
        panel.addView(text("דעיכת שמע חלה בתחילת ובסוף כל קטע ברצף.", 11));
        panel.addView(button("חלץ את השמע הערוך · M4A", () -> export(true)));
        break;
      case "text":
        panel.addView(
            button(edit.caption.isEmpty() ? "הוסף טקסט לסרטון" : "שנה טקסט", this::caption));
        choices(
            new String[] {"למעלה", "במרכז", "למטה"},
            edit.captionPosition,
            n -> edit.captionPosition = n);
        choices(
            new String[] {"לבן", "שחור", "צהוב", "אדום", "כחול", "סגול"},
            -1,
            n ->
                edit.captionColor =
                    new int[] {
                          Color.WHITE, Color.BLACK, 0xffffd54f, 0xffef5350, 0xff42a5f5, 0xffb388ff
                        }
                        [n]);
        slider(
            "גודל טקסט %",
            Math.round(edit.captionScale * 100) - 50,
            150,
            p -> edit.captionScale = (p + 50) / 100f);
        panel.addView(button("הסר טקסט", () -> change(() -> edit.caption = "")));
        break;
      case "sequence":
        panel.addView(
            text(
                "עד 6 קטעים · החיתוך בזמן חל על הקטע הראשון; האפקטים על כולם. יחס ומידות אחידים"
                    + " ביצוא, עם שוליים לפי הצורך.",
                12));
        panel.addView(button("הוסף קטעי וידאו", this::addClips));
        for (int i = 0; i < edit.clips.size(); i++) {
          final int index = i;
          LinearLayout row = a.row();
          row.addView(
              text("קטע " + (i + 1) + " · " + stamp(edit.clips.get(i).duration), 13),
              new LinearLayout.LayoutParams(0, -2, 1));
          if (i > 1)
            row.addView(
                icon(
                    "undo",
                    "הזז קטע למעלה",
                    () -> change(() -> Collections.swap(edit.clips, index, index - 1))));
          if (i > 0)
            row.addView(
                icon("trash", "הסר קטע מהרצף", () -> change(() -> edit.clips.remove(index))));
          panel.addView(row);
        }
        break;
      case "ai":
        panel.addView(
            text(
                "AI לפריים נבחר ומיקוד סטטי לפי פנים. אין כאן מעקב פנים, מחיקת פריטים מכל הווידאו"
                    + " או הגדלת וידאו מלאה ב־AI.",
                12));
        panel.addView(button("ערוך את הפריים הנוכחי ב־Lume AI", () -> frame(true)));
        panel.addView(button("מרכוז 9:16 לפי הפנים בפריים", this::faceFocus));
        panel.addView(button("התאמת אור אוטומטית לפי הפריים", this::automaticTone));
        panel.addView(
            text(
                "מיקוד הפנים משתמש במודל המקומי. התאמת האור היא חישוב מקומי רגיל. בדוק את החיתוך"
                    + " לאורך כל הסרטון.",
                11));
        break;
      case "export":
        choices(
            new String[] {"מקור", "2160p", "1080p", "720p", "480p"},
            edit.resolution,
            n -> edit.resolution = n);
        choices(new String[] {"H.264", "H.265"}, edit.codec, n -> edit.codec = n);
        choices(
            new String[] {"FPS מקורי", "24 FPS", "30 FPS", "60 FPS"},
            edit.fps == 0 ? 0 : edit.fps == 24 ? 1 : edit.fps == 30 ? 2 : 3,
            n -> edit.fps = new int[] {0, 24, 30, 60}[n]);
        choices(
            new String[] {"קצב אוטומטי", "4 Mbps", "8 Mbps", "16 Mbps", "32 Mbps"},
            edit.bitrate == 0
                ? 0
                : edit.bitrate == 4000000
                    ? 1
                    : edit.bitrate == 8000000 ? 2 : edit.bitrate == 16000000 ? 3 : 4,
            n -> edit.bitrate = new int[] {0, 4000000, 8000000, 16000000, 32000000}[n]);
        panel.addView(
            text(
                "MP4 חדש · HDR מומר ל־SDR לתאימות. יכולות הקידוד והרזולוציה תלויות במכשיר; אין"
                    + " תוספת פרטים שאינם במקור.",
                11));
        break;
    }
    panel.addView(
        button(
            "איפוס כל העריכה",
            () ->
                change(
                    () -> {
                      VideoRecipe fresh = new VideoRecipe();
                      fresh.clips.addAll(edit.clips);
                      fresh.end = duration;
                      edit = fresh;
                    })));
    buttons();
  }

  int speedIndex() {
    float[] values = {.25f, .5f, .75f, 1, 1.25f, 1.5f, 2, 4};
    for (int i = 0; i < values.length; i++) if (edit.speed == values[i]) return i;
    return 3;
  }

  void preciseTrim() {
    LinearLayout form = a.column();
    EditText start = a.field(form, "התחלה בשניות", String.valueOf(edit.start / 1000d), false),
        end = a.field(form, "סיום בשניות", String.valueOf(edit.end / 1000d), false);
    start.setInputType(
        android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
    end.setInputType(
        android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
    AlertDialog d =
        new PrivacyDialogBuilder(a, false)
            .setTitle("חיתוך מדויק")
            .setView(form)
            .setPositiveButton("החל", null)
            .setNegativeButton("ביטול", null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(-1)
                .setOnClickListener(
                    w -> {
                      try {
                        double s = Double.parseDouble(start.getText().toString()),
                            e = Double.parseDouble(end.getText().toString());
                        if (!Double.isFinite(s)
                            || !Double.isFinite(e)
                            || s < 0
                            || e <= s
                            || e * 1000 > duration)
                          throw new IOException("בחר זמנים בתוך אורך הסרטון");
                        change(
                            () -> {
                              edit.start = Math.round(s * 1000);
                              edit.end = Math.round(e * 1000);
                            });
                        d.dismiss();
                      } catch (Exception error) {
                        a.message("זמני חיתוך", error.getMessage());
                      }
                    }));
    d.show();
  }

  void caption() {
    EditText input = new EditText(a);
    input.setTextColor(Color.WHITE);
    input.setText(edit.caption);
    input.setFilters(
        new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(200)});
    new PrivacyDialogBuilder(a, false)
        .setTitle("טקסט לאורך הסרטון")
        .setView(input)
        .setPositiveButton("החל", (d, n) -> change(() -> edit.caption = input.getText().toString()))
        .setNegativeButton("ביטול", null)
        .show();
  }

  void addClips() {
    if (edit.clips.size() >= 6) {
      a.message("רצף וידאו", "אפשר עד 6 קטעים");
      return;
    }
    preview.pause();
    a.pickEditorVideos(
        uris -> {
          if (!isShowing()) return;
          java.util.Set<Uri> existing = new java.util.HashSet<>();
          for (VideoRecipe.Clip c : edit.clips) existing.add(c.uri);
          int remaining = 6 - existing.size();
          a.io.execute(
              () -> {
                ArrayList<VideoRecipe.Clip> selected = new ArrayList<>();
                for (Uri uri : uris) {
                  if (selected.size() >= remaining) break;
                  try {
                    if (existing.add(uri)) selected.add(VideoRecipe.Clip.read(a, uri));
                  } catch (Exception ignored) {
                  }
                }
                a.runOnUiThread(
                    () -> {
                      if (!isShowing()) return;
                      if (selected.isEmpty()) {
                        a.message("הוספת קטעים", "לא נוספו סרטונים קריאים חדשים");
                        return;
                      }
                      change(
                          () -> {
                            for (VideoRecipe.Clip clip : selected) {
                              if (edit.clips.size() >= 6) break;
                              if (edit.clips.stream().noneMatch(c -> c.uri.equals(clip.uri)))
                                edit.clips.add(clip);
                            }
                            if (edit.resolution == 0) edit.resolution = 2;
                          });
                    });
              });
        });
  }

  interface FrameAction {
    void accept(Bitmap frame) throws Exception;
  }

  void currentFrame(FrameAction action) {
    if (!ready || aiRunning) return;
    preview.pause();
    int index = Math.min(preview.currentClip(), edit.clips.size() - 1);
    VideoRecipe.Clip clip = edit.clips.get(index);
    long position = preview.getCurrentPosition();
    a.io.execute(
        () -> {
          MediaMetadataRetriever r = new MediaMetadataRetriever();
          try {
            r.setDataSource(a, clip.uri);
            Bitmap frame =
                r.getFrameAtTime(
                    Math.max(0, Math.min(clip.duration - 1, position)) * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST);
            if (frame == null) throw new IOException("הפריים אינו זמין");
            action.accept(PhotoRecipe.sampled(frame, 1600));
          } catch (Exception | OutOfMemoryError error) {
            a.runOnUiThread(
                () -> {
                  if (isShowing())
                    a.message(
                        "פריים מהסרטון",
                        error instanceof OutOfMemoryError
                            ? "אין מספיק זיכרון לפריים הזה"
                            : error.getMessage());
                });
          } finally {
            try {
              r.release();
            } catch (Exception ignored) {
            }
          }
        });
  }

  void frame(boolean ai) {
    currentFrame(
        image -> {
          MainActivity.Media frame = EditorFiles.stage(a, image, "פריים מהסרטון.png");
          a.runOnUiThread(
              () -> {
                if (isShowing()) a.openPhotoWork(frame, ai ? "ai" : "adjust");
              });
        });
  }

  void automaticTone() {
    currentFrame(
        image -> {
          float stops = PhotoEffects.automaticExposure(image);
          a.runOnUiThread(
              () -> {
                if (isShowing())
                  change(() -> edit.brightness = Math.max(-.35f, Math.min(.35f, stops * .18f)));
              });
        });
  }

  void faceFocus() {
    if (a.ai.busy || !a.ai.require("faces")) return;
    currentFrame(
        image -> {
          a.runOnUiThread(
              () -> {
                if (!isShowing()) return;
                aiRunning = true;
                buttons();
                a.ai.task(
                    "מאתר פנים למיקוד סטטי",
                    () -> {
                      try (AiEngine engine = new AiEngine(a)) {
                        List<RectF> faces = engine.faceBoxes(image);
                        if (faces.isEmpty())
                          throw new IOException("לא נמצאו פנים ברורות בפריים הזה");
                        RectF largest =
                            faces.stream()
                                .max(Comparator.comparingDouble(r -> r.width() * r.height()))
                                .get();
                        float x = largest.centerX() / image.getWidth(),
                            y = largest.centerY() / image.getHeight();
                        if (edit.flipX) x = 1 - x;
                        if (edit.flipY) y = 1 - y;
                        for (int n = 0; n < edit.rotation / 90; n++) {
                          float old = x;
                          x = y;
                          y = 1 - old;
                        }
                        final float cx = x, cy = y;
                        a.ai.ui(
                            () -> {
                              aiRunning = false;
                              if (isShowing()) {
                                change(
                                    () -> {
                                      edit.aspect = 2;
                                      edit.centerX = cx;
                                      edit.centerY = cy;
                                    });
                                note.setText("מיקוד סטטי לפי הפריים · בדוק את כל הקטע; אין מעקב");
                              }
                            });
                      } catch (Exception e) {
                        a.ai.ui(
                            () -> {
                              aiRunning = false;
                              buttons();
                              if (isShowing()) a.message("מיקוד פנים", e.getMessage());
                            });
                      }
                    });
              });
        });
  }

  void export(boolean audioOnly) {
    if (!ready || aiRunning) return;
    if (a.transformer != null) {
      a.message("יצוא פעיל", "המתן לסיום היצוא הנוכחי או בטל אותו בהגדרות");
      return;
    }
    VideoRecipe snapshot = edit.copy();
    try {
      Composition composition = snapshot.composition(audioOnly);
      File file =
          new File(
              a.getCacheDir(),
              "Lume-" + System.currentTimeMillis() + (audioOnly ? ".m4a" : ".mp4"));
      a.exportFile = file;
      DefaultEncoderFactory.Builder encoders =
          new DefaultEncoderFactory.Builder(a).setEnableFallback(true);
      if (snapshot.bitrate > 0)
        encoders.setRequestedVideoEncoderSettings(
            new VideoEncoderSettings.Builder().setBitrate(snapshot.bitrate).build());
      encoders.setRequestedAudioEncoderSettings(
          new AudioEncoderSettings.Builder().setBitrate(192000).build());
      a.transformer =
          new Transformer.Builder(a)
              .setEncoderFactory(encoders.build())
              .setVideoMimeType(snapshot.codec == 0 ? MimeTypes.VIDEO_H264 : MimeTypes.VIDEO_H265)
              .setAudioMimeType(MimeTypes.AUDIO_AAC)
              .addListener(
                  new Transformer.Listener() {
                    @Override
                    public void onCompleted(Composition c, ExportResult result) {
                      a.transformer = null;
                      a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                      a.work(
                          () -> {
                            try {
                              EditorFiles.saveFile(
                                  a,
                                  file,
                                  audioOnly ? "audio/mp4" : "video/mp4",
                                  audioOnly ? "m4a" : "mp4");
                            } finally {
                              file.delete();
                            }
                            a.runOnUiThread(
                                () -> {
                                  a.load();
                                  Toast.makeText(
                                          a,
                                          audioOnly
                                              ? "שמע נשמר ב־Music/Lume"
                                              : "סרטון נשמר · "
                                                  + result.width
                                                  + "×"
                                                  + result.height,
                                          Toast.LENGTH_LONG)
                                      .show();
                                });
                          });
                    }

                    @Override
                    public void onError(Composition c, ExportResult r, ExportException e) {
                      a.transformer = null;
                      file.delete();
                      a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                      a.message(
                          "היצוא לא הושלם",
                          e.getMessage() + "\nנסה H.264, קצב פריימים מקורי או 1080p.");
                    }
                  })
              .build();
      a.transformer.start(composition, file.getAbsolutePath());
      a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      dismiss();
      Handler progress = new Handler(Looper.getMainLooper());
      progress.post(
          new Runnable() {
            public void run() {
              if (a.transformer == null || a.destroyed) return;
              ProgressHolder p = new ProgressHolder();
              if (a.transformer.getProgress(p) == Transformer.PROGRESS_STATE_AVAILABLE)
                a.status.setText(
                    "מייצא " + (audioOnly ? "שמע" : "סרטון") + " · " + p.progress + "%");
              progress.postDelayed(this, 500);
            }
          });
    } catch (Exception e) {
      a.message("הגדרות יצוא", e.getMessage());
    }
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
    handler.removeCallbacksAndMessages(null);
    if (preview != null) preview.stopPlayback();
    if (aiRunning) a.ai.cancel.set(true);
    super.dismiss();
  }
}
