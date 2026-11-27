package il.luma.gallery;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** UI and background work for downloadable offline models and derived gallery metadata. */
final class AiController {
  final ModernActivity a;
  final AiIndex index;
  final ConcurrentHashMap<String, AiIndex.Entry> cached = new ConcurrentHashMap<>();
  final ExecutorService worker = Executors.newSingleThreadExecutor();
  final AtomicBoolean cancel = new AtomicBoolean();
  volatile boolean busy = false, closed = false;
  volatile String operation = "", activeModel = "";
  volatile int percent = 0;
  Dialog manager;
  TextView progressLabel;

  AiController(ModernActivity activity) {
    a = activity;
    index = new AiIndex(a.getApplicationContext());
    worker.execute(
        () -> {
          if (closed) return;
          try {
            cached.putAll(index.all());
            ui(
                () -> {
                  if (a.tab.equals("albums")) a.render();
                });
          } catch (Exception error) {
            if (!closed) ui(() -> a.message("אינדקס מקומי", error.getMessage()));
          }
        });
  }

  void ui(Runnable r) {
    a.runOnUiThread(
        () -> {
          if (!closed && !a.destroyed) r.run();
        });
  }

  boolean search(MainActivity.Media m, String needle) {
    AiIndex.Entry entry = cached.get(m.uri.toString());
    return entry != null
        && entry.fingerprint.equals(AiIndex.fingerprint(m))
        && entry.tags.toLowerCase(Locale.ROOT).contains(needle);
  }

  void task(String description, MainActivity.Work work) {
    if (busy) {
      a.message("פעולה פעילה", operation + ". אפשר לבטל דרך מסך AI מקומי.");
      return;
    }
    busy = true;
    operation = description;
    a.status.setText(description + "…");
    cancel.set(false);
    worker.execute(
        () -> {
          try {
            work.run();
          } catch (Exception e) {
            ui(() -> a.message(description, e.getMessage()));
          } finally {
            busy = false;
            operation = "";
            activeModel = "";
            ui(
                () -> {
                  a.status.setText(a.media.size() + " פריטים · במכשיר");
                  if (manager != null && manager.isShowing()) renderManager();
                  if (a.automaticPeople != null) a.automaticPeople.maybe();
                });
          }
        });
  }

  String installedSummary() {
    int installed = 0;
    for (String id : LocalModels.IDS) if (LocalModels.installed(a, id)) installed++;
    return installed == LocalModels.IDS.length
        ? "Lume AI מוכן במכשיר"
        : installed == 0 ? "Lume AI · התקנה אחת לכלים החכמים" : "Lume AI · יכולות בסיסיות מותקנות";
  }

  void showManager() {
    if (manager == null) {
      manager =
          new Dialog(
              a,
              a.prefs.getBoolean("light", true)
                  ? android.R.style.Theme_Material_Light_NoActionBar
                  : android.R.style.Theme_Material_NoActionBar);
    }
    renderManager();
    manager.getWindow().setWindowAnimations(a.motion() ? R.style.GallerySheetMotion : 0);
    AppPrivacy.apply(manager.getWindow(), a);
    manager.show();
    manager.getWindow().setLayout(-1, -1);
  }

