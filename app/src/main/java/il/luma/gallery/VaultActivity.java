package il.luma.gallery;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/**
 * Isolated secure window, device-password entry, encrypted files and private ephemeral previews.
 */
public final class VaultActivity extends Activity {
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private LinearLayout content;
  private volatile boolean unlocked;
  private boolean authenticating, picking;
  private volatile int generation;
  private Dialog preview;
  private final ArrayList<Dialog> secureDialogs = new ArrayList<>();
  private VideoView video;
  private ArrayList<Uri> pending = new ArrayList<>();
  private File tempDir;
  private final Handler timer = new Handler(Looper.getMainLooper());
  private final Runnable expire =
      () -> {
        lock();
        authenticate();
      };

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    getWindow().setStatusBarColor(0xff101018);
    getWindow().setNavigationBarColor(0xff101018);
    tempDir = new File(getCacheDir(), "vault-preview");
    tempDir.mkdirs();
    cleanTemps();
    lockedView();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (!unlocked && !authenticating && !picking) authenticate();
  }

  private void authenticate() {
    if (isFinishing() || authenticating) return;
    KeyguardManager manager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
    if (!manager.isDeviceSecure()) {
      new SecureBuilder(this)
          .setTitle("הגדר סיסמה למכשיר")
          .setMessage("הכספת נפתחת באמצעות סיסמת או קוד נעילת Android. הגדר נעילת מסך לפני השימוש.")
          .setPositiveButton("סגירה", (d, w) -> finish())
          .setOnCancelListener(d -> finish())
          .show();
      return;
    }
    try {
      VaultStore.key();
    } catch (Exception e) {
      error("יצירת מפתח הכספת נכשלה");
      return;
    }
    authenticating = true;
    startActivityForResult(
        manager.createConfirmDeviceCredentialIntent(
            "כספת Lume", "הזן את סיסמת או קוד נעילת המכשיר"),
        91);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 91) {
      authenticating = false;
      if (result != RESULT_OK) {
        finish();
        return;
      }
      unlocked = true;
      timer.removeCallbacks(expire);
      timer.postDelayed(expire, 240000);
      refresh();
      if (!pending.isEmpty()) {
        ArrayList<Uri> items = new ArrayList<>(pending);
        pending.clear();
        importItems(items);
      }
    } else if (request == 92) {
      picking = false;
      pending.clear();
      if (result == RESULT_OK && data != null) {
        if (data.getClipData() != null)
          for (int i = 0; i < data.getClipData().getItemCount(); i++)
            pending.add(data.getClipData().getItemAt(i).getUri());
        else if (data.getData() != null) pending.add(data.getData());
      }
      authenticate();
    }
  }

  private static final class SecureBuilder extends AlertDialog.Builder {
    SecureBuilder(Context c) {
      super(c);
    }

    @Override
    public AlertDialog create() {
      AlertDialog d = super.create();
      d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
      if (getContext() instanceof VaultActivity)
        ((VaultActivity) getContext()).secureDialogs.add(d);
      return d;
    }

    @Override
    public AlertDialog show() {
      AlertDialog d = create();
      d.show();
      return d;
    }
  }

  private int dp(int v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  private TextView text(String value, int size) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextColor(0xfff2effa);
    t.setTextSize(size);
    t.setPadding(dp(16), dp(12), dp(16), dp(12));
    return t;
  }

  private Button button(String name, Runnable click) {
    Button b = new Button(this);
    b.setText(name);
    b.setTextColor(0xffdfd0ff);
    b.setAllCaps(false);
    b.setMinHeight(dp(52));
    GradientDrawable g = new GradientDrawable();
    g.setColor(0xff30283f);
    g.setCornerRadius(dp(24));
    b.setBackground(g);
    b.setOnClickListener(v -> click.run());
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(dp(8), dp(6), dp(8), dp(6));
    b.setLayoutParams(lp);
    return b;
  }

  private void base() {
    content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    content.setPadding(dp(16), dp(24), dp(16), dp(24));
    content.setBackgroundColor(0xff101018);
    content.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
          view.setPadding(
              dp(16) + bars.left, dp(16) + bars.top, dp(16) + bars.right, dp(16) + bars.bottom);
          return insets;
        });
    setContentView(content);
    content.requestApplyInsets();
  }

  private void lockedView() {
    base();
    content.addView(text("כספת נעולה", 26));
    content.addView(button("פתיחה עם סיסמת המכשיר", this::authenticate));
    content.addView(button("חזרה לגלריה", this::finish));
  }

  private void refresh() {
    if (!unlocked) return;
    base();
    content.addView(text("הכספת שלך", 28));
    content.addView(text("מוצפן במכשיר · צילום והקלטת מסך חסומים", 14));
    content.addView(
        text(
            "הסרת האפליקציה או ניקוי נתוניה מוחקים את הכספת. הייבוא שומר עותק; המקור נשאר בגלריה.",
            13));
    content.addView(
        button(
            "הוסף תמונות וסרטונים",
            () -> {
              picking = true;
              Intent intent =
                  new Intent(Intent.ACTION_OPEN_DOCUMENT)
                      .setType("*/*")
                      .putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/*", "video/*"})
                      .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                      .addCategory(Intent.CATEGORY_OPENABLE);
              startActivityForResult(intent, 92);
            }));
    content.addView(button("נעל וחזור לגלריה", this::finish));
    ScrollView scroll = new ScrollView(this);
    LinearLayout list = new LinearLayout(this);
    list.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(list);
    content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    list.addView(text("טוען את הקבצים המוצפנים…", 14));
    final int token = generation;
    worker.execute(
        () -> {
          ArrayList<File> files = new ArrayList<>();
          ArrayList<String> names = new ArrayList<>();
          try {
            File[] all = VaultStore.folder(this).listFiles((d, n) -> n.endsWith(".lume"));
            if (all != null)
              for (File f : all) {
                JSONObject m = VaultStore.read(f, null, VaultStore.key());
                files.add(f);
                names.add(m.optString("name", "מדיה"));
              }
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (unlocked && token == generation)
                    error(
                        "לא ניתן לפתוח את הכספת. ייתכן שנדרש אימות מחדש או שמפתח המכשיר אינו"
                            + " זמין.");
                });
            return;
          }
          runOnUiThread(
              () -> {
                if (!unlocked || token != generation) return;
                list.removeAllViews();
                if (files.isEmpty())
                  list.addView(
                      text(
                          "הכספת ריקה. הייבוא שומר עותק מוצפן ומאמת אותו; המקור נשאר בגלריה עד"
                              + " שתמחק אותו בעצמך.",
                          15));
                for (int i = 0; i < files.size(); i++) {
                  File file = files.get(i);
                  list.addView(button(names.get(i), () -> open(file)));
                }
              });
        });
  }

  private void importItems(ArrayList<Uri> items) {
    if (!unlocked) return;
    int token = generation;
    content.addView(text("מעתיק, מצפין ומאמת. המקורות נשארים במקומם…", 14));
    worker.execute(
        () -> {
          int count = 0;
          String failure = "";
          for (Uri uri : items) {
            if (token != generation) break;
            try {
              VaultStore.importCopy(this, uri, VaultStore.key());
              count++;
            } catch (Exception e) {
              failure = "חלק מהקבצים לא יובאו. בדוק אחסון והרשאות או היכנס שוב לכספת.";
              break;
            }
          }
          final String result =
              count
                  + " קבצים נשמרו ואומתו בכספת. המקורות נשארו בגלריה; אפשר למחוק אותם ממנה לאחר"
                  + " שבדקת שהעותקים נפתחים."
                  + (failure.isEmpty() ? "" : "\n" + failure);
          runOnUiThread(
              () -> {
                if (unlocked && token == generation) {
                  refresh();
                  new SecureBuilder(this)
                      .setMessage(result)
                      .setPositiveButton("הבנתי", null)
                      .show()
                      .getWindow()
                      .addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                }
              });
        });
  }

  private void open(File encrypted) {
    if (!unlocked) return;
    int token = generation;
    worker.execute(
        () -> {
          File plain = new File(tempDir, UUID.randomUUID() + ".media");
          try {
            JSONObject metadata;
            try (FileOutputStream output = new FileOutputStream(plain)) {
              metadata = VaultStore.read(encrypted, output, VaultStore.key());
            }
            runOnUiThread(
                () -> {
                  if (!unlocked || token != generation) {
                    plain.delete();
                    return;
                  }
                  showPreview(encrypted, plain, metadata);
                });
          } catch (Exception e) {
            plain.delete();
            runOnUiThread(
                () -> {
                  if (unlocked && token == generation)
                    error("הפתיחה נכשלה. היכנס שוב עם סיסמת המכשיר; קובץ הכספת נשמר ללא שינוי.");
                });
          }
        });
  }

  private void showPreview(File encrypted, File plain, JSONObject metadata) {
    if (preview != null) preview.dismiss();
    preview = new Dialog(this);
    preview.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    LinearLayout shell = new LinearLayout(this);
    shell.setOrientation(LinearLayout.VERTICAL);
    shell.setBackgroundColor(0xff101018);
    shell.addView(text(metadata.optString("name"), 18));
    if (metadata.optString("mime").startsWith("video/")) {
      video = new VideoView(this);
      shell.addView(video, new LinearLayout.LayoutParams(-1, 0, 1));
      MediaController controls = new MediaController(this);
      controls.setAnchorView(video);
      video.setMediaController(controls);
      video.setVideoPath(plain.toString());
      VideoView player = video;
      video.setOnPreparedListener(
          mp -> {
            if (unlocked && video == player && preview != null && preview.isShowing())
              player.start();
          });
      video.setOnErrorListener(
          (mp, what, extra) -> {
            if (unlocked && video == player)
              error("לא ניתן לנגן את הסרטון במכשיר הזה. העותק המוצפן נשמר.");
            return true;
          });
    } else {
      ImageView photo = new ImageView(this);
      photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
      BitmapFactory.Options o = new BitmapFactory.Options();
      o.inJustDecodeBounds = true;
      BitmapFactory.decodeFile(plain.toString(), o);
      o.inSampleSize = 1;
      while (Math.max(o.outWidth, o.outHeight) / o.inSampleSize > 2048) o.inSampleSize *= 2;
      o.inJustDecodeBounds = false;
      photo.setImageBitmap(BitmapFactory.decodeFile(plain.toString(), o));
      shell.addView(photo, new LinearLayout.LayoutParams(-1, 0, 1));
    }
    shell.addView(
        button(
            "שמור עותק בגלריה הרגילה",
            () ->
                new SecureBuilder(this)
                    .setMessage("העותק יהיה נגיש בגלריה הרגילה וניתן לצילום מסך. להמשיך?")
                    .setPositiveButton("שמור עותק", (d, w) -> export(plain, metadata))
                    .setNegativeButton("ביטול", null)
                    .show()
                    .getWindow()
                    .addFlags(WindowManager.LayoutParams.FLAG_SECURE)));
    shell.addView(
        button(
            "מחק את העותק מהכספת",
            () ->
                new SecureBuilder(this)
                    .setTitle("מחיקה לצמיתות מהכספת")
                    .setMessage(
                        "העותק המוצפן יימחק ללא סל שחזור. המקור בגלריה, אם עדיין קיים, לא ישתנה.")
                    .setPositiveButton(
                        "מחק מהכספת",
                        (d, w) -> {
                          if (!unlocked) return;
                          if (encrypted.delete()) {
                            preview.dismiss();
                            refresh();
                          } else error("המחיקה נכשלה; הקובץ נשמר.");
                        })
                    .setNegativeButton("ביטול", null)
                    .show()));
    shell.addView(button("סגור", () -> preview.dismiss()));
    preview.setContentView(shell);
    preview.setOnDismissListener(
        d -> {
          if (video != null) {
            video.stopPlayback();
            video = null;
          }
          plain.delete();
        });
    preview.show();
    preview.getWindow().setLayout(-1, -1);
  }

  private void export(File plain, JSONObject metadata) {
    int token = generation;
    worker.execute(
        () -> {
          Uri uri = null;
          try {
            if (!unlocked || token != generation) return;
            String mime = metadata.optString("mime");
            boolean movie = mime.startsWith("video/");
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, metadata.optString("name", "Lume"));
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(
                MediaStore.MediaColumns.RELATIVE_PATH, movie ? "Movies/Lume" : "Pictures/Lume");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            uri =
                getContentResolver()
                    .insert(
                        movie
                            ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            : MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values);
            if (uri == null) throw new IOException();
            try (InputStream in = new FileInputStream(plain);
                OutputStream out = getContentResolver().openOutputStream(uri)) {
              if (out == null) throw new IOException();
              byte[] buffer = new byte[65536];
              int read;
              while ((read = in.read(buffer)) != -1) {
                if (token != generation || !unlocked) throw new IOException();
                out.write(buffer, 0, read);
              }
            }
            if (!unlocked || token != generation) throw new IOException();
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);
            runOnUiThread(
                () -> Toast.makeText(this, "עותק נשמר בגלריה הרגילה", Toast.LENGTH_LONG).show());
          } catch (Exception e) {
            if (uri != null) getContentResolver().delete(uri, null, null);
            runOnUiThread(
                () -> {
                  if (unlocked && token == generation) error("הייצוא לא הושלם; המקור המוצפן נשמר.");
                });
          }
        });
  }

  private void error(String message) {
    new SecureBuilder(this)
        .setMessage(message)
        .setPositiveButton("סגירה", null)
        .show()
        .getWindow()
        .addFlags(WindowManager.LayoutParams.FLAG_SECURE);
  }

  private void cleanTemps() {
    File[] files = tempDir.listFiles();
    if (files != null) for (File f : files) f.delete();
  }

  private void lock() {
    unlocked = false;
    generation++;
    for (Dialog dialog : new ArrayList<>(secureDialogs)) dialog.dismiss();
    secureDialogs.clear();
    timer.removeCallbacks(expire);
    if (preview != null) {
      preview.dismiss();
      preview = null;
    }
    if (video != null) {
      video.stopPlayback();
      video = null;
    }
    cleanTemps();
    lockedView();
  }

  @Override
  protected void onStop() {
    lock();
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    timer.removeCallbacksAndMessages(null);
    lock();
    worker.shutdownNow();
    cleanTemps();
    super.onDestroy();
  }
}
