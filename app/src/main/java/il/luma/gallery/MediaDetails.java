package il.luma.gallery;

import android.content.Context;
import android.media.*;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.util.*;

/** Best-effort local metadata reader. Does not modify or upload media. */
final class MediaDetails {
  static LinkedHashMap<String, String> read(Context context, MainActivity.Media media) {
    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    if (media.video()) readVideo(context, media, out);
    else readPhoto(context, media, out);
    return out;
  }

  static void add(Map<String, String> out, String name, String value) {
    if (value != null && !value.trim().isEmpty() && !value.equals("null"))
      out.put(name, value.trim());
  }

  static String cleanCamera(String make, String model) {
    if (model == null || model.isBlank()) return make;
    if (make == null
        || make.isBlank()
        || model.toLowerCase(Locale.ROOT).startsWith(make.toLowerCase(Locale.ROOT))) return model;
    return make + " " + model;
  }

  static String exposure(double seconds) {
    if (!Double.isFinite(seconds) || seconds <= 0) return null;
    return seconds < .5
        ? String.format(Locale.ROOT, "1/%.0f שנ׳", 1 / seconds)
        : String.format(Locale.ROOT, "%.3f שנ׳", seconds);
  }

  static String duration(long ms) {
    if (ms < 0) return null;
    long total = ms / 1000, h = total / 3600, m = (total % 3600) / 60, s = total % 60;
    return h > 0
        ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        : String.format(Locale.ROOT, "%d:%02d", m, s);
  }

