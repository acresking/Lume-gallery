package il.luma.gallery;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Only downloads pinned, public model weights. This class never reads or uploads gallery files. */
final class LocalModels {
  static final String[] IDS = {
    "classify", "similar", "portrait", "faces", "repair", "facegroup", "enhance", "objects"
  };
  static final String[] NAMES = {
    "זיהוי תוכן ותגיות",
    "תמונות דומות",
    "כלי דיוקן ורקע",
    "איתור וטשטוש פנים",
    "תיקון והשלמת אזורים",
    "קיבוץ פנים מקומי",
    "הגדלה חכמה פי 3",
    "איתור פריטים לסימון"
  };
  static final String[] DESCRIPTIONS = {
    "EfficientNet Lite0 · סיווג תמונות ותגיות באנגלית עם מילות חיפוש בעברית",
    "MobileNet V3 Small · השוואה חזותית בין תמונות שאונדקסו",
    "Selfie Segmenter · הפרדת אדם בלבד, רקע שקוף או מטושטש",
    "BlazeFace · איתור פנים",
    "LaMa · מילוי אזור מסומן והרחבת שוליים",
    "SFace · קיבוץ דמיון בין פנים, בלי שמות אוטומטיים",
    "ESPCN · הגדלת רזולוציה",
    "EfficientDet · תיבות פריטים לבחירה"
  };

  interface Progress {
    void update(long bytes, long total);
  }

  static File dir(Context c) {
    File d = new File(c.getFilesDir(), "ai-models");
    d.mkdirs();
    return d;
  }

  static JSONObject catalog(Context c) throws Exception {
    try (InputStream in = c.getAssets().open("ai-models.json")) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int n;
      while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
      return new JSONObject(
          new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
    }
  }

  static File file(Context c, String id) {
    return new File(
        dir(c),
        id
            + ((id.equals("repair") || id.equals("facegroup") || id.equals("enhance"))
                ? ".onnx"
                : ".tflite"));
  }

  static boolean installed(Context c, String id) {
    try {
      return file(c, id).isFile()
          && file(c, id).length() == catalog(c).getJSONObject(id).getLong("size");
    } catch (Exception e) {
      return false;
    }
  }

  static String path(Context c, String id) throws Exception {
    if (!installed(c, id)) throw new IOException("המודל לא מותקן. פתח את ניהול AI והורד אותו.");
    File f = file(c, id);
    String expected = catalog(c).getJSONObject(id).getString("sha256");
    if (!sha(f).equals(expected))
      throw new IOException("בדיקת תקינות המודל נכשלה. מחק והורד מחדש.");
    return f.getAbsolutePath();
  }

  static String sha(File f) throws Exception {
    byte[] value = Store.digest(new FileInputStream(f));
    StringBuilder out = new StringBuilder();
    for (byte b : value) out.append(String.format(Locale.ROOT, "%02x", b & 255));
    return out.toString();
  }

  static void download(Context context, String id, AtomicBoolean cancel, Progress progress)
      throws Exception {
    JSONObject spec = catalog(context).getJSONObject(id);
    long total = spec.getLong("size");
    if (dir(context).getUsableSpace() < total + 32 * 1024 * 1024L)
      throw new IOException("אין מספיק מקום להתקנת המודל");
    File partial = new File(dir(context), id + ".part");
    HttpURLConnection connection = null;
    try {
      URL url = new URL(spec.getString("url"));
      if (!url.getProtocol().equals("https")
          || !(url.getHost().equals("storage.googleapis.com")
              || url.getHost().equals("huggingface.co")))
        throw new IOException("מקור מודל לא מורשה");
      for (int redirect = 0; redirect < 8; redirect++) {
        connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(false);
        int code = connection.getResponseCode();
        if (code == 200) break;
        if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
          String location = connection.getHeaderField("Location");
          connection.disconnect();
          url = new URL(url, location);
          String host = url.getHost();
          if (!url.getProtocol().equals("https")
              || !(host.equals("huggingface.co")
                  || host.endsWith(".hf.co")
                  || host.endsWith(".huggingface.co")))
            throw new IOException("הפניית הורדה לא מורשית");
        } else throw new IOException("הורדת החבילה נכשלה: " + code);
      }
      if (connection.getResponseCode() != 200) throw new IOException("יותר מדי הפניות בהורדה");
      long count = 0, last = 0;
      byte[] buffer = new byte[32768];
      try (InputStream in = connection.getInputStream();
          FileOutputStream out = new FileOutputStream(partial)) {
        int n;
        while ((n = in.read(buffer)) != -1) {
          if (cancel.get() || Thread.currentThread().isInterrupted())
            throw new InterruptedIOException("ההורדה בוטלה");
          count += n;
          if (count > total) throw new IOException("גודל מודל לא צפוי");
          out.write(buffer, 0, n);
          long now = System.currentTimeMillis();
          if (now - last > 200) {
            progress.update(count, total);
            last = now;
          }
        }
        out.getFD().sync();
      }
      if (count != total || !sha(partial).equals(spec.getString("sha256")))
        throw new IOException("קובץ המודל אינו תקין; הוא לא יופעל");
      if (cancel.get()) throw new InterruptedIOException("ההורדה בוטלה");
      java.nio.file.Files.move(
          partial.toPath(),
          file(context, id).toPath(),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING,
          java.nio.file.StandardCopyOption.ATOMIC_MOVE);
      progress.update(total, total);
    } finally {
      if (connection != null) connection.disconnect();
      partial.delete();
    }
  }
}
