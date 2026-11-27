package il.luma.gallery;

import android.content.Context;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.security.keystore.*;
import java.io.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/**
 * Authenticated chunked encryption: bounded memory, ordered chunks and authenticated end marker.
 */
final class VaultStore {
  static final int CHUNK = 65536;
  private static final byte[] MAGIC = new byte[] {'L', 'U', 'M', 'E', 'V', '1', '1', 0};

  static javax.crypto.SecretKey key() throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    String alias = "lume.vault.device-credential.v1";
    if (store.containsAlias(alias)) return (javax.crypto.SecretKey) store.getKey(alias, null);
    KeyGenerator generator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
    generator.init(
        new KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(300, KeyProperties.AUTH_DEVICE_CREDENTIAL)
            .setUnlockedDeviceRequired(true)
            .build());
    return generator.generateKey();
  }

  static File folder(Context c) {
    File dir = new File(c.getFilesDir(), "vault");
    if (!dir.isDirectory() && !dir.mkdirs())
      throw new IllegalStateException("Vault storage unavailable");
    return dir;
  }

  static byte[] aad(byte[] id, int index) throws IOException {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    DataOutputStream d = new DataOutputStream(b);
    d.write(MAGIC);
    d.write(id);
    d.writeInt(index);
    return b.toByteArray();
  }

  static void writeChunk(
      DataOutputStream out, byte[] plain, byte[] id, int index, javax.crypto.SecretKey key)
      throws Exception {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, key);
    cipher.updateAAD(aad(id, index));
    byte[] sealed = cipher.doFinal(plain);
    out.writeInt(sealed.length);
    out.write(cipher.getIV());
    out.write(sealed);
  }

  static byte[] readChunk(DataInputStream in, byte[] id, int index, javax.crypto.SecretKey key)
      throws Exception {
    int size = in.readInt();
    if (size < 16 || size > CHUNK + 16) throw new IOException("Invalid encrypted chunk");
    byte[] iv = new byte[12], sealed = new byte[size];
    in.readFully(iv);
    in.readFully(sealed);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
    cipher.updateAAD(aad(id, index));
    return cipher.doFinal(sealed);
  }

  static JSONObject read(File file, OutputStream plain, javax.crypto.SecretKey key)
      throws Exception {
    try (DataInputStream in =
        new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
      byte[] magic = new byte[8], id = new byte[16];
      in.readFully(magic);
      if (!Arrays.equals(magic, MAGIC)) throw new IOException("Invalid vault file");
      in.readFully(id);
      JSONObject metadata =
          new JSONObject(
              new String(readChunk(in, id, 0, key), java.nio.charset.StandardCharsets.UTF_8));
      if (plain == null) return metadata;
      for (int index = 1; ; index++) {
        byte[] chunk = readChunk(in, id, index, key);
        if (chunk.length == 0) {
          if (in.read() != -1) throw new IOException("Trailing encrypted data");
          break;
        }
        plain.write(chunk);
      }
      return metadata;
    }
  }

  static File importCopy(Context c, Uri uri, javax.crypto.SecretKey key) throws Exception {
    String name = "מדיה", mime = c.getContentResolver().getType(uri);
    try (android.database.Cursor cursor =
        c.getContentResolver()
            .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
    }
    if (mime == null || !(mime.startsWith("image/") || mime.startsWith("video/")))
      throw new IOException("בחר תמונה או סרטון");
    if (name == null) name = "מדיה";
    JSONObject metadata = new JSONObject().put("name", name).put("mime", mime);
    byte[] id = new byte[16];
    new SecureRandom().nextBytes(id);
    File temp = new File(folder(c), UUID.randomUUID() + ".pending"),
        target = new File(folder(c), UUID.randomUUID() + ".lume");
    MessageDigest original = MessageDigest.getInstance("SHA-256");
    try {
      try (InputStream source = c.getContentResolver().openInputStream(uri);
          FileOutputStream raw = new FileOutputStream(temp);
          DataOutputStream out = new DataOutputStream(new BufferedOutputStream(raw))) {
        if (source == null) throw new IOException("הקובץ אינו זמין");
        out.write(MAGIC);
        out.write(id);
        writeChunk(
            out, metadata.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), id, 0, key);
        byte[] buffer = new byte[CHUNK];
        int index = 1, amount;
        while ((amount = source.read(buffer)) != -1) {
          if (Thread.currentThread().isInterrupted()) throw new IOException("הייבוא נעצר");
          if (amount == 0) continue;
          if (folder(c).getUsableSpace() < CHUNK * 8L) throw new IOException("אין מספיק מקום פנוי");
          original.update(buffer, 0, amount);
          writeChunk(out, Arrays.copyOf(buffer, amount), id, index++, key);
        }
        writeChunk(out, new byte[0], id, index, key);
        out.flush();
        raw.getFD().sync();
      }
      MessageDigest check = MessageDigest.getInstance("SHA-256");
      read(
          temp,
          new java.security.DigestOutputStream(
              new OutputStream() {
                @Override
                public void write(int b) {}

                @Override
                public void write(byte[] b, int off, int len) {}
              },
              check),
          key);
      if (!MessageDigest.isEqual(original.digest(), check.digest()))
        throw new IOException("אימות העותק נכשל");
      if (!temp.renameTo(target)) throw new IOException("שמירת העותק נכשלה");
      return target;
    } finally {
      temp.delete();
    }
  }
}
