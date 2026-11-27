package il.luma.gallery;

import static org.junit.Assert.*;

import android.location.Address;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import javax.crypto.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w320dp-h640dp-mdpi")
public class VaultAndLocationTest {
  @Test
  public void cityCountryFormattingDeduplicatesAndFallsBackWithoutInventingCity() {
    Address address = new Address(new Locale("he"));
    address.setLocality("עיר לדוגמה");
    address.setCountryName("מדינה לדוגמה");
    assertEquals("עיר לדוגמה, מדינה לדוגמה", LocationNames.format(address));
    address.setLocality(null);
    address.setSubAdminArea("מחוז לדוגמה");
    assertEquals("מחוז לדוגמה, מדינה לדוגמה", LocationNames.format(address));
    address.setSubAdminArea(null);
    assertEquals("מדינה לדוגמה", LocationNames.format(address));
  }

  @Test
  public void mapUsesExactStoredPointAndRejectsInvalidNumbers() {
    assertEquals(
        "geo:12.345678,23.456789?q=12.345678,23.456789",
        LocationNames.mapUri(12.345678, 23.456789).toString());
    assertFalse(LocationNames.valid(Double.NaN, 0));
    assertFalse(LocationNames.valid(91, 0));
    assertThrows(IllegalArgumentException.class, () -> LocationNames.mapUri(0, 181));
  }

  @Test
  public void nameResolutionCanBeDisabledWithoutContactingProvider() {
    var c = RuntimeEnvironment.getApplication();
    c.getSharedPreferences("luma", 0).edit().putBoolean("placeNames", false).apply();
    assertEquals("", LocationNames.resolve(c, 12, 23));
  }

  @Test
  public void videoGpsParserAcceptsSignedCoordinatesAndRejectsMalformedValues() {
    assertArrayEquals(
        new double[] {12.3456, 23.4567}, LocationNames.parseIso6709("+12.3456+023.4567/"), .00001);
    assertArrayEquals(
        new double[] {-12.3, -23.4}, LocationNames.parseIso6709("-12.3-023.4+15.0/"), .00001);
    assertNull(LocationNames.parseIso6709("+91.0+023.0/"));
    assertNull(LocationNames.parseIso6709("12,23"));
    assertNull(LocationNames.parseIso6709(null));
  }

