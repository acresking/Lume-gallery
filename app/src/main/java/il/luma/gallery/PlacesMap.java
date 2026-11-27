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
  private boolean libraryLoaded;

  PlacesMap(ModernActivity activity, double lat, double lon) {
    super(activity);
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
    LinearLayout root = host.column();
    root.setBackground(host.rounded(host.bg, 28));
    root.setPadding(host.dp(12), host.dp(12), host.dp(12), host.dp(12));
    LinearLayout heading = host.row();
    heading.addView(
        host.label("המקומות שלך", 23, host.ink, true), new LinearLayout.LayoutParams(0, -2, 1));
    heading.addView(host.iconButton("close", "סגירת מפה", this::dismiss));
    root.addView(heading);
    status =
        host.label(
            host.prefs.getBoolean("mapNetwork", true)
                ? "מפה בתוך Lume · גרירה וצביטה לזום"
                : "רשת למפה כבויה · יוצגו אריחים שכבר נשמרו, אם יש",
            13,
            host.ink,
            false);
    status.setPadding(host.dp(8), host.dp(8), host.dp(8), host.dp(12));
    root.addView(status);
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
    map.setMinZoomLevel(2d);
    map.setMaxZoomLevel(19d);
    map.setUseDataConnection(host.prefs.getBoolean("mapNetwork", true));
    map.getController().setZoom(13d);
    map.getController().setCenter(new GeoPoint(latitude, longitude));
    root.addView(map, new LinearLayout.LayoutParams(-1, 0, 1));
    add(latitude, longitude, "המיקום בתמונה", null);
    LinearLayout controls = host.row();
    controls.addView(
        host.button(
            "מרכז למיקום", () -> map.getController().animateTo(new GeoPoint(latitude, longitude))),
        new LinearLayout.LayoutParams(0, -2, 1));
    controls.addView(
        host.button("תמונות מהאוסף במפה", this::loadLibrary),
        new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(controls);
    TextView credit =
        host.label(
            "© OpenStreetMap contributors · המפה דורשת רשת; האזור המבוקש וכתובת IP נשלחים לספק"
                + " האריחים. תמונות אינן נשלחות.",
            12,
            host.ink,
            false);
    credit.setPadding(host.dp(8), host.dp(10), host.dp(8), host.dp(8));
    android.text.SpannableString attributed = new android.text.SpannableString(credit.getText());
    attributed.setSpan(
        new android.text.style.URLSpan("https://www.openstreetmap.org/copyright"),
        0,
        "© OpenStreetMap contributors".length(),
        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    credit.setText(attributed);
    credit.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
    root.addView(credit);
    setContentView(root);
  }

  void add(double lat, double lon, String name, MainActivity.Media item) {
    Marker marker = new Marker(map);
    marker.setPosition(new GeoPoint(lat, lon));
    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
    marker.setTitle(name);
    marker.setOnMarkerClickListener(
        (m, v) -> {
          status.setText(name);
          if (item != null)
            new PrivacyDialogBuilder(host)
                .setTitle(name)
                .setItems(
                    new String[] {"פתח תמונה", "מרכז במפה"},
                    (d, n) -> {
                      if (n == 0) {
                        dismiss();
                        host.viewer(item);
                      } else map.getController().animateTo(m.getPosition());
                    })
                .show();
          else m.showInfoWindow();
          return true;
        });
    map.getOverlays().add(marker);
    map.invalidate();
  }

  void loadLibrary() {
    if (libraryLoaded) return;
    libraryLoaded = true;
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
                if (!canceled.get() && isShowing())
                  status.setText(
                      total + " תמונות עם מיקום נוספו · עד 500 סמנים · הקש על סמן לפתיחה");
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
    getWindow().setLayout(-1, (int) (host.getResources().getDisplayMetrics().heightPixels * .9f));
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