  void renderManager() {
    LinearLayout root = a.column();
    root.setBackgroundColor(a.bg);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          var bars = insets.getInsets(WindowInsets.Type.systemBars());
          root.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    LinearLayout top = a.row();
    top.setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(8));
    top.addView(a.iconButton("back", "חזרה", () -> manager.dismiss()));
    top.addView(a.label("Lume AI", 27, a.ink, true), new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(top);
    ScrollView scroll = new ScrollView(a);
    scroll.setVerticalScrollBarEnabled(false);
    LinearLayout content = a.column();
    content.setPadding(0, a.dp(8), 0, a.dp(24));
    scroll.addView(content);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    content.addView(a.text("מנוע אחד לשימוש. הכל במכשיר.", 22));
    long total = 0;
    try {
      for (String id : LocalModels.IDS)
        total += LocalModels.catalog(a).getJSONObject(id).getLong("size");
    } catch (Exception ignored) {
    }
    content.addView(
        a.text(
            installedSummary()
                + "\nחבילת רכיבי ראייה במשקל "
                + android.text.format.Formatter.formatFileSize(a, total)
                + ". ההתקנה משלימה רק רכיבים שחסרים. מתחת לממשק המאוחד פועלים מודלים ייעודיים"
                + " לתפקידים שונים.",
            13));
    progressLabel =
        a.text(
            busy
                ? operation + (activeModel.isEmpty() ? "" : " · " + percent + "%")
                : "העיבוד מקומי · אין העלאת תמונות",
            14);
    progressLabel.setTextColor(a.accent);
    content.addView(progressLabel);
    a.action(content, "התקן או השלם את Lume AI", () -> download(Arrays.asList(LocalModels.IDS)));
    String[][] features = {
      {"classify", "תגיות וחיפוש בתוכן"},
      {"similar", "תמונות דומות"},
      {"repair", "הסרת פריטים והשלמת שוליים"},
      {"objects", "איתור פריטים לסימון"},
      {"facegroup", "קבוצות אנשים מקומיות"},
      {"enhance", "הגדלה חכמה פי 3"},
      {"portrait", "דיוקן ורקע"},
      {"faces", "איתור וטשטוש פנים"}
    };
    for (String[] feature : features)
      content.addView(
          a.text(
              (LocalModels.installed(a, feature[0]) ? "מוכן · " : "נדרשת התקנה · ") + feature[1],
              14));
    a.action(
        content,
        busy ? "עצור פעולה פעילה" : "סרוק תמונות לחיפוש חכם",
        () -> {
          if (busy) {
            cancel.set(true);
            progressLabel.setText("עוצר לאחר הפעולה הנוכחית…");
          } else startIndex();
        });
    a.action(content, "אנשים · קיבוץ ותיקון קבוצות", () -> a.peoplePlaces.open("person"));
    a.action(content, "מקומות מתוך התמונות", () -> a.peoplePlaces.open("place"));
    a.action(content, "הגדרות סריקה וחיפוש", this::settings);
    Switch wifi = new Switch(a);
    wifi.setText("הורדת החבילה ב־Wi-Fi בלבד");
    wifi.setTextColor(a.ink);
    wifi.setPadding(a.dp(24), a.dp(14), a.dp(24), a.dp(14));
    wifi.setChecked(a.prefs.getBoolean("aiWifi", true));
    wifi.setOnCheckedChangeListener((v, on) -> a.prefs.edit().putBoolean("aiWifi", on).apply());
    content.addView(wifi);
    a.action(
        content,
        "מחק את חבילת Lume AI מהמכשיר",
        () -> {
          if (busy) {
            a.message("פעולה פעילה", "עצור את הפעולה לפני מחיקת החבילה");
            return;
          }
          new PrivacyDialogBuilder(a)
              .setMessage("למחוק את רכיבי AI? התמונות והאלבומים יישארו.")
              .setPositiveButton(
                  "מחק חבילה",
                  (d, n) -> {
                    for (String id : LocalModels.IDS) LocalModels.file(a, id).delete();
                    renderManager();
                  })
              .setNegativeButton("ביטול", null)
              .show();
        });
    a.action(
        content,
        "נקה תגיות ואינדקס דמיון",
        () -> {
          if (busy) return;
          new PrivacyDialogBuilder(a)
              .setMessage(
                  "לנקות את אינדקס החיפוש בלבד? התמונות, קבוצות אנשים ומקומות והמודלים יישארו.")
              .setPositiveButton(
                  "נקה",
                  (d, n) ->
                      task(
                          "ניקוי אינדקס",
                          () -> {
                            index.clear();
                            cached.clear();
                          }))
              .setNegativeButton("ביטול", null)
              .show();
        });
    content.addView(
        a.text(
            "תיקון אזורים והרחבה מייצרים תוכן משוער ועלולים לטעות. ההשתקפות שאבד מאחוריה מידע אינה"
                + " משוחזרת בוודאות. קיבוץ פנים אינו מזהה שמות; שמות ניתנים על ידך. תמיד מוצגת"
                + " תצוגה מקדימה ונשמר עותק.",
            12));
    manager.setContentView(root);
  }

