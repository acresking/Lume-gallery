package il.luma.gallery;

import static org.junit.Assert.*;

import android.os.Bundle;
import android.view.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w320dp-h640dp-mdpi")
public class NavigationLayoutTest {
  @Test
  public void headersAndNavigationFitWithLargeText() {
    try (var controller = Robolectric.buildActivity(ModernActivity.class).setup()) {
      ModernActivity a = controller.get();
      a.getResources().getDisplayMetrics().scaledDensity = 1.5f;
      a.getResources().getConfiguration().fontScale = 1.5f;
      for (String tab : new String[] {"media", "albums", "rules", "settings"}) {
        a.tab = tab;
        a.render();
        a.root.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
        a.root.layout(0, 0, 320, 640);
        ViewGroup header = (ViewGroup) a.root.getChildAt(0);
        for (int i = 0; i < header.getChildCount(); i++) {
          View child = header.getChildAt(i);
          assertTrue(child.getLeft() >= 0);
          assertTrue(child.getRight() <= header.getWidth());
          for (int j = i + 1; j < header.getChildCount(); j++) {
            View other = header.getChildAt(j);
            assertFalse(
                "header controls overlap",
                child.getLeft() < other.getRight() && other.getLeft() < child.getRight());
          }
        }
        assertEquals(4, a.nav.getChildCount());
        assertTrue(a.nav.getTop() >= a.body.getBottom());
        assertTrue(a.nav.getBottom() <= 640);
      }
    }
  }

  @Test
  public void recreationKeepsTheCurrentCollection() {
    Bundle state = new Bundle();
    try (var controller = Robolectric.buildActivity(ModernActivity.class).setup()) {
      ModernActivity a = controller.get();
      a.openNamedAlbum("טיול");
      a.onSaveInstanceState(state);
    }
    try (var restored = Robolectric.buildActivity(ModernActivity.class).setup(state)) {
      ModernActivity a = restored.get();
      assertEquals("media", a.tab);
      assertEquals("טיול", a.collectionTitle());
    }
  }

  @Test
  public void rebuildingTheGalleryKeepsItsScrollAnchor() throws Exception {
    try (var controller = Robolectric.buildActivity(ModernActivity.class).setup()) {
      ModernActivity a = controller.get();
      a.io.submit(() -> {}).get();
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      a.media.clear();
      android.graphics.Bitmap thumb =
          android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888);
      for (int i = 0; i < 300; i++) {
        MainActivity.Media m = new MainActivity.Media();
        m.uri = android.net.Uri.parse("content://media/external/images/media/" + (1000 + i));
        m.name = "Photo_" + i;
        m.path = "DCIM/Camera/";
        m.mime = "image/jpeg";
        m.date = System.currentTimeMillis();
        a.media.add(m);
        a.cache.put(m.uri.toString(), thumb);
      }
      a.render();
      layout(a);
      androidx.recyclerview.widget.RecyclerView list = findList(a.body);
      assertNotNull(list);
      var manager = (androidx.recyclerview.widget.GridLayoutManager) list.getLayoutManager();
      manager.scrollToPositionWithOffset(90, 0);
      layout(a);
      int before = manager.findFirstVisibleItemPosition();
      assertTrue(before > 0);
      a.render();
      layout(a);
      var restored =
          (androidx.recyclerview.widget.GridLayoutManager) findList(a.body).getLayoutManager();
      assertEquals(before, restored.findFirstVisibleItemPosition());
    }
  }

  private void layout(ModernActivity a) {
    a.root.measure(
        View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
    a.root.layout(0, 0, 320, 640);
  }

  private androidx.recyclerview.widget.RecyclerView findList(View view) {
    if (view instanceof androidx.recyclerview.widget.RecyclerView)
      return (androidx.recyclerview.widget.RecyclerView) view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        var result = findList(group.getChildAt(i));
        if (result != null) return result;
      }
    }
    return null;
  }
}
