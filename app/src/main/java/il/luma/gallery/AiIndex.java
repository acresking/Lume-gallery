package il.luma.gallery;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import java.nio.*;
import java.util.*;

/** Local, disposable derived metadata. Original media is never changed by indexing. */
final class AiIndex extends SQLiteOpenHelper {
  static final class Entry {
    String uri, fingerprint, tags, models;
    float[] vector;

    Entry(String u, String f, String t, String m, float[] v) {
      uri = u;
      fingerprint = f;
      tags = t;
      models = m;
      vector = v;
    }
  }

  AiIndex(Context c) {
    super(c, "local-ai.db", null, 1);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE images(uri TEXT PRIMARY KEY,fingerprint TEXT NOT NULL,tags TEXT NOT"
            + " NULL,models TEXT NOT NULL,vector BLOB)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int old, int next) {}

  static String fingerprint(MainActivity.Media m) {
    return m.size + ":" + m.modified + ":" + m.date;
  }

  synchronized Map<String, Entry> all() {
    Map<String, Entry> result = new HashMap<>();
    try (Cursor c = getReadableDatabase().query("images", null, null, null, null, null, null)) {
      while (c.moveToNext()) {
        byte[] raw = c.isNull(4) ? null : c.getBlob(4);
        float[] vector = null;
        if (raw != null) {
          FloatBuffer data = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
          vector = new float[data.remaining()];
          data.get(vector);
        }
        result.put(
            c.getString(0),
            new Entry(c.getString(0), c.getString(1), c.getString(2), c.getString(3), vector));
      }
    }
    return result;
  }

  synchronized void put(Entry e) {
    ContentValues v = new ContentValues();
    v.put("uri", e.uri);
    v.put("fingerprint", e.fingerprint);
    v.put("tags", e.tags);
    v.put("models", e.models);
    if (e.vector != null) {
      ByteBuffer bytes = ByteBuffer.allocate(e.vector.length * 4).order(ByteOrder.LITTLE_ENDIAN);
      bytes.asFloatBuffer().put(e.vector);
      v.put("vector", bytes.array());
    } else v.putNull("vector");
    getWritableDatabase().insertWithOnConflict("images", null, v, SQLiteDatabase.CONFLICT_REPLACE);
  }

  synchronized void clear() {
    getWritableDatabase().delete("images", null, null);
  }

  static double cosine(float[] a, float[] b) {
    if (a == null || b == null || a.length != b.length) return -1;
    double dot = 0, x = 0, y = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      x += a[i] * a[i];
      y += b[i] * b[i];
    }
    return x == 0 || y == 0 ? -1 : dot / Math.sqrt(x * y);
  }

  static String keywords(String labels) {
    String lower = labels.toLowerCase(Locale.ROOT);
    StringBuilder out = new StringBuilder(labels);
    String[][] map = {
      {"dog retriever terrier poodle hound shepherd puppy", "כלב כלבים חיות בעלי חיים"},
      {"cat tabby persian siamese kitten", "חתול חתולים חיות בעלי חיים"},
      {"mountain alp cliff valley volcano", "הר הרים טבע נוף טיול"},
      {"lakeside seashore beach coast ocean sea", "ים חוף אגם מים טבע נוף"},
      {"forest tree wood pine", "יער עצים טבע טיול"},
      {"flower daisy rose sunflower plant", "פרח פרחים צמח טבע"},
      {"bird robin eagle penguin parrot", "ציפור ציפורים חיות"},
      {"car cab taxi minivan truck jeep bus", "רכב מכונית תחבורה"},
      {"pizza sandwich plate broccoli banana orange apple cake food", "אוכל מזון ארוחה פרי"},
      {"person people face bride groom bikini suit", "אדם אנשים פנים דיוקן"},
      {"castle palace monastery church building tower", "בניין מבנה עיר אדריכלות"},
      {"book notebook paper menu envelope", "ספר דף נייר מסמך"},
      {"computer laptop monitor keyboard screen", "מחשב מסך טכנולוגיה"},
      {"snow ski ice glacier", "שלג חורף קרח טבע"},
      {"boat ship sail canoe", "סירה ספינה ים מים"}
    };
    for (String[] pair : map)
      for (String word : pair[0].split(" "))
        if (java.util.regex.Pattern.compile("\\b" + word + "\\b").matcher(lower).find()) {
          out.append(' ').append(pair[1]);
          break;
        }
    return out.toString();
  }
}