  void download(java.util.List<String> ids) {
    if (a.prefs.getBoolean("aiWifi", true)) {
      ConnectivityManager cm =
          (ConnectivityManager) a.getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkCapabilities network = cm.getNetworkCapabilities(cm.getActiveNetwork());
      if (network == null || !network.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
        a.message("נדרש Wi-Fi", "התחבר ל־Wi-Fi או כבה את הגבלת ההורדה במסך הזה.");
        return;
      }
    }
    task(
        "מוריד מודלים",
        () -> {
          for (String id : ids) {
            if (cancel.get()) break;
            if (LocalModels.installed(a, id)) continue;
            activeModel = id;
            LocalModels.download(
                a,
                id,
                cancel,
                (bytes, total) -> {
                  percent = (int) (100 * bytes / total);
                  ui(
                      () -> {
                        if (progressLabel != null)
                          progressLabel.setText("מתקין Lume AI · " + percent + "% מהרכיב הנוכחי");
                      });
                });
          }
          activeModel = "";
          ui(
              () -> {
                a.status.setText(cancel.get() ? "ההתקנה נעצרה" : "המודלים הותקנו במכשיר");
                AutoOrganizeJob.schedule(a);
                a.render();
              });
        });
    if (manager != null && manager.isShowing()) renderManager();
  }

  void settings() {
    String[] items = {
      "תמונות בכל סריקה: " + a.prefs.getInt("aiBatch", 200),
      "סף ביטחון לתגיות: " + Math.round(a.prefs.getFloat("aiConfidence", .2f) * 100) + "%",
      "סף דמיון חזותי: " + Math.round(a.prefs.getFloat("aiSimilarity", .75f) * 100) + "%"
    };
    new PrivacyDialogBuilder(a)
        .setTitle("הגדרות AI")
        .setItems(
            items,
            (d, n) -> {
              if (n == 0)
                new PrivacyDialogBuilder(a)
                    .setTitle("גודל אצווה · אפשר להמשיך מסריקה לסריקה")
                    .setItems(
                        new String[] {"50 · חיסכון בסוללה", "200 · מאוזן", "1000 · ספרייה גדולה"},
                        (x, p) ->
                            a.prefs
                                .edit()
                                .putInt("aiBatch", p == 0 ? 50 : p == 1 ? 200 : 1000)
                                .apply())
                    .show();
              else if (n == 1)
                new PrivacyDialogBuilder(a)
                    .setTitle("סף ביטחון לתגיות")
                    .setItems(
                        new String[] {"15% · יותר תגיות", "30% · מאוזן", "50% · מחמיר"},
                        (x, p) ->
                            a.prefs
                                .edit()
                                .putFloat("aiConfidence", p == 0 ? .15f : p == 1 ? .30f : .50f)
                                .apply())
                    .show();
              else
                new PrivacyDialogBuilder(a)
                    .setTitle("סף דמיון")
                    .setItems(
                        new String[] {"65% · רחב", "75% · מאוזן", "85% · מחמיר"},
                        (x, p) ->
                            a.prefs
                                .edit()
                                .putFloat("aiSimilarity", p == 0 ? .65f : p == 1 ? .75f : .85f)
                                .apply())
                    .show();
            })
        .show();
  }

