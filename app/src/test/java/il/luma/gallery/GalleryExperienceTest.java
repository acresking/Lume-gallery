package il.luma.gallery;

import static org.junit.Assert.*;

import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w320dp-h640dp-mdpi")
public class GalleryExperienceTest {
  MainActivity.Media add(ModernActivity a, int id, String mime, String path, int day) {
    MainActivity.Media m = new MainActivity.Media();
    m.uri =
        Uri.parse(
            "content://media/external/"
                + (mime.startsWith("video") ? "video" : "images")
                + "/media/"
                + id);
    m.mime = mime;
    m.path = path;
    m.name = "sample-" + id;
    Calendar c = Calendar.getInstance();
    c.clear();
    c.set(2020, Calendar.JANUARY, day, 12, 0);
    m.date = c.getTimeInMillis();
    a.media.add(m);
    return m;
  }

  void ready(ModernActivity a) throws Exception {
    a.io.submit(() -> {}).get();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    a.media.clear();
  }

  @Test
  public void quickFiltersPreserveAlbumScopeAndNewCollectionsResetThem() throws Exception {
    try (var controller = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = controller.get();
      ready(a);
      var photo = add(a, 1, "image/jpeg", "DCIM/Camera/", 10);
      add(a, 2, "video/mp4", "DCIM/Camera/", 10);
      add(a, 3, "image/png", "Pictures/Screenshots/", 11);
      a.favorites.add(photo.uri.toString());
      a.render();
      a.setQuickFilter(1);
      assertEquals(2, a.filtered().size());
      a.album = "DCIM/Camera/";
      a.render();
      assertEquals(1, a.filtered().size());
      a.setQuickFilter(3);
      assertSame(photo, a.filtered().get(0));
      a.openCollection("videos");
      assertEquals(0, a.filter);
      assertEquals(1, a.filtered().size());
      assertTrue(a.filtered().get(0).video());
    }
  }

