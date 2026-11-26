package il.luma.gallery;

import static org.junit.Assert.*;

import android.content.Context;
import java.io.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class LocalAiTest {
  @Test
  public void unifiedPackIsWithinBudgetAndPinned() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    long total = 0;
    for (String id : LocalModels.IDS) {
      var entry = LocalModels.catalog(c).getJSONObject(id);
      String url = entry.getString("url");
      assertTrue(
          url.startsWith("https://storage.googleapis.com/mediapipe-models/")
              || url.matches("https://huggingface.co/.+/resolve/[0-9a-f]{40}/.+"));
      assertTrue(entry.getString("sha256").matches("[0-9a-f]{64}"));
      total += entry.getLong("size");
    }
    assertTrue(total < 150_000_000);
  }

  @Test
  public void corruptedModelWithCorrectLengthIsRejectedBeforeInference() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    File f = LocalModels.file(c, "portrait");
    try {
      try (FileOutputStream out = new FileOutputStream(f)) {
        out.write(new byte[(int) LocalModels.catalog(c).getJSONObject("portrait").getLong("size")]);
      }
      assertTrue(LocalModels.installed(c, "portrait"));
      try {
        LocalModels.path(c, "portrait");
        fail("corrupt model accepted");
      } catch (IOException expected) {
        assertTrue(expected.getMessage().contains("תקינות"));
      }
    } finally {
      f.delete();
    }
  }

  @Test
  public void derivedIndexRoundTripsVectorsAndClearDoesNotTouchOriginals() {
    try (AiIndex index = new AiIndex(RuntimeEnvironment.getApplication())) {
      index.clear();
      index.put(
          new AiIndex.Entry(
              "content://media/1",
              "100:2:3",
              "ים lakeside",
              "similar",
              new float[] {.1f, .2f, .3f}));
      var entry = index.all().get("content://media/1");
      assertEquals("100:2:3", entry.fingerprint);
      assertArrayEquals(new float[] {.1f, .2f, .3f}, entry.vector, 0);
      assertEquals(1, AiIndex.cosine(entry.vector, entry.vector), .000001);
      assertEquals(-1, AiIndex.cosine(entry.vector, new float[] {0, 0, 0}), 0);
      index.clear();
      assertTrue(index.all().isEmpty());
    }
  }

  @Test
  public void hebrewKeywordsFollowClassificationLabels() {
    assertTrue(AiIndex.keywords("lakeside").contains("ים"));
    assertTrue(AiIndex.keywords("golden retriever").contains("כלב"));
    assertFalse(AiIndex.keywords("catamaran").contains("חתול"));
  }

  @Test
  public void modelManagerCanOpenWithoutInstalledModels() {
    try (var controller = Robolectric.buildActivity(ModernActivity.class).setup()) {
      ModernActivity a = controller.get();
      a.ai.showManager();
      assertTrue(a.ai.manager.isShowing());
      a.ai.manager.dismiss();
    }
  }
}