  void startIndex() {
    boolean classify = LocalModels.installed(a, "classify"),
        similar = LocalModels.installed(a, "similar"),
        faces = LocalModels.installed(a, "faces");
    if (!classify && !similar && !faces) {
      a.message("נדרש מודל", "התקן מודל לזיהוי תוכן, דמיון או פנים לפני הסריקה.");
      return;
    }
    ArrayList<MainActivity.Media> snapshot = new ArrayList<>(a.media);
    String flags =
        (classify ? "classify" + a.prefs.getFloat("aiConfidence", .2f) : "")
            + (similar ? "similar" : "")
            + (faces ? "faces" : "");
    int limit = a.prefs.getInt("aiBatch", 200);
    task(
        "מאנדקס תמונות",
        () -> {
          int done = 0, failed = 0;
          try (AiEngine engine = new AiEngine(a)) {
            for (MainActivity.Media m : snapshot) {
              if (cancel.get() || done + failed >= limit) break;
              if (m.video()) continue;
              AiIndex.Entry existing = cached.get(m.uri.toString());
              if (existing != null
                  && existing.fingerprint.equals(AiIndex.fingerprint(m))
                  && existing.models.equals(flags)) continue;
              try {
                Bitmap bitmap = a.decode(m.uri, 768);
                String tags = classify ? engine.labels(bitmap) : "";
                float[] vector = similar ? engine.vector(bitmap) : null;
                if (faces) {
                  int count = engine.faceBoxes(bitmap).size();
                  if (count > 0)
                    tags += " people person face portrait אדם אנשים פנים דיוקן " + count + " פנים";
                }
                AiIndex.Entry entry =
                    new AiIndex.Entry(
                        m.uri.toString(), AiIndex.fingerprint(m), tags, flags, vector);
                index.put(entry);
                cached.put(entry.uri, entry);
                if (!bitmap.isRecycled()) bitmap.recycle();
                done++;
                if (done % 5 == 0) {
                  final int count = done;
                  ui(
                      () -> {
                        a.status.setText("AI מקומי · " + count + " תמונות נסרקו");
                        if (progressLabel != null)
                          progressLabel.setText(count + " תמונות נסרקו · אפשר לעצור");
                      });
                }
              } catch (Exception e) {
                failed++;
              }
            }
          }
          final int count = done, errors = failed;
          ui(
              () -> {
                a.render();
                a.message(
                    "הסריקה הסתיימה",
                    count
                        + " תמונות נסרקו, "
                        + errors
                        + " לא נבדקו.\n"
                        + "התוצאות נשמרו במכשיר. אפשר להמשיך בסריקה נוספת; תמונות שלא השתנו"
                        + " ידולגו.");
              });
        });
    if (manager != null && manager.isShowing()) renderManager();
  }

  void tools(MainActivity.Media m) {
    if (m.video()) {
      a.message("כלים לתמונות", "כלי העריכה החכמים פועלים על תמונות בלבד");
      return;
    }
    new PrivacyDialogBuilder(a)
        .setTitle("Lume AI · כלים לתמונה")
        .setItems(
            new String[] {
              "זיהוי תוכן ותגיות",
              "מצא תמונות דומות",
              "הפרד אדם לרקע שקוף",
              "טשטש רקע סביב אדם",
              "טשטש פנים שזוהו",
              "הסר פריטים בסימון או באיתור",
              "הרחב שוליים בהשלמה משוערת",
              "הגדלה חכמה פי 3",
              "נקה השתקפות מקומית בסימון",
              "ניהול Lume AI"
            },
            (d, n) -> {
              if (n == 9) showManager();
              else if (n == 1) findSimilar(m);
              else if (n == 0) classify(m);
              else if (n == 5 || n == 8) {
                if (require("repair")) new RepairStudio(a, m).show();
              } else if (n == 6) {
                if (!require("repair")) return;
                new PrivacyDialogBuilder(a)
                    .setTitle("הרחבת שוליים · השלמה משוערת")
                    .setItems(
                        new String[] {"10% מכל צד", "20% מכל צד"},
                        (x, p) ->
                            task(
                                "משלים שוליים במכשיר",
                                () -> {
                                  try (AdvancedEngine engine = new AdvancedEngine(a)) {
                                    Bitmap result =
                                        engine.expand(a.decode(m.uri, 1280), p == 0 ? 10 : 20);
                                    ui(
                                        () ->
                                            preview(result, "תוכן חדש משוער בשוליים · המקור נשמר"));
                                  }
                                }))
                    .show();
              } else if (n == 7) {
                if (!require("enhance")) return;
                task(
                    "מגדיל רזולוציה במכשיר",
                    () -> {
                      try (AdvancedEngine engine = new AdvancedEngine(a)) {
                        Bitmap result = engine.upscale(a.decode(m.uri, 768));
                        ui(() -> preview(result, "הגדלה חכמה פי 3 · בדוק את הפרטים"));
                      }
                    });
              } else transform(m, n);
            })
        .show();
  }

  boolean require(String id) {
    if (LocalModels.installed(a, id)) return true;
    new PrivacyDialogBuilder(a)
        .setTitle("נדרשת התקנת מודל קטן")
        .setMessage("הורד את המודל למסך AI מקומי. לאחר ההתקנה אפשר לעבוד ללא אינטרנט.")
        .setPositiveButton("לניהול מודלים", (d, n) -> showManager())
        .setNegativeButton("ביטול", null)
        .show();
    return false;
  }

