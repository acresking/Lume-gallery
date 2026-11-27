package il.luma.gallery;

import android.graphics.*;
import java.util.*;

/** Small bounded renderer. Only creates new pixels; never writes to source images. */
final class CollageEngine {
  static int[] dimensions(int edge, int aspect) {
    if (edge < 1 || edge > 3072) throw new IllegalArgumentException("גודל קולאז׳ אינו תקין");
    return aspect == 1
        ? new int[] {edge, Math.max(1, edge * 3 / 4)}
        : aspect == 2 ? new int[] {Math.max(1, edge * 9 / 16), edge} : new int[] {edge, edge};
  }

  static java.util.List<RectF> cells(int count, int width, int height, int layout, float spacing) {
    if (count < 2 || count > 6 || width < 1 || height < 1)
      throw new IllegalArgumentException("בחר בין 2 ל־6 תמונות");
    float gap = Math.min(width, height) * Math.max(0, Math.min(.04f, spacing));
    int columns = layout == 1 ? 1 : layout == 2 ? count : count >= 5 ? 3 : 2;
    int rows = (count + columns - 1) / columns;
    float cellH = (height - (rows + 1) * gap) / rows;
    ArrayList<RectF> out = new ArrayList<>();
    for (int row = 0, index = 0; row < rows; row++) {
      int cols = Math.min(columns, count - index);
      float cellW = (width - (cols + 1) * gap) / cols;
      for (int col = 0; col < cols; col++, index++) {
        float x = gap + col * (cellW + gap), y = gap + row * (cellH + gap);
        out.add(new RectF(x, y, x + cellW, y + cellH));
      }
    }
    return out;
  }

  static Bitmap render(
      java.util.List<Bitmap> images,
      int edge,
      int aspect,
      int layout,
      float spacing,
      int background,
      boolean fit) {
    int[] size = dimensions(edge, aspect);
    java.util.List<RectF> cells = cells(images.size(), size[0], size[1], layout, spacing);
    Bitmap output = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(output);
    canvas.drawColor(background);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    for (int i = 0; i < images.size(); i++) {
      Bitmap source = images.get(i);
      if (source == null || source.isRecycled()) {
        output.recycle();
        throw new IllegalArgumentException("תמונה אינה זמינה");
      }
      RectF cell = cells.get(i);
      float scale =
          fit
              ? Math.min(cell.width() / source.getWidth(), cell.height() / source.getHeight())
              : Math.max(cell.width() / source.getWidth(), cell.height() / source.getHeight());
      float w = source.getWidth() * scale, h = source.getHeight() * scale;
      RectF target =
          new RectF(
              cell.centerX() - w / 2,
              cell.centerY() - h / 2,
              cell.centerX() + w / 2,
              cell.centerY() + h / 2);
      canvas.save();
      canvas.clipRect(cell);
      canvas.drawBitmap(source, null, target, paint);
      canvas.restore();
    }
    return output;
  }
}
