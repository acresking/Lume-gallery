package il.luma.gallery;

import static org.junit.Assert.*;

import android.graphics.*;
import android.net.Uri;
import android.os.Looper;
import android.view.*;
import android.widget.ScrollView;
import androidx.exifinterface.media.ExifInterface;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 30, qualifiers = "w320dp-h640dp-mdpi")
public class ReferenceGalleryTest {
  MainActivity.Media photo(int id) {
    MainActivity.Media m = new MainActivity.Media();
    m.uri = Uri.parse("content://media/external/images/media/" + id);
    m.mime = "image/jpeg";
    m.name = "sample.jpg";
    m.path = "DCIM/";
    m.size = 100;
    m.date = 1000;
    return m;
  }

  @Test
  public void smartCategoriesOnlyUseFreshLocalEntriesAndHideArchivedMedia() {
    var first = photo(1);
    var second = photo(2);
    Map<String, AiIndex.Entry> index = new HashMap<>();
    index.put(
        first.uri.toString(),
        new AiIndex.Entry(
            first.uri.toString(), AiIndex.fingerprint(first), "beach; טבע נוף", "classify", null));
    index.put(
        second.uri.toString(),
        new AiIndex.Entry(
            second.uri.toString(), AiIndex.fingerprint(second), "dog; חיות", "classify", null));
    var groups =
        SmartCollections.groups(List.of(first, second), index, Set.of(second.uri.toString()));
    assertEquals(1, groups.get(SmartCollections.find("nature")).size());
    assertTrue(groups.get(SmartCollections.find("animals")).isEmpty());
    first.modified = 2;
    assertTrue(
        SmartCollections.groups(List.of(first), index, Set.of())
            .get(SmartCollections.find("nature"))
            .isEmpty());
  }

  @Test
  public void categoryMatchingDoesNotTreatSubstringsAsAnimalLabels() {
    var m = photo(1);
    var e =
        new AiIndex.Entry(
            m.uri.toString(), AiIndex.fingerprint(m), "cathedral; hotdog", "classify", null);
    assertFalse(SmartCollections.find("animals").matches(m, e));
    assertNull(SmartCollections.find("unknown"));
  }

  @Test
  public void smartCollectionFiltersTheRealVisibleLibrary() throws Exception {
    try (var controller = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = controller.get();
      a.io.submit(() -> {}).get();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      a.ai.worker.submit(() -> {}).get();
      a.media.clear();
      var m = photo(1);
      a.media.add(m);
      a.ai.cached.put(
          m.uri.toString(),
          new AiIndex.Entry(
              m.uri.toString(), AiIndex.fingerprint(m), "pizza food אוכל", "classify", null));
      a.openCollection("smart:food");
      assertEquals(1, a.filtered().size());
      assertEquals("אוכל", a.collectionTitle());
      m.modified++;
      assertTrue(a.filtered().isEmpty());
    }
  }

  @Test
  public void albumColumnsAdaptToLargeTextAndTheSavedDensity() {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.prefs.edit().putInt("albumColumns", 3).apply();
      assertEquals(3, a.albumColumns());
      a.getResources().getConfiguration().fontScale = 1.6f;
      assertEquals(2, a.albumColumns());
      a.getResources().getConfiguration().fontScale = 1;
      a.prefs.edit().putInt("albumColumns", 2).apply();
      assertEquals(2, a.albumColumns());
    }
  }

  @Test
  public void trueBlackOnlyAppliesInDarkAppearance() {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.prefs.edit().putBoolean("light", false).putBoolean("oledBlack", true).apply();
      a.applyColors();
      assertEquals(Color.BLACK, a.bg);
      a.prefs.edit().putBoolean("oledBlack", false).apply();
      a.applyColors();
      assertNotEquals(Color.BLACK, a.bg);
      a.prefs.edit().putBoolean("light", true).apply();
      a.applyColors();
      assertEquals(0xfffafafa, a.bg);
    }
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  public void exifDetailsAreReadWithoutChangingTheOriginal() throws Exception {
    var context = RuntimeEnvironment.getApplication();
    var file = new java.io.File(context.getCacheDir(), "synthetic-exif.jpg");
    var bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888);
    try (var output = new java.io.FileOutputStream(file)) {
      bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output);
    }
    ExifInterface exif = new ExifInterface(file);
    exif.setAttribute(ExifInterface.TAG_MAKE, "Demo");
    exif.setAttribute(ExifInterface.TAG_MODEL, "Demo Phone");
    exif.setAttribute(ExifInterface.TAG_LENS_MODEL, "Sample Lens");
    exif.setAttribute(ExifInterface.TAG_F_NUMBER, "1.8");
    exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "0.01");
    exif.saveAttributes();
    byte[] before = java.nio.file.Files.readAllBytes(file.toPath());
    var m = photo(1);
    m.uri = Uri.fromFile(file);
    var data = MediaDetails.read(context, m);
    assertEquals("Demo Phone", data.get("מצלמה"));
    assertEquals("Sample Lens", data.get("עדשה"));
    assertEquals("f/1.8", data.get("צמצם"));
    assertTrue(data.get("זמן חשיפה").contains("1/100"));
    assertArrayEquals(before, java.nio.file.Files.readAllBytes(file.toPath()));
  }

  @Test
  public void infoSheetScrollsAndFollowsScreenshotProtection() throws Exception {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.details(photo(1));
      var dialog = ShadowDialog.getLatestDialog();
      assertTrue(dialog.isShowing());
      assertTrue(
          (dialog.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) == 0);
      assertTrue(hasScroll(dialog.getWindow().getDecorView()));
      a.io.submit(() -> {}).get();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      dialog.dismiss();
    }
  }

  boolean hasScroll(View v) {
    if (v instanceof ScrollView) return true;
    if (v instanceof ViewGroup) {
      var g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) if (hasScroll(g.getChildAt(i))) return true;
    }
    return false;
  }

  @Test
  public void durationAndCameraFormattingHandleAbsentMetadata() {
    assertEquals("1:01:01", MediaDetails.duration(3661000));
    assertEquals("0:00", MediaDetails.duration(0));
    assertNull(MediaDetails.exposure(0));
    assertNull(MediaDetails.cleanCamera(null, null));
    assertEquals("Demo Phone", MediaDetails.cleanCamera("Demo", "Phone"));
  }
}
