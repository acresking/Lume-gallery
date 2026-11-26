package il.luma.gallery;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.*;
import android.provider.MediaStore;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;

/** Incremental local organiser shared by manual scans and Android background jobs. */
final class OrganizeEngine {
  static final AtomicBoolean RUNNING = new AtomicBoolean();

  static final class Result {
    int processed, faces, places, failed;
    boolean pending;
    String message = "";
  }

  static String fingerprint(MainActivity.Media m) {
    return m.uri + ":" + AiIndex.fingerprint(m);
  }

  static JSONArray albums(Context c) throws JSONException {
    return new JSONArray(PrivateData.read(c, "personalAlbums", "[]"));
  }

  static Result run(
      Context context, AtomicBoolean cancel, int limit, boolean requestFaces, boolean requestPlaces)
      throws Exception {
    Result result = new Result();
    if (!RUNNING.compareAndSet(false, true)) {
      result.message = "סריקה אחרת פעילה";
      result.pending = true;
      return result;
    }
    try {
      var prefs = context.getSharedPreferences("luma", 0);
      boolean faces =
          requestFaces
              && LocalModels.installed(context, "faces")
              && LocalModels.installed(context, "facegroup");
      boolean places =
          requestPlaces
              && context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)
                  == PackageManager.PERMISSION_GRANTED;
      if (!MediaAccess.canRead(context)) {
        result.message = "ממתין להרשאת תמונות";
        return result;
      }
      if (!faces && !places) {
        result.message = "ממתין לרכיבי AI או להרשאת מיקום במדיה";
        return result;
      }
      ArrayList<MainActivity.Media> photos = MediaAccess.images(context);
      Set<String> visible = new HashSet<>(), fingerprints = new HashSet<>();
      for (var m : photos) {
        visible.add(m.uri.toString());
        fingerprints.add(fingerprint(m));
      }
      Set<String>
          faceDone = new HashSet<>(prefs.getStringSet("peopleScanned", Collections.emptySet())),
          placeDone = new HashSet<>(prefs.getStringSet("placesScanned", Collections.emptySet()));
      JSONObject profiles = new JSONObject(PrivateData.read(context, "faceProfiles", "{}")),
          retries = new JSONObject(prefs.getString("organizeRetries", "{}"));
      Map<String, float[]> vectors = new LinkedHashMap<>();
      Map<String, LinkedHashSet<String>> members = new LinkedHashMap<>(),
          locations = new LinkedHashMap<>();
      Map<String, String> names = new HashMap<>();
      Set<String> originalGroups = new HashSet<>(),
          usedNames = new HashSet<>(),
          rescannedFaces = new HashSet<>(),
          rescannedPlaces = new HashSet<>();
      JSONArray snapshot = albums(context);
      for (int i = 0; i < snapshot.length(); i++) {
        JSONObject g = snapshot.getJSONObject(i);
        usedNames.add(g.optString("name"));
        if (g.optString("kind").equals("person")) {
          String id = g.optString("id");
          originalGroups.add(id);
          JSONArray array = profiles.optJSONArray(id);
          if (array != null) {
            float[] vector = new float[array.length()];
            for (int j = 0; j < vector.length; j++) vector[j] = (float) array.optDouble(j);
            vectors.put(id, vector);
            members.put(id, new LinkedHashSet<>());
            names.put(id, g.optString("name"));
          }
        }
      }
      boolean changed = false;
      long now = System.currentTimeMillis();
      try (AiEngine detector = faces ? new AiEngine(context) : null;
          AdvancedEngine engine = faces ? new AdvancedEngine(context) : null) {
        for (var m : photos) {
          if (cancel.get() || Thread.currentThread().isInterrupted()) break;
          String fp = fingerprint(m);
          if (retries.optLong(fp, 0) > now) continue;
          boolean findFaces = faces && !faceDone.contains(fp),
              findPlace = places && !placeDone.contains(fp);
          if (!findFaces && !findPlace) continue;
          if (result.processed >= limit) {
            result.pending = true;
            break;
          }
          result.processed++;
          changed = true;
          boolean failed = false;
          if (findFaces) {
            Bitmap bitmap = null;
            try {
              bitmap = MediaAccess.decode(context, m.uri, 1000);
              for (Bitmap face : detector.alignedFaces(bitmap)) {
                try {
                  if (cancel.get()) break;
                  float[] vector = engine.faceVector(face);
                  String match = null;
                  double best = prefs.getFloat("faceSimilarity", .55f);
                  for (var entry : vectors.entrySet()) {
                    double score = AiIndex.cosine(vector, entry.getValue());
                    if (score > best) {
                      best = score;
                      match = entry.getKey();
                    }
                  }
                  if (match == null) {
                    match = UUID.randomUUID().toString();
                    int number = vectors.size() + 1;
                    String name = "אדם " + number;
                    while (usedNames.contains(name)) name = "אדם " + (++number);
                    usedNames.add(name);
                    vectors.put(match, vector);
                    members.put(match, new LinkedHashSet<>());
                    names.put(match, name);
                  }
                  if (!new File(new File(context.getFilesDir(), "people-covers"), match + ".enc")
                      .isFile()) PrivateData.cover(context, match, face);
                  members.get(match).add(m.uri.toString());
                  result.faces++;
                } finally {
                  if (!face.isRecycled()) face.recycle();
                }
              }
              if (!cancel.get()) {
                faceDone.add(fp);
                rescannedFaces.add(m.uri.toString());
              }
            } catch (Exception error) {
              failed = true;
            } finally {
              if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            }
          }
          if (findPlace) {
            try (InputStream stream =
                context
                    .getContentResolver()
                    .openInputStream(MediaStore.setRequireOriginal(m.uri))) {
              if (stream == null) throw new IOException("קריאת המדיה נכשלה");
              double[] gps = new ExifInterface(stream).getLatLong();
              if (gps != null) {
                String id = String.format(Locale.ROOT, "%.1f, %.1f", gps[0], gps[1]);
                locations.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(m.uri.toString());
                result.places++;
              }
              placeDone.add(fp);
              rescannedPlaces.add(m.uri.toString());
            } catch (Exception error) {
              failed = true;
            }
          }
          if (failed) {
            result.failed++;
            retries.put(fp, now + 24 * 60 * 60 * 1000L);
          } else retries.remove(fp);
        }
      }
      if (!changed) {
        for (int i = 0; i < snapshot.length(); i++) {
          JSONObject group = snapshot.getJSONObject(i);
          if (!isAutoGroup(group)) continue;
          JSONArray items = group.optJSONArray("items");
          if (items != null)
            for (int j = 0; j < items.length(); j++)
              if (!visible.contains(items.optString(j))) changed = true;
        }
        if (!changed) {
          result.message = "הספרייה מעודכנת";
          return result;
        }
      }
      JSONObject nextProfiles = new JSONObject();
      for (var entry : vectors.entrySet()) {
        JSONArray array = new JSONArray();
        for (float value : entry.getValue()) array.put(value);
        nextProfiles.put(entry.getKey(), array);
      }
      faceDone.retainAll(fingerprints);
      placeDone.retainAll(fingerprints);
      ArrayList<String> stale = new ArrayList<>();
      for (var keys = retries.keys(); keys.hasNext(); ) {
        String key = keys.next();
        if (!fingerprints.contains(key)) stale.add(key);
      }
      for (String key : stale) retries.remove(key);
      CountDownLatch complete = new CountDownLatch(1);
      AtomicReference<Exception> failure = new AtomicReference<>();
      Runnable commit =
          () -> {
            try {
              JSONArray current = albums(context);
              Set<String> currentIds = new HashSet<>();
              for (int i = 0; i < current.length(); i++) {
                JSONObject g = current.getJSONObject(i);
                currentIds.add(g.optString("id"));
                removeMembers(
                    g,
                    g.optString("kind").equals("person")
                        ? rescannedFaces
                        : g.optString("kind").equals("place")
                            ? rescannedPlaces
                            : Collections.emptySet());
              }
              for (var entry : members.entrySet()) {
                if (entry.getValue().isEmpty()
                    || (originalGroups.contains(entry.getKey())
                        && !currentIds.contains(entry.getKey()))) continue;
                JSONObject g = find(current, entry.getKey());
                if (g == null) {
                  g =
                      new JSONObject()
                          .put("id", entry.getKey())
                          .put("kind", "person")
                          .put("name", names.get(entry.getKey()))
                          .put("items", new JSONArray());
                  current.put(g);
                }
                merge(g, entry.getValue());
              }
              for (var entry : locations.entrySet()) {
                String id = "place:" + entry.getKey();
                JSONObject g = find(current, id);
                if (g == null) {
                  g =
                      new JSONObject()
                          .put("id", id)
                          .put("kind", "place")
                          .put("name", "מקום " + entry.getKey())
                          .put("items", new JSONArray());
                  current.put(g);
                }
                merge(g, entry.getValue());
              }
              for (int i = current.length() - 1; i >= 0; i--) {
                JSONObject g = current.getJSONObject(i);
                if (!isAutoGroup(g)) continue;
                JSONArray old = g.optJSONArray("items"), kept = new JSONArray();
                if (old != null)
                  for (int j = 0; j < old.length(); j++)
                    if (visible.contains(old.optString(j))) kept.put(old.optString(j));
                g.put("items", kept);
                if (kept.length() == 0) {
                  String id = g.optString("id");
                  current.remove(i);
                  nextProfiles.remove(id);
                  if (id.matches("[a-zA-Z0-9-]{1,80}"))
                    new File(new File(context.getFilesDir(), "people-covers"), id + ".enc")
                        .delete();
                }
              }
              for (String id : vectors.keySet())
                if (find(current, id) == null) nextProfiles.remove(id);
              if (!prefs
                  .edit()
                  .putString("personalAlbums", PrivateData.encrypt(current.toString()))
                  .putString("faceProfiles", PrivateData.encrypt(nextProfiles.toString()))
                  .putStringSet("peopleScanned", faceDone)
                  .putStringSet("placesScanned", placeDone)
                  .putString("organizeRetries", retries.toString())
                  .commit()) throw new IOException("שמירת הסידור נכשלה");
            } catch (Exception error) {
              failure.set(error);
            } finally {
              complete.countDown();
            }
          };
      if (Looper.myLooper() == Looper.getMainLooper()) commit.run();
      else {
        new Handler(Looper.getMainLooper()).post(commit);
        if (!complete.await(30, TimeUnit.SECONDS))
          throw new IOException("שמירת הסידור לא הושלמה בזמן");
      }
      if (failure.get() != null) throw failure.get();
      result.message = "נסרקו " + result.processed + " תמונות · " + result.failed + " לא נבדקו";
      return result;
    } finally {
      RUNNING.set(false);
    }
  }

  static boolean isAutoGroup(JSONObject g) {
    return g.optString("kind").equals("person") || g.optString("kind").equals("place");
  }

  static JSONObject find(JSONArray albums, String id) throws JSONException {
    for (int i = 0; i < albums.length(); i++)
      if (albums.getJSONObject(i).optString("id").equals(id)) return albums.getJSONObject(i);
    return null;
  }

  static void removeMembers(JSONObject g, Set<String> removed) throws JSONException {
    if (removed.isEmpty()) return;
    JSONArray old = g.optJSONArray("items"), kept = new JSONArray();
    if (old != null)
      for (int i = 0; i < old.length(); i++)
        if (!removed.contains(old.optString(i))) kept.put(old.optString(i));
    g.put("items", kept);
  }

  static void merge(JSONObject g, Set<String> added) throws JSONException {
    Set<String> all = new LinkedHashSet<>();
    JSONArray old = g.optJSONArray("items");
    if (old != null) for (int i = 0; i < old.length(); i++) all.add(old.optString(i));
    all.addAll(added);
    g.put("items", new JSONArray(all));
  }
}
