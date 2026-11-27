package il.luma.gallery;

import android.app.Dialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

/** Native in-app map; tiles request the visible region only, never media or live location. */
final class PlacesMap extends Dialog {
  final ModernActivity host;
  final double latitude, longitude;
  MapView map;
  final AtomicBoolean canceled = new AtomicBoolean();
  private TextView status;
  private android.graphics.drawable.Drawable pinDrawable;
  private boolean libraryLoaded;
  private LinearLayout selection;
  private android.widget.Button loadButton;

  PlacesMap(ModernActivity activity, double lat, double lon) {
    super(activity, android.R.style.Theme_Material_NoActionBar);
    host = activity;
    if (!LocationNames.valid(lat, lon)) throw new IllegalArgumentException("Invalid point");
    latitude = lat;
    longitude = lon;
  }

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    File cache = new File(host.getCacheDir(), "map-tiles");
    cache.mkdirs();
    Configuration.getInstance().setOsmdroidBasePath(cache);
    File tiles = new File(cache, "tiles");
    tiles.mkdirs();
    Configuration.getInstance().setOsmdroidTileCache(tiles);
    Configuration.getInstance()
        .setUserAgentValue("LumeGallery/12.0 (https://github.com/acresking/Lume-gallery)");
    Configuration.getInstance().setTileFileSystemCacheMaxBytes(64L * 1024 * 1024);
    Configuration.getInstance().setTileFileSystemCacheTrimBytes(48L * 1024 * 1024);
    android.widget.FrameLayout root = new android.widget.FrameLayout(host);
    root.setBackgroundColor(host.bg);
    map = new MapView(host);
    map.setTileSource(
        new XYTileSource(
            "LumeOSM",
            0,
            19,
            256,
            ".png",
            new String[] {"https://tile.openstreetmap.org/"},
            "© OpenStreetMap contributors"));
    map.setMultiTouchControls(true);
    map.setBuiltInZoomControls(false);
    map.setMinZoomLevel(2d);
    map.setMaxZoomLevel(19d);
    map.setUseDataConnection(host.prefs.getBoolean("mapNetwork", true));
    map.getController().setZoom(13d);
    map.getController().setCenter(new GeoPoint(latitude, longitude));
    root.addView(map, new android.widget.FrameLayout.LayoutParams(-1, -1));
    LinearLayout chrome = host.column();
    chrome.setPadding(host.dp(12), host.dp(12), host.dp(12), 0);
    LinearLayout heading = host.row();
    heading.setPadding(host.dp(8), host.dp(6), host.dp(8), host.dp(6));
    heading.setElevation(host.dp(6));
    heading.setBackground(host.rounded(host.card, 22));
    heading.addView(host.iconButton("back", "חזרה מהמפה", this::dismiss));
    heading.addView(
        host.label("המקומות שלך", 20, host.ink, true), new LinearLayout.LayoutParams(0, -2, 1));
    heading.addView(
        host.iconButton(
            "info",
            "פרטיות ומקור המפה",
            () ->
                new PrivacyDialogBuilder(host)
                    .setTitle("מפה פרטית בתוך Lume")
                    .setMessage(
                        "התמונות נשארות במכשיר. אריחי OpenStreetMap דורשים רשת ומשתפים עם הספק את"
                            + " אזור המפה וכתובת IP. אין שימוש במיקום החי של הטלפון. אפשר לכבות רשת"
                            + " למפה בהגדרות.\n"
                            + "© OpenStreetMap contributors")
                    .setPositiveButton("הבנתי", null)
                    .show()));
    chrome.addView(heading);
    android.widget.FrameLayout.LayoutParams top =
        new android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
    root.addView(chrome, top);
    LinearLayout bottom = host.column();
    bottom.setPadding(host.dp(16), host.dp(12), host.dp(16), host.dp(10));
    bottom.setBackground(host.rounded(host.card, 24));
    bottom.setElevation(host.dp(8));
    status =
        host.label(
            host.prefs.getBoolean("mapNetwork", true)
                ? "צביטה לזום · הקש על סמן לבחירת תמונה"
                : "רשת למפה כבויה · מטמון בלבד",
            12,
            host.muted,
            false);
    bottom.addView(status);
    selection = host.column();
    bottom.addView(selection);
    LinearLayout controls = host.row();
    controls.addView(
        host.iconButton(
            "place",
            "מרכוז למקום המקורי",
            () -> map.getController().animateTo(new GeoPoint(latitude, longitude))));
    controls.addView(host.iconButton("plus", "התקרבות במפה", () -> map.getController().zoomIn()));
    loadButton = host.button("תמונות במפה", this::loadLibrary);
    controls.addView(loadButton, new LinearLayout.LayoutParams(0, -2, 1));
    bottom.addView(controls);
    TextView credit = host.label("© OpenStreetMap contributors", 11, host.muted, false);
    credit.setGravity(Gravity.CENTER);
    credit.setPadding(0, host.dp(6), 0, 0);
    android.text.SpannableString attributed = new android.text.SpannableString(credit.getText());
    attributed.setSpan(
        new android.text.style.URLSpan("https://www.openstreetmap.org/copyright"),
        0,
        attributed.length(),
        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    credit.setText(attributed);
    credit.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
    bottom.addView(credit);
    android.widget.FrameLayout.LayoutParams bottomLp =
        new android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
    bottomLp.setMargins(host.dp(12), 0, host.dp(12), host.dp(12));
    root.addView(bottom, bottomLp);
    root.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          var bars = insets.getInsets(WindowInsets.Type.systemBars());
          view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    add(latitude, longitude, "המיקום בתמונה", null);
    setContentView(root);
  }

