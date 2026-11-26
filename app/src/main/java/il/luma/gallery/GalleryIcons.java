package il.luma.gallery;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import androidx.core.graphics.PathParser;
import java.util.*;

/** A single consistent 24dp outline icon family; no emoji or font glyph icons. */
final class GalleryIcons extends Drawable {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final String name;
  private final int color;
  private static final Map<String, String> PATHS = new HashMap<>();

  static {
    PATHS.put(
        "people",
        "M9,3 A4,4 0,1 0,9,11 A4,4 0,1 0,9,3 M2,21 L2,18 C2,12 16,12 16,18 L16,21 M17,4 C23,4 23,11"
            + " 18,11 M19,15 C23,15 23,18 23,21");
    PATHS.put(
        "place",
        "M12,22 C8,17 3,12 3,8 A9,9 0,1 1,21,8 C21,12 16,17 12,22 Z M12,5 A3,3 0,1 0,12,11 A3,3 0,1"
            + " 0,12,5");
    PATHS.put(
        "photos",
        "M5,3 L19,3 Q21,3 21,5 L21,19 Q21,21 19,21 L5,21 Q3,21 3,19 L3,5 Q3,3 5,3 M3,16 L9,10"
            + " L15,16 L18,13 L21,16 M15,7 L15.01,7");
    PATHS.put(
        "albums",
        "M3,8 L3,19 Q3,21 5,21 L17,21 M8,3 L19,3 Q21,3 21,5 L21,16 Q21,18 19,18 L8,18 Q6,18 6,16"
            + " L6,5 Q6,3 8,3 M6,13 L11,8 L16,13 L18,11 L21,14");
    PATHS.put(
        "clean",
        "M12,3 L13.8,8.2 L19,10 L13.8,11.8 L12,17 L10.2,11.8 L5,10 L10.2,8.2 Z M20,16 L20,22 M17,19"
            + " L23,19 M4,2 L4,6 M2,4 L6,4");
    PATHS.put(
        "settings",
        "M9,3 L15,3 L16,6 L19,7 L22,11 L20,14 L20,17 L16,20 L13,20 L10,22 L6,19 L6,16 L3,13 L4,8"
            + " L7,7 Z M12,8 A4,4 0,1 0,12,16 A4,4 0,1 0,12,8");
    PATHS.put("search", "M10.5,3 A7.5,7.5 0,1 0,10.5,18 A7.5,7.5 0,1 0,10.5,3 M16,16 L22,22");
    PATHS.put("plus", "M12,5 L12,19 M5,12 L19,12");
    PATHS.put("close", "M6,6 L18,18 M18,6 L6,18");
    PATHS.put("back", "M14,5 L7,12 L14,19 M7,12 L21,12");
    PATHS.put("more", "M5,12 L5.01,12 M12,12 L12.01,12 M19,12 L19.01,12");
    PATHS.put("heart", "M12,21 L3,12 C-3,3 7,-1 12,6 C17,-1 27,3 21,12 Z");
    PATHS.put(
        "share", "M12,16 L12,3 M7,8 L12,3 L17,8 M5,13 L5,20 Q5,22 7,22 L17,22 Q19,22 19,20 L19,13");
    PATHS.put("edit", "M4,16 L16,4 L20,8 L8,20 L3,21 Z M13,7 L17,11");
    PATHS.put(
        "trash",
        "M4,6 L20,6 M9,6 L9,3 L15,3 L15,6 M6,6 L7,21 L17,21 L18,6 M10,10 L10,17 M14,10 L14,17");
    PATHS.put("archive", "M3,4 L21,4 L21,9 L3,9 Z M5,9 L5,21 L19,21 L19,9 M10,13 L14,13");
    PATHS.put("info", "M12,2 A10,10 0,1 0,12,22 A10,10 0,1 0,12,2 M12,10 L12,17 M12,7 L12.01,7");
    PATHS.put("check", "M5,12 L10,17 L20,6");
    PATHS.put("select", "M12,2 A10,10 0,1 0,12,22 A10,10 0,1 0,12,2 M7,12 L10,15 L17,8");
    PATHS.put(
        "grid",
        "M3,3 L9,3 L9,9 L3,9 Z M15,3 L21,3 L21,9 L15,9 Z M3,15 L9,15 L9,21 L3,21 Z M15,15 L21,15"
            + " L21,21 L15,21 Z");
    PATHS.put(
        "video",
        "M4,3 L20,3 Q22,3 22,5 L22,19 Q22,21 20,21 L4,21 Q2,21 2,19 L2,5 Q2,3 4,3 M10,8 L17,12"
            + " L10,16 Z");
    PATHS.put(
        "screenshot",
        "M8,2 L3,2 L3,7 M16,2 L21,2 L21,7 M21,17 L21,22 L16,22 M8,22 L3,22 L3,17 M7,7 L17,7 L17,17"
            + " L7,17 Z");
    PATHS.put(
        "folder",
        "M3,6 Q3,4 5,4 L10,4 L12,7 L19,7 Q21,7 21,9 L21,19 Q21,21 19,21 L5,21 Q3,21 3,19 Z");
    PATHS.put("play", "M9,5 L20,12 L9,19 Z");
    PATHS.put("sort", "M4,6 L20,6 M7,12 L17,12 M10,18 L14,18");
    PATHS.put("wallpaper", "M5,3 L19,3 L19,21 L5,21 Z M5,17 L10,11 L14,15 L16,13 L19,16");
    PATHS.put("clock", "M12,2 A10,10 0,1 0,12,22 A10,10 0,1 0,12,2 M12,6 L12,12 L16,14");
  }

  GalleryIcons(String name, int color) {
    this.name = name;
    this.color = color;
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(1.7f);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
  }

  @Override
  public void draw(Canvas c) {
    c.save();
    c.translate(getBounds().left, getBounds().top);
    c.scale(getBounds().width() / 24f, getBounds().height() / 24f);
    paint.setColor(color);
    c.drawPath(
        PathParser.createPathFromPathData(PATHS.getOrDefault(name, PATHS.get("photos"))), paint);
    c.restore();
  }

  @Override
  public void setAlpha(int alpha) {
    paint.setAlpha(alpha);
  }

  @Override
  public void setColorFilter(ColorFilter filter) {
    paint.setColorFilter(filter);
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
