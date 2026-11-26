package il.luma.gallery;

import android.content.Context;
import android.graphics.*;
import android.security.keystore.*;
import android.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Extra at-rest protection for albums and face data; keys never leave Android Keystore. */
final class PrivateData {
  private static final String ALIAS = "lume.private.metadata.v1";
  private static final byte[] AAD = "Lume private metadata v1".getBytes(StandardCharsets.UTF_8);

  private static synchronized SecretKey key() throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
    KeyGenerator generator =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
    generator.init(
        new KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build());
    return generator.generateKey();
  }

  static byte[] seal(byte[] plain, SecretKey key) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, key);
    cipher.updateAAD(AAD);
    byte[] encrypted = cipher.doFinal(plain),
        iv = cipher.getIV(),
        result = new byte[1 + iv.length + encrypted.length];
    result[0] = (byte) iv.length;
    System.arraycopy(iv, 0, result, 1, iv.length);
    System.arraycopy(encrypted, 0, result, 1 + iv.length, encrypted.length);
    return result;
  }

  static byte[] open(byte[] data, SecretKey key) throws Exception {
    if (data.length < 29 || (data[0] & 255) != 12)
      throw new IOException("נתונים מוצפנים אינם תקינים");
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, data, 1, 12));
    cipher.updateAAD(AAD);
    return cipher.doFinal(data, 13, data.length - 13);
  }

  static String encrypt(String text) {
    try {
      return "v1:"
          + Base64.encodeToString(
              seal(text.getBytes(StandardCharsets.UTF_8), key()), Base64.NO_WRAP);
    } catch (Exception e) {
      throw new IllegalStateException("לא ניתן לשמור נתונים פרטיים מוצפנים", e);
    }
  }

  static String decrypt(String text) {
    if (text == null) return "";
    if (!text.startsWith("v1:")) return text;
    try {
      return new String(
          open(Base64.decode(text.substring(3), Base64.NO_WRAP), key()), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("לא ניתן לפתוח את הנתונים הפרטיים במכשיר זה", e);
    }
  }

  static String read(Context c, String name, String fallback) {
    return decrypt(c.getSharedPreferences("luma", 0).getString(name, fallback));
  }

  static void write(Context c, String name, String text) {
    if (!c.getSharedPreferences("luma", 0).edit().putString(name, encrypt(text)).commit())
      throw new IllegalStateException("שמירת הנתונים נכשלה");
  }

  static void cover(Context c, String id, Bitmap bitmap) throws Exception {
    if (!id.matches("[a-zA-Z0-9-]{1,80}")) throw new IOException("מזהה קבוצה לא תקין");
    File folder = new File(c.getFilesDir(), "people-covers");
    folder.mkdirs();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes);
    byte[] encrypted = seal(bytes.toByteArray(), key());
    AtomicFile file = new AtomicFile(new File(folder, id + ".enc"));
    FileOutputStream stream = null;
    try {
      stream = file.startWrite();
      stream.write(encrypted);
      file.finishWrite(stream);
    } catch (Exception e) {
      if (stream != null) file.failWrite(stream);
      throw e;
    }
  }

  static Bitmap cover(Context c, String id) {
    if (!id.matches("[a-zA-Z0-9-]{1,80}")) return null;
    File folder = new File(c.getFilesDir(), "people-covers"), file = new File(folder, id + ".enc");
    try {
      if (file.isFile()) {
        byte[] bytes = open(java.nio.file.Files.readAllBytes(file.toPath()), key());
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
      }
    } catch (Exception e) {
      return null;
    }
    File old = new File(folder, id + ".jpg");
    return old.isFile() ? BitmapFactory.decodeFile(old.toString()) : null;
  }

  static void migrateMetadata(Context c) {
    var prefs = c.getSharedPreferences("luma", 0);
    for (String name : new String[] {"personalAlbums", "faceProfiles"}) {
      String value = prefs.getString(name, null);
      if (value != null && !value.startsWith("v1:")) write(c, name, value);
    }
  }

  static synchronized void clearCovers(Context c) {
    var prefs = c.getSharedPreferences("luma", 0);
    prefs.edit().putInt("coverGeneration", prefs.getInt("coverGeneration", 0) + 1).commit();
    File[] files = new File(c.getFilesDir(), "people-covers").listFiles();
    if (files != null) for (File file : files) file.delete();
  }

  static void migrateCovers(Context c) {
    int generation = c.getSharedPreferences("luma", 0).getInt("coverGeneration", 0);
    File[] files =
        new File(c.getFilesDir(), "people-covers").listFiles((d, n) -> n.endsWith(".jpg"));
    if (files == null) return;
    for (File old : files) {
      Bitmap bitmap = BitmapFactory.decodeFile(old.toString());
      if (bitmap == null) continue;
      try {
        synchronized (PrivateData.class) {
          if (generation != c.getSharedPreferences("luma", 0).getInt("coverGeneration", 0)) return;
          if (!old.isFile()) continue;
          String id = old.getName().replace(".jpg", "");
          cover(c, id, bitmap);
          Bitmap check = cover(c, id);
          if (check != null) {
            check.recycle();
            old.delete();
          }
        }
      } catch (Exception ignored) {
      } finally {
        bitmap.recycle();
      }
    }
  }
}