  File encrypted(SecretKey key, byte[] source) throws Exception {
    File file =
        new File(RuntimeEnvironment.getApplication().getCacheDir(), UUID.randomUUID() + ".lume");
    byte[] id = new byte[16];
    new java.security.SecureRandom().nextBytes(id);
    try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
      out.write(new byte[] {'L', 'U', 'M', 'E', 'V', '1', '1', 0});
      out.write(id);
      VaultStore.writeChunk(
          out,
          "{\"name\":\"private.jpg\",\"mime\":\"image/jpeg\"}"
              .getBytes(java.nio.charset.StandardCharsets.UTF_8),
          id,
          0,
          key);
      int index = 1;
      for (int start = 0; start < source.length; start += VaultStore.CHUNK)
        VaultStore.writeChunk(
            out,
            Arrays.copyOfRange(source, start, Math.min(source.length, start + VaultStore.CHUNK)),
            id,
            index++,
            key);
      VaultStore.writeChunk(out, new byte[0], id, index, key);
    }
    return file;
  }

  SecretKey key() throws Exception {
    KeyGenerator g = KeyGenerator.getInstance("AES");
    g.init(256);
    return g.generateKey();
  }

  @Test
  public void vaultRoundTripAcrossChunksHidesNamesAndPayload() throws Exception {
    SecretKey key = key();
    byte[] source = new byte[VaultStore.CHUNK * 3 + 17];
    new Random(11).nextBytes(source);
    File file = encrypted(key, source);
    ByteArrayOutputStream plain = new ByteArrayOutputStream();
    assertEquals("private.jpg", VaultStore.read(file, plain, key).getString("name"));
    assertArrayEquals(source, plain.toByteArray());
    assertFalse(
        new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.ISO_8859_1)
            .contains("private.jpg"));
    assertThrows(Exception.class, () -> VaultStore.read(file, new ByteArrayOutputStream(), key()));
  }

  @Test
  public void vaultRejectsModifiedTruncatedAndAppendedCiphertext() throws Exception {
    SecretKey key = key();
    File file = encrypted(key, new byte[VaultStore.CHUNK + 1]);
    byte[] original = Files.readAllBytes(file.toPath());
    byte[] modified = original.clone();
    modified[modified.length - 1] ^= 1;
    Files.write(file.toPath(), modified);
    assertThrows(Exception.class, () -> VaultStore.read(file, new ByteArrayOutputStream(), key));
    Files.write(file.toPath(), Arrays.copyOf(original, original.length - 33));
    assertThrows(Exception.class, () -> VaultStore.read(file, new ByteArrayOutputStream(), key));
    byte[] appended = Arrays.copyOf(original, original.length + 1);
    Files.write(file.toPath(), appended);
    assertThrows(Exception.class, () -> VaultStore.read(file, new ByteArrayOutputStream(), key));
  }

  @Test
  public void vaultRejectsReorderedChunks() throws Exception {
    SecretKey key = key();
    File file = encrypted(key, new byte[VaultStore.CHUNK * 2]);
    byte[] data = Files.readAllBytes(file.toPath());
    java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(data);
    int meta = b.getInt(24), start = 24 + 4 + 12 + meta;
    int size = b.getInt(start) + 16;
    byte[] first = Arrays.copyOfRange(data, start, start + size);
    System.arraycopy(data, start + size, data, start, size);
    System.arraycopy(first, 0, data, start + size, size);
    Files.write(file.toPath(), data);
    assertThrows(Exception.class, () -> VaultStore.read(file, new ByteArrayOutputStream(), key));
  }

  @Test
  public void vaultWindowIsSecureBeforeAuthenticationAndEmptyOfPrivateContent() {
    try (var c = Robolectric.buildActivity(VaultActivity.class).create()) {
      var a = c.get();
      assertTrue(
          (a.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);
      assertTrue(findText(a.getWindow().getDecorView(), "כספת נעולה"));
    }
  }

  boolean findText(View v, String value) {
    if (v instanceof TextView && value.contentEquals(((TextView) v).getText())) return true;
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) if (findText(g.getChildAt(i), value)) return true;
    }
    return false;
  }

  @Test
  public void pinchChangesActualGridAndFullSpanDateHeaders() {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.media.clear();
      MainActivity.Media m = new MainActivity.Media();
      m.uri = android.net.Uri.parse("content://media/external/images/media/1");
      m.mime = "image/jpeg";
      m.name = "sample";
      m.path = "DCIM/";
      m.date = System.currentTimeMillis();
      a.media.add(m);
      a.render();
      RecyclerView grid = findGrid(a.root);
      assertNotNull(grid);
      GridLayoutManager manager = (GridLayoutManager) grid.getLayoutManager();
      PinchGrid pinch = new PinchGrid(a, grid);
      pinch.change(1);
      assertEquals(4, manager.getSpanCount());
      assertEquals(4, a.prefs.getInt("columns", 0));
      assertEquals(4, manager.getSpanSizeLookup().getSpanSize(0));
      pinch.change(1);
      pinch.change(1);
      assertEquals(5, manager.getSpanCount());
      for (int i = 0; i < 5; i++) pinch.change(-1);
      assertEquals(2, manager.getSpanCount());
    }
  }

  @Test
  public void recycledGridTilesStaySquareAtEveryDensity() {
    var tile = new SquareTile(RuntimeEnvironment.getApplication());
    for (int width : new int[] {160, 106, 80, 64}) {
      tile.measure(
          View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(106, View.MeasureSpec.EXACTLY));
      assertEquals(width, tile.getMeasuredWidth());
      assertEquals(width, tile.getMeasuredHeight());
    }
  }

  @Test
  public void vaultImportVerifiesCopyWithoutRemovingOriginal() throws Exception {
    var context = RuntimeEnvironment.getApplication();
    File source = new File(context.getCacheDir(), "source.png");
    byte[] bytes = new byte[VaultStore.CHUNK * 2 + 11];
    new Random(14).nextBytes(bytes);
    Files.write(source.toPath(), bytes);
    android.content.ContentProvider provider =
        new android.content.ContentProvider() {
          public boolean onCreate() {
            return true;
          }

          public String getType(android.net.Uri u) {
            return "image/png";
          }

          public android.database.Cursor query(
              android.net.Uri u, String[] p, String sel, String[] args, String sort) {
            android.database.MatrixCursor cursor =
                new android.database.MatrixCursor(
                    new String[] {android.provider.OpenableColumns.DISPLAY_NAME});
            cursor.addRow(new Object[] {"source.png"});
            return cursor;
          }

          public android.os.ParcelFileDescriptor openFile(android.net.Uri u, String mode)
              throws FileNotFoundException {
            return android.os.ParcelFileDescriptor.open(
                source, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
          }

          public android.net.Uri insert(android.net.Uri u, android.content.ContentValues v) {
            throw new UnsupportedOperationException();
          }

          public int delete(android.net.Uri u, String sel, String[] args) {
            throw new AssertionError("Import must never delete originals");
          }

          public int update(
              android.net.Uri u, android.content.ContentValues v, String sel, String[] args) {
            throw new UnsupportedOperationException();
          }
        };
    android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
    info.authority = "lume.test";
    provider.attachInfo(context, info);
    org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("lume.test", provider);
    SecretKey key = key();
    File stored =
        VaultStore.importCopy(context, android.net.Uri.parse("content://lume.test/photo"), key);
    assertTrue(stored.getName().endsWith(".lume"));
    assertTrue(source.exists());
    assertArrayEquals(bytes, Files.readAllBytes(source.toPath()));
    ByteArrayOutputStream restored = new ByteArrayOutputStream();
    VaultStore.read(stored, restored, key);
    assertArrayEquals(bytes, restored.toByteArray());
    File[] pending = VaultStore.folder(context).listFiles((d, n) -> n.endsWith(".pending"));
    assertEquals(0, pending.length);
  }

  @Test
  public void actualTwoFingerScaleChangesDensityWithoutSelectingAnItem() {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.prefs.edit().putInt("columns", 3).apply();
      a.render();
      RecyclerView grid = findGrid(a.root);
      PinchGrid listener = new PinchGrid(a, grid);
      long time = 100;
      MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 80, 200, 0);
      listener.onInterceptTouchEvent(grid, down);
      down.recycle();
      MotionEvent begin =
          two(
              time,
              time + 20,
              MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
              80,
              240);
      listener.onInterceptTouchEvent(grid, begin);
      begin.recycle();
      for (int n = 0; n < 6; n++) {
        MotionEvent move =
            two(time, time + 40 + n * 20, MotionEvent.ACTION_MOVE, 80 - n * 20, 240 + n * 20);
        listener.onTouchEvent(grid, move);
        move.recycle();
      }
      assertTrue(a.prefs.getInt("columns", 3) < 3);
      MotionEvent end = MotionEvent.obtain(time, time + 200, MotionEvent.ACTION_UP, 0, 200, 0);
      listener.onTouchEvent(grid, end);
      end.recycle();
    }
  }

  MotionEvent two(long down, long time, int action, float x1, float x2) {
    MotionEvent.PointerProperties first = new MotionEvent.PointerProperties(),
        second = new MotionEvent.PointerProperties();
    first.id = 0;
    second.id = 1;
    first.toolType = second.toolType = MotionEvent.TOOL_TYPE_FINGER;
    MotionEvent.PointerCoords a = new MotionEvent.PointerCoords(),
        b = new MotionEvent.PointerCoords();
    a.x = x1;
    b.x = x2;
    a.y = b.y = 200;
    a.pressure = b.pressure = 1;
    a.size = b.size = 1;
    return MotionEvent.obtain(
        down,
        time,
        action,
        2,
        new MotionEvent.PointerProperties[] {first, second},
        new MotionEvent.PointerCoords[] {a, b},
        0,
        0,
        1,
        1,
        0,
        0,
        android.view.InputDevice.SOURCE_TOUCHSCREEN,
        0);
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
}
