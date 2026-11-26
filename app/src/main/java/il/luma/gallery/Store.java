package il.luma.gallery;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.util.*;
import org.json.*;

final class Store {
  static JSONArray rules(Context c) {
    try {
      return new JSONArray(c.getSharedPreferences("luma", 0).getString("rules", "[]"));
    } catch (Exception e) {
      return new JSONArray();
    }
  }

  static void save(Context c, JSONArray a) {
    c.getSharedPreferences("luma", 0).edit().putString("rules", a.toString()).commit();
  }

  static File trash(Context c) {
    File f = new File(c.getFilesDir(), "trash");
    f.mkdirs();
    return f;
  }

  static void copy(InputStream in, OutputStream out) throws IOException {
    if (in == null || out == null) throw new IOException("לא ניתן לפתוח קובץ");
    try (in;
        out) {
      byte[] b = new byte[65536];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
      out.flush();
      if (out instanceof FileOutputStream) ((FileOutputStream) out).getFD().sync();
    }
  }

  static class Doc {
    Uri uri;
    String name, mime;
    long date, size;

    Doc(Uri u, String n, String m, long d, long s) {
      uri = u;
      name = n;
      mime = m;
      date = d;
      size = s;
    }
  }

  static List<Doc> candidates(Context c, JSONObject r) throws Exception {
    List<Doc> out = new ArrayList<>();
    Uri tree = Uri.parse(r.getString("tree"));
    walk(c, tree, DocumentsContract.getTreeDocumentId(tree), r, out, 0);
    return out;
  }

  static void walk(Context c, Uri tree, String id, JSONObject r, List<Doc> out, int depth)
      throws Exception {
    if (depth > 12) return;
    Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
    try (Cursor q =
        c.getContentResolver()
            .query(
                kids,
                new String[] {
                  "document_id", "_display_name", "mime_type", "last_modified", "_size"
                },
                null,
                null,
                null)) {
      if (q == null) throw new IOException("אין גישה לתיקייה");
      while (q.moveToNext()) {
        String doc = q.getString(0), name = q.getString(1), mime = q.getString(2);
        long date = q.getLong(3), size = q.getLong(4);
        if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
          if (r.optBoolean("recursive")) walk(c, tree, doc, r, out, depth + 1);
          continue;
        }
        String type = r.optString("type", "all");

        // Never infer age when the provider has no timestamp, and never process non-media.
        if (RulePolicy.matches(mime, date, System.currentTimeMillis(), r.getInt("days"), type))
          out.add(
              new Doc(
                  DocumentsContract.buildDocumentUriUsingTree(tree, doc), name, mime, date, size));
      }
    }
  }

  static synchronized int clean(Context c, JSONObject rule) throws Exception {
    int count = 0;
    for (Doc d : candidates(c, rule)) {
      if (Thread.currentThread().isInterrupted()) break;
      long limit = c.getSharedPreferences("luma", 0).getInt("trashLimit", 2048) * 1024L * 1024L;
      File dir = trash(c);
      long used = 0;
      for (File f : Objects.requireNonNull(dir.listFiles())) used += f.length();
      if (used + d.size > limit || dir.getUsableSpace() < d.size + 128 * 1024 * 1024L)
        throw new IOException("סל השחזור מלא או אין מספיק מקום; הניקוי נעצר");
      String key = UUID.randomUUID().toString();
      File data = new File(dir, key + ".bin"), meta = new File(dir, key + ".json");
      try {
        copy(c.getContentResolver().openInputStream(d.uri), new FileOutputStream(data));
        if (data.length() != d.size && d.size > 0) throw new IOException("גודל הגיבוי אינו תואם");
        JSONObject j =
            new JSONObject()
                .put("name", d.name)
                .put("mime", d.mime)
                .put("tree", rule.getString("tree"))
                .put("source", d.uri.toString())
                .put("time", System.currentTimeMillis())
                .put("deleted", false);
        try (FileOutputStream w = new FileOutputStream(meta)) {
          w.write(j.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
          w.getFD().sync();
        }
        try (Cursor verify =
            c.getContentResolver()
                .query(d.uri, new String[] {"last_modified", "_size"}, null, null, null)) {
          if (verify == null
              || !verify.moveToFirst()
              || verify.getLong(0) != d.date
              || verify.getLong(1) != d.size)
            throw new IOException("הקובץ השתנה בזמן הגיבוי; הוא לא יימחק");
        }
        if (!Arrays.equals(
            digest(new FileInputStream(data)),
            digest(c.getContentResolver().openInputStream(d.uri))))
          throw new IOException("אימות תוכן הגיבוי נכשל; המקור לא יימחק");
        if (!DocumentsContract.deleteDocument(c.getContentResolver(), d.uri))
          throw new IOException("הספק לא אישר מחיקה");
        count++;
      } catch (
          Exception
              e) { // Retain any completed backup on uncertainty; never remove the only surviving
        // copy.
        if (!meta.exists()) data.delete();
        throw e;
      }
    }
    return count;
  }

  static byte[] digest(InputStream input) throws Exception {
    if (input == null) throw new IOException("לא ניתן לאמת את הקובץ");
    java.security.MessageDigest hash = java.security.MessageDigest.getInstance("SHA-256");
    try (InputStream in = input) {
      byte[] buffer = new byte[65536];
      int n;
      while ((n = in.read(buffer)) != -1) hash.update(buffer, 0, n);
    }
    return hash.digest();
  }

  static JSONObject metadata(File f) throws Exception {
    return new JSONObject(
        new String(
            java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8));
  }

  static synchronized void restore(Context c, File meta) throws Exception {
    JSONObject j = metadata(meta);
    Uri tree = Uri.parse(j.getString("tree"));
    Uri parent =
        DocumentsContract.buildDocumentUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree));
    Uri dest =
        DocumentsContract.createDocument(
            c.getContentResolver(), parent, j.getString("mime"), j.getString("name"));
    if (dest == null) throw new IOException("יצירת הקובץ נכשלה");
    File data = new File(meta.getPath().replace(".json", ".bin"));
    try {
      copy(new FileInputStream(data), c.getContentResolver().openOutputStream(dest));
      if (!Arrays.equals(
          digest(new FileInputStream(data)), digest(c.getContentResolver().openInputStream(dest))))
        throw new IOException("אימות השחזור נכשל; עותק הגיבוי נשמר");
    } catch (Exception e) {
      DocumentsContract.deleteDocument(c.getContentResolver(), dest);
      throw e;
    }
    data.delete();
    meta.delete();
  }
}
