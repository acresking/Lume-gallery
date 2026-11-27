package il.luma.gallery;

import static org.junit.Assert.*;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DesignRefinementTest {
  Bitmap people() {
    Bitmap b = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(b);
    Paint p = new Paint();
    p.setColor(Color.RED);
    c.drawRect(10, 10, 40, 90, p);
    p.setColor(Color.BLUE);
    c.drawRect(60, 10, 90, 90, p);
    return b;
  }

  void event(SubjectBoard b, int action, float x, float y) {
    MotionEvent e = MotionEvent.obtain(0, 10, action, x, y, 0);
    b.onTouchEvent(e);
    e.recycle();
  }

  SubjectBoard board() throws Exception {
    Bitmap source = people();
    SubjectBoard b =
        new SubjectBoard(
            RuntimeEnvironment.getApplication(), SubjectCutout.select(source, .2f, .5f), source);
    b.layout(0, 0, 100, 100);
    return b;
  }

  @Test
  public void maskCorrectionsSupportUndoRedoAndReset() throws Exception {
    SubjectBoard b = board();
    event(b, MotionEvent.ACTION_DOWN, 20, 50);
    event(b, MotionEvent.ACTION_UP, 20, 50);
    assertEquals(0, Color.alpha(b.image.getPixel(20, 50)));
    b.undo();
    assertEquals(Color.RED, b.image.getPixel(20, 50));
    b.redo();
    assertEquals(0, Color.alpha(b.image.getPixel(20, 50)));
    b.reset();
    assertEquals(Color.RED, b.image.getPixel(20, 50));
    assertEquals(Color.RED, b.original.getPixel(20, 50));
  }

  @Test
  public void choosingAnotherPersonReplacesMaskAndCanBeUndone() throws Exception {
    SubjectBoard b = board();
    b.picking = true;
    event(b, MotionEvent.ACTION_DOWN, 75, 50);
    event(b, MotionEvent.ACTION_UP, 75, 50);
    assertFalse(b.picking);
    assertEquals(Color.BLUE, b.image.getPixel(75, 50));
    assertEquals(0, Color.alpha(b.image.getPixel(20, 50)));
    b.undo();
    assertEquals(Color.RED, b.image.getPixel(20, 50));
  }

  @Test
  public void brushSizeChangesAffectedAreaAndDisabledBoardCannotEdit() throws Exception {
    SubjectBoard a = board(), b = board();
    a.brushSize = .01f;
    b.brushSize = .1f;
    event(a, MotionEvent.ACTION_DOWN, 25, 50);
    event(b, MotionEvent.ACTION_DOWN, 25, 50);
    assertTrue(Color.alpha(a.image.getPixel(28, 50)) > Color.alpha(b.image.getPixel(28, 50)));
    b.setEnabled(false);
    int old = b.image.getPixel(25, 30);
    event(b, MotionEvent.ACTION_DOWN, 25, 30);
    assertEquals(old, b.image.getPixel(25, 30));
  }

  @Test
  public void compactEditorChoicesUseTwoColumnsAndInsetControlCards() {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      assertEquals(2, StudioUI.choiceColumns(a));
      LinearLayout parent = a.column();
      TextView value = a.label("50", 12, StudioUI.ACCENT, true);
      LinearLayout setting = StudioUI.setting(a, parent, "חשיפה", value);
      assertSame(setting, parent.getChildAt(0));
      assertTrue(value.getMinWidth() >= 48);
      assertNotNull(setting.getBackground());
    }
  }

  @Test
  public void mapStaysInsideAppAndHonorsNetworkOff() throws Exception {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.prefs.edit().putBoolean("mapNetwork", false).commit();
      PlacesMap map = new PlacesMap(a, 0, 0);
      map.show();
      assertFalse(map.map.useDataConnection());
      assertEquals(-1, map.getWindow().getAttributes().width);
      assertEquals(-1, map.getWindow().getAttributes().height);
      ViewGroup mapFrame = map.getWindow().getDecorView().findViewById(android.R.id.content);
      capture(mapFrame.getChildAt(0), "places-map-v15");
      map.dismiss();
      assertTrue(map.canceled.get());
    }
  }

  @Test
  public void extractionControlsStayScrollableAtLargeFont() throws Exception {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.getResources().getDisplayMetrics().scaledDensity = 1.5f;
      Bitmap source = people();
      SubjectStudio d = new SubjectStudio(a, SubjectCutout.select(source, .2f, .5f), source);
      d.show();
      ViewGroup host = d.getWindow().getDecorView().findViewById(android.R.id.content);
      View root = host.getChildAt(0);
      root.measure(
          View.MeasureSpec.makeMeasureSpec(320, 1073741824),
          View.MeasureSpec.makeMeasureSpec(560, 1073741824));
      root.layout(0, 0, 320, 560);
      assertTrue(d.board.getHeight() > 100);
      assertNotNull(find(root, "בחר אדם מחדש"));
      assertTrue(hasScroll(root));
      capture(root, "subject-studio-v15");
      d.dismiss();
    }
  }

  @Test
  public void collectionMapDoesNotCenterOnArchivedPlaces() throws Exception {
    try (var controller = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = controller.get();
      a.io.submit(() -> {}).get();
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      a.prefs.edit().putBoolean("mapNetwork", false).commit();
      a.media.clear();
      MainActivity.Media hidden = new MainActivity.Media();
      hidden.uri = android.net.Uri.parse("content://media/external/images/media/81");
      hidden.name = "hidden.jpg";
      hidden.mime = "image/jpeg";
      hidden.path = "Camera";
      MainActivity.Media visible = new MainActivity.Media();
      visible.uri = android.net.Uri.parse("content://media/external/images/media/82");
      visible.name = "visible.jpg";
      visible.mime = "image/jpeg";
      visible.path = "Camera";
      a.media.add(hidden);
      a.media.add(visible);
      java.lang.reflect.Field archiveField = ModernActivity.class.getDeclaredField("archived");
      archiveField.setAccessible(true);
      ((java.util.Set<String>) archiveField.get(a)).add(hidden.uri.toString());
      org.json.JSONArray groups = new org.json.JSONArray();
      groups.put(
          new org.json.JSONObject()
              .put("kind", "place")
              .put("id", "place:10,10")
              .put("items", new org.json.JSONArray().put(hidden.uri.toString())));
      groups.put(
          new org.json.JSONObject()
              .put("kind", "place")
              .put("id", "place:20,20")
              .put("items", new org.json.JSONArray().put(visible.uri.toString())));
      a.prefs.edit().putString("personalAlbums", groups.toString()).commit();
      a.openPlacesMap();
      assertNotNull(a.mapSheet);
      assertEquals(20, a.mapSheet.latitude, 0);
      a.mapSheet.dismiss();
    }
  }

  void capture(View root, String name) throws Exception {
    root.measure(
        View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
    root.layout(0, 0, 320, 640);
    Bitmap image = Bitmap.createBitmap(320, 640, Bitmap.Config.ARGB_8888);
    root.draw(new Canvas(image));
    java.io.File dir = new java.io.File("build/previews");
    dir.mkdirs();
    try (var out = new java.io.FileOutputStream(new java.io.File(dir, name + ".png"))) {
      image.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
  }

  View find(View v, String name) {
    if (v instanceof TextView && name.contentEquals(((TextView) v).getText())) return v;
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) {
        View result = find(g.getChildAt(i), name);
        if (result != null) return result;
      }
    }
    return null;
  }

  boolean hasScroll(View v) {
    if (v instanceof ScrollView) return true;
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) if (hasScroll(g.getChildAt(i))) return true;
    }
    return false;
  }
}
