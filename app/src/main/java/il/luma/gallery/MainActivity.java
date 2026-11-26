package il.luma.gallery;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.text.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.effect.*;
import androidx.media3.transformer.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class MainActivity extends Activity {
  final ExecutorService io = Executors.newSingleThreadExecutor(),
      thumbs = Executors.newFixedThreadPool(3);
  final LruCache<String, Bitmap> cache =
      new LruCache<String, Bitmap>(24 * 1024) {
        protected int sizeOf(String k, Bitmap b) {
          return b.getAllocationByteCount() / 1024;
        }
      };
  LinearLayout root, body, nav;
  TextView status;
  android.content.SharedPreferences prefs;
  String tab = "media", search = "", album = "";
  int filter = 0;
  boolean sortOld = false, destroyed = false;
  ArrayList<Media> media = new ArrayList<>();
  Set<String> favorites;
  Transformer transformer;
  File exportFile;
  int bg = Color.rgb(16, 25, 22),
      card = Color.rgb(29, 42, 35),
      ink = Color.rgb(234, 245, 236),
      accent = Color.rgb(166, 233, 199);

  static class Media {
    Uri uri;
    String name, path, mime;
    long date, size, duration, modified;
    int width, height;

    boolean video() {
      return mime.startsWith("video/");
    }
  }

  interface Work {
    void run() throws Exception;
  }

  void work(Work w) {
    io.execute(
        () -> {
          try {
            w.run();
          } catch (Exception e) {
            runOnUiThread(() -> message("לא ניתן להשלים", e.getMessage()));
          }
        });
  }

  int dp(float v) {
    return (int) (v * getResources().getDisplayMetrics().density);
  }

  @Override
  public void onCreate(Bundle b) {
    if (getSharedPreferences("luma", 0).getBoolean("light", false))
      setTheme(R.style.AppTheme_Light);
    AppPrivacy.apply(getWindow(), this);
    super.onCreate(b);
    prefs = getSharedPreferences("luma", 0);
    favorites = new HashSet<>(prefs.getStringSet("favorites", new HashSet<>()));
    sortOld = prefs.getBoolean("old", false);
    applyColors();
    shell();
    CleanupJob.schedule(this);
    load();
  }

  void applyColors() {
    if (prefs.getBoolean("light", false)) {
      bg = 0xfff3f6f1;
      card = 0xffe2eae1;
      ink = 0xff172d22;
      accent = 0xff246f50;
    }
    getWindow().setStatusBarColor(bg);
    getWindow().setNavigationBarColor(bg);
  }

  TextView text(String s, int size) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(ink);
    t.setPadding(dp(12), dp(8), dp(12), dp(8));
    return t;
  }

  LinearLayout column() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    return l;
  }

  Button button(String s, Runnable r) {
    Button b = new Button(this);
    b.setText(s);
    b.setTextColor(ink);
    b.setAllCaps(false);
    b.setTextSize(14);
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    b.setMinHeight(dp(44));
    GradientDrawable shape = new GradientDrawable();
    shape.setColor(card);
    shape.setCornerRadius(dp(14));
    shape.setStroke(dp(1), 0x33889e90);
    b.setBackground(
        new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x4488bb99), shape, null));
    b.setPadding(dp(10), dp(6), dp(10), dp(6));
    b.setOnClickListener(v -> r.run());
    return b;
  }

  void action(LinearLayout l, String s, Runnable r) {
    l.addView(button(s, r));
  }

  void message(String title, String s) {
    if (!isFinishing() && !destroyed)
      new PrivacyDialogBuilder(this)
          .setTitle(title)
          .setMessage(s == null ? "שגיאה לא ידועה" : s)
          .setPositiveButton("סגירה", null)
          .show();
  }

  void shell() {
    root = column();
    root.setBackgroundColor(bg);
    root.setPadding(dp(12), dp(28), dp(12), dp(16));
    root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    setContentView(root);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
          root.setPadding(
              dp(12) + bars.left, dp(8) + bars.top, dp(12) + bars.right, dp(8) + bars.bottom);
          return insets;
        });
    root.requestApplyInsets();
    LinearLayout head = new LinearLayout(this);
    head.setGravity(Gravity.CENTER_VERTICAL);
    TextView logo = text("Lume", 32);
    logo.setTypeface(null, Typeface.BOLD);
    head.addView(logo, new LinearLayout.LayoutParams(0, -2, 1));
    head.addView(text("המרחב שלך לזיכרונות", 13));
    root.addView(head);
    status = text("הגלריה שלך, על המכשיר שלך", 13);
    root.addView(status);
    body = column();
    root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
    nav = new LinearLayout(this);
    root.addView(nav);
    for (String[] n :
        new String[][] {
          {"media", "גלריה"}, {"albums", "אלבומים"}, {"rules", "ניקוי"}, {"settings", "הגדרות"}
        }) {
      Button b =
          button(
              n[1],
              () -> {
                tab = n[0];
                album = "";
                render();
              });
      nav.addView(b, new LinearLayout.LayoutParams(0, dp(54), 1));
    }
    render();
  }

  void load() {
    status.setText("טוען מדיה…");
    io.execute(
        () -> {
          ArrayList<Media> result = new ArrayList<>();
          String error = "";
          try {
            Uri uri = MediaStore.Files.getContentUri("external");
            String[] cols = {
              "_id",
              "_display_name",
              "relative_path",
              "mime_type",
              "date_added",
              "_size",
              "media_type",
              "datetaken",
              "duration",
              "width",
              "height",
              "date_modified"
            };
            try (Cursor q =
                getContentResolver()
                    .query(
                        uri,
                        cols,
                        "media_type IN (1,3) AND is_trashed=0",
                        null,
                        "date_added DESC")) {
              if (q != null)
                while (q.moveToNext()) {
                  Media m = new Media();
                  m.uri =
                      ContentUris.withAppendedId(
                          q.getInt(6) == 3
                              ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                              : MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                          q.getLong(0));
                  m.name = q.getString(1);
                  m.path = q.getString(2);
                  m.mime = q.getString(3);
                  m.date = q.getLong(4) * 1000L;
                  m.size = q.getLong(5);
                  if (q.getLong(7) > 0) m.date = q.getLong(7);
                  m.duration = q.getLong(8);
                  m.width = q.getInt(9);
                  m.height = q.getInt(10);
                  m.modified = q.getLong(11);
                  if (m.mime != null) result.add(m);
                }
            }
          } catch (Exception e) {
            error = e.getMessage();
          }
          String err = error;
          runOnUiThread(
              () -> {
                media = result;
                status.setText(
                    err.isEmpty()
                        ? result.size() + " תמונות וסרטונים · נשמרים במכשיר"
                        : "נדרשת הרשאת גישה למדיה");
                render();
              });
        });
  }

  void permission() {
    ArrayList<String> p = new ArrayList<>();
    if (Build.VERSION.SDK_INT >= 33) {
      p.add(Manifest.permission.READ_MEDIA_IMAGES);
      p.add(Manifest.permission.READ_MEDIA_VIDEO);
      if (Build.VERSION.SDK_INT >= 34) p.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
    } else p.add(Manifest.permission.READ_EXTERNAL_STORAGE);
    requestPermissions(p.toArray(new String[0]), 11);
  }

  @Override
  public void onRequestPermissionsResult(int r, String[] p, int[] g) {
    super.onRequestPermissionsResult(r, p, g);
    load();
  }

  void render() {
    if (body == null) return;
    body.removeAllViews();
    if (tab.equals("rules")) {
      rules();
      return;
    }
    if (tab.equals("settings")) {
      settings();
      return;
    }
    if (tab.equals("albums")) {
      albums();
      return;
    }
    gallery();
  }

  LinearLayout scroll() {
    ScrollView s = new ScrollView(this);
    LinearLayout c = column();
    s.addView(c);
    body.addView(s, new LinearLayout.LayoutParams(-1, -1));
    return c;
  }

  void gallery() {
    EditText query = new EditText(this);
    query.setSingleLine();
    query.setTextColor(ink);
    query.setHintTextColor(0xff8b9e91);
    query.setHint("חיפוש בשם או בתיקייה");
    query.setText(search);
    body.addView(query);
    LinearLayout chips = new LinearLayout(this);
    String[] names = {"הכול", "תמונות", "סרטונים", "מועדפים"};
    for (int i = 0; i < names.length; i++) {
      final int f = i;
      Button b =
          button(
              names[i],
              () -> {
                filter = f;
                render();
              });
      chips.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    }
    body.addView(chips);
    GridView grid = new GridView(this);
    grid.setNumColumns(prefs.getInt("columns", 3));
    grid.setHorizontalSpacing(dp(4));
    grid.setVerticalSpacing(dp(4));
    body.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
    ArrayList<Media> shown = new ArrayList<>();
    for (Media m : media) {
      if (!album.isEmpty() && !album.equals(m.path)) continue;
      if (filter == 1 && m.video()
          || filter == 2 && !m.video()
          || filter == 3 && !favorites.contains(m.uri.toString())) continue;
      if (!(m.name + " " + m.path)
          .toLowerCase(Locale.ROOT)
          .contains(search.toLowerCase(Locale.ROOT))) continue;
      shown.add(m);
    }
    if (sortOld) Collections.reverse(shown);
    grid.setAdapter(
        new BaseAdapter() {
          public int getCount() {
            return shown.size();
          }

          public Object getItem(int p) {
            return shown.get(p);
          }

          public long getItemId(int p) {
            return p;
          }

          public View getView(int p, View old, android.view.ViewGroup parent) {
            Media m = shown.get(p);
            FrameLayout frame =
                old instanceof FrameLayout ? (FrameLayout) old : new FrameLayout(MainActivity.this);
            frame.removeAllViews();
            int side =
                (getResources().getDisplayMetrics().widthPixels - dp(36))
                    / prefs.getInt("columns", 3);
            frame.setLayoutParams(new AbsListView.LayoutParams(-1, side));
            ImageView im = new ImageView(MainActivity.this);
            im.setScaleType(ImageView.ScaleType.CENTER_CROP);
            im.setBackgroundColor(card);
            frame.addView(im, new FrameLayout.LayoutParams(-1, -1));
            String key = m.uri.toString();
            im.setTag(key);
            Bitmap cached = cache.get(key);
            if (cached != null) im.setImageBitmap(cached);
            else
              thumbs.execute(
                  () -> {
                    try {
                      Bitmap b =
                          getContentResolver().loadThumbnail(m.uri, new Size(320, 320), null);
                      cache.put(key, b);
                      runOnUiThread(
                          () -> {
                            if (key.equals(im.getTag())) im.setImageBitmap(b);
                          });
                    } catch (Exception ignored) {
                    }
                  });
            if (m.video() || favorites.contains(key)) {
              TextView badge = text(m.video() ? "▶" : "★", 16);
              badge.setTextColor(Color.WHITE);
              badge.setBackgroundColor(0x66000000);
              frame.addView(badge);
            }
            return frame;
          }
        });
    grid.setOnItemClickListener((a, v, p, id) -> viewer(shown.get(p)));
    query.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            search = s.toString();
          }

          public void afterTextChanged(
              Editable e) { // Update after explicit search action to preserve keyboard focus.
          }
        });
    query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
    query.setOnEditorActionListener(
        (v, a, event) -> {
          render();
          return true;
        });
    if (media.isEmpty()) {
      action(body, "אפשר גישה לתמונות ולסרטונים", this::permission);
      body.addView(
          text("אפשר לבחור גם תמונות מסוימות בלבד. חיפוש מתעדכן בלחיצה על חיפוש במקלדת.", 13));
    }
  }

  void albums() {
    LinearLayout c = scroll();
    c.addView(text("אלבומים", 26));
    TreeMap<String, Integer> a = new TreeMap<>();
    for (Media m : media)
      a.put(m.path == null ? "" : m.path, a.getOrDefault(m.path == null ? "" : m.path, 0) + 1);
    for (String path : a.keySet())
      action(
          c,
          path + " · " + a.get(path),
          () -> {
            album = path;
            tab = "media";
            render();
          });
    if (a.isEmpty()) action(c, "אפשר גישה לגלריה", this::permission);
  }

  void viewer(Media m) {
    LinearLayout c = column();
    ImageView image = new ImageView(this);
    image.setAdjustViewBounds(true);
    image.setMaxHeight(dp(350));
    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    c.addView(image);
    thumbs.execute(
        () -> {
          try {
            Bitmap b =
                m.video()
                    ? getContentResolver().loadThumbnail(m.uri, new Size(1000, 1000), null)
                    : decode(m.uri, 1800);
            runOnUiThread(() -> image.setImageBitmap(b));
          } catch (Exception ignored) {
          }
        });
    c.addView(
        text(
            m.name
                + "\n"
                + m.path
                + "\n"
                + android.text.format.Formatter.formatFileSize(this, m.size),
            14));
    action(
        c,
        m.video() ? "נגן סרטון" : "פתח תמונה",
        () -> {
          openMedia(m);
        });
    action(
        c,
        "שתף",
        () ->
            startActivity(
                Intent.createChooser(
                    new Intent(Intent.ACTION_SEND)
                        .setType(m.mime)
                        .putExtra(Intent.EXTRA_STREAM, m.uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    "שיתוף")));
    action(
        c,
        favorites.contains(m.uri.toString()) ? "הסר ממועדפים" : "הוסף למועדפים",
        () -> {
          String k = m.uri.toString();
          if (!favorites.add(k)) favorites.remove(k);
          prefs.edit().putStringSet("favorites", new HashSet<>(favorites)).apply();
          Toast.makeText(this, "המועדפים עודכנו", Toast.LENGTH_SHORT).show();
        });
    action(
        c,
        "עריכה והמרה",
        () -> {
          if (m.video()) videoEditor(m);
          else photoEditor(m);
        });
    action(
        c,
        "העבר לסל של Android",
        () -> {
          try {
            startIntentSenderForResult(
                MediaStore.createTrashRequest(
                        getContentResolver(), Collections.singletonList(m.uri), true)
                    .getIntentSender(),
                32,
                null,
                0,
                0,
                0);
          } catch (Exception e) {
            message("מחיקה", e.getMessage());
          }
        });
    ScrollView sv = new ScrollView(this);
    sv.addView(c);
    new PrivacyDialogBuilder(this).setView(sv).setPositiveButton("סגירה", null).show();
  }

  void openMedia(Media m) {
    Dialog dialog = new Dialog(this, android.R.style.Theme_Material_NoActionBar);
    AppPrivacy.apply(dialog.getWindow(), this);
    LinearLayout content = column();
    content.setBackgroundColor(Color.BLACK);
    content.addView(button("סגירה", dialog::dismiss));
    if (m.video()) {
      VideoView video = new VideoView(this);
      content.addView(video, new LinearLayout.LayoutParams(-1, 0, 1));
      MediaController controller = new MediaController(this);
      controller.setAnchorView(video);
      video.setMediaController(controller);
      video.setOnErrorListener(
          (mp, what, extra) -> {
            message("ניגון נכשל", "קידוד הסרטון אינו נתמך בנגן המכשיר");
            return true;
          });
      video.setVideoURI(m.uri);
      video.setOnPreparedListener(player -> video.start());
      dialog.setOnDismissListener(v -> video.stopPlayback());
    } else {
      ZoomPhotoView image = new ZoomPhotoView(this);
      content.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
      thumbs.execute(
          () -> {
            try {
              Bitmap b = decode(m.uri, 4096);
              runOnUiThread(() -> image.setBitmap(b));
            } catch (Exception e) {
              runOnUiThread(() -> message("לא ניתן לפתוח", e.getMessage()));
            }
          });
    }
    dialog.setContentView(content);
    dialog.show();
    dialog.getWindow().setLayout(-1, -1);
  }

  Bitmap decode(Uri uri, int max) throws Exception {
    ImageDecoder.Source s = ImageDecoder.createSource(getContentResolver(), uri);
    return ImageDecoder.decodeBitmap(
        s,
        (decoder, info, src) -> {
          int w = info.getSize().getWidth(), h = info.getSize().getHeight();
          float ratio = Math.min(1f, max / (float) Math.max(w, h));
          decoder.setTargetSize(Math.max(1, (int) (w * ratio)), Math.max(1, (int) (h * ratio)));
          decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
          decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
        });
  }

  EditText field(LinearLayout c, String hint, String value, boolean number) {
    EditText e = new EditText(this);
    e.setTextColor(ink);
    e.setHintTextColor(0xff90a698);
    e.setHint(hint);
    e.setText(value);
    if (number)
      e.setInputType(
          android.text.InputType.TYPE_CLASS_NUMBER
              | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
    c.addView(text(hint, 13));
    c.addView(e);
    return e;
  }

  Spinner options(LinearLayout c, String label, String[] values) {
    c.addView(text(label, 13));
    Spinner s = new Spinner(this);
    ArrayAdapter<String> a =
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values);
    s.setAdapter(a);
    c.addView(s);
    return s;
  }

  CheckBox check(LinearLayout c, String label, boolean value) {
    CheckBox b = new CheckBox(this);
    b.setText(label);
    b.setTextColor(ink);
    b.setChecked(value);
    c.addView(b);
    return b;
  }

  void photoEditor(Media m) {
    LinearLayout c = column();
    c.addView(text("עריכת תמונה · שמירה כעותק", 22));
    c.addView(text("המקור נשמר. יצוא עד 4096 פיקסלים בצלע הארוכה, ללא נתוני מיקום.", 13));
    Spinner fmt = options(c, "פורמט", new String[] {"JPEG", "PNG", "WEBP"});
    Spinner rotate = options(c, "סיבוב", new String[] {"0°", "90°", "180°", "270°"});
    Spinner crop =
        options(c, "חיתוך מהמרכז", new String[] {"ללא חיתוך", "ריבוע 1:1", "4:3", "16:9"});
    CheckBox flip = check(c, "היפוך אופקי", false), gray = check(c, "שחור לבן", false);
    EditText width = field(c, "רוחב מרבי (1–4096)", "4096", true);
    c.addView(text("איכות דחיסה", 14));
    SeekBar quality = new SeekBar(this);
    quality.setMax(99);
    quality.setProgress(89);
    c.addView(quality);
    c.addView(text("בהירות", 14));
    SeekBar brightness = new SeekBar(this);
    brightness.setMax(200);
    brightness.setProgress(100);
    c.addView(brightness);
    ScrollView sv = new ScrollView(this);
    sv.addView(c);
    AlertDialog d =
        new PrivacyDialogBuilder(this)
            .setView(sv)
            .setNegativeButton("ביטול", null)
            .setPositiveButton("שמור עותק", null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(-1)
                .setOnClickListener(
                    w -> {
                      try {
                        int max = Integer.parseInt(width.getText().toString());
                        if (max < 1 || max > 4096) throw new Exception("בחר רוחב בין 1 ל־4096");
                        int f = fmt.getSelectedItemPosition(),
                            r = rotate.getSelectedItemPosition(),
                            cr = crop.getSelectedItemPosition(),
                            q = quality.getProgress() + 1,
                            br = brightness.getProgress() - 100;
                        boolean fl = flip.isChecked(), g = gray.isChecked();
                        d.dismiss();
                        status.setText("מייצא תמונה…");
                        work(
                            () -> {
                              Bitmap original = decode(m.uri, max);
                              int ow = original.getWidth(),
                                  oh = original.getHeight(),
                                  cw = ow,
                                  ch = oh;
                              if (cr > 0) {
                                float aspect = cr == 1 ? 1f : cr == 2 ? 4f / 3 : 16f / 9;
                                if (ow / (float) oh > aspect) cw = (int) (oh * aspect);
                                else ch = (int) (ow / aspect);
                              }
                              Matrix mat = new Matrix();
                              mat.postScale(fl ? -1 : 1, 1);
                              mat.postRotate(r * 90);
                              Bitmap transformed =
                                  Bitmap.createBitmap(
                                      original, (ow - cw) / 2, (oh - ch) / 2, cw, ch, mat, true);
                              Bitmap result =
                                  Bitmap.createBitmap(
                                      transformed.getWidth(),
                                      transformed.getHeight(),
                                      Bitmap.Config.ARGB_8888);
                              Canvas canvas = new Canvas(result);
                              canvas.drawColor(Color.WHITE);
                              Paint paint = new Paint(3);
                              ColorMatrix cm = new ColorMatrix();
                              if (g) cm.setSaturation(0);
                              cm.postConcat(
                                  new ColorMatrix(
                                      new float[] {
                                        1, 0, 0, 0, br, 0, 1, 0, 0, br, 0, 0, 1, 0, br, 0, 0, 0, 1,
                                        0
                                      }));
                              paint.setColorFilter(new ColorMatrixColorFilter(cm));
                              canvas.drawBitmap(transformed, 0, 0, paint);
                              String ext = f == 0 ? "jpg" : f == 1 ? "png" : "webp";
                              Uri out =
                                  createOutput(false, "image/" + (f == 0 ? "jpeg" : ext), ext);
                              try {
                                try (OutputStream os = getContentResolver().openOutputStream(out)) {
                                  if (!result.compress(
                                      f == 0
                                          ? Bitmap.CompressFormat.JPEG
                                          : f == 1
                                              ? Bitmap.CompressFormat.PNG
                                              : Bitmap.CompressFormat.WEBP_LOSSY,
                                      q,
                                      os)) throw new IOException("הדחיסה נכשלה");
                                }
                                publish(out);
                              } catch (Exception e) {
                                getContentResolver().delete(out, null, null);
                                throw e;
                              }
                              runOnUiThread(
                                  () -> {
                                    message("נשמר", "העותק נשמר באלבום Lume");
                                    load();
                                  });
                            });
                      } catch (Exception e) {
                        message("בדוק את הערכים", e.getMessage());
                      }
                    }));
    d.show();
  }

  Uri createOutput(boolean video, String mime, String ext) throws Exception {
    ContentValues v = new ContentValues();
    v.put("_display_name", "Lume_" + System.currentTimeMillis() + "." + ext);
    v.put("mime_type", mime);
    v.put("relative_path", video ? "Movies/Lume" : "Pictures/Lume");
    v.put("is_pending", 1);
    Uri u =
        getContentResolver()
            .insert(
                video
                    ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    : MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                v);
    if (u == null) throw new IOException("לא ניתן ליצור קובץ");
    return u;
  }

  void publish(Uri u) {
    ContentValues v = new ContentValues();
    v.put("is_pending", 0);
    getContentResolver().update(u, v, null, null);
  }

  void videoEditor(Media m) {
    if (transformer != null) {
      message("יצוא פעיל", "המתן לסיום היצוא הנוכחי");
      return;
    }
    LinearLayout c = column();
    c.addView(text("עורך סרטונים", 24));
    c.addView(
        text(
            "חיתוך בזמן, סיבוב, שינוי רזולוציה, השתקה והמרה ל־MP4. המקור נשמר. השאר את האפליקציה"
                + " פתוחה בזמן היצוא.",
            14));
    EditText start = field(c, "התחלה בשניות", "0", true),
        end = field(c, "סיום בשניות (ריק = עד הסוף)", "", true);
    Spinner codec =
        options(c, "קידוד MP4", new String[] {"H.264 · תאימות גבוהה", "H.265 · קובץ קטן יותר"});
    Spinner resolution = options(c, "רזולוציה", new String[] {"מקור", "1080p", "720p", "480p"});
    Spinner rotation = options(c, "סיבוב", new String[] {"0°", "90°", "180°", "270°"});
    CheckBox mute = check(c, "הסרת שמע", false);
    ScrollView sv = new ScrollView(this);
    sv.addView(c);
    AlertDialog d =
        new PrivacyDialogBuilder(this)
            .setView(sv)
            .setNegativeButton("ביטול", null)
            .setPositiveButton("יצוא", null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(-1)
                .setOnClickListener(
                    w -> {
                      try {
                        double s = Double.parseDouble(start.getText().toString()),
                            e =
                                end.getText().length() == 0
                                    ? -1
                                    : Double.parseDouble(end.getText().toString());
                        if (!Double.isFinite(s)
                            || !Double.isFinite(e)
                            || s < 0
                            || (e != -1 && e <= s))
                          throw new Exception("זמן הסיום חייב להיות גדול מההתחלה");
                        android.media.MediaMetadataRetriever retriever =
                            new android.media.MediaMetadataRetriever();
                        long duration;
                        try {
                          retriever.setDataSource(this, m.uri);
                          duration =
                              Long.parseLong(
                                  retriever.extractMetadata(
                                      android.media.MediaMetadataRetriever.METADATA_KEY_DURATION));
                        } finally {
                          retriever.release();
                        }
                        if (s * 1000 >= duration || e * 1000 > duration)
                          throw new Exception("הזמנים חורגים מאורך הסרטון");
                        MediaItem.ClippingConfiguration.Builder clip =
                            new MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs((long) (s * 1000));
                        if (e != -1) clip.setEndPositionMs((long) (e * 1000));
                        MediaItem item =
                            new MediaItem.Builder()
                                .setUri(m.uri)
                                .setClippingConfiguration(clip.build())
                                .build();
                        ArrayList<Effect> effects = new ArrayList<>();
                        int res = resolution.getSelectedItemPosition();
                        if (res > 0)
                          effects.add(
                              androidx.media3.effect.Presentation.createForHeight(
                                  res == 1 ? 1080 : res == 2 ? 720 : 480));
                        int rot = rotation.getSelectedItemPosition();
                        if (rot > 0)
                          effects.add(
                              new ScaleAndRotateTransformation.Builder()
                                  .setRotationDegrees(rot * 90)
                                  .build());
                        EditedMediaItem edited =
                            new EditedMediaItem.Builder(item)
                                .setRemoveAudio(mute.isChecked())
                                .setEffects(new Effects(Collections.emptyList(), effects))
                                .build();
                        exportFile =
                            new File(
                                getCacheDir(), "export-" + System.currentTimeMillis() + ".mp4");
                        transformer =
                            new Transformer.Builder(this)
                                .setVideoMimeType(
                                    codec.getSelectedItemPosition() == 0
                                        ? MimeTypes.VIDEO_H264
                                        : MimeTypes.VIDEO_H265)
                                .addListener(
                                    new Transformer.Listener() {
                                      @Override
                                      public void onCompleted(
                                          Composition composition, ExportResult result) {
                                        File file = exportFile;
                                        transformer = null;
                                        getWindow()
                                            .clearFlags(
                                                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                                        work(
                                            () -> {
                                              Uri out = createOutput(true, "video/mp4", "mp4");
                                              try {
                                                Store.copy(
                                                    new FileInputStream(file),
                                                    getContentResolver().openOutputStream(out));
                                                publish(out);
                                              } catch (Exception ex) {
                                                getContentResolver().delete(out, null, null);
                                                throw ex;
                                              } finally {
                                                file.delete();
                                              }
                                              runOnUiThread(
                                                  () -> {
                                                    message(
                                                        "היצוא הושלם", "הסרטון נשמר באלבום Lume");
                                                    load();
                                                  });
                                            });
                                      }

                                      @Override
                                      public void onError(
                                          Composition composition,
                                          ExportResult result,
                                          ExportException exception) {
                                        transformer = null;
                                        if (exportFile != null) exportFile.delete();
                                        getWindow()
                                            .clearFlags(
                                                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                                        message(
                                            "היצוא נכשל",
                                            exception.getMessage()
                                                + "\nנסה H.264 או רזולוציה נמוכה יותר.");
                                      }
                                    })
                                .build();
                        transformer.start(edited, exportFile.getAbsolutePath());
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        d.dismiss();
                        status.setText("מייצא סרטון… אפשר לבטל בהגדרות");
                        Handler h = new Handler(Looper.getMainLooper());
                        h.post(
                            new Runnable() {
                              public void run() {
                                if (transformer == null || destroyed) return;
                                ProgressHolder p = new ProgressHolder();
                                if (transformer.getProgress(p)
                                    == Transformer.PROGRESS_STATE_AVAILABLE)
                                  status.setText("יצוא סרטון · " + p.progress + "%");
                                h.postDelayed(this, 600);
                              }
                            });
                      } catch (Exception ex) {
                        message("לא ניתן לייצא", ex.getMessage());
                      }
                    }));
    d.show();
  }

  void rules() {
    LinearLayout c = scroll();
    c.addView(text("ניקוי אוטומטי", 27));
    c.addView(text("פחות עומס. יותר מקום לזיכרונות.", 15));
    c.addView(
        text(
            "בחר תיקייה, סוג מדיה וגיל קבצים. הגיל נספר מהשינוי האחרון בקובץ. הסריקה מתבצעת בערך כל"
                + " 6 שעות, בהתאם למגבלות Android. קבצים ללא תאריך אינם נמחקים.",
            14));
    action(
        c,
        "הוסף כלל לתיקייה",
        () -> {
          Intent i =
              new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                  .addFlags(
                      Intent.FLAG_GRANT_READ_URI_PERMISSION
                          | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                          | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
          startActivityForResult(i, 70);
        });
    action(c, "סל שחזור של הניקוי", this::trashScreen);
    c.addView(text(prefs.getString("lastRun", "עדיין לא בוצע ניקוי"), 12));
    JSONArray a = Store.rules(this);
    for (int i = 0; i < a.length(); i++) {
      final int index = i;
      JSONObject r = a.optJSONObject(i);
      LinearLayout box = column();
      box.setPadding(dp(8), dp(12), dp(8), dp(12));
      GradientDrawable shape = new GradientDrawable();
      shape.setColor(card);
      shape.setCornerRadius(dp(18));
      box.setBackground(shape);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
      lp.setMargins(0, dp(8), 0, dp(8));
      c.addView(box, lp);
      box.addView(text(r.optString("name"), 20));
      box.addView(
          text(
              "אחרי "
                  + r.optInt("days")
                  + " ימים · "
                  + r.optString("type")
                  + "\n"
                  + Uri.decode(r.optString("tree")),
              12));
      Switch enabled = new Switch(this);
      enabled.setText("כלל פעיל");
      enabled.setTextColor(ink);
      enabled.setChecked(r.optBoolean("enabled"));
      box.addView(enabled);
      enabled.setOnCheckedChangeListener(
          (v, on) -> {
            try {
              JSONArray current = Store.rules(this);
              current.getJSONObject(index).put("enabled", on);
              Store.save(this, current);
            } catch (Exception ex) {
              message("שגיאה", ex.getMessage());
            }
          });
      action(box, "תצוגה מקדימה וניקוי עכשיו", () -> previewRule(r));
      action(box, "עריכת כלל", () -> ruleEditor(Uri.parse(r.optString("tree")), index));
      action(
          box,
          "הסר כלל",
          () ->
              new PrivacyDialogBuilder(this)
                  .setMessage("להסיר את הכלל? קבצים לא יימחקו.")
                  .setPositiveButton(
                      "הסר",
                      (v, w) -> {
                        JSONArray current = Store.rules(this);
                        current.remove(index);
                        Store.save(this, current);
                        render();
                      })
                  .setNegativeButton("ביטול", null)
                  .show());
    }
  }

  void ruleEditor(Uri tree, int index) {
    JSONObject old = index >= 0 ? Store.rules(this).optJSONObject(index) : new JSONObject();
    LinearLayout c = column();
    EditText name = field(c, "שם הכלל", old.optString("name", "ניקוי תיקייה"), false),
        days = field(c, "גיל הקבצים בימים (לפחות יום אחד)", old.optString("days", "30"), true);
    Spinner type =
        options(c, "סוג קבצים", new String[] {"תמונות וסרטונים", "תמונות בלבד", "סרטונים בלבד"});
    type.setSelection(
        old.optString("type").equals("image") ? 1 : old.optString("type").equals("video") ? 2 : 0);
    CheckBox recursive = check(c, "כולל תיקיות משנה", old.optBoolean("recursive"));
    c.addView(
        text(
            "הקבצים יגובו בסל פרטי לפני מחיקה מהתיקייה. הסל תופס מקום עד שמרוקנים אותו. מחיקת"
                + " האפליקציה מוחקת גם את סל השחזור. הכלל החדש נשמר כבוי.",
            14));
    AlertDialog d =
        new PrivacyDialogBuilder(this)
            .setTitle("כלל ניקוי")
            .setView(c)
            .setNegativeButton("ביטול", null)
            .setPositiveButton("שמירת כלל", null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(-1)
                .setOnClickListener(
                    w -> {
                      try {
                        int age = Integer.parseInt(days.getText().toString());
                        if (age < 1 || age > 36500)
                          throw new Exception("הכנס מספר ימים בין 1 ל־36500");
                        if (name.getText().toString().trim().isEmpty())
                          throw new Exception("הכנס שם לכלל");
                        JSONObject r =
                            new JSONObject()
                                .put("tree", tree.toString())
                                .put("name", name.getText().toString())
                                .put("days", age)
                                .put(
                                    "type",
                                    type.getSelectedItemPosition() == 0
                                        ? "all"
                                        : type.getSelectedItemPosition() == 1 ? "image" : "video")
                                .put("recursive", recursive.isChecked())
                                .put("enabled", false);
                        JSONArray a = Store.rules(this);
                        if (index < 0) a.put(r);
                        else a.put(index, r);
                        Store.save(this, a);
                        d.dismiss();
                        render();
                      } catch (Exception ex) {
                        message("בדוק את הכלל", ex.getMessage());
                      }
                    }));
    d.show();
  }

  void previewRule(JSONObject r) {
    status.setText("סורק תיקייה…");
    work(
        () -> {
          java.util.List<Store.Doc> list = Store.candidates(this, r);
          long bytes = 0;
          StringBuilder names = new StringBuilder();
          for (Store.Doc doc : list) {
            bytes += doc.size;
            if (names.length() < 5000) names.append(doc.name).append('\n');
          }
          String msg =
              list.size()
                  + " קבצים · "
                  + android.text.format.Formatter.formatFileSize(this, bytes)
                  + "\n\n"
                  + names;
          runOnUiThread(
              () -> {
                status.setText("הסריקה הושלמה");
                new PrivacyDialogBuilder(this)
                    .setTitle("מועמדים לניקוי")
                    .setMessage(msg)
                    .setNegativeButton("סגירה", null)
                    .setPositiveButton(
                        "העבר לסל",
                        (v, w) -> {
                          if (list.isEmpty()) return;
                          new PrivacyDialogBuilder(this)
                              .setMessage(
                                  "להעביר את הקבצים המתאימים לכלל לסל השחזור? הרשימה תיבדק מחדש"
                                      + " לפני הפעולה.")
                              .setPositiveButton(
                                  "העבר",
                                  (x, y) ->
                                      work(
                                          () -> {
                                            int n = Store.clean(this, r);
                                            prefs
                                                .edit()
                                                .putString(
                                                    "lastRun",
                                                    new Date() + " · " + n + " הועברו לסל")
                                                .apply();
                                            runOnUiThread(
                                                () -> {
                                                  load();
                                                  message("הניקוי הושלם", n + " קבצים הועברו לסל");
                                                });
                                          }))
                              .setNegativeButton("ביטול", null)
                              .show();
                        })
                    .show();
              });
        });
  }

  void trashScreen() {
    LinearLayout c = column();
    c.addView(text("סל השחזור של Lume", 24));
    c.addView(
        text(
            "השחזור יוצר קובץ בתיקייה הראשית של הכלל. קבצים כאן נשמרים עד ריקון ידני. הסל של"
                + " Android מנוהל דרך גלריית המכשיר.",
            14));
    File[] metas = Store.trash(this).listFiles((dir, name) -> name.endsWith(".json"));
    if (metas != null)
      for (File f : metas) {
        try {
          JSONObject j = Store.metadata(f);
          action(
              c,
              j.optString("name") + " · שחזור",
              () ->
                  work(
                      () -> {
                        Store.restore(this, f);
                        runOnUiThread(
                            () -> {
                              message("שוחזר", "הקובץ הוחזר לתיקייה");
                              load();
                            });
                      }));
        } catch (Exception ignored) {
        }
      }
    action(
        c,
        "רוקן סל לצמיתות",
        () ->
            new PrivacyDialogBuilder(this)
                .setTitle("מחיקה לצמיתות")
                .setMessage("כל עותקי השחזור של Lume יימחקו. אי אפשר לבטל פעולה זו.")
                .setPositiveButton(
                    "מחק לצמיתות",
                    (v, w) ->
                        work(
                            () -> {
                              synchronized (Store.class) {
                                File[] all = Store.trash(this).listFiles();
                                if (all != null)
                                  for (File f : all)
                                    if (!f.delete())
                                      throw new IOException("לא ניתן להסיר קובץ מהסל");
                              }
                              runOnUiThread(() -> message("הסל רוקן", "הקבצים הוסרו"));
                            }))
                .setNegativeButton("ביטול", null)
                .show());
    ScrollView sv = new ScrollView(this);
    sv.addView(c);
    new PrivacyDialogBuilder(this).setView(sv).setPositiveButton("סגירה", null).show();
  }

  void settings() {
    LinearLayout c = scroll();
    c.addView(text("הגדרות", 27));
    action(c, "הרשאות וגישה למדיה", this::permission);
    Switch theme = new Switch(this);
    theme.setText("מראה בהיר");
    theme.setTextColor(ink);
    theme.setChecked(prefs.getBoolean("light", false));
    c.addView(theme);
    theme.setOnCheckedChangeListener(
        (v, on) -> {
          prefs.edit().putBoolean("light", on).apply();
          recreate();
        });
    Spinner columns = options(c, "מספר עמודות", new String[] {"2", "3", "4", "5"});
    columns.setSelection(prefs.getInt("columns", 3) - 2);
    columns.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            prefs.edit().putInt("columns", pos + 2).apply();
          }

          public void onNothingSelected(AdapterView<?> p) {}
        });
    Switch order = new Switch(this);
    order.setText("הצג ישנים תחילה");
    order.setTextColor(ink);
    order.setChecked(sortOld);
    c.addView(order);
    order.setOnCheckedChangeListener(
        (v, on) -> {
          sortOld = on;
          prefs.edit().putBoolean("old", on).apply();
        });
    EditText limit =
        field(
            c, "מגבלת סל שחזור במגה־בייט", String.valueOf(prefs.getInt("trashLimit", 2048)), true);
    action(
        c,
        "שמור מגבלת סל",
        () -> {
          try {
            int val = Integer.parseInt(limit.getText().toString());
            if (val < 100 || val > 100000) throw new Exception("בחר ערך בין 100 ל־100000");
            prefs.edit().putInt("trashLimit", val).apply();
            message("נשמר", "הניקוי ייעצר כשהסל יגיע למגבלה");
          } catch (Exception e) {
            message("ערך לא תקין", e.getMessage());
          }
        });
    action(c, "פתח סל שחזור", this::trashScreen);
    action(c, "רענן גלריה", this::load);
    action(
        c,
        "ביטול יצוא סרטון",
        () -> {
          if (transformer != null) {
            transformer.cancel();
            transformer = null;
            if (exportFile != null) exportFile.delete();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            status.setText("היצוא בוטל");
          }
        });
    c.addView(
        text(
            "Lume 2.0.0\n"
                + "עבודה מקומית, ללא חשבון וללא העלאת מדיה לרשת.\n"
                + "תמונות: JPEG / PNG / WebP. סרטונים: MP4 עם H.264 או H.265 בהתאם למכשיר.\n"
                + "לניקוי ברקע אפשר להתיר שימוש בסוללה ללא הגבלה דרך הגדרות Android. אחרי אתחול"
                + " מחדש פתח את האפליקציה כדי לתזמן שוב ניקוי.\n"
                + "קבצים בתיקיות Android/data, תיקיות מוגנות וקבצים שספק האחסון אינו חושף אינם"
                + " זמינים.\n"
                + "סל Lume נמצא באחסון האפליקציה ונמחק בהסרתה.",
            14));
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 70 && result == RESULT_OK && data != null && data.getData() != null) {
      Uri tree = data.getData();
      try {
        int flags =
            data.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if ((flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0)
          throw new Exception("נדרשת הרשאת כתיבה לתיקייה");
        getContentResolver()
            .takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        ruleEditor(tree, -1);
      } catch (Exception e) {
        message("גישה לתיקייה", e.getMessage());
      }
    } else if (request == 32) load();
  }

  @Override
  protected void onDestroy() {
    destroyed = true;
    if (transformer != null) {
      transformer.cancel();
      if (exportFile != null) exportFile.delete();
    }
    io.shutdown();
    thumbs.shutdown();
    super.onDestroy();
  }
}