  void classify(MainActivity.Media m) {
    if (!require("classify")) return;
    task(
        "מזהה תוכן בתמונה",
        () -> {
          try (AiEngine engine = new AiEngine(a)) {
            Bitmap bitmap = a.decode(m.uri, 768);
            String tags = engine.labels(bitmap);
            ui(
                () ->
                    a.message(
                        "תגיות משוערות",
                        tags.isEmpty()
                            ? "לא זוהה תוכן בביטחון מספיק. אפשר להנמיך את הסף בהגדרות AI."
                            : tags
                                + "\n\n"
                                + "הסיווג הוא הערכה. סרוק את הספרייה כדי להשתמש בתגיות"
                                + " בחיפוש."));
          }
        });
  }

  void findSimilar(MainActivity.Media source) {
    if (!require("similar")) return;
    ArrayList<MainActivity.Media> snapshot = new ArrayList<>(a.media);
    task(
        "מחפש תמונות דומות",
        () -> {
          AiIndex.Entry known = cached.get(source.uri.toString());
          float[] query;
          if (known != null
              && known.fingerprint.equals(AiIndex.fingerprint(source))
              && known.vector != null) query = known.vector;
          else
            try (AiEngine engine = new AiEngine(a)) {
              query = engine.vector(a.decode(source.uri, 768));
            }
          HashMap<String, Double> scores = new HashMap<>();
          float threshold = a.prefs.getFloat("aiSimilarity", .75f);
          for (MainActivity.Media m : snapshot) {
            AiIndex.Entry e = cached.get(m.uri.toString());
            if (e != null && e.fingerprint.equals(AiIndex.fingerprint(m))) {
              double score = AiIndex.cosine(query, e.vector);
              if (score >= threshold) scores.put(m.uri.toString(), score);
            }
          }
          scores.put(source.uri.toString(), 1d);
          ui(
              () -> {
                a.showSimilar(scores);
                a.message(
                    "דמיון חזותי",
                    Math.max(0, scores.size() - 1)
                        + " תמונות דומות נמצאו באינדקס, בנוסף למקור.\n"
                        + "סרוק תמונות נוספות במסך AI כדי להרחיב את החיפוש.");
              });
        });
  }

  void transform(MainActivity.Media m, int which) {
    if (!require(which == 4 ? "faces" : "portrait")) return;
    task(
        "מעבד תמונה במכשיר",
        () -> {
          try (AiEngine engine = new AiEngine(a)) {
            Bitmap source = a.decode(m.uri, 2048);
            Bitmap result =
                which == 4 ? engine.pixelateFaces(source) : engine.portrait(source, which == 2);
            ui(
                () ->
                    preview(
                        result,
                        which == 4
                            ? "טשטוש פנים · בדוק שלא נותרו פנים חשופות"
                            : which == 2 ? "הפרדת אדם לרקע שקוף" : "טשטוש רקע דיוקן"));
          }
        });
  }

  void preview(Bitmap bitmap, String title) {
    LinearLayout content = a.column();
    content.addView(a.text(title, 18));
    ImageView image = new ImageView(a);
    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    image.setImageBitmap(bitmap);
    image.setBackgroundColor(0xff92949c);
    content.addView(image, new LinearLayout.LayoutParams(-1, a.dp(360)));
    content.addView(
        a.text(
            "תצוגה מקדימה · המקור לא ישתנה · PNG " + bitmap.getWidth() + " × " + bitmap.getHeight(),
            12));
    new PrivacyDialogBuilder(a)
        .setView(content)
        .setNegativeButton("ביטול", null)
        .setPositiveButton(
            "שמור עותק",
            (d, n) ->
                a.work(
                    () -> {
                      Uri out = a.createOutput(false, "image/png", "png");
                      try {
                        try (OutputStream stream = a.getContentResolver().openOutputStream(out)) {
                          if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                            throw new IOException("השמירה נכשלה");
                        }
                        a.publish(out);
                      } catch (Exception e) {
                        a.getContentResolver().delete(out, null, null);
                        throw e;
                      }
                      ui(
                          () -> {
                            a.load();
                            Toast.makeText(a, "העותק נשמר באלבום Lume", Toast.LENGTH_SHORT).show();
                          });
                    }))
        .show();
  }

  void close() {
    if (closed) return;
    closed = true;
    cancel.set(true);
    if (manager != null) manager.dismiss();
    worker.execute(index::close);
    worker.shutdown();
  }
}
