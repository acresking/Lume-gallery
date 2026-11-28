package il.luma.gallery;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;

/**
 * Local automatic and manually requested organisation. Names are user supplied; GPS comes only from
 * image EXIF.
 */
final class PeoplePlaces {
  final ModernActivity a;

  PeoplePlaces(ModernActivity a) {
    this.a = a;
  }

  void open(String kind) {
    final AlertDialog[] opened = {null};
    LinearLayout c = a.column();
    c.addView(a.text(kind.equals("person") ? "אנשים" : "מקומות", 25));
    c.addView(
        a.text(
            kind.equals("person")
                ? "קבוצות לפי דמיון בין פנים, לא זיהוי זהות. אפשר לתת שם, לשנות ולתקן קבוצה."
                : "רק תמונות עם מיקום GPS בקובץ. אין מעקב אחר מיקום הטלפון. שמות מקומות נפתרים"
                    + " באמצעות שירות Android, שעשוי להעביר את הקואורדינטות לשרת. אפשר לכבות שמות"
                    + " בהגדרות.",
            13));
    a.action(
        c,
        kind.equals("person") ? "סרוק פנים לקבוצות" : "סרוק נתוני מיקום",
        () -> {
          if (opened[0] != null) opened[0].dismiss();
          if (kind.equals("person")) scanPeople();
          else scanPlaces();
        });
    if (kind.equals("place"))
      a.action(
          c,
          "המפה שלי · תמונות לפי מקום",
          () -> {
            if (opened[0] != null) opened[0].dismiss();
            a.openPlacesMap();
          });
    TextView liveStatus = a.text(AutoOrganizeJob.status(a), 12);
    c.addView(liveStatus);
    if (kind.equals("person")
        && (!LocalModels.installed(a, "faces") || !LocalModels.installed(a, "facegroup")))
      a.action(c, "להפעלת זיהוי אנשים אוטומטי · התקן Lume AI", () -> a.ai.showManager());
    else
      c.addView(
          a.text(
              "הסידור מתעדכן אוטומטית כשהרכיבים וההרשאות זמינים. אפשר להמשיך להשתמש בגלריה.", 13));
    LinearLayout groups = a.column();
    c.addView(groups);
    drawGroups(groups, kind, opened);
    a.action(
        c,
        kind.equals("person") ? "אפס קבוצות פנים ונתוני קיבוץ" : "אפס מקומות",
        () ->
            new PrivacyDialogBuilder(a)
                .setMessage("למחוק קבוצות ולכבות את הסידור האוטומטי שלהן? קובצי התמונות לא ישתנו.")
                .setPositiveButton(
                    "אפס קבוצות",
                    (d, n) -> {
                      if (OrganizeEngine.RUNNING.get() || a.ai.busy) {
                        a.message("סריקה פעילה", "עצור את הסריקה או המתן לסיומה לפני איפוס קבוצות");
                        return;
                      }
                      JSONArray current = a.personalAlbums();
                      for (int i = current.length() - 1; i >= 0; i--)
                        if (current.optJSONObject(i).optString("kind").equals(kind))
                          current.remove(i);
                      var edit =
                          a.prefs
                              .edit()
                              .putString("personalAlbums", PrivateData.encrypt(current.toString()));
                      if (kind.equals("person")) {
                        edit.remove("faceProfiles").remove("faceSamples").remove("faceCoverQuality").remove("faceGroupingSchema")
                            .remove("peopleScanned")
                            .putBoolean("autoPeople", false);
                        PrivateData.clearCovers(a);
                      } else edit.remove("placesScanned").putBoolean("autoPlaces", false);
                      edit.apply();
                      AutoOrganizeJob.schedule(a);
                      if (opened[0] != null) opened[0].dismiss();
                      a.render();
                    })
                .setNegativeButton("ביטול", null)
                .show());
    ScrollView scroll = new ScrollView(a);
    scroll.addView(c);
    opened[0] = new PrivacyDialogBuilder(a).setView(scroll).setPositiveButton("סגירה", null).show();
    android.os.Handler updates = new android.os.Handler(android.os.Looper.getMainLooper());
    String[] previous = {a.prefs.getString("personalAlbums", "[]")};
    Runnable refresh =
        new Runnable() {
          public void run() {
            if (a.destroyed || opened[0] == null || !opened[0].isShowing()) return;
            liveStatus.setText(AutoOrganizeJob.status(a));
            String current = a.prefs.getString("personalAlbums", "[]");
            if (!current.equals(previous[0])) {
              previous[0] = current;
              drawGroups(groups, kind, opened);
            }
            updates.postDelayed(this, 1800);
          }
        };
    opened[0].setOnDismissListener(d -> updates.removeCallbacksAndMessages(null));
    updates.postDelayed(refresh, 1800);
    if (a.automaticPeople != null) a.automaticPeople.maybe();
  }

