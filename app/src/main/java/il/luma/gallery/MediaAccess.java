package il.luma.gallery;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.util.*;

final class MediaAccess {
  static boolean canRead(Context c) {
    return Build.VERSION.SDK_INT >= 33
        ? (c.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)
                == PackageManager.PERMISSION_GRANTED
            || (Build.VERSION.SDK_INT >= 34
                && c.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                    == PackageManager.PERMISSION_GRANTED))
        : c.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED;
  }

  static ArrayList<MainActivity.Media> images(Context c) {
    ArrayList<MainActivity.Media> list = new ArrayList<>();
    if (!canRead(c)) return list;
    String[] fields = {
      "_id",
      "_display_name",
      "relative_path",
      "mime_type",
      "date_added",
      "_size",
      "datetaken",
      "width",
      "height",
      "date_modified"
    };
    try (Cursor cursor =
        c.getContentResolver()
            .query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                fields,
                "is_trashed=0",
                null,
                "date_added DESC")) {
      if (cursor != null)
        while (cursor.moveToNext()) {
          MainActivity.Media m = new MainActivity.Media();
          m.uri =
              ContentUris.withAppendedId(
                  MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0));
          m.name = cursor.getString(1);
          m.path = cursor.getString(2);
          m.mime = cursor.getString(3);
          m.date = cursor.getLong(6) > 0 ? cursor.getLong(6) : cursor.getLong(4) * 1000L;
          m.size = cursor.getLong(5);
          m.width = cursor.getInt(7);
          m.height = cursor.getInt(8);
          m.modified = cursor.getLong(9);
          if (m.mime != null) list.add(m);
        }
    }
    return list;
  }

  static Bitmap decode(Context c, Uri uri, int max) throws Exception {
    return ImageDecoder.decodeBitmap(
        ImageDecoder.createSource(c.getContentResolver(), uri),
        (decoder, info, source) -> {
          int w = info.getSize().getWidth(), h = info.getSize().getHeight();
          float factor = Math.min(1, max / (float) Math.max(w, h));
          decoder.setTargetSize(Math.max(1, (int) (w * factor)), Math.max(1, (int) (h * factor)));
          decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
          decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
        });
  }
}