  @Test
  public void recreationRetainsActiveQuickFilter() {
    Bundle state = new Bundle();
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      c.get().setQuickFilter(2);
      c.get().onSaveInstanceState(state);
    }
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup(state)) {
      assertEquals(2, c.get().filter);
    }
  }

  @Test
  public void dateSelectionAndSelectAllCanBeReversedAndDateJumpFindsHeader() throws Exception {
    try (var controller = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = controller.get();
      ready(a);
      var first = add(a, 1, "image/jpeg", "DCIM/", 10);
      add(a, 2, "video/mp4", "DCIM/", 10);
      add(a, 3, "image/jpeg", "DCIM/", 11);
      a.render();
      String day = a.dateHeading(first.date);
      assertEquals(2, a.toggleDateGroup(day));
      assertTrue(a.dateGroupSelected(day));
      assertEquals(0, a.toggleDateGroup(day));
      assertEquals(3, a.toggleVisibleSelection());
      assertEquals(0, a.toggleVisibleSelection());
      assertEquals(3, a.nearestDatePosition(first.date));
    }
  }

  @Test
  public void groupingAndFilterControlsFitNarrowScreenWithLargeText() throws Exception {
    try (var controller = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = controller.get();
      ready(a);
      add(a, 1, "image/jpeg", "DCIM/", 10);
      a.getResources().getConfiguration().fontScale = 1.6f;
      a.getResources().getDisplayMetrics().scaledDensity = 1.6f;
      a.render();
      a.root.measure(
          View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
      a.root.layout(0, 0, 320, 640);
      ViewGroup row = (ViewGroup) description(a.body, "קיבוץ תאריך ופעולות גלריה");
      assertNotNull(row);
      assertTrue(row.getChildAt(0) instanceof android.widget.TextView);
      for (int i = 0; i < row.getChildCount(); i++) {
        View child = row.getChildAt(i);
        assertTrue(child.getLeft() >= 0);
        assertTrue(child.getRight() <= row.getWidth());
        for (int j = i + 1; j < row.getChildCount(); j++)
          assertFalse(
              child.getLeft() < row.getChildAt(j).getRight()
                  && row.getChildAt(j).getLeft() < child.getRight());
      }
      assertTrue(description(a.body, "סינון בתוך האוסף הנוכחי") instanceof android.widget.TextView);
    }
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  public void viewerCanHideAndRestoreItsControls() throws Exception {
    try (var controller = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = controller.get();
      ready(a);
      a.prefs.edit().putBoolean("motion", false).apply();
      var m = add(a, 1, "image/png", "DCIM/", 10);
      java.io.File sample = new java.io.File(a.getCacheDir(), "viewer-sample.png");
      Bitmap image = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
      try (var output = new java.io.FileOutputStream(sample)) {
        image.compress(Bitmap.CompressFormat.PNG, 100, output);
      }
      m.uri = Uri.fromFile(sample);
      a.render();
      a.getResources().getDisplayMetrics().scaledDensity = 1.6f;
      a.getResources().getConfiguration().fontScale = 1.6f;
      var viewer = a.new Viewer(m);
      viewer.show();
      ViewGroup host = viewer.getWindow().getDecorView().findViewById(android.R.id.content);
      View root = host.getChildAt(0);
      // ViewPager's first layout dispatches onPageSelected and rebuilds the toolbar.
      // Measure the requested follow-up layout before inspecting the new children.
      for (int pass = 0; pass < 2; pass++) {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(560, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 320, 560);
      }
      for (int i = 0; i < viewer.tools.getChildCount(); i++) {
        ViewGroup tool = (ViewGroup) viewer.tools.getChildAt(i);
        assertTrue(
            "unmeasured toolbar cell "
                + i
                + " row="
                + viewer.tools.getWidth()
                + "x"
                + viewer.tools.getHeight()
                + " cell="
                + tool.getWidth()
                + "x"
                + tool.getHeight(),
            tool.getWidth() > 0 && tool.getHeight() > 0);
        assertTrue(tool.getRight() <= viewer.tools.getWidth());
        for (int k = 0; k < tool.getChildCount(); k++)
          assertTrue(
              "viewer caption clipped "
                  + i
                  + ":"
                  + k
                  + " tool="
                  + tool.getWidth()
                  + "x"
                  + tool.getHeight()
                  + " child="
                  + tool.getChildAt(k).getTop()
                  + ".."
                  + tool.getChildAt(k).getBottom()
                  + " row="
                  + viewer.tools.getHeight(),
              tool.getChildAt(k).getBottom() <= tool.getHeight());
      }
      viewer.toggleControls();
      assertFalse(viewer.controls);
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);
      assertEquals(View.GONE, viewer.top.getVisibility());
      assertEquals(View.GONE, viewer.strip.getVisibility());
      viewer.toggleControls();
      assertTrue(viewer.controls);
      assertEquals(View.VISIBLE, viewer.tools.getVisibility());
      viewer.dismiss();
    }
  }

  @Test
  public void draggingDoesNotTriggerTheSingleTapAction() {
    var view = new ZoomPhotoView(RuntimeEnvironment.getApplication());
    AtomicInteger clicks = new AtomicInteger();
    view.setOnClickListener(v -> clicks.incrementAndGet());
    long now = SystemClock.uptimeMillis();
    send(view, now, now, MotionEvent.ACTION_DOWN, 10);
    send(view, now, now + 30, MotionEvent.ACTION_MOVE, 90);
    send(view, now, now + 60, MotionEvent.ACTION_UP, 90);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS);
    assertEquals(0, clicks.get());
    now = SystemClock.uptimeMillis();
    send(view, now, now, MotionEvent.ACTION_DOWN, 10);
    send(view, now, now + 30, MotionEvent.ACTION_UP, 10);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS);
    assertEquals(1, clicks.get());
  }

  void send(ZoomPhotoView view, long down, long time, int action, float x) {
    MotionEvent e = MotionEvent.obtain(down, time, action, x, 10, 0);
    view.onTouchEvent(e);
    e.recycle();
  }

  View description(View v, String text) {
    if (text.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription()))
      return v;
    if (v instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) v;
      for (int i = 0; i < group.getChildCount(); i++) {
        View found = description(group.getChildAt(i), text);
        if (found != null) return found;
      }
    }
    return null;
  }
}
