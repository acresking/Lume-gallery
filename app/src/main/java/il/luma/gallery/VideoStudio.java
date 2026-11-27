package il.luma.gallery;

import android.app.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.effect.*;
import androidx.media3.transformer.*;
import java.io.*;
import java.util.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class VideoStudio extends Dialog {
  final ModernActivity a;
  final MainActivity.Media media;
  VideoView preview;
  LinearLayout panel;
  TextView time;
  SeekBar from, to;
  long duration;
  int resolution, rotation, codec;
  boolean mute;
  Button playButton;
  android.media.MediaPlayer preparedPlayer;
  String mode = "trim";

  VideoStudio(ModernActivity a, MainActivity.Media m) {
    super(a, android.R.style.Theme_Material_NoActionBar);
    this.a = a;
    media = m;
    duration = m.duration;
  }

  TextView text(String s, int size) {
    TextView v = new TextView(a);
    v.setText(s);
    v.setTextSize(size);
    v.setTextColor(Color.WHITE);
    return v;
  }

  Button button(String s, Runnable r) {
    return StudioUI.control(a, s, r, false);
  }

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    LinearLayout root = a.column();
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
    TextView title = text("עריכת סרטון", 18);
    title.setGravity(Gravity.CENTER);
    top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    top.addView(
        StudioUI.control(a, "יצוא", this::export, true),
        new LinearLayout.LayoutParams(a.dp(84), -2));
    root.addView(top);
    android.widget.FrameLayout stage = new android.widget.FrameLayout(a);
    preview = new VideoView(a);
    stage.setBackground(a.rounded(0xff15131c, 24));
    stage.setClipToOutline(true);
    stage.addView(preview, new android.widget.FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
    root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
    preview.setContentDescription("תצוגת הסרטון · ניגון והשהיה בכפתור שמתחת לתצוגה");
    preview.setVideoURI(media.uri);
    preview.setOnPreparedListener(
        mp -> {
          preparedPlayer = mp;
          mp.setVolume(mute ? 0 : 1, mute ? 0 : 1);
          duration = mp.getDuration();
          mp.setLooping(true);
          preview.seekTo(1);
          updateTimes();
        });
    preview.setOnErrorListener(
        (mp, w, e) -> {
          a.message("תצוגת סרטון", "לא ניתן להציג תצוגה מקדימה לקידוד הזה");
          return true;
        });
    panel = a.column();
    panel.setPadding(a.dp(20), a.dp(18), a.dp(20), a.dp(18));
    StudioUI.panel(a, panel);
    time = text("", 14);
    time.setGravity(Gravity.CENTER);
    panel.addView(time);
    panel.addView(text("תחילת הקטע", 12));
    from = new SeekBar(a);
    from.setMax(1000);
    StudioUI.slider(from);
    panel.addView(from, new LinearLayout.LayoutParams(-1, a.dp(48)));
    panel.addView(text("סוף הקטע", 12));
    to = new SeekBar(a);
    to.setMax(1000);
    to.setProgress(1000);
    StudioUI.slider(to);
    panel.addView(to, new LinearLayout.LayoutParams(-1, a.dp(48)));
    SeekBar.OnSeekBarChangeListener listener =
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar s, int p, boolean user) {
            if (from == null || to == null) return;
            if (from.getProgress() >= to.getProgress()) {
              if (s == from) from.setProgress(Math.max(0, to.getProgress() - 1));
              else to.setProgress(Math.min(1000, from.getProgress() + 1));
            }
            updateTimes();
            if (user && duration > 0)
              preview.seekTo(
                  (int) ((s == from ? from.getProgress() : to.getProgress()) * duration / 1000));
          }

          public void onStartTrackingTouch(SeekBar s) {
            preview.pause();
          }

          public void onStopTrackingTouch(SeekBar s) {}
        };
    from.setOnSeekBarChangeListener(listener);
    to.setOnSeekBarChangeListener(listener);
    LinearLayout options = a.row();
    playButton =
        button(
            "נגן קטע",
            () -> {
              if (preview.isPlaying()) {
                preview.pause();
                playButton.setText("נגן קטע");
              } else {
                int current = preview.getCurrentPosition();
                if (current < duration * from.getProgress() / 1000
                    || current >= duration * to.getProgress() / 1000)
                  preview.seekTo((int) (duration * from.getProgress() / 1000));
                preview.start();
                playButton.setText("השהה");
              }
            });
    options.addView(playButton, new LinearLayout.LayoutParams(0, -2, 1));
    options.addView(button("התאמה ויצוא", this::options), new LinearLayout.LayoutParams(0, -2, 1));
    panel.addView(options);
    TextView note = text("המקור נשמר · יצוא MP4 · השאר את האפליקציה פתוחה", 11);
    note.setPadding(0, a.dp(12), 0, 0);
    note.setTextColor(0xffa3a6b0);
    panel.addView(note);
    ScrollView controlsScroll = new ScrollView(a);
    controlsScroll.addView(panel);
    root.addView(
        controlsScroll,
        new LinearLayout.LayoutParams(
            -1,
            Math.min(a.dp(280), (int) (a.getResources().getDisplayMetrics().heightPixels * .4f))));
    setContentView(root);
    getWindow().getDecorView().setSystemUiVisibility(0);
    Handler h = new Handler(Looper.getMainLooper());
    h.postDelayed(
        new Runnable() {
          public void run() {
            if (!isShowing()) return;
            if (duration > 0
                && preview.isPlaying()
                && preview.getCurrentPosition() >= duration * to.getProgress() / 1000) {
              preview.pause();
              if (playButton != null) playButton.setText("נגן קטע");
              preview.seekTo((int) (duration * from.getProgress() / 1000));
            }
            h.postDelayed(this, 120);
          }
        },
        120);
  }

  @Override
  public void show() {
    AppPrivacy.apply(getWindow(), a);
    getWindow().setWindowAnimations(a.motion() ? R.style.GallerySheetMotion : 0);
    super.show();
    getWindow().setLayout(-1, -1);
  }

  void updateTimes() {
    if (time == null || from == null || to == null) return;
    time.setText(
        stamp(duration * from.getProgress() / 1000)
            + " — "
            + stamp(duration * to.getProgress() / 1000));
  }

  String stamp(long ms) {
    return String.format(
        Locale.ROOT, "%d:%02d.%01d", ms / 60000, (ms / 1000) % 60, (ms % 1000) / 100);
  }

  void options() {
    new PrivacyDialogBuilder(a, false)
        .setTitle("התאמה ויצוא")
        .setItems(
            new String[] {
              "רזולוציה: " + new String[] {"מקור", "1080p", "720p", "480p"}[resolution],
              "סיבוב: " + rotation + "°",
              "קידוד: " + (codec == 0 ? "H.264" : "H.265"),
              mute ? "שמע: מושתק" : "שמע: מקורי"
            },
            (d, n) -> {
              if (n == 0)
                new PrivacyDialogBuilder(a, false)
                    .setTitle("רזולוציה")
                    .setSingleChoiceItems(
                        new String[] {"מקור", "1080p", "720p", "480p"},
                        resolution,
                        (x, p) -> {
                          resolution = p;
                          x.dismiss();
                        })
                    .show();
              else if (n == 1)
                new PrivacyDialogBuilder(a, false)
                    .setTitle("סיבוב")
                    .setSingleChoiceItems(
                        new String[] {"0°", "90°", "180°", "270°"},
                        rotation / 90,
                        (x, p) -> {
                          rotation = p * 90;
                          x.dismiss();
                        })
                    .show();
              else if (n == 2)
                new PrivacyDialogBuilder(a, false)
                    .setTitle("קידוד MP4")
                    .setSingleChoiceItems(
                        new String[] {"H.264 · תאימות גבוהה", "H.265 · בהתאם לתמיכת המכשיר"},
                        codec,
                        (x, p) -> {
                          codec = p;
                          x.dismiss();
                        })
                    .show();
              else {
                mute = !mute;
                if (preparedPlayer != null)
                  try {
                    preparedPlayer.setVolume(mute ? 0 : 1, mute ? 0 : 1);
                  } catch (IllegalStateException ignored) {
                  }
                Toast.makeText(
                        a, mute ? "השמע יוסר מהעותק המיוצא" : "השמע יישמר", Toast.LENGTH_SHORT)
                    .show();
              }
            })
        .show();
  }

  void export() {
    if (duration <= 0) {
      a.message("הסרטון נטען", "המתן לסיום טעינת הסרטון");
      return;
    }
    if (a.transformer != null) {
      a.message("יצוא פעיל", "המתן לסיום היצוא הנוכחי");
      return;
    }
    try {
      long start = duration * from.getProgress() / 1000, end = duration * to.getProgress() / 1000;
      if (end <= start) throw new IOException("בחר קטע באורך חיובי");
      MediaItem.ClippingConfiguration.Builder clipping =
          new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(start);
      if (to.getProgress() < 1000) clipping.setEndPositionMs(end);
      MediaItem item =
          new MediaItem.Builder()
              .setUri(media.uri)
              .setClippingConfiguration(clipping.build())
              .build();
      ArrayList<Effect> effects = new ArrayList<>();
      if (resolution > 0)
        effects.add(
            androidx.media3.effect.Presentation.createForHeight(
                resolution == 1 ? 1080 : resolution == 2 ? 720 : 480));
      if (rotation > 0)
        effects.add(
            new ScaleAndRotateTransformation.Builder().setRotationDegrees(rotation).build());
      EditedMediaItem edited =
          new EditedMediaItem.Builder(item)
              .setRemoveAudio(mute)
              .setEffects(new Effects(Collections.emptyList(), effects))
              .build();
      File file = new File(a.getCacheDir(), "Lume-" + System.currentTimeMillis() + ".mp4");
      a.exportFile = file;
      a.transformer =
          new Transformer.Builder(a)
              .setVideoMimeType(codec == 0 ? MimeTypes.VIDEO_H264 : MimeTypes.VIDEO_H265)
              .addListener(
                  new Transformer.Listener() {
                    @Override
                    public void onCompleted(Composition composition, ExportResult result) {
                      a.transformer = null;
                      a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                      a.work(
                          () -> {
                            Uri out = a.createOutput(true, "video/mp4", "mp4");
                            try {
                              Store.copy(
                                  new FileInputStream(file),
                                  a.getContentResolver().openOutputStream(out));
                              a.publish(out);
                            } catch (Exception e) {
                              a.getContentResolver().delete(out, null, null);
                              throw e;
                            } finally {
                              file.delete();
                            }
                            a.runOnUiThread(
                                () -> {
                                  a.load();
                                  Toast.makeText(a, "הסרטון נשמר באלבום Lume", Toast.LENGTH_SHORT)
                                      .show();
                                });
                          });
                    }

                    @Override
                    public void onError(Composition c, ExportResult result, ExportException e) {
                      a.transformer = null;
                      file.delete();
                      a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                      a.message(
                          "היצוא לא הושלם", e.getMessage() + "\nנסה H.264 או רזולוציה נמוכה יותר.");
                    }
                  })
              .build();
      a.transformer.start(edited, file.getAbsolutePath());
      a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      dismiss();
      Handler h = new Handler(Looper.getMainLooper());
      h.post(
          new Runnable() {
            public void run() {
              if (a.transformer == null || a.destroyed) return;
              ProgressHolder progress = new ProgressHolder();
              if (a.transformer.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE)
                a.status.setText("מייצא סרטון · " + progress.progress + "%");
              h.postDelayed(this, 500);
            }
          });
    } catch (Exception e) {
      a.message("יצוא", e.getMessage());
    }
  }

  @Override
  public void dismiss() {
    if (preview != null) preview.stopPlayback();
    super.dismiss();
  }
}
