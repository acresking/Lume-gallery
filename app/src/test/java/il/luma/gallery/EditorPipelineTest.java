package il.luma.gallery;

import static org.junit.Assert.*;

import android.graphics.*;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.effect.*;
import java.nio.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class EditorPipelineTest {
  Bitmap image(int w, int h, int color) {
    Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    b.eraseColor(color);
    return b;
  }

  VideoRecipe video() {
    VideoRecipe v = new VideoRecipe();
    v.clips.add(new VideoRecipe.Clip(Uri.parse("content://video/1"), 10000, 1920, 1080, true));
    v.start = 1000;
    v.end = 9000;
    return v;
  }

  @Test
  public void historyIsBoundedAndNewEditClearsRedo() {
    EditorHistory<Integer> h = new EditorHistory<>(2);
    h.record(1);
    h.record(2);
    h.record(3);
    assertEquals(Integer.valueOf(3), h.undo(4));
    assertEquals(Integer.valueOf(2), h.undo(3));
    assertFalse(h.canUndo());
    assertEquals(Integer.valueOf(3), h.redo(2));
    h.record(9);
    assertFalse(h.canRedo());
    h.clear();
    assertFalse(h.canUndo());
  }

  @Test
  public void photoCopiesMutableParameters() {
    PhotoRecipe r = new PhotoRecipe();
    r.details.exposure = 1;
    r.freeCrop.left = .1f;
    PhotoMarks.Stroke s = new PhotoMarks.Stroke();
    s.points.add(new PointF(.2f, .3f));
    r.marks.strokes.add(s);
    PhotoRecipe copy = r.copy();
    copy.details.exposure = 2;
    copy.freeCrop.left = .4f;
    copy.marks.strokes.get(0).points.get(0).x = .8f;
    assertEquals(1, r.details.exposure, 0);
    assertEquals(.1f, r.freeCrop.left, 0);
    assertEquals(.2f, s.points.get(0).x, 0);
  }

  @Test
  public void neutralRenderPreservesSourceAndAlpha() {
    Bitmap b = image(20, 16, Color.argb(128, 80, 120, 160));
    PhotoRecipe r = new PhotoRecipe();
    Bitmap out = r.render(b, true);
    assertNotSame(b, out);
    assertEquals(b.getPixel(8, 8), out.getPixel(8, 8));
    out.eraseColor(Color.RED);
    assertEquals(128, Color.alpha(b.getPixel(8, 8)));
  }

  @Test
  public void rotationAndFreeCropProduceExpectedDimensions() {
    Bitmap b = image(80, 40, Color.BLUE);
    PhotoRecipe r = new PhotoRecipe();
    r.rotation = 90;
    r.freeCrop = new RectF(0, 0, .5f, .5f);
    Bitmap out = r.render(b, false);
    assertEquals(20, out.getWidth());
    assertEquals(40, out.getHeight());
    assertEquals(80, b.getWidth());
  }

  @Test
  public void lightEffectsChangePixelsWithoutFlatteningTransparency() {
    Bitmap b = image(30, 30, Color.argb(128, 60, 60, 60));
    PhotoRecipe r = new PhotoRecipe();
    r.details.exposure = 1;
    Bitmap out = r.render(b, false);
    assertTrue(Color.red(out.getPixel(15, 15)) > Color.red(b.getPixel(15, 15)));
    assertEquals(128, Color.alpha(out.getPixel(15, 15)));
  }

  @Test
  public void vignetteDarkensCornersMoreThanCenter() {
    PhotoRecipe r = new PhotoRecipe();
    r.details.vignette = .8f;
    Bitmap out = r.render(image(64, 64, Color.GRAY), false);
    assertTrue(Color.red(out.getPixel(0, 0)) < Color.red(out.getPixel(32, 32)));
  }

  @Test
  public void autoExposureIsBounded() {
    assertTrue(Math.abs(PhotoEffects.automaticExposure(image(16, 16, Color.BLACK))) <= 1.2f);
    assertTrue(PhotoEffects.automaticExposure(image(16, 16, Color.WHITE)) < 0);
  }

  @Test
  public void vectorMarksScaleWithOutputAndDoNotTouchSource() {
    PhotoRecipe r = new PhotoRecipe();
    PhotoMarks.Stroke s = new PhotoMarks.Stroke();
    s.color = Color.RED;
    s.width = .1f;
    s.points.add(new PointF(.2f, .5f));
    s.points.add(new PointF(.8f, .5f));
    r.marks.strokes.add(s);
    Bitmap source = image(100, 100, Color.BLUE);
    Bitmap a = r.render(source, true), b = r.render(image(200, 200, Color.BLUE), true);
    assertEquals(Color.RED, a.getPixel(50, 50));
    assertEquals(Color.RED, b.getPixel(100, 100));
    assertEquals(Color.BLUE, source.getPixel(50, 50));
    assertEquals(Color.BLUE, r.render(source, false).getPixel(50, 50));
  }

  @Test
  public void videoDurationUsesFirstTrimAndAllClipsAtChosenSpeed() throws Exception {
    VideoRecipe v = video();
    v.clips.add(new VideoRecipe.Clip(Uri.parse("content://video/2"), 4000, 1080, 1920, false));
    v.speed = 2;
    assertEquals(6000, v.outputDuration());
    assertEquals(1000, v.mediaItem(0).clippingConfiguration.startPositionMs);
    assertEquals(9000, v.mediaItem(0).clippingConfiguration.endPositionMs);
    assertEquals(0, v.mediaItem(1).clippingConfiguration.startPositionMs);
    assertEquals(2, v.composition(false).sequences.get(0).editedMediaItems.size());
  }

  @Test
  public void videoSnapshotIsIndependentOfLiveSlidersAndSequence() {
    VideoRecipe v = video(), copy = v.copy();
    copy.speed = 2;
    copy.clips.clear();
    assertEquals(1, v.speed, 0);
    assertEquals(1, v.clips.size());
  }

  @Test
  public void cropRemainsInsideFrameAndMatchesRequestedRatio() {
    VideoRecipe v = video();
    v.aspect = 2;
    v.zoom = 2;
    v.centerX = 0;
    v.centerY = 1;
    RectF c = v.crop(v.clips.get(0));
    assertTrue(c.left >= 0 && c.top >= 0 && c.right <= 1 && c.bottom <= 1);
    assertEquals(9f / 16, 1920 * c.width() / (1080 * c.height()), .001f);
    v.resolution = 3;
    assertEquals(720, v.outputSize()[1]);
    assertEquals(0, v.outputSize()[0] % 2);
  }

  @Test
  public void videoExportsActualEffectsAndCaption() throws Exception {
    VideoRecipe v = video();
    v.brightness = .2f;
    v.contrast = .1f;
    v.saturation = 20;
    v.caption = "שלום Lume";
    v.aspect = 1;
    v.resolution = 3;
    v.fps = 30;
    var effects = v.videoEffects(v.clips.get(0), true);
    assertTrue(effects.stream().anyMatch(x -> x instanceof Brightness));
    assertTrue(effects.stream().anyMatch(x -> x instanceof Crop));
    assertTrue(effects.stream().anyMatch(x -> x instanceof OverlayEffect));
    assertTrue(effects.stream().anyMatch(x -> x instanceof Presentation));
    assertEquals(30, v.composition(false).sequences.get(0).editedMediaItems.get(0).frameRate);
  }

  @Test
  public void audioExtractionRemovesVideoAndRejectsMutedAudio() throws Exception {
    VideoRecipe v = video();
    assertTrue(v.composition(true).sequences.get(0).editedMediaItems.get(0).removeVideo);
    v.mute = true;
    assertTrue(v.composition(false).sequences.get(0).editedMediaItems.get(0).removeAudio);
    assertThrows(java.io.IOException.class, () -> v.composition(true));
  }

  @Test
  public void invalidTimeAndSpeedAreRejected() {
    VideoRecipe v = video();
    v.end = 11000;
    assertThrows(java.io.IOException.class, v::validate);
    v.end = 9000;
    v.speed = Float.NaN;
    assertThrows(java.io.IOException.class, v::validate);
  }

  @Test
  public void pcmGainAppliesEquallyToStereoChannels() throws Exception {
    GainFadeProcessor p = new GainFadeProcessor(.5f, 0, 0, 1000);
    p.configure(new AudioProcessor.AudioFormat(1000, 2, C.ENCODING_PCM_16BIT));
    p.flush();
    ByteBuffer in = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder());
    in.putShort((short) 10000)
        .putShort((short) -10000)
        .putShort((short) 20000)
        .putShort((short) -20000)
        .flip();
    p.queueInput(in);
    ByteBuffer out = p.getOutput().order(ByteOrder.nativeOrder());
    assertEquals(5000, out.getShort());
    assertEquals(-5000, out.getShort());
    assertEquals(10000, out.getShort());
    assertEquals(-10000, out.getShort());
    assertEquals(2, p.frames);
  }

  @Test
  public void floatingPointFadeUsesTimeAndResetsOnFlush() throws Exception {
    GainFadeProcessor p = new GainFadeProcessor(1, 2, 2, 4);
    p.configure(new AudioProcessor.AudioFormat(1000, 1, C.ENCODING_PCM_FLOAT));
    p.flush();
    ByteBuffer in = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
    for (int i = 0; i < 4; i++) in.putFloat(1);
    in.flip();
    p.queueInput(in);
    ByteBuffer out = p.getOutput().order(ByteOrder.nativeOrder());
    assertEquals(0, out.getFloat(), 0);
    assertEquals(.5f, out.getFloat(), 0);
    assertEquals(1, out.getFloat(), 0);
    assertEquals(.5f, out.getFloat(), 0);
    p.flush();
    assertEquals(0, p.frames);
  }
}
