package il.luma.gallery;

import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.*;
import java.util.UUID;

/** Private working images and explicit published copies; source media is never overwritten. */
final class EditorFiles {
  static MainActivity.Media stage(Context c, Bitmap image, String name) throws Exception {
    File dir = new File(c.getCacheDir(), "editor-inputs");
    if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("לא ניתן להכין תמונת עבודה");
    File file = new File(dir, UUID.randomUUID() + ".png");
    try (OutputStream out = new FileOutputStream(file)) {
      if (!image.compress(Bitmap.CompressFormat.PNG, 100, out))
        throw new IOException("שמירת תמונת העבודה נכשלה");
    }
    MainActivity.Media m = new MainActivity.Media();
    m.uri = Uri.fromFile(file);
    m.mime = "image/png";
    m.name = name;
    m.path = "עבודה פרטית";
    m.width = image.getWidth();
    m.height = image.getHeight();
    m.size = file.length();
    m.date = System.currentTimeMillis();
    return m;
  }

  static void clean(Context c) {
    File[] files = new File(c.getCacheDir(), "editor-inputs").listFiles();
    if (files != null)
      for (File f : files)
        if (System.currentTimeMillis() - f.lastModified() > 2 * 24 * 60 * 60_000L) f.delete();
  }

  static void publish(Context c, Uri uri) throws IOException {
    ContentValues values = new ContentValues();
    values.put(MediaStore.MediaColumns.IS_PENDING, 0);
    if (c.getContentResolver().update(uri, values, null, null) < 1)
      throw new IOException("פרסום העותק לא הושלם");
  }

  static Uri saveFile(Context c, File input, String mime, String extension) throws Exception {
    boolean audio = mime.startsWith("audio/");
    Uri collection =
        audio
            ? MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            : MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
    ContentValues v = new ContentValues();
    v.put(
        MediaStore.MediaColumns.DISPLAY_NAME,
        "Lume-" + System.currentTimeMillis() + "." + extension);
    v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
    v.put(MediaStore.MediaColumns.RELATIVE_PATH, audio ? "Music/Lume" : "Movies/Lume");
    v.put(MediaStore.MediaColumns.IS_PENDING, 1);
    Uri uri = c.getContentResolver().insert(collection, v);
    if (uri == null) throw new IOException("יצירת עותק נכשלה");
    try (InputStream in = new FileInputStream(input);
        OutputStream out = c.getContentResolver().openOutputStream(uri)) {
      if (out == null) throw new IOException("פתיחת הפלט נכשלה");
      byte[] buffer = new byte[65536];
      int n;
      while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
    } catch (Exception e) {
      c.getContentResolver().delete(uri, null, null);
      throw e;
    }
    try {
      publish(c, uri);
      return uri;
    } catch (Exception e) {
      c.getContentResolver().delete(uri, null, null);
      throw e;
    }
  }
}
