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
public class EditingLayoutTest {
  @Test
  public void editingActionsDoNotOverlapAtNarrowWidthAndLargeFont() throws Exception {
    try (var controller = Robolectric.buildActivity(ModernActivity.class).setup()) {
      ModernActivity a = controller.get();
      a.getResources().getConfiguration().fontScale = 1.5f;
      a.getResources().getDisplayMetrics().scaledDensity = 1.5f;
      MainActivity.Media media = new MainActivity.Media();
      java.io.File sample = new java.io.File(a.getCacheDir(), "layout-sample.png");
      Bitmap fixture = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
      fixture.eraseColor(Color.rgb(140, 110, 190));
      try (java.io.FileOutputStream output = new java.io.FileOutputStream(sample)) {
        fixture.compress(Bitmap.CompressFormat.PNG, 100, output);
      }
      media.uri = android.net.Uri.fromFile(sample);
      media.name = "sample.jpg";
      media.mime = "image/jpeg";
      PhotoStudio editor = new PhotoStudio(a, media);
      editor.show();
      ViewGroup host = editor.getWindow().getDecorView().findViewById(android.R.id.content);
      View root = host.getChildAt(0);
      root.measure(
          View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(560, View.MeasureSpec.EXACTLY));
      root.layout(0, 0, 320, 560);
      checkRows(root);
      capture(root, "photo-studio");
      editor.dismiss();
    }
  }

  @Test
  public void videoStudioControlsFitAtLargeFont() throws Exception {
    try (var controller = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = controller.get();
      a.getResources().getDisplayMetrics().scaledDensity = 1.5f;
      MainActivity.Media m = new MainActivity.Media();
      m.uri = android.net.Uri.parse("content://media/external/video/media/900");
      m.name = "sample.mp4";
      m.mime = "video/mp4";
      m.duration = 120000;
      VideoStudio editor = new VideoStudio(a, m);
      editor.show();
      ViewGroup frame = editor.getWindow().getDecorView().findViewById(android.R.id.content);
      View root = frame.getChildAt(0);
      root.measure(
          View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(560, View.MeasureSpec.EXACTLY));
      root.layout(0, 0, 320, 560);
      checkRows(root);
      assertTrue(editor.from.getMeasuredHeight() >= 48);
      assertTrue(editor.to.getMeasuredHeight() >= 48);
      assertNotNull(editor.playButton);
      capture(root, "video-studio");
      editor.dismiss();
    }
  }

  void capture(View view, String name) throws Exception {
    Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
    view.draw(new Canvas(image));
    java.io.File folder = new java.io.File("build/previews");
    folder.mkdirs();
    try (var out = new java.io.FileOutputStream(new java.io.File(folder, name + ".png"))) {
      image.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
  }

  private void checkRows(View view) {
    if (!(view instanceof ViewGroup)) return;
    ViewGroup group = (ViewGroup) view;
    if (view instanceof LinearLayout
        && ((LinearLayout) view).getOrientation() == LinearLayout.HORIZONTAL) {
      for (int i = 0; i < group.getChildCount(); i++) {
        View a = group.getChildAt(i);
        if (!(a instanceof Button)) continue;
        assertTrue("action starts outside row", a.getLeft() >= 0);
        assertTrue("action ends outside row", a.getRight() <= group.getWidth());
        for (int j = i + 1; j < group.getChildCount(); j++) {
          View b = group.getChildAt(j);
          if (b instanceof Button)
            assertFalse(
                "actions overlap", a.getLeft() < b.getRight() && b.getLeft() < a.getRight());
        }
      }
    }
    for (int i = 0; i < group.getChildCount(); i++) checkRows(group.getChildAt(i));
  }

  @Test
  public void perspectiveAndGlareHaveNonDestructiveNeutralSettings() {
    Bitmap source = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
    source.eraseColor(Color.rgb(100, 140, 190));
    Bitmap neutral = AdvancedEngine.perspective(source, 0, 0);
    assertEquals(source.getPixel(8, 8), neutral.getPixel(8, 8));
    Bitmap glare = AdvancedEngine.glare(source, 0);
    assertEquals(source.getPixel(8, 8), glare.getPixel(8, 8));
    Bitmap corrected = AdvancedEngine.perspective(source, .6f, .2f);
    assertEquals(source.getWidth(), corrected.getWidth());
    assertEquals(source.getHeight(), corrected.getHeight());
  }
}