  private android.graphics.drawable.Drawable pin() {
    if (pinDrawable != null) return pinDrawable;
    int w = host.dp(36), h = host.dp(44);
    android.graphics.Bitmap bitmap =
        android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
    android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
    canvas.scale(w / 36f, h / 44f);
    android.graphics.Path path = new android.graphics.Path();
    path.moveTo(18, 42);
    path.cubicTo(13, 34, 3, 25, 3, 17);
    path.cubicTo(3, -2, 33, -2, 33, 17);
    path.cubicTo(33, 25, 23, 34, 18, 42);
    path.close();
    android.graphics.Paint paint = new android.graphics.Paint(3);
    paint.setColor(0xffbda3ff);
    canvas.drawPath(path, paint);
    paint.setColor(android.graphics.Color.WHITE);
    paint.setStyle(android.graphics.Paint.Style.STROKE);
    paint.setStrokeWidth(2);
    canvas.drawPath(path, paint);
    paint.setStyle(android.graphics.Paint.Style.FILL);
    paint.setColor(0xff251b35);
    canvas.drawCircle(18, 16, 6, paint);
    pinDrawable = new android.graphics.drawable.BitmapDrawable(host.getResources(), bitmap);
    return pinDrawable;
  }

  void add(double lat, double lon, String name, MainActivity.Media item) {
    Marker marker = new Marker(map);
    marker.setIcon(pin());
    marker.setPosition(new GeoPoint(lat, lon));
    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
    marker.setTitle(name);
    marker.setOnMarkerClickListener(
        (m, v) -> {
          status.setText(name);
          if (item != null) select(item, name);
          map.getController().animateTo(m.getPosition());
          return true;
        });
    map.getOverlays().add(marker);
    map.invalidate();
  }

  void select(MainActivity.Media item, String name) {
    if (selection == null) return;
    selection.removeAllViews();
    LinearLayout card = host.row();
    card.setPadding(0, host.dp(12), 0, host.dp(8));
    ImageView image = new ImageView(host);
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    image.setBackground(host.rounded(host.bg, 14));
    image.setClipToOutline(true);
    card.addView(image, new LinearLayout.LayoutParams(host.dp(64), host.dp(64)));
    host.thumbnail(image, item, 160);
    LinearLayout text = host.column();
    text.setPadding(host.dp(12), 0, host.dp(8), 0);
    TextView title = host.label(name, 15, host.ink, true);
    title.setMaxLines(2);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    text.addView(title);
    text.addView(host.label("פתח תמונה  ←", 12, host.accent, true));
    card.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
    card.setContentDescription("פתח תמונה מהמפה: " + name);
    card.setOnClickListener(
        v -> {
          dismiss();
          host.viewer(item);
        });
    selection.addView(card);
  }

  void loadLibrary() {
    if (libraryLoaded) return;
    libraryLoaded = true;
    if (loadButton != null) {
      loadButton.setEnabled(false);
      loadButton.setText("טוען תמונות…");
    }
    ArrayList<MainActivity.Media> items = host.filtered();
    status.setText("קורא מיקומים מהמכשיר…");
    host.io.execute(
        () -> {
          int count = 0;
          for (MainActivity.Media item : items) {
            if (canceled.get() || count >= 500) break;
            if (item.video()) continue;
            try {
              android.net.Uri uri = item.uri;
              if ("media".equals(uri.getAuthority())
                  && host.checkSelfPermission(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
                      == android.content.pm.PackageManager.PERMISSION_GRANTED)
                uri = android.provider.MediaStore.setRequireOriginal(uri);
              double[] gps;
              try (InputStream in = host.getContentResolver().openInputStream(uri)) {
                if (in == null) continue;
                gps = new androidx.exifinterface.media.ExifInterface(in).getLatLong();
              }
              if (gps == null || !LocationNames.valid(gps[0], gps[1])) continue;
              count++;
              host.runOnUiThread(
                  () -> {
                    if (!canceled.get() && isShowing()) add(gps[0], gps[1], item.name, item);
                  });
            } catch (Exception ignored) {
            }
          }
          final int total = count;
          host.runOnUiThread(
              () -> {
                if (!canceled.get() && isShowing()) {
                  if (loadButton != null) loadButton.setText(total + " תמונות");
                  status.setText(
                      total == 0
                          ? "לא נמצאו תמונות עם GPS קריא באוסף הזה"
                          : total + " תמונות עם מיקום · הקש על סמן לבחירה");
                }
              });
        });
  }

  @Override
  public void show() {
    AppPrivacy.apply(getWindow(), host);
    getWindow().setWindowAnimations(host.motion() ? R.style.GallerySheetMotion : 0);
    super.show();
    getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    getWindow().setLayout(-1, -1);
    getWindow().setStatusBarColor(host.bg);
    getWindow().setNavigationBarColor(host.bg);
    map.onResume();
  }

  @Override
  public void dismiss() {
    boolean alreadyClosed = canceled.getAndSet(true);
    if (map != null && !alreadyClosed) {
      map.onPause();
      map.onDetach();
    }
    super.dismiss();
  }
}
