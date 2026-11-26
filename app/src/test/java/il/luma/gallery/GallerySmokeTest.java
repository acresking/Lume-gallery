package il.luma.gallery;

import static org.junit.Assert.*;

import android.net.Uri;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

/** Launch and state-transition checks for the redesigned Activity. No real user files. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class GallerySmokeTest {
  @Test
  public void startsAndNavigatesWithoutMedia() {
    try (var controller = Robolectric.buildActivity(ModernActivity.class).setup()) {
      ModernActivity a = controller.get();
      assertNotNull(a.root);
      assertEquals(4, a.nav.getChildCount());
      a.tab = "albums";
      a.render();
      assertTrue(a.body.getChildCount() > 0);
      a.tab = "rules";
      a.render();
      assertTrue(a.body.getChildCount() > 0);
      a.tab = "settings";
      a.render();
      assertTrue(a.body.getChildCount() > 0);
    }
  }

  @Test
  public void archiveFavoritesAndSearchFilterRealEntries() {
    try (var controller = Robolectric.buildActivity(ModernActivity.class).setup()) {
      ModernActivity a = controller.get();
      a.media.clear();
      MainActivity.Media photo = new MainActivity.Media();
      photo.uri = Uri.parse("content://media/external/images/media/1");
      photo.name = "trip.jpg";
      photo.path = "DCIM/Camera/";
      photo.mime = "image/jpeg";
      photo.date = System.currentTimeMillis();
      MainActivity.Media screenshot = new MainActivity.Media();
      screenshot.uri = Uri.parse("content://media/external/images/media/2");
      screenshot.name = "Screenshot_2.png";
      screenshot.path = "Pictures/Screenshots/";
      screenshot.mime = "image/png";
      screenshot.date = System.currentTimeMillis();
      a.media.add(photo);
      a.media.add(screenshot);
      assertEquals(2, a.filtered().size());
      a.openCollection("screenshots");
      assertEquals(1, a.filtered().size());
      assertSame(screenshot, a.filtered().get(0));
      a.favorites.add(photo.uri.toString());
      a.openCollection("favorites");
      assertEquals(1, a.filtered().size());
      assertSame(photo, a.filtered().get(0));
      a.openCollection("");
      a.search = "trip";
      assertEquals(1, a.filtered().size());
    }
  }

  @Test
  @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
  @Config(sdk = 30, qualifiers = "w432dp-h936dp-mdpi")
  public void rendersPhotoAndCollectionScreens() throws Exception {
    try (var controller = Robolectric.buildActivity(ModernActivity.class).setup()) {
      ModernActivity a = controller.get();
      a.media.clear();
      for (int i = 0; i < 9; i++) {
        MainActivity.Media m = new MainActivity.Media();
        m.uri = Uri.parse("content://media/external/images/media/" + (i + 10));
        m.name = "Photo_" + i + ".jpg";
        m.path =
            i < 5
                ? "DCIM/Camera/"
                : i < 7 ? "Pictures/Screenshots/" : "Android/media/com.whatsapp/WhatsApp Images/";
        m.mime = "image/jpeg";
        m.date = System.currentTimeMillis() - (i / 3) * 86400000L;
        m.width = 600;
        m.height = 800;
        m.size = 50000;
        a.media.add(m);
        java.io.File f = new java.io.File("../../tools/fixtures/Photo_0" + i + ".jpg");
        android.graphics.Bitmap bitmap =
            android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
        if (bitmap == null) {
          bitmap =
              android.graphics.Bitmap.createBitmap(
                  300, 400, android.graphics.Bitmap.Config.ARGB_8888);
          bitmap.eraseColor(android.graphics.Color.rgb(80 + i * 15, 140, 180 - i * 12));
        }
        a.cache.put(m.uri.toString(), bitmap);
      }
      a.favorites.add(a.media.get(0).uri.toString());
      a.render();
      capture(a, "photos");
      a.tab = "albums";
      a.render();
      capture(a, "collections");
      a.tab = "settings";
      a.render();
      capture(a, "settings");
    }
  }

  private void capture(ModernActivity a, String name) throws Exception {
    int width = 432, height = 936;
    a.root.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    a.root.layout(0, 0, width, height);
    a.root.setAlpha(1);
    a.body.animate().cancel();
    a.body.setAlpha(1);
    android.graphics.Bitmap bitmap =
        android.graphics.Bitmap.createBitmap(
            width, height, android.graphics.Bitmap.Config.ARGB_8888);
    a.root.draw(new android.graphics.Canvas(bitmap));
    java.io.File dir = new java.io.File("build/previews");
    dir.mkdirs();
    try (java.io.FileOutputStream out =
        new java.io.FileOutputStream(new java.io.File(dir, name + ".png"))) {
      bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
    }
  }
}
