package il.luma.gallery;

import static org.junit.Assert.*;

import android.graphics.*;
import android.net.Uri;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w320dp-h640dp-mdpi")
public class CreationExperienceTest {
  long date(int year, int month, int day) {
    Calendar c = Calendar.getInstance();
    c.clear();
    c.set(year, month - 1, day, 12, 0);
    return c.getTimeInMillis();
  }

  MainActivity.Media media(int id, long when) {
    var m = new MainActivity.Media();
    m.uri = Uri.parse("content://media/external/images/media/" + id);
    m.mime = "image/jpeg";
    m.name = "sample-" + id;
    m.path = "DCIM/";
    m.date = when;
    return m;
  }

  @Test
  public void memoriesUseRealDatesAndExcludeHiddenAndFutureItems() {
    long now = date(2026, 6, 20);
    var old = media(1, date(2025, 6, 20));
    var recent = media(2, date(2026, 6, 18));
    var hidden = media(3, date(2024, 6, 20));
    var future = media(4, date(2027, 6, 20));
    var groups =
        MemoryCollections.build(
            List.of(old, recent, hidden, future), Set.of(hidden.uri.toString()), now);
    assertEquals(2, groups.size());
    assertEquals("week", groups.get(0).id);
    assertSame(recent, groups.get(0).items.get(0));
    assertEquals("year:2025", groups.get(1).id);
    assertSame(old, groups.get(1).items.get(0));
    assertTrue(
        MemoryCollections.build(List.of(media(5, date(2020, 1, 1))), Set.of(), now).isEmpty());
  }

  @Test
  public void memoryRangesCoverCalendarDaysAndRejectInvalidLeapYears() {
    long now = date(2024, 2, 29);
    assertFalse(MemoryCollections.matches("year:2023", media(1, date(2023, 3, 1)), now));
    assertTrue(MemoryCollections.matches("year:2020", media(2, date(2020, 2, 29)), now));
    assertFalse(MemoryCollections.matches("year:2030", media(3, date(2030, 2, 28)), now));
    assertFalse(MemoryCollections.matches("week", media(4, date(2024, 2, 22)), now));
    assertTrue(MemoryCollections.matches("week", media(5, date(2024, 2, 23)), now));
  }

  @Test
  public void pinnedChoicesPersistAndUnknownValuesAreIgnored() {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.prefs
          .edit()
          .putStringSet("pinnedCollections", Set.of("videos", "trash", "unknown"))
          .apply();
      assertEquals(List.of("videos", "trash"), a.pinnedCollections());
      a.prefs.edit().putStringSet("pinnedCollections", Set.of()).apply();
      assertEquals(3, a.pinnedCollections().size());
    }
  }

  @Test
  public void disablingMemoriesRemovesTheirUi() {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.media.clear();
      a.media.add(media(1, System.currentTimeMillis()));
      LinearLayout parent = a.column();
      a.prefs.edit().putBoolean("showMemories", false).apply();
      a.drawMemories(parent);
      assertEquals(0, parent.getChildCount());
      a.prefs.edit().putBoolean("showMemories", true).apply();
      a.drawMemories(parent);
      assertTrue(parent.getChildCount() > 0);
    }
  }

  @Test
  public void memoryCollectionFiltersOnlyTheRequestedDateRange() throws Exception {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.io.submit(() -> {}).get();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      a.media.clear();
      a.media.add(media(1, System.currentTimeMillis()));
      a.media.add(media(2, date(2000, 1, 1)));
      a.openCollection("memory:week");
      assertEquals(1, a.filtered().size());
      assertEquals("השבוע האחרון", a.collectionTitle());
    }
  }

  @Test
  public void collageCellsStayInsideTheCanvasAndNeverOverlap() {
    for (int count = 2; count <= 6; count++)
      for (int layout = 0; layout < 3; layout++) {
        var cells = CollageEngine.cells(count, 640, 480, layout, .02f);
        assertEquals(count, cells.size());
        for (int i = 0; i < cells.size(); i++) {
          RectF r = cells.get(i);
          assertTrue(r.left >= 0 && r.top >= 0 && r.right <= 640.01 && r.bottom <= 480.01);
          assertTrue(r.width() > 0 && r.height() > 0);
          for (int j = i + 1; j < cells.size(); j++) assertFalse(RectF.intersects(r, cells.get(j)));
        }
      }
    assertThrows(IllegalArgumentException.class, () -> CollageEngine.cells(7, 640, 480, 0, 0));
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  public void collageRenderingKeepsOriginalPixelsAndPlacesBothImages() {
    var red = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888);
    red.eraseColor(Color.RED);
    var blue = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888);
    blue.eraseColor(Color.BLUE);
    var output = CollageEngine.render(List.of(red, blue), 600, 0, 0, 0, Color.WHITE, false);
    assertEquals(Color.RED, output.getPixel(150, 300));
    assertEquals(Color.BLUE, output.getPixel(450, 300));
    assertEquals(Color.RED, red.getPixel(0, 0));
    assertEquals(Color.BLUE, blue.getPixel(0, 0));
    assertArrayEquals(new int[] {337, 600}, CollageEngine.dimensions(600, 2));
    output.recycle();
    red.recycle();
    blue.recycle();
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  public void collageEditorFitsLargeTextAndProtectsItsWindow() throws Exception {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.io.submit(() -> {}).get();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      ArrayList<MainActivity.Media> photos = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        var m = media(i, System.currentTimeMillis());
        var file = new java.io.File(a.getCacheDir(), "collage-sample-" + i + ".png");
        Bitmap image = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        image.eraseColor(i == 0 ? Color.RED : Color.BLUE);
        try (var stream = new java.io.FileOutputStream(file)) {
          image.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        image.recycle();
        m.uri = Uri.fromFile(file);
        photos.add(m);
      }
      a.getResources().getDisplayMetrics().scaledDensity = 1.6f;
      a.getResources().getConfiguration().fontScale = 1.6f;
      CollageStudio editor = new CollageStudio(a, photos);
      editor.show();
      editor.worker.submit(() -> {}).get();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertFalse(editor.loading);
      assertNotNull(editor.previewBitmap);
      assertTrue(editor.save.isEnabled());
      Button portrait = (Button) findText(editor.getWindow().getDecorView(), "אנכי 9:16");
      assertNotNull(portrait);
      portrait.performClick();
      assertTrue(portrait.isSelected());
      assertTrue(editor.previewBitmap.getHeight() > editor.previewBitmap.getWidth());
      assertTrue(
          (editor.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) == 0);
      ViewGroup host = editor.getWindow().getDecorView().findViewById(android.R.id.content);
      View root = host.getChildAt(0);
      root.measure(
          View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(560, View.MeasureSpec.EXACTLY));
      root.layout(0, 0, 320, 560);
      ViewGroup header = (ViewGroup) ((ViewGroup) root).getChildAt(0);
      for (int i = 0; i < header.getChildCount(); i++) {
        View child = header.getChildAt(i);
        assertTrue(child.getLeft() >= 0 && child.getRight() <= 320);
        for (int j = i + 1; j < header.getChildCount(); j++)
          assertFalse(
              child.getLeft() < header.getChildAt(j).getRight()
                  && header.getChildAt(j).getLeft() < child.getRight());
      }
      editor.dismiss();
    }
  }

  View findText(View view, String text) {
    if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View found = findText(group.getChildAt(i), text);
        if (found != null) return found;
      }
    }
    return null;
  }
}