  void drawGroups(LinearLayout c, String kind, AlertDialog[] opened) {
    c.removeAllViews();
    JSONArray albums = a.personalAlbums();
    LinearLayout row = null;
    int cells = 0;
    for (int i = 0; i < albums.length(); i++) {
      JSONObject item = albums.optJSONObject(i);
      if (item == null || !item.optString("kind").equals(kind)) continue;
      if (cells % 2 == 0) {
        row = a.row();
        c.addView(row);
      }
      String name = item.optString("name");
      int count = item.optJSONArray("items") == null ? 0 : item.optJSONArray("items").length();
      LinearLayout cell = a.column();
      cell.setGravity(android.view.Gravity.CENTER);
      cell.setPadding(a.dp(10), a.dp(14), a.dp(10), a.dp(14));
      ImageView cover = new ImageView(a);
      cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
      android.graphics.drawable.GradientDrawable shape = a.rounded(a.card, 24);
      if (kind.equals("person")) shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
      cover.setBackground(shape);
      cover.setClipToOutline(true);
      cover.setImageDrawable(new GalleryIcons(kind.equals("person") ? "people" : "place",a.accent));
      if(kind.equals("person")) {
        String groupId=item.optString("id");cover.setTag(groupId);
        a.thumbs.execute(()->{Bitmap faceCover=PrivateData.cover(a,groupId);a.runOnUiThread(()->{if(!a.destroyed && groupId.equals(cover.getTag()) && faceCover!=null)cover.setImageBitmap(faceCover);});});
        cell.setContentDescription(name+" · "+count+" תמונות · לחיצה ארוכה לאיחוד קבוצות");
        cell.setOnLongClickListener(v->{PeopleMerge.choose(a,groupId,()->drawGroups(c,kind,opened));return true;});
      }
      cell.addView(cover, new LinearLayout.LayoutParams(a.dp(104), a.dp(104)));
      TextView title =
          a.label(
              kind.equals("place") && name.startsWith("מקום ") ? "מיקום שמור · שם בטעינה" : name,
              15,
              a.ink,
              true);
      if (kind.equals("place")) {
        String coordinate = item.optString("id").replace("place:", "");
        // Group identifiers are rounded coordinates; keep IDs/membership unchanged.
        a.io.execute(
            () -> {
              try {
                String[] point = coordinate.split(",");
                if (point.length != 2) return;
                String resolved =
                    LocationNames.resolve(
                        a,
                        item.has("latitude") ? item.optDouble("latitude") : Double.parseDouble(point[0].trim()),
                        item.has("longitude") ? item.optDouble("longitude") : Double.parseDouble(point[1].trim()));
                a.runOnUiThread(
                    () -> {
                      if (!a.destroyed && name.startsWith("מקום ")) {
                        if(resolved.isBlank()){title.setText("מיקום שמור · ללא שם כרגע");return;}
                        title.setText(resolved);
                        a.placeDisplayNames.put(name, resolved);
                      }
                    });
              } catch (Exception ignored) {
              }
            });
      }
      title.setGravity(android.view.Gravity.CENTER);
      title.setMaxLines(2);
      cell.addView(title);
      TextView subtitle = a.label(count + " תמונות", 12, a.ink, false);
      subtitle.setGravity(android.view.Gravity.CENTER);
      cell.addView(subtitle);
      cell.setOnClickListener(
          v -> {
            if (opened[0] != null) opened[0].dismiss();
            a.openNamedAlbum(name);
          });
      row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
      cells++;
    }
    if (cells % 2 == 1)
      row.addView(new android.view.View(a), new LinearLayout.LayoutParams(0, 1, 1));
  }

  void scanPeople() {
    if (!a.ai.require("faces") || !a.ai.require("facegroup")) return;
    scan(true, false);
  }

  void scanPlaces() {
    if (a.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
      a.requestPermissions(new String[] {Manifest.permission.ACCESS_MEDIA_LOCATION}, 44);
      return;
    }
    scan(false, true);
  }

  void scan(boolean faces, boolean places) {
    a.ai.task(
        faces ? "מקבץ פנים במכשיר" : "קורא מיקום מתמונות",
        () -> {
          OrganizeEngine.Result result =
              OrganizeEngine.run(a, a.ai.cancel, a.prefs.getInt("aiBatch", 200), faces, places);
          a.ai.ui(
              () -> {
                a.render();
                a.message(
                    "סידור הגלריה",
                    result.message + "\nהקיבוץ הוא הערכה; אפשר לתקן שמות וחברות באלבום.");
                AutoOrganizeJob.schedule(a);
              });
        });
  }
}
