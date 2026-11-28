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
  final LinkedHashMap<String,PlaceCluster> clusters=new LinkedHashMap<>();
  private PlaceCluster selectedCluster;
  private Marker centerMarker;
  private MapAlbumDialog locationGallery;
  private final android.os.CancellationSignal thumbnailsCanceled=new android.os.CancellationSignal();
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

  android.graphics.drawable.Drawable photoPin(PlaceCluster group) {
    int w=host.dp(44),h=host.dp(52);
    android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888);
    android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);canvas.scale(w/44f,h/52f);
    android.graphics.Paint paint=new android.graphics.Paint(3);paint.setColor(0xffcfb5ff);canvas.drawRoundRect(0,0,44,44,12,12,paint);
    if(group.thumbnail!=null) {
      canvas.save();android.graphics.Path clip=new android.graphics.Path();clip.addRoundRect(new android.graphics.RectF(3,3,41,41),9,9,android.graphics.Path.Direction.CW);canvas.clipPath(clip);
      android.graphics.Bitmap image=group.thumbnail;int side=Math.min(image.getWidth(),image.getHeight());int left=(image.getWidth()-side)/2,top=(image.getHeight()-side)/2;
      canvas.drawBitmap(image,new android.graphics.Rect(left,top,left+side,top+side),new android.graphics.RectF(3,3,41,41),paint);canvas.restore();
    }else{GalleryIcons icon=new GalleryIcons("photos",0xff251b35);icon.setBounds(10,10,34,34);icon.draw(canvas);}
    android.graphics.Path tail=new android.graphics.Path();tail.moveTo(16,43);tail.lineTo(22,51);tail.lineTo(28,43);tail.close();canvas.drawPath(tail,paint);
    paint.setColor(0xff251b35);canvas.drawRoundRect(22,0,44,18,8,8,paint);
    paint.setColor(android.graphics.Color.WHITE);paint.setTextSize(10);paint.setTextAlign(android.graphics.Paint.Align.CENTER);paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);canvas.drawText(String.valueOf(group.members.size()),33,12,paint);
    return new android.graphics.drawable.BitmapDrawable(host.getResources(),bitmap);
  }

  void add(double lat,double lon,String name,MainActivity.Media item) {
    if(item==null){centerMarker=new Marker(map);centerMarker.setIcon(pin());centerMarker.setPosition(new GeoPoint(lat,lon));centerMarker.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);map.getOverlays().add(centerMarker);return;}
    String key=PlaceCluster.key(lat,lon);PlaceCluster group=clusters.get(key);
    if(group==null){
      group=new PlaceCluster(lat,lon);clusters.put(key,group);
      group.marker=new Marker(map);group.marker.setPosition(new GeoPoint(lat,lon));group.marker.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);
      final PlaceCluster chosen=group;group.marker.setOnMarkerClickListener((marker,view)->{selectGroup(chosen);map.getController().animateTo(marker.getPosition());return true;});
      if(centerMarker!=null){map.getOverlays().remove(centerMarker);centerMarker=null;}map.getOverlays().add(group.marker);
    }
    group.add(item);group.marker.setTitle(group.members.size()+" תמונות");group.marker.setIcon(photoPin(group));
    if(!group.thumbnailRequested){
      group.thumbnailRequested=true;final PlaceCluster target=group;
      host.thumbs.execute(()->{
        try{
          android.graphics.Bitmap image=host.getContentResolver().loadThumbnail(item.uri,new android.util.Size(96,96),thumbnailsCanceled);
          host.runOnUiThread(()->{if(!canceled.get() && isShowing()){target.thumbnail=image;target.marker.setIcon(photoPin(target));map.invalidate();}});
        }catch(Exception ignored){}
      });
    }
    map.invalidate();
  }

  void select(MainActivity.Media item,String name) {
    add(latitude,longitude,name,item);
    PlaceCluster group=clusters.get(PlaceCluster.key(latitude,longitude));group.name=name;selectGroup(group);
    loadLibrary();
  }

  void selectGroup(PlaceCluster group) {
    selectedCluster=group;selection.removeAllViews();
    ArrayList<MainActivity.Media> visible=group.visible(host.filtered());
    if(visible.isEmpty()){status.setText("התמונות אינן זמינות באוסף הנוכחי");return;}
    String name=group.name.isBlank()?"תמונות מהמיקום הזה":group.name;
    LinearLayout card=host.row();card.setPadding(0,host.dp(12),0,host.dp(8));
    ImageView image=new ImageView(host);image.setScaleType(ImageView.ScaleType.CENTER_CROP);card.addView(image,new LinearLayout.LayoutParams(host.dp(64),host.dp(64)));host.thumbnail(image,visible.get(0),160);image.setBackground(host.rounded(host.bg,14));image.setClipToOutline(true);
    LinearLayout text=host.column();text.setPadding(host.dp(12),0,host.dp(8),0);
    TextView title=host.label(name,15,host.ink,true);title.setMaxLines(2);text.addView(title);
    text.addView(host.label("פתח את כל "+visible.size()+" התמונות  ←",13,host.accent,true));card.addView(text,new LinearLayout.LayoutParams(0,-2,1));
    card.setContentDescription("פתח את כל "+visible.size()+" התמונות מהמיקום");
    card.setOnClickListener(view->{
      ArrayList<MainActivity.Media> current=group.visible(host.filtered());if(current.isEmpty())return;
      if(locationGallery!=null)locationGallery.dismiss();locationGallery=new MapAlbumDialog(host,name,current);locationGallery.show();
    });selection.addView(card);
    if(group.name.isBlank())host.io.execute(()->{
      String resolved=LocationNames.resolve(host,group.latitude,group.longitude);
      host.runOnUiThread(()->{if(!canceled.get() && !resolved.isBlank()){group.name=resolved;if(selectedCluster==group)selectGroup(group);}});
    });
  }

  void loadLibrary() {
    if (libraryLoaded) return;
    libraryLoaded = true;
    if (loadButton != null) {
      loadButton.setEnabled(false);
      loadButton.setText("טוען תמונות…");
    }
    ArrayList<MainActivity.Media> items = host.filtered();
    Map<String,double[]> indexed=new HashMap<>();
    org.json.JSONArray albums=host.personalAlbums();
    for(int i=0;i<albums.length();i++) {
      org.json.JSONObject g=albums.optJSONObject(i);if(g==null || !"place".equals(g.optString("kind")))continue;
      try {
        String[] cell=g.optString("id").replace("place:","").split(",");
        double lat=g.has("latitude")?g.optDouble("latitude"):Double.parseDouble(cell[0].trim());
        double lon=g.has("longitude")?g.optDouble("longitude"):Double.parseDouble(cell[1].trim());
        if(!LocationNames.valid(lat,lon))continue;
        org.json.JSONArray members=g.optJSONArray("items");
        if(members!=null)for(int j=0;j<members.length();j++)indexed.put(members.optString(j),new double[]{lat,lon});
      }catch(Exception ignored){}
    }
    status.setText("קורא מיקומים מהמכשיר…");
    host.io.execute(()->{
      LinkedHashMap<String,PlaceCluster> collected=new LinkedHashMap<>();
      for(MainActivity.Media item:items) {
        if(canceled.get())break;
        if(item.video())continue;
        double[] gps=indexed.get(item.uri.toString());
        if(gps==null){
          if(collected.size()>=500)continue;
          gps=readPoint(item);
        }
        if(gps==null || !LocationNames.valid(gps[0],gps[1]))continue;
        String key=PlaceCluster.key(gps[0],gps[1]);PlaceCluster group=collected.get(key);
        if(group==null){if(collected.size()>=500)continue;group=new PlaceCluster(gps[0],gps[1]);collected.put(key,group);}
        group.add(item);
      }
      host.runOnUiThread(()->{
        if(canceled.get() || !isShowing())return;
        int total=0;
        for(PlaceCluster group:collected.values()){
          if(group.members.isEmpty())continue;
          MainActivity.Media first=group.members.values().iterator().next();
          add(group.latitude,group.longitude,"",first);
          PlaceCluster target=clusters.get(PlaceCluster.key(group.latitude,group.longitude));
          target.members.putAll(group.members);target.marker.setIcon(photoPin(target));total+=group.members.size();
        }
        if(loadButton!=null)loadButton.setText(total+" תמונות");
        if(selectedCluster!=null)selectGroup(selectedCluster);
        status.setText(total==0?"לא נמצאו תמונות עם מיקום באוסף הזה":total+" תמונות ב־"+clusters.size()+" מקומות · הקש על תמונת סמן");
        map.invalidate();
      });
    });
  }

  private double[] readPoint(MainActivity.Media item) {
    try {
      android.net.Uri uri=item.uri;
      if("media".equals(uri.getAuthority()) && host.checkSelfPermission(android.Manifest.permission.ACCESS_MEDIA_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED)
        uri=android.provider.MediaStore.setRequireOriginal(uri);
      try(InputStream in=host.getContentResolver().openInputStream(uri)){
        return in==null?null:new androidx.exifinterface.media.ExifInterface(in).getLatLong();
      }
    }catch(Exception ignored){return null;}
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
      thumbnailsCanceled.cancel();
      if(locationGallery!=null)locationGallery.dismiss();
      selectedCluster=null;
      clusters.clear();
      map.onPause();
      map.onDetach();
    }
    super.dismiss();
  }
}
