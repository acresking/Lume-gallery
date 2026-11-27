package il.luma.gallery;

import android.app.Dialog;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Local, read-only metadata surface. Copies require an explicit tap. */
final class MediaInfoSheet extends Dialog {
  final ModernActivity host;
  final MainActivity.Media media;
  private LinearLayout entries, tabs;
  private ScrollView scroll;
  private final LinkedHashMap<String, String> file = new LinkedHashMap<>();
  private final LinkedHashMap<String, String> capture = new LinkedHashMap<>();
  private final LinkedHashMap<String, String> location = new LinkedHashMap<>();
  private String selected = "הכול";
  private boolean loading = true, expanded;
  private TextView sizeButton;
  private double[] gps;

  MediaInfoSheet(ModernActivity activity, MainActivity.Media item) {
    super(activity);
    host = activity;
    media = item;
  }

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout shell = host.column();
    shell.setPadding(host.dp(18), host.dp(4), host.dp(18), host.dp(12));
    shell.setBackground(host.rounded(host.bg, 30));
    sizeButton = host.label("━━", 20, host.accent, true);
    sizeButton.setGravity(Gravity.CENTER);
    sizeButton.setMinimumHeight(host.dp(48));
    sizeButton.setContentDescription("הרחבת חלונית הפרטים");
    sizeButton.setOnClickListener(v -> resize(!expanded));
    sizeButton.setOnTouchListener(
        new View.OnTouchListener() {
          float y;

          public boolean onTouch(View v, MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) y = e.getRawY();
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
              float delta = e.getRawY() - y;
              if (Math.abs(delta) > host.dp(48)) {
                if (delta < 0) resize(true);
                else if (expanded) resize(false);
                else dismiss();
                return true;
              }
              v.performClick();
              return true;
            }
            return true;
          }
        });
    shell.addView(sizeButton);
    LinearLayout heading = host.row();
    heading.addView(
        host.label("פרטי המדיה", 24, host.ink, true), new LinearLayout.LayoutParams(0, -2, 1));
    heading.addView(host.iconButton("close", "סגירת פרטי מדיה", this::dismiss));
    shell.addView(heading);
    LinearLayout summary = host.row();
    summary.setPadding(0, host.dp(12), 0, host.dp(12));
    ImageView thumbnail = new ImageView(host);
    thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
    thumbnail.setBackground(host.rounded(host.card, 16));
    thumbnail.setClipToOutline(true);
    summary.addView(thumbnail, new LinearLayout.LayoutParams(host.dp(72), host.dp(72)));
    host.thumbnail(thumbnail, media, 180);
    LinearLayout words = host.column();
    TextView name = host.label(media.name, 16, host.ink, true);
    name.setMaxLines(2);
    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
    words.addView(name);
    TextView subtitle =
        host.label(
            (media.video() ? "סרטון" : "תמונה")
                + " · "
                + android.text.format.Formatter.formatFileSize(host, media.size),
            13,
            host.accent,
            false);
    subtitle.setPadding(0, host.dp(6), 0, 0);
    words.addView(subtitle);
    LinearLayout.LayoutParams wordsLp = new LinearLayout.LayoutParams(0, -2, 1);
    wordsLp.setMarginStart(host.dp(14));
    summary.addView(words, wordsLp);
    shell.addView(summary);
    HorizontalScrollView tabScroll = new HorizontalScrollView(host);
    tabScroll.setHorizontalScrollBarEnabled(false);
    tabs = host.row();
    tabScroll.addView(tabs);
    shell.addView(tabScroll);
    scroll = new ScrollView(host);
    scroll.setFillViewport(false);
    entries = host.column();
    entries.setPadding(0, host.dp(12), 0, host.dp(24));
    scroll.addView(entries);
    shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    TextView footer = host.label("נתוני המקור · קריאה מקומית בלבד", 12, host.accent, false);
    footer.setPadding(host.dp(8), host.dp(10), host.dp(8), host.dp(8));
    shell.addView(footer);
    file.put("שם", media.name);
    file.put(
        "תאריך בספרייה",
        new SimpleDateFormat("EEEE, d MMMM yyyy · HH:mm", new Locale("he"))
            .format(new Date(media.date)));
    file.put("סוג", media.mime);
    file.put("גודל", android.text.format.Formatter.formatFileSize(host, media.size));
    if (media.width > 0 && media.height > 0) file.put("מידות", media.width + " × " + media.height);
    file.put("תיקייה", media.path == null ? "לא ידועה" : media.path);
    setContentView(shell);
    draw();
    host.io.execute(
        () -> {
          Map<String, String> data = MediaDetails.read(host, media);
          host.runOnUiThread(
              () -> {
                if (!isShowing() || host.destroyed) return;
                for (var e : data.entrySet()) {
                  if (e.getKey().equals("מיקום GPS")) {
                    try {
                      String[] parts = e.getValue().split(",");
                      double lat = Double.parseDouble(parts[0].trim()),
                          lon = Double.parseDouble(parts[1].trim());
                      if (LocationNames.valid(lat, lon)) gps = new double[] {lat, lon};
                    } catch (Exception ignored) {
                    }
                    continue;
                  }
                  if (e.getKey().startsWith("מיקום") || e.getKey().equals("גובה"))
                    location.put(e.getKey(), e.getValue());
                  else capture.put(e.getKey(), e.getValue());
                }
                loading = false;
                draw();
              });
        });
  }

  @Override
  public void show() {
    AppPrivacy.apply(getWindow(), host);
    getWindow().setWindowAnimations(host.motion() ? R.style.GallerySheetMotion : 0);
    super.show();
    getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    getWindow().setGravity(Gravity.BOTTOM);
    resize(false);
  }

  void resize(boolean full) {
    expanded = full;
    sizeButton.setContentDescription(full ? "הקטנת חלונית הפרטים" : "הרחבת חלונית הפרטים");
    int height =
        (int) (host.getResources().getDisplayMetrics().heightPixels * (full ? .94f : .78f));
    getWindow().setLayout(-1, height);
  }

  private void draw() {
    tabs.removeAllViews();
    for (String text : new String[] {"הכול", "צילום", "מיקום", "קובץ"}) {
      TextView chip = host.galleryChip(text, selected.equals(text));
      chip.setOnClickListener(
          v -> {
            selected = text;
            scroll.scrollTo(0, 0);
            draw();
          });
      tabs.addView(chip);
    }
    entries.removeAllViews();
    if (selected.equals("הכול") || selected.equals("קובץ")) group("מידע על הקובץ", file);
    if (selected.equals("הכול") || selected.equals("צילום")) {
      group(media.video() ? "וידאו ושמע" : "מצלמה וחשיפה", capture);
      if (loading) note("קורא נתוני צילום מהמכשיר…");
      else if (capture.isEmpty()) note("לא נמצאו נתוני צילום בקובץ הזה.");
    }
    if (selected.equals("הכול") || selected.equals("מיקום")) {
      group("מיקום בקובץ", location);
      if (gps != null && host.prefs.getBoolean("detailsLocation", true)) {
        if (location.isEmpty())
          note("שם העיר והמדינה אינו זמין כעת. אפשר לפתוח את המיקום השמור במפה.");
        LinearLayout mapCard = host.column();
        mapCard.setPadding(host.dp(16), host.dp(14), host.dp(16), host.dp(14));
        mapCard.setBackground(host.rounded(host.card, 20));
        LinearLayout mapTitle = host.row();
        mapTitle.addView(host.icon("place", host.accent, 24));
        TextView mapLabel = host.label("המיקום בתמונה", 17, host.ink, true);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, -2, 1);
        mlp.setMarginStart(host.dp(12));
        mapTitle.addView(mapLabel, mlp);
        mapCard.addView(mapTitle);
        TextView mapHint =
            host.label("גרירה, זום ותמונות מהאוסף — בתוך Lume", 13, host.muted, false);
        mapHint.setPadding(0, host.dp(8), 0, host.dp(8));
        mapCard.addView(mapHint);
        mapCard.addView(host.button("הצג על מפה", this::openMap));
        entries.addView(mapCard, 0);
        note(
            "שמות מקומות עשויים להשתמש בשירות המיקום של Android. המפה מוצגת בתוך Lume; ספק"
                + " האריחים מקבל את האזור הגלוי וכתובת IP, ללא תמונות.");
      }
      if (!host.prefs.getBoolean("detailsLocation", true)) note("הצגת המיקום כבויה בהגדרות.");
      else if (!loading && location.isEmpty() && gps == null)
        note(
            "לא נמצא מיקום קריא בקובץ. Android עשוי להסתיר GPS ללא הרשאת מיקום בתמונות; אפשר לנהל"
                + " אותה בהגדרות הגלריה.");
    }
    note("אפשר להקיש על כפתור ההעתקה ליד כל נתון. שיתוף המקור עשוי לכלול פרטי צילום ומיקום.");
  }

  private void group(String heading, Map<String, String> data) {
    if (data.isEmpty()) return;
    TextView title = host.label(heading, 15, host.accent, true);
    title.setPadding(host.dp(8), host.dp(14), host.dp(8), host.dp(10));
    entries.addView(title);
    for (var e : data.entrySet()) {
      if (e.getValue() == null || e.getValue().isBlank()) continue;
      LinearLayout row = host.row();
      row.setPadding(host.dp(14), host.dp(12), host.dp(8), host.dp(12));
      row.setBackground(host.rounded(host.card, 20));
      LinearLayout text = host.column();
      text.addView(host.label(e.getKey(), 12, host.accent, false));
      TextView value = host.label(e.getValue(), 15, host.ink, false);
      value.setPadding(0, host.dp(5), 0, 0);
      value.setTextIsSelectable(true);
      text.addView(value);
      row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
      row.addView(
          host.iconButton("copy", "העתקת " + e.getKey(), () -> copy(e.getKey(), e.getValue())));
      LinearLayout.LayoutParams margin = new LinearLayout.LayoutParams(-1, -2);
      margin.bottomMargin = host.dp(6);
      entries.addView(row, margin);
    }
  }

  private void note(String text) {
    TextView note = host.label(text, 13, host.ink, false);
    note.setPadding(host.dp(8), host.dp(12), host.dp(8), host.dp(8));
    entries.addView(note);
  }

  void openMap() {
    if (gps == null || !host.prefs.getBoolean("detailsLocation", true)) return;
    dismiss();
    host.openMap(gps[0], gps[1]);
    host.mapSheet.select(
        media, location.isEmpty() ? "המיקום בתמונה" : location.values().iterator().next());
  }

  void copy(String name, String value) {
    ClipboardManager clipboard =
        (ClipboardManager) host.getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData clip = ClipData.newPlainText(name, value);
    PersistableBundle extras = new PersistableBundle();
    extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
    clip.getDescription().setExtras(extras);
    clipboard.setPrimaryClip(clip);
    Toast.makeText(host, "הועתק", Toast.LENGTH_SHORT).show();
  }
}
