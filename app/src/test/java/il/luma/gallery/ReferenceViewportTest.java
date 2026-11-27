package il.luma.gallery;

import static org.junit.Assert.*;

import android.graphics.*;
import android.net.Uri;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w320dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReferenceViewportTest {
  void fill(ModernActivity a) throws Exception {
    a.io.submit(() -> {}).get();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    a.media.clear();
    for (int i = 0; i < 150; i++) {
      MainActivity.Media m = new MainActivity.Media();
      m.uri = Uri.parse("content://media/external/images/media/" + (1000 + i));
      m.name = "sample" + i;
      m.mime = i % 5 == 0 ? "video/mp4" : "image/jpeg";
      m.path = "DCIM/Camera/";
      m.date = System.currentTimeMillis() - (i / 15) * 86400000L;
      m.width = 160;
      m.height = 120;
      a.media.add(m);
      Bitmap b = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888);
      b.eraseColor(Color.rgb(70 + i % 100, 100, 180));
      a.cache.put(m.uri.toString(), b);
    }
    a.prefs.edit().putBoolean("motion", false).apply();
    a.render();
    layout(a);
  }

  void layout(ModernActivity a) {
    a.root.measure(
        View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
    a.root.layout(0, 0, 320, 720);
  }

  GalleryGrid grid(ModernActivity a) {
    return (GalleryGrid) findGrid(a.body);
  }

  RecyclerView findGrid(View v) {
    if (v instanceof RecyclerView) return (RecyclerView) v;
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) {
        RecyclerView r = findGrid(g.getChildAt(i));
        if (r != null) return r;
      }
    }
    return null;
  }

  View find(View v, String desc) {
    if (desc.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription()))
      return v;
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) {
        View r = find(g.getChildAt(i), desc);
        if (r != null) return r;
      }
    }
    return null;
  }

  @Test
  public void photoGridGetsMostOfViewportAndChromeScrollsAway() throws Exception {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      fill(a);
      GalleryGrid grid = grid(a);
      assertTrue(grid.getHeight() > 720 * .72);
      assertTrue(a.galleryChrome.getHeight() <= 56);
      assertNotNull(find(a.root, "הרחבת רגעים"));
      grid.scrollBy(0, 620);
      layout(a);
      assertTrue(((GridLayoutManager) grid.getLayoutManager()).findFirstVisibleItemPosition() > 0);
      assertNull(find(a.root, "הרחבת רגעים"));
      assertTrue(grid.getHeight() > 720 * .72);
    }
  }

  @Test
  public void momentsExpandOnlyOnRequestAndExpandedContentAlsoScrollsAway() throws Exception {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      fill(a);
      find(a.root, "הרחבת רגעים").performClick();
      layout(a);
      assertTrue(a.prefs.getBoolean("memoriesExpanded", false));
      assertTrue(a.galleryChrome.getHeight() > 120);
      assertNotNull(find(a.root, "צמצום רגעים"));
      grid(a).scrollBy(0, 900);
      layout(a);
      assertTrue(
          ((GridLayoutManager) grid(a).getLayoutManager()).findFirstVisibleItemPosition() > 0);
      assertNull(find(a.root, "צמצום רגעים"));
    }
  }

  @Test
  public void referencePinchUsesAllPhotosInMonthAndYearMosaics() throws Exception {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      fill(a);
      a.applyGridDensity(5);
      layout(a);
      PinchGrid pinch = new PinchGrid(a, grid(a));
      pinch.change(1);
      assertEquals(9, a.prefs.getInt("columns", 0));
      assertEquals("photos", a.viewMode);
      int count = 0;
      for (int i = 0; i < grid(a).getAdapter().getItemCount(); i++)
        if (a.gridMedia(i) != null) count++;
      assertEquals(150, count);
      pinch.change(1);
      assertEquals(15, ((GridLayoutManager) grid(a).getLayoutManager()).getSpanCount());
      pinch.change(-1);
      assertEquals(9, a.prefs.getInt("columns", 0));
      pinch.change(-1);
      assertEquals(5, a.prefs.getInt("columns", 0));
    }
  }

  @Test
  public void nextDensityStopsMatchLargeNormalAndDenseReferenceGrids() {
    assertEquals(3, PinchGrid.nextColumns(2, 1));
    assertEquals(5, PinchGrid.nextColumns(3, 1));
    assertEquals(9, PinchGrid.nextColumns(5, 1));
    assertEquals(15, PinchGrid.nextColumns(9, 1));
    assertEquals(15, PinchGrid.nextColumns(15, 1));
    assertEquals(9, PinchGrid.nextColumns(15, -1));
    assertEquals(3, PinchGrid.nextColumns(4, -1));
    assertEquals(2, PinchGrid.nextColumns(2, -1));
  }

  @Test
  public void pinchKeepsThePointInsideTheAnchoredImageVertically() throws Exception {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      fill(a);
      GalleryGrid grid = grid(a);
      grid.scrollBy(0, 650);
      layout(a);
      float x = 160, y = 230;
      View child = grid.findChildViewUnder(x, y);
      assertNotNull(child);
      MainActivity.Media item = a.gridMedia(grid.getChildAdapterPosition(child));
      assertNotNull(item);
      float fraction = (y - child.getTop()) / child.getHeight();
      new PinchGrid(a, grid).changeAt(1, x, y);
      layout(a);
      View after =
          ((GridLayoutManager) grid.getLayoutManager()).findViewByPosition(a.gridPosition(item));
      assertNotNull(after);
      assertEquals(y, after.getTop() + fraction * after.getHeight(), 4f);
    }
  }

  @Test
  public void previewScalesCanvasWithoutChangingTouchBoundsOrFading() {
    var grid = new GalleryGrid(RuntimeEnvironment.getApplication());
    grid.preview(.8f, 100, 100);
    assertEquals(1, grid.getScaleX(), .001f);
    assertEquals(1, grid.getScaleY(), .001f);
    assertEquals(200, grid.contentX(180), .001f);
    assertEquals(300, grid.contentY(260), .001f);
    assertEquals(1, grid.getAlpha(), .001f);
    grid.settle(false);
    assertEquals(180, grid.contentX(180), .001f);
  }

  @Test
  public void filtersStillRespectTheCurrentAlbumAndManualCoverViewsRemain() throws Exception {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      fill(a);
      a.openCollection("videos");
      a.applyGridDensity(9);
      assertTrue(a.filtered().stream().allMatch(MainActivity.Media::video));
      a.viewMode = "months";
      a.render();
      layout(a);
      assertTrue(grid(a).getAdapter() instanceof CalendarOverview);
      assertNotNull(find(a.root, "סינון בתוך האוסף הנוכחי"));
    }
  }

  @Test
  public void explicitLightThemeSurvivesReferenceStyleMigration() {
    var p = RuntimeEnvironment.getApplication().getSharedPreferences("luma", 0);
    p.edit().putString("themeMode", "light").remove("referenceLayout13").apply();
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      assertTrue(c.get().prefs.getBoolean("light", false));
    }
  }

  @Test
  public void recycledOrReboundThumbnailsCancelTheirPreviousRequest() throws Exception {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      fill(a);
      ImageView image = new ImageView(a);
      a.thumbnail(image, a.media.get(0), 96);
      ModernActivity.ThumbnailTicket first = (ModernActivity.ThumbnailTicket) image.getTag();
      a.thumbnail(image, a.media.get(1), 360);
      assertTrue(first.cancel.isCanceled());
      ModernActivity.ThumbnailTicket second = (ModernActivity.ThumbnailTicket) image.getTag();
      a.cancelThumbnails(image);
      assertTrue(second.cancel.isCanceled());
    }
  }

  @Test
  public void lateCompactThumbnailCannotReplaceTheHigherResolutionCache() {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      Bitmap large = Bitmap.createBitmap(360, 240, Bitmap.Config.ARGB_8888),
          small = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888);
      a.cacheThumbnail("quality", large);
      a.cacheThumbnail("quality", small);
      assertSame(large, a.cache.get("quality"));
    }
  }

  @Test
  public void denseMonthGroupingSurvivesAColdActivityStart() throws Exception {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      fill(c.get());
      c.get().applyGridDensity(9);
      assertEquals("months", c.get().prefs.getString("gridGrouping", ""));
    }
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      assertEquals(9, c.get().prefs.getInt("columns", 0));
      assertEquals("months", c.get().prefs.getString("gridGrouping", ""));
      assertEquals("photos", c.get().viewMode);
    }
  }

  @Test
  public void reboundCellsClearPreviousMorphTransforms() throws Exception {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      fill(a);
      GalleryGrid grid = grid(a);
      int pos = -1;
      for (int i = 0; i < grid.getAdapter().getItemCount(); i++)
        if (a.gridMedia(i) != null) {
          pos = i;
          break;
        }
      View cell = ((GridLayoutManager) grid.getLayoutManager()).findViewByPosition(pos);
      assertNotNull(cell);
      RecyclerView.ViewHolder holder = grid.getChildViewHolder(cell);
      cell.setTranslationX(45);
      cell.setTranslationY(60);
      cell.setScaleX(.6f);
      cell.setScaleY(.8f);
      ((RecyclerView.Adapter) grid.getAdapter()).onBindViewHolder(holder, pos);
      assertEquals(0, cell.getTranslationX(), .001f);
      assertEquals(0, cell.getTranslationY(), .001f);
      assertEquals(1, cell.getScaleX(), .001f);
      assertEquals(1, cell.getScaleY(), .001f);
    }
  }
}