  static void readPhoto(Context c, MainActivity.Media media, Map<String, String> out) {
    android.net.Uri source = media.uri;
    boolean location = c.getSharedPreferences("luma", 0).getBoolean("detailsLocation", true);
    if (location
        && "media".equals(source.getAuthority())
        && c.checkSelfPermission(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
      try {
        source = android.provider.MediaStore.setRequireOriginal(source);
      } catch (Exception ignored) {
      }
    }
    try (InputStream input = open(c, source, media.uri)) {
      if (input == null) return;
      ExifInterface exif = new ExifInterface(input);
      add(
          out,
          "מצלמה",
          cleanCamera(
              exif.getAttribute(ExifInterface.TAG_MAKE),
              exif.getAttribute(ExifInterface.TAG_MODEL)));
      add(out, "עדשה", exif.getAttribute(ExifInterface.TAG_LENS_MODEL));
      double aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0);
      if (aperture > 0) add(out, "צמצם", String.format(Locale.ROOT, "f/%.1f", aperture));
      add(out, "זמן חשיפה", exposure(exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0)));
      add(out, "ISO", exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY));
      double focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0);
      if (focal > 0) add(out, "אורך מוקד", String.format(Locale.ROOT, "%.1f mm", focal));
      add(out, "תאריך צילום בקובץ", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL));
      String[][] fields = {
        {"תוכנה", ExifInterface.TAG_SOFTWARE},
        {"זמן דיגיטציה", ExifInterface.TAG_DATETIME_DIGITIZED},
        {"אזור זמן צילום", ExifInterface.TAG_OFFSET_TIME_ORIGINAL},
        {"מוקד שקול 35 מ״מ", ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM},
        {"כיוון תמונה (EXIF)", ExifInterface.TAG_ORIENTATION}
      };
      for (String[] field : fields) add(out, field[0], exif.getAttribute(field[1]));
      if (exif.hasAttribute(ExifInterface.TAG_WHITE_BALANCE))
        add(
            out,
            "איזון לבן",
            exif.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, 0) == 0 ? "אוטומטי" : "ידני");
      if (exif.hasAttribute(ExifInterface.TAG_FLASH))
        add(
            out,
            "מבזק",
            (exif.getAttributeInt(ExifInterface.TAG_FLASH, 0) & 1) == 1 ? "הופעל" : "לא הופעל");
      if (location) {
        double[] gps = exif.getLatLong();
        if (gps != null)
          add(out, "מיקום GPS", String.format(Locale.ROOT, "%.6f, %.6f", gps[0], gps[1]));
        if (gps != null) {
          String place = LocationNames.resolve(c, gps[0], gps[1]);
          if (!place.isBlank()) add(out, "מיקום · עיר ומדינה", place);
        }
        double altitude = exif.getAltitude(Double.NaN);
        if (Double.isFinite(altitude))
          add(out, "גובה", String.format(Locale.ROOT, "%.1f m", altitude));
      }
      if (out.isEmpty()) add(out, "נתוני צילום", "לא נמצאו נתוני מצלמה בקובץ הזה");
    } catch (Exception error) {
      add(out, "נתוני צילום", "לא ניתן לקרוא את נתוני הצילום בקובץ הזה");
    }
  }

  private static InputStream open(Context c, android.net.Uri source, android.net.Uri fallback)
      throws IOException {
    try {
      return c.getContentResolver().openInputStream(source);
    } catch (Exception error) {
      if (!source.equals(fallback)) return c.getContentResolver().openInputStream(fallback);
      if (error instanceof IOException) throw (IOException) error;
      throw new IOException(error);
    }
  }

  static void readVideo(Context c, MainActivity.Media media, Map<String, String> out) {
    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
    try {
      android.net.Uri source = media.uri;
      if (c.getSharedPreferences("luma", 0).getBoolean("detailsLocation", true)
          && "media".equals(source.getAuthority())
          && c.checkSelfPermission(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
              == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        try {
          source = android.provider.MediaStore.setRequireOriginal(source);
        } catch (Exception ignored) {
        }
      }
      try {
        retriever.setDataSource(c, source);
      } catch (Exception e) {
        if (!source.equals(media.uri)) retriever.setDataSource(c, media.uri);
        else throw e;
      }
      String ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
      if (ms != null) add(out, "משך", duration(Long.parseLong(ms)));
      String bitRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
      if (bitRate != null)
        add(
            out,
            "קצב נתונים",
            String.format(Locale.ROOT, "%.1f Mbps", Long.parseLong(bitRate) / 1_000_000d));
      if (c.getSharedPreferences("luma", 0).getBoolean("detailsLocation", true)) {
        double[] gps =
            LocationNames.parseIso6709(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION));
        if (gps != null) {
          add(out, "מיקום GPS", String.format(Locale.ROOT, "%.6f, %.6f", gps[0], gps[1]));
          String place = LocationNames.resolve(c, gps[0], gps[1]);
          if (!place.isBlank()) add(out, "מיקום · עיר ומדינה", place);
        }
      }
      String rotation =
          retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
      if (rotation != null) add(out, "סיבוב", rotation + "°");
    } catch (Exception error) {
      add(out, "פרטי סרטון", "חלק מהנתונים אינם זמינים בקובץ הזה");
    } finally {
      try {
        retriever.release();
      } catch (Exception ignored) {
      }
    }
    MediaExtractor extractor = new MediaExtractor();
    try {
      extractor.setDataSource(c, media.uri, null);
      for (int i = 0; i < extractor.getTrackCount(); i++) {
        MediaFormat f = extractor.getTrackFormat(i);
        String mime = f.getString(MediaFormat.KEY_MIME);
        if (mime == null) continue;
        if (mime.startsWith("audio/")) {
          add(out, "קידוד שמע", mime);
          if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE))
            add(out, "קצב דגימת שמע", f.getInteger(MediaFormat.KEY_SAMPLE_RATE) + " Hz");
          if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
            add(out, "ערוצי שמע", String.valueOf(f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)));
          continue;
        }
        if (!mime.startsWith("video/")) continue;
        add(
            out,
            "קידוד וידאו",
            mime.equals("video/hevc")
                ? "H.265 / HEVC"
                : mime.equals("video/avc") ? "H.264 / AVC" : mime);
        if (f.containsKey(MediaFormat.KEY_FRAME_RATE)) {
          Number rate = f.getNumber(MediaFormat.KEY_FRAME_RATE);
          if (rate != null && rate.doubleValue() > 0)
            add(
                out,
                "קצב פריימים בקובץ",
                String.format(Locale.ROOT, "%.2f FPS", rate.doubleValue()));
        }
        if (f.containsKey(MediaFormat.KEY_WIDTH) && f.containsKey(MediaFormat.KEY_HEIGHT))
          add(
              out,
              "רזולוציית וידאו",
              f.getInteger(MediaFormat.KEY_WIDTH) + " × " + f.getInteger(MediaFormat.KEY_HEIGHT));
      }
    } catch (Exception ignored) {
    } finally {
      extractor.release();
    }
  }
}
