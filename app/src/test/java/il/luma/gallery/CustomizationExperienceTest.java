package il.luma.gallery;

import static org.junit.Assert.*;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.exifinterface.media.ExifInterface;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w320dp-h640dp-mdpi")
public class CustomizationExperienceTest {
  MainActivity.Media photo() {
    MainActivity.Media m = new MainActivity.Media();
    m.uri = Uri.parse("content://media/external/images/media/999");
    m.name = "sample.jpg";
    m.mime = "image/jpeg";
    m.path = "DCIM/Camera/";
    m.date = System.currentTimeMillis();
    m.width = 1200;
    m.height = 900;
    return m;
  }

  View find(View v, String tag) {
    if (tag.equals(v.getTag()) || tag.equals(v.getContentDescription())) return v;
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) {
        View r = find(g.getChildAt(i), tag);
        if (r != null) return r;
      }
    }
    return null;
  }

  @Test
  public void settingsSearchRetainsHiddenControlsAndStoredChoices() {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.tab = "settings";
      a.render();
      View film = find(a.root, "settings-item:פס תמונות ממוזערות בצופה");
      assertNotNull(film);
      film.performClick();
      assertFalse(a.prefs.getBoolean("viewerFilmstrip", true));
      EditText search = (EditText) find(a.root, "חיפוש בהגדרות");
      search.setText("אוטומטי");
      assertEquals(View.GONE, film.getVisibility());
      assertEquals(View.VISIBLE, find(a.root, "settings-item:כללי ניקוי אוטומטי").getVisibility());
      search.setText("");
      assertEquals(View.VISIBLE, film.getVisibility());
      assertFalse(a.prefs.getBoolean("viewerFilmstrip", true));
      assertNotNull(find(a.root, "settings-item:AI מקומי · מודלים, תגיות וכלי דיוקן"));
      assertNotNull(find(a.root, "settings-item:בחירת מדיה והרשאות"));
    }
  }

  @Test
  public void largeTextSettingsHaveSeparateLabelAndSwitchBounds() {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.getResources().getDisplayMetrics().scaledDensity = 1.6f;
      a.tab = "settings";
      a.render();
      a.root.measure(
          View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
      a.root.layout(0, 0, 320, 640);
      FrameLayout row =
          (FrameLayout)
              find(
                  a.root,
                  "settings-item:שמות עיר ומדינה · שירות המיקום של Android, עשוי להשתמש ברשת");
      View label = row.getChildAt(0), toggle = row.getChildAt(1);
      assertTrue(label.getWidth() > 80);
      assertTrue(toggle.getWidth() >= 48);
      assertFalse(
          Rect.intersects(
              new Rect(label.getLeft(), label.getTop(), label.getRight(), label.getBottom()),
              new Rect(toggle.getLeft(), toggle.getTop(), toggle.getRight(), toggle.getBottom())));
    }
  }

  @Test
  public void hidingCollectionSectionsDoesNotDeleteAlbumsOrMedia() {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.media.clear();
      a.media.add(photo());
      a.prefs
          .edit()
          .putBoolean("collectionFolders", false)
          .putBoolean("collectionPinned", false)
          .apply();
      a.tab = "albums";
      a.render();
      assertEquals(1, a.media.size());
      assertNotNull(find(a.root, "כל כלי הגלריה"));
      a.prefs.edit().putBoolean("collectionFolders", true).apply();
      a.render();
      assertEquals(1, a.media.size());
      a.openCollection("videos");
      assertTrue(a.filtered().isEmpty());
      a.openCollection("");
      assertEquals(1, a.filtered().size());
    }
  }

  @Test
  public void swipeOpensOnceAndRejectsHorizontalDisabledAndCanceledGestures() {
    var context = org.robolectric.RuntimeEnvironment.getApplication();
    FrameLayout parent = new FrameLayout(context);
    DetailSwipeFrame frame = new DetailSwipeFrame(context);
    parent.addView(frame);
    int[] calls = {0};
    frame.open = () -> calls[0]++;
    assertTrue(gesture(frame, 100, 280, false));
    assertEquals(1, calls[0]);
    assertFalse(gesture(frame, 290, 60, false));
    assertEquals(1, calls[0]);
    frame.allowed = () -> false;
    assertFalse(gesture(frame, 100, 280, false));
    frame.allowed = () -> true;
    assertTrue(gesture(frame, 100, 280, true));
    assertEquals(1, calls[0]);
  }

  @Test
  public void settingsCategoriesFilterWithoutDiscardingControls() {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.tab = "settings";
      a.render();
      clickText(a.root, "ספרייה");
      assertEquals(View.GONE, find(a.root, "settings-item:רגעים מעל התמונות").getVisibility());
      assertEquals(
          View.VISIBLE,
          find(a.root, "settings-item:שמות עיר ומדינה · שירות המיקום של Android, עשוי להשתמש ברשת")
              .getVisibility());
      clickText(a.root, "הכול");
      assertEquals(View.VISIBLE, find(a.root, "settings-item:רגעים מעל התמונות").getVisibility());
    }
  }

  boolean clickText(View v, String text) {
    if (v instanceof TextView && text.contentEquals(((TextView) v).getText())) {
      v.performClick();
      return true;
    }
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) if (clickText(g.getChildAt(i), text)) return true;
    }
    return false;
  }

  @Test
  public void memoryScopeAndViewerStripSettingsAffectActualViews() {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.media.clear();
      var m = photo();
      a.media.add(m);
      a.prefs
          .edit()
          .putBoolean("memoriesPhotos", false)
          .putBoolean("viewerFilmstrip", false)
          .putBoolean("gestureHint", false)
          .apply();
      a.tab = "media";
      a.render();
      assertFalse(containsText(a.root, "רגעים מהספרייה"));
      ModernActivity.Viewer viewer = a.new Viewer(m);
      viewer.show();
      assertEquals(View.GONE, viewer.strip.getVisibility());
      viewer.toggleControls();
      viewer.toggleControls();
      assertEquals(View.GONE, viewer.strip.getVisibility());
      assertEquals(View.GONE, viewer.gestureHint.getVisibility());
      viewer.dismiss();
      a.prefs.edit().putBoolean("memoriesPhotos", true).apply();
      a.render();
      a.root.measure(
          View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
      a.root.layout(0, 0, 320, 640);
      assertNotNull(find(a.root, "הרחבת רגעים"));
      assertFalse(containsText(a.root, "רגעים מהספרייה"));
    }
  }

  boolean containsText(View v, String text) {
    if (v instanceof TextView && text.contentEquals(((TextView) v).getText())) return true;
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++)
        if (containsText(g.getChildAt(i), text)) return true;
    }
    return false;
  }

  @Test
  public void multitouchBlocksSwipeEvenIfZoomReturnsToFit() {
    var context = org.robolectric.RuntimeEnvironment.getApplication();
    FrameLayout parent = new FrameLayout(context);
    DetailSwipeFrame frame = new DetailSwipeFrame(context);
    parent.addView(frame);
    int[] calls = {0};
    frame.open = () -> calls[0]++;
    MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100, 400, 0);
    frame.onInterceptTouchEvent(down);
    MotionEvent pointer = MotionEvent.obtain(0, 10, MotionEvent.ACTION_POINTER_DOWN, 100, 400, 0);
    frame.onInterceptTouchEvent(pointer);
    MotionEvent move = MotionEvent.obtain(0, 60, MotionEvent.ACTION_MOVE, 100, 100, 0);
    assertFalse(frame.onInterceptTouchEvent(move));
    assertEquals(0, calls[0]);
    down.recycle();
    pointer.recycle();
    move.recycle();
  }

  boolean gesture(DetailSwipeFrame frame, float x, float up, boolean cancel) {
    MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100, 400, 0);
    MotionEvent move = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, x, 400 - up, 0);
    MotionEvent end =
        MotionEvent.obtain(
            0, 70, cancel ? MotionEvent.ACTION_CANCEL : MotionEvent.ACTION_UP, x, 400 - up, 0);
    frame.onInterceptTouchEvent(down);
    boolean intercept = frame.onInterceptTouchEvent(move);
    if (intercept) frame.onTouchEvent(end);
    down.recycle();
    move.recycle();
    end.recycle();
    return intercept;
  }

  @Test
  public void metadataGpsToggleReadsLocallyWithoutChangingOriginal() throws Exception {
    var context = org.robolectric.RuntimeEnvironment.getApplication();
    java.io.File f = new java.io.File(context.getCacheDir(), "gps.jpg");
    Bitmap b = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
    try (var out = new java.io.FileOutputStream(f)) {
      b.compress(Bitmap.CompressFormat.JPEG, 90, out);
    }
    ExifInterface exif = new ExifInterface(f);
    exif.setLatLong(12.345, 23.456);
    exif.saveAttributes();
    var m = photo();
    m.uri = Uri.fromFile(f);
    byte[] original = java.nio.file.Files.readAllBytes(f.toPath());
    context.getSharedPreferences("luma", 0).edit().putBoolean("detailsLocation", true).apply();
    assertTrue(MediaDetails.read(context, m).containsKey("מיקום GPS"));
    context.getSharedPreferences("luma", 0).edit().putBoolean("detailsLocation", false).apply();
    assertFalse(MediaDetails.read(context, m).containsKey("מיקום GPS"));
    assertArrayEquals(original, java.nio.file.Files.readAllBytes(f.toPath()));
  }

  @Test
  public void detailsCopyIsExplicitSensitiveAndSheetCanExpand() throws Exception {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.details(photo());
      var sheet = a.infoSheet;
      a.io.submit(() -> {}).get();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertTrue(sheet.isShowing());
      int old = sheet.getWindow().getAttributes().height;
      sheet.resize(true);
      assertTrue(sheet.getWindow().getAttributes().height > old);
      sheet.copy("שם", "sample.jpg");
      ClipboardManager clipboard = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
      assertEquals("sample.jpg", clipboard.getPrimaryClip().getItemAt(0).getText());
      assertTrue(
          clipboard
              .getPrimaryClip()
              .getDescription()
              .getExtras()
              .getBoolean("android.content.extra.IS_SENSITIVE"));
      assertTrue(
          (sheet.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) == 0);
      sheet.dismiss();
    }
  }
}
